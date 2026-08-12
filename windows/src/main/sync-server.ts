import { WebSocketServer, WebSocket } from 'ws'
import { Bonjour } from 'bonjour-service'
import { ipcMain, BrowserWindow } from 'electron'
import Database from 'better-sqlite3'
import { app } from 'electron'
import { join } from 'path'
import { networkInterfaces } from 'os'
import { execSync } from 'child_process'

let wss: WebSocketServer | null = null
let bonjour: Bonjour | null = null
let bonjourService: any = null
const SYNC_PORT = 9847
const SERVICE_NAME = 'wellness-companion-sync'

function getDb(): Database.Database {
  const dbPath = join(app.getPath('userData'), 'wellness.db')
  return new Database(dbPath, { readonly: false })
}

function broadcastSyncStatus(status: string, detail?: string): void {
  BrowserWindow.getAllWindows().forEach(win => {
    win.webContents.send('sync:status', { status, detail })
  })
}

function getLocalIp(): string {
  const nets = networkInterfaces()
  for (const name of Object.keys(nets)) {
    for (const net of nets[name] || []) {
      if (net.family === 'IPv4' && !net.internal) {
        return net.address
      }
    }
  }
  return '127.0.0.1'
}

function ensureFirewallRule(): void {
  try {
    // Check if rule already exists
    const check = execSync('netsh advfirewall firewall show rule name="Wellness Companion Sync"', { encoding: 'utf8', stdio: ['pipe', 'pipe', 'pipe'] })
    if (check.includes('Wellness Companion Sync')) return
  } catch {
    // Rule doesn't exist, create it
  }
  try {
    execSync(`netsh advfirewall firewall add rule name="Wellness Companion Sync" dir=in action=allow protocol=TCP localport=${SYNC_PORT}`, { stdio: 'ignore' })
    console.log('Firewall rule added for port', SYNC_PORT)
  } catch (err) {
    console.warn('Could not add firewall rule (needs admin):', err)
  }
}

export function startSyncServer(): void {
  if (wss) return

  ensureFirewallRule()

  wss = new WebSocketServer({ port: SYNC_PORT })
  broadcastSyncStatus('listening', `Port ${SYNC_PORT}`)

  wss.on('connection', (ws) => {
    broadcastSyncStatus('connected', 'Android device connected')

    ws.on('message', (raw) => {
      try {
        const msg = JSON.parse(raw.toString())
        handleSyncMessage(ws, msg)
      } catch (err) {
        console.error('Sync message parse error:', err)
        ws.send(JSON.stringify({ type: 'error', message: 'Invalid JSON' }))
      }
    })

    ws.on('close', () => {
      broadcastSyncStatus('listening', 'Device disconnected')
    })

    ws.on('error', (err) => {
      console.error('Sync WebSocket error:', err)
    })

    // Send a hello with our latest sync state
    ws.send(JSON.stringify({ type: 'hello', version: 2 }))
  })

  wss.on('error', (err) => {
    console.error('Sync server error:', err)
    broadcastSyncStatus('error', err.message)
  })

  // Advertise via mDNS
  try {
    bonjour = new Bonjour()
    bonjourService = bonjour.publish({
      name: SERVICE_NAME,
      type: 'http',
      port: SYNC_PORT,
      txt: { app: 'wellness-companion', version: '1' }
    })
    console.log(`mDNS: advertising ${SERVICE_NAME} on port ${SYNC_PORT}`)
  } catch (err) {
    console.error('mDNS publish failed:', err)
  }
}

function handleSyncMessage(ws: WebSocket, msg: any): void {
  const db = getDb()
  try {
    switch (msg.type) {
      case 'push': {
        // Android is pushing entries to us
        const entries = msg.entries || []
        let inserted = 0
        let updated = 0

        const insertStmt = db.prepare(`
          INSERT OR IGNORE INTO entries (id, category, timestamp, date, data, version, modified_at, synced)
          VALUES (?, ?, ?, ?, ?, ?, ?, 1)
        `)
        const updateStmt = db.prepare(`
          UPDATE entries SET data = ?, version = ?, modified_at = ?, synced = 1
          WHERE id = ? AND modified_at < ?
        `)

        const tx = db.transaction(() => {
          for (const e of entries) {
            const existing: any = db.prepare('SELECT id, modified_at FROM entries WHERE id = ?').get(e.id)
            if (!existing) {
              insertStmt.run(e.id, e.category, e.timestamp, e.date, e.data, e.version, e.modified_at)
              inserted++
            } else if (e.modified_at > existing.modified_at) {
              updateStmt.run(e.data, e.version, e.modified_at, e.id, existing.modified_at)
              updated++
            }
          }
        })
        tx()

        // Also sync auxiliary tables
        if (msg.hobbies) syncAuxTable(db, 'hobbies', msg.hobbies, ['id', 'name', 'color', 'created_at'])
        if (msg.people) syncAuxTable(db, 'people', msg.people, ['id', 'name', 'created_at'])
        if (msg.chore_templates) syncAuxTable(db, 'chore_templates', msg.chore_templates, ['id', 'name', 'category', 'recurrence', 'created_at'])

        ws.send(JSON.stringify({ type: 'push_ack', inserted, updated }))
        broadcastSyncStatus('synced', `Received: ${inserted} new, ${updated} updated`)
        break
      }

      case 'pull': {
        // Android is requesting our entries since a given timestamp
        const since = msg.since || 0
        const entries = db.prepare(
          'SELECT * FROM entries WHERE modified_at > ? ORDER BY modified_at ASC'
        ).all(since)

        const hobbies = db.prepare('SELECT * FROM hobbies').all()
        const people = db.prepare('SELECT * FROM people').all()
        const choreTemplates = db.prepare('SELECT * FROM chore_templates').all()

        ws.send(JSON.stringify({
          type: 'pull_response',
          entries,
          hobbies,
          people,
          chore_templates: choreTemplates
        }))
        broadcastSyncStatus('synced', `Sent ${entries.length} entries`)
        break
      }

      case 'full_sync': {
        // Bidirectional: receive their entries, send ours
        // 1. Accept their data
        const theirEntries = msg.entries || []
        let inserted = 0
        let updated = 0

        const insertStmt = db.prepare(`
          INSERT OR IGNORE INTO entries (id, category, timestamp, date, data, version, modified_at, synced)
          VALUES (?, ?, ?, ?, ?, ?, ?, 1)
        `)
        const updateStmt = db.prepare(`
          UPDATE entries SET data = ?, version = ?, modified_at = ?, synced = 1
          WHERE id = ? AND modified_at < ?
        `)

        const tx = db.transaction(() => {
          for (const e of theirEntries) {
            const existing: any = db.prepare('SELECT id, modified_at FROM entries WHERE id = ?').get(e.id)
            if (!existing) {
              insertStmt.run(e.id, e.category, e.timestamp, e.date, e.data, e.version, e.modified_at)
              inserted++
            } else if (e.modified_at > existing.modified_at) {
              updateStmt.run(e.data, e.version, e.modified_at, e.id, existing.modified_at)
              updated++
            }
          }
        })
        tx()

        if (msg.hobbies) syncAuxTable(db, 'hobbies', msg.hobbies, ['id', 'name', 'color', 'created_at'])
        if (msg.people) syncAuxTable(db, 'people', msg.people, ['id', 'name', 'created_at'])
        if (msg.chore_templates) syncAuxTable(db, 'chore_templates', msg.chore_templates, ['id', 'name', 'category', 'recurrence', 'created_at'])

        // 2. Send our data back
        const ourEntries = db.prepare('SELECT * FROM entries ORDER BY modified_at ASC').all()
        const hobbies = db.prepare('SELECT * FROM hobbies').all()
        const people = db.prepare('SELECT * FROM people').all()
        const choreTemplates = db.prepare('SELECT * FROM chore_templates').all()

        ws.send(JSON.stringify({
          type: 'full_sync_response',
          entries: ourEntries,
          hobbies, people, chore_templates: choreTemplates,
          received: { inserted, updated }
        }))
        broadcastSyncStatus('synced', `Full sync: +${inserted} new, ${updated} updated`)
        break
      }

      default:
        ws.send(JSON.stringify({ type: 'error', message: `Unknown type: ${msg.type}` }))
    }
  } finally {
    db.close()
  }
}

function syncAuxTable(db: Database.Database, table: string, rows: any[], columns: string[]): void {
  const placeholders = columns.map(() => '?').join(', ')
  const stmt = db.prepare(`INSERT OR IGNORE INTO ${table} (${columns.join(', ')}) VALUES (${placeholders})`)
  const tx = db.transaction(() => {
    for (const row of rows) {
      stmt.run(...columns.map(c => row[c] ?? null))
    }
  })
  tx()
}

export function stopSyncServer(): void {
  if (bonjourService) { try { bonjourService.stop?.() } catch {} bonjourService = null }
  if (bonjour) { try { bonjour.destroy() } catch {} bonjour = null }
  if (wss) {
    wss.clients.forEach(c => c.close())
    wss.close()
    wss = null
  }
}

export function registerSyncHandlers(): void {
  ipcMain.handle('sync:getStatus', () => {
    return wss ? 'listening' : 'stopped'
  })
  ipcMain.handle('sync:getPort', () => SYNC_PORT)
  ipcMain.handle('sync:getLocalIp', () => getLocalIp())
}

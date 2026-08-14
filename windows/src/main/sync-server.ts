import { WebSocketServer, WebSocket } from 'ws'
import { Bonjour } from 'bonjour-service'
import { ipcMain, BrowserWindow } from 'electron'
import type Database from 'better-sqlite3'
import { networkInterfaces } from 'os'
import { randomInt, timingSafeEqual } from 'crypto'
import { getDatabase } from './database'

let wss: WebSocketServer | null = null
let bonjour: Bonjour | null = null
let bonjourService: any = null
let heartbeat: NodeJS.Timeout | null = null

const SYNC_PORT = 9847
const SERVICE_NAME = 'wellness-companion-sync'

/** ws default is 100 MB; a sync payload is orders of magnitude smaller. */
const MAX_PAYLOAD_BYTES = 16 * 1024 * 1024
const MAX_CONNECTIONS = 4
const MAX_ENTRIES_PER_MESSAGE = 20000
const MAX_DATA_LENGTH = 256 * 1024
const HEARTBEAT_MS = 30_000
/** Wrong codes per socket before it is dropped — blunts online guessing. */
const MAX_AUTH_ATTEMPTS = 5

const TOKEN_SETTING_KEY = 'sync.pairing_token'
/**
 * Character set pairing codes are drawn FROM — not a secret itself. Excludes
 * 0/O/1/I/L, which are the pairs people misread when copying a code off a
 * screen onto a phone.
 */
const CODE_ALPHABET = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789' // gitleaks:allow
const TOKEN_LENGTH = 8

interface PeerState {
  authed: boolean
  isAlive: boolean
  attempts: number
}

const peers = new Map<WebSocket, PeerState>()

/** Categories the desktop knows about; anything else is rejected on ingest. */
const KNOWN_CATEGORIES = new Set([
  'water', 'food', 'bathroom', 'health', 'sleep', 'emotions',
  'interactions', 'chores', 'hobbies', 'ideas', 'cycle', 'badhabits'
])

/**
 * Fixed shapes for the auxiliary tables. Previously the table and column names
 * were interpolated into the SQL from the caller; they were only ever passed
 * literals, but an allowlist removes the possibility entirely.
 */
const AUX_TABLES = {
  hobbies: ['id', 'name', 'color', 'created_at'],
  people: ['id', 'name', 'created_at', 'deleted_at'],
  chore_templates: ['id', 'name', 'category', 'recurrence', 'created_at']
} as const

type AuxTable = keyof typeof AUX_TABLES

function broadcastSyncStatus(status: string, detail?: string): void {
  BrowserWindow.getAllWindows().forEach((win) => {
    if (!win.isDestroyed()) win.webContents.send('sync:status', { status, detail })
  })
}

function getLocalIp(): string {
  const nets = networkInterfaces()
  for (const name of Object.keys(nets)) {
    for (const net of nets[name] || []) {
      if (net.family === 'IPv4' && !net.internal) return net.address
    }
  }
  return '127.0.0.1'
}

/**
 * The pairing code. Generated once and kept in the settings table so it stays
 * stable across restarts — the phone stores it after the first pairing.
 */
export function getPairingToken(): string {
  const db = getDatabase()
  const row: any = db.prepare('SELECT value FROM settings WHERE key = ?').get(TOKEN_SETTING_KEY)
  if (row?.value) return row.value

  let token = ''
  for (let i = 0; i < TOKEN_LENGTH; i++) {
    token += CODE_ALPHABET[randomInt(CODE_ALPHABET.length)]
  }
  db.prepare('INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)').run(
    TOKEN_SETTING_KEY,
    token
  )
  return token
}

export function regeneratePairingToken(): string {
  getDatabase().prepare('DELETE FROM settings WHERE key = ?').run(TOKEN_SETTING_KEY)
  // Drop every paired socket so an old code cannot keep a session alive.
  peers.forEach((state, ws) => {
    state.authed = false
    try {
      ws.close(4001, 'pairing code changed')
    } catch {
      /* already gone */
    }
  })
  return getPairingToken()
}

function tokenMatches(candidate: unknown): boolean {
  if (typeof candidate !== 'string') return false
  const expected = Buffer.from(getPairingToken().toUpperCase(), 'utf8')
  const given = Buffer.from(candidate.trim().toUpperCase().replace(/-/g, ''), 'utf8')
  // timingSafeEqual throws on a length mismatch, so compare lengths first.
  if (given.length !== expected.length) return false
  return timingSafeEqual(given, expected)
}

/**
 * Reject malformed rows before they reach SQLite. Values are parameterised, so
 * this is not about injection — it stops an unauthenticated-shaped payload from
 * poisoning the table with junk categories or unbounded blobs.
 */
function isValidEntry(e: any): boolean {
  if (!e || typeof e !== 'object') return false
  if (typeof e.id !== 'string' || e.id.length < 1 || e.id.length > 64) return false
  if (typeof e.category !== 'string' || !KNOWN_CATEGORIES.has(e.category)) return false
  if (!Number.isFinite(e.timestamp) || !Number.isFinite(e.modified_at)) return false
  if (typeof e.date !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(e.date)) return false
  if (typeof e.data !== 'string' || e.data.length > MAX_DATA_LENGTH) return false
  if (e.version != null && !Number.isFinite(e.version)) return false
  return true
}

function send(ws: WebSocket, payload: unknown): void {
  if (ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify(payload))
}

export function startSyncServer(): void {
  if (wss) return

  // The Windows Firewall rule is deliberately NOT added here. Adding an inbound
  // allow rule silently, at every startup, with no profile restriction, opened
  // this port on public and guest networks without the user ever being asked.
  // Windows raises its own prompt on first bind; that prompt is the consent.
  wss = new WebSocketServer({ port: SYNC_PORT, maxPayload: MAX_PAYLOAD_BYTES })
  broadcastSyncStatus('listening', `Port ${SYNC_PORT}`)

  wss.on('connection', (ws) => {
    if (peers.size >= MAX_CONNECTIONS) {
      try {
        ws.close(4003, 'too many connections')
      } catch {
        /* ignore */
      }
      return
    }

    peers.set(ws, { authed: false, isAlive: true, attempts: 0 })
    broadcastSyncStatus('pairing', 'Device connecting…')

    ws.on('pong', () => {
      const state = peers.get(ws)
      if (state) state.isAlive = true
    })

    ws.on('message', (raw) => {
      let msg: any
      try {
        msg = JSON.parse(raw.toString())
      } catch {
        send(ws, { type: 'error', message: 'Invalid JSON' })
        return
      }
      try {
        handleSyncMessage(ws, msg)
      } catch (err: any) {
        console.error('Sync handler error:', err)
        send(ws, { type: 'error', message: 'Internal error' })
      }
    })

    ws.on('close', () => {
      peers.delete(ws)
      broadcastSyncStatus('listening', 'Device disconnected')
    })

    ws.on('error', (err) => {
      console.error('Sync WebSocket error:', err)
      peers.delete(ws)
    })

    // Tell the client a code is required. No data moves before it authenticates.
    send(ws, { type: 'hello', version: 3, requiresAuth: true })
  })

  wss.on('error', (err) => {
    console.error('Sync server error:', err)
    broadcastSyncStatus('error', err.message)
    // Without this the server object stayed non-null after e.g. EADDRINUSE, so
    // startSyncServer() short-circuited forever and mDNS kept advertising a
    // port nothing was listening on.
    void stopSyncServer()
  })

  // Drop half-open sockets: a phone that walks out of Wi-Fi never sends a close
  // frame, so its entry in wss.clients (and its buffers) would live forever.
  heartbeat = setInterval(() => {
    peers.forEach((state, ws) => {
      if (!state.isAlive) {
        try {
          ws.terminate()
        } catch {
          /* ignore */
        }
        peers.delete(ws)
        return
      }
      state.isAlive = false
      try {
        ws.ping()
      } catch {
        /* ignore */
      }
    })
  }, HEARTBEAT_MS)

  try {
    bonjour = new Bonjour()
    bonjourService = bonjour.publish({
      name: SERVICE_NAME,
      type: 'http',
      port: SYNC_PORT,
      txt: { app: 'wellness-companion', version: '3' }
    })
    console.log(`mDNS: advertising ${SERVICE_NAME} on port ${SYNC_PORT}`)
  } catch (err) {
    console.error('mDNS publish failed:', err)
  }
}

function handleSyncMessage(ws: WebSocket, msg: any): void {
  const state = peers.get(ws)
  if (!state) return

  if (msg?.type === 'auth') {
    if (tokenMatches(msg.token)) {
      state.authed = true
      send(ws, { type: 'auth_ok' })
      broadcastSyncStatus('connected', 'Phone paired')
    } else {
      state.attempts++
      send(ws, { type: 'auth_failed', attemptsLeft: MAX_AUTH_ATTEMPTS - state.attempts })
      broadcastSyncStatus('error', 'A device supplied the wrong pairing code')
      if (state.attempts >= MAX_AUTH_ATTEMPTS) {
        try {
          ws.close(4002, 'too many failed attempts')
        } catch {
          /* ignore */
        }
      }
    }
    return
  }

  if (!state.authed) {
    send(ws, { type: 'auth_required', message: 'Send the pairing code first' })
    return
  }

  const db = getDatabase()

  switch (msg.type) {
    case 'push': {
      const { inserted, updated, rejected } = ingestEntries(db, msg.entries)
      ingestAux(db, msg)
      send(ws, { type: 'push_ack', inserted, updated, rejected })
      broadcastSyncStatus('synced', `Received: ${inserted} new, ${updated} updated`)
      break
    }

    case 'pull': {
      send(ws, { type: 'pull_response', ...readSince(db, msg.since) })
      broadcastSyncStatus('synced', 'Sent updates to the phone')
      break
    }

    case 'full_sync': {
      const { inserted, updated, rejected } = ingestEntries(db, msg.entries)
      ingestAux(db, msg)
      // `since` keeps this incremental. It used to SELECT * FROM entries and
      // stringify the whole history on every sync, so peak memory grew with
      // the log. A client that has never synced still sends 0 and gets all.
      send(ws, {
        type: 'full_sync_response',
        ...readSince(db, msg.since),
        received: { inserted, updated, rejected }
      })
      broadcastSyncStatus('synced', `Full sync: +${inserted} new, ${updated} updated`)
      break
    }

    default:
      send(ws, { type: 'error', message: `Unknown type: ${msg.type}` })
  }
}

function readSince(db: Database.Database, since: unknown): Record<string, unknown> {
  const cursor = Number.isFinite(since) ? Number(since) : 0
  return {
    entries: db
      .prepare('SELECT * FROM entries WHERE modified_at > ? ORDER BY modified_at ASC')
      .all(cursor),
    hobbies: db.prepare('SELECT * FROM hobbies').all(),
    people: db.prepare('SELECT * FROM people').all(),
    chore_templates: db.prepare('SELECT * FROM chore_templates').all()
  }
}

function ingestEntries(
  db: Database.Database,
  raw: unknown
): { inserted: number; updated: number; rejected: number } {
  const all = Array.isArray(raw) ? raw : []
  const entries = all.slice(0, MAX_ENTRIES_PER_MESSAGE)
  let inserted = 0
  let updated = 0
  let rejected = all.length - entries.length

  // Prepared once, outside the transaction — this used to allocate a fresh
  // Statement per incoming entry.
  const selectStmt = db.prepare('SELECT id, modified_at FROM entries WHERE id = ?')
  const insertStmt = db.prepare(`
    INSERT OR IGNORE INTO entries (id, category, timestamp, date, data, version, modified_at, synced)
    VALUES (?, ?, ?, ?, ?, ?, ?, 1)
  `)
  // LWW is whole-row — pinning `date` to the first-seen value silently broke any feature that legitimately re-dates an entry.
  const updateStmt = db.prepare(`
    UPDATE entries SET data = ?, date = ?, version = ?, modified_at = ?, synced = 1
    WHERE id = ? AND modified_at < ?
  `)

  db.transaction(() => {
    for (const e of entries) {
      if (!isValidEntry(e)) {
        rejected++
        continue
      }
      const existing: any = selectStmt.get(e.id)
      if (!existing) {
        insertStmt.run(e.id, e.category, e.timestamp, e.date, e.data, e.version ?? 1, e.modified_at)
        inserted++
      } else if (e.modified_at > existing.modified_at) {
        updateStmt.run(e.data, e.date, e.version ?? 1, e.modified_at, e.id, existing.modified_at)
        updated++
      }
    }
  })()

  return { inserted, updated, rejected }
}

function ingestAux(db: Database.Database, msg: any): void {
  ;(Object.keys(AUX_TABLES) as AuxTable[]).forEach((table) => {
    if (Array.isArray(msg[table])) syncAuxTable(db, table, msg[table])
  })
}

function syncAuxTable(db: Database.Database, table: AuxTable, rows: any[]): void {
  const columns = AUX_TABLES[table]
  const placeholders = columns.map(() => '?').join(', ')
  const stmt = db.prepare(
    `INSERT OR IGNORE INTO ${table} (${columns.join(', ')}) VALUES (${placeholders})`
  )
  // Tombstones are grow-only: a delete on either device wins over any live
  // copy, and a stale live row from the peer can never resurrect a deleted one
  // (INSERT OR IGNORE keeps the local tombstone; this UPDATE only ever sets).
  const tombstone =
    table === 'people'
      ? db.prepare('UPDATE people SET deleted_at = ? WHERE id = ? AND deleted_at IS NULL')
      : null
  db.transaction(() => {
    for (const row of rows.slice(0, MAX_ENTRIES_PER_MESSAGE)) {
      if (!row || typeof row !== 'object' || typeof row.id !== 'string') continue
      stmt.run(...columns.map((c) => row[c] ?? null))
      if (tombstone && Number.isFinite(row.deleted_at)) tombstone.run(row.deleted_at, row.id)
    }
  })()
}

export async function stopSyncServer(): Promise<void> {
  if (heartbeat) {
    clearInterval(heartbeat)
    heartbeat = null
  }
  if (bonjourService) {
    // Await the goodbye packet so the service stops being advertised promptly.
    await new Promise<void>((resolve) => {
      try {
        bonjourService.stop?.(() => resolve())
      } catch {
        resolve()
      }
      setTimeout(resolve, 500)
    })
    bonjourService = null
  }
  if (bonjour) {
    try {
      bonjour.destroy()
    } catch {
      /* ignore */
    }
    bonjour = null
  }
  if (wss) {
    const server = wss
    wss = null
    server.clients.forEach((c) => {
      try {
        c.close()
      } catch {
        /* ignore */
      }
    })
    await new Promise<void>((resolve) => {
      server.close(() => resolve())
      setTimeout(resolve, 1000)
    })
  }
  peers.clear()
}

export function registerSyncHandlers(): void {
  ipcMain.handle('sync:getStatus', () => (wss ? 'listening' : 'stopped'))
  ipcMain.handle('sync:getPort', () => SYNC_PORT)
  ipcMain.handle('sync:getLocalIp', () => getLocalIp())
  ipcMain.handle('sync:getPairingToken', () => getPairingToken())
  ipcMain.handle('sync:regeneratePairingToken', () => regeneratePairingToken())
}

export function unregisterSyncHandlers(): void {
  ;['sync:getStatus', 'sync:getPort', 'sync:getLocalIp',
    'sync:getPairingToken', 'sync:regeneratePairingToken'
  ].forEach((channel) => ipcMain.removeHandler(channel))
}

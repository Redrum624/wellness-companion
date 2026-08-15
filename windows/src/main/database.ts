import Database from 'better-sqlite3'
import { app, ipcMain } from 'electron'
import { existsSync, mkdirSync, readdirSync, renameSync, statSync, unlinkSync } from 'fs'
import { join } from 'path'
import { v4 as uuidv4 } from 'uuid'

let db: Database.Database

export function initDatabase(): void {
  const dbPath = join(app.getPath('userData'), 'wellness.db')
  const hadExistingDb = existsSync(dbPath)
  db = new Database(dbPath)
  db.pragma('journal_mode = WAL')
  createTables()
  // Guard: this runs BEFORE backupOnVersionChange(), so today's additive-only
  // migrations are safe, but a future DESTRUCTIVE migration must snapshot the
  // db before migrating, not rely on the post-migration backup below to
  // protect it (see backupOnVersionChange's doc comment).
  migrateTables()
  backupOnVersionChange(hadExistingDb)
}

/**
 * The single shared connection. The sync server used to open its own handle per
 * inbound message, which churned file handles against the WAL under load and
 * left a close() on every error path.
 */
export function getDatabase(): Database.Database {
  if (!db?.open) throw new Error('Database is not initialised')
  return db
}

export function closeDatabase(): void {
  try {
    if (db?.open) db.close()
  } catch (err) {
    console.error('Error closing database:', err)
  }
}

function createTables(): void {
  db.exec(`
    CREATE TABLE IF NOT EXISTS entries (
      id TEXT PRIMARY KEY,
      category TEXT NOT NULL,
      timestamp INTEGER NOT NULL,
      date TEXT NOT NULL,
      data TEXT NOT NULL,
      version INTEGER DEFAULT 1,
      modified_at INTEGER NOT NULL,
      synced INTEGER DEFAULT 0
    );
    CREATE INDEX IF NOT EXISTS idx_entries_date ON entries(date);
    CREATE INDEX IF NOT EXISTS idx_entries_category ON entries(category);
    CREATE INDEX IF NOT EXISTS idx_entries_date_category ON entries(date, category);

    CREATE TABLE IF NOT EXISTS chore_templates (
      id TEXT PRIMARY KEY,
      name TEXT NOT NULL,
      category TEXT,
      recurrence TEXT,
      created_at INTEGER NOT NULL
    );

    CREATE TABLE IF NOT EXISTS hobbies (
      id TEXT PRIMARY KEY,
      name TEXT NOT NULL,
      color TEXT NOT NULL,
      created_at INTEGER NOT NULL
    );

    CREATE TABLE IF NOT EXISTS people (
      id TEXT PRIMARY KEY,
      name TEXT NOT NULL,
      created_at INTEGER NOT NULL,
      deleted_at INTEGER
    );

    CREATE TABLE IF NOT EXISTS settings (
      key TEXT PRIMARY KEY,
      value TEXT NOT NULL
    );
  `)
}

/** Idempotent column adds for databases created before the column existed. */
function migrateTables(): void {
  const peopleCols = db.prepare('PRAGMA table_info(people)').all().map((c: any) => c.name)
  if (!peopleCols.includes('deleted_at')) {
    db.exec('ALTER TABLE people ADD COLUMN deleted_at INTEGER')
  }
}

const LAST_VERSION_KEY = 'app.last_run_version'
const MAX_DB_BACKUPS = 5

/** Tracks the in-flight pre-update backup, if any, so quit can wait on it. */
let pendingBackup: Promise<void> | null = null

/**
 * Update safety net: the first launch after an app update snapshots the
 * database before normal use resumes, so a broken build or bad data writes
 * introduced by the new version can never take the only copy of the user's
 * data with it. This runs AFTER migrateTables(), so it does NOT protect
 * against the migration itself — a destructive migration would already have
 * mutated the live db by the time this snapshot is taken. Harmless today
 * because all migrations are additive (see the guard note on the
 * migrateTables() call in initDatabase). Uses SQLite's online backup API
 * (safe under WAL). The version marker is only advanced after a successful
 * backup, so a failed backup retries on the next launch.
 *
 * Everything past the fresh-install early return is wrapped in try/catch:
 * this runs inside initDatabase(), which runs inside the un-caught
 * app.whenReady().then(...) chain in index.ts, so a synchronous throw here
 * (e.g. mkdirSync failing on a full disk or locked-down ACL) must never be
 * allowed to propagate and silently prevent the window from ever opening.
 */
function backupOnVersionChange(hadExistingDb: boolean): void {
  const current = app.getVersion()
  const row: any = db.prepare('SELECT value FROM settings WHERE key = ?').get(LAST_VERSION_KEY)
  const previous: string | null = row?.value ?? null
  if (previous === current) return

  const stamp = (): void => {
    db.prepare('INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)').run(LAST_VERSION_KEY, current)
  }
  if (!hadExistingDb) {
    stamp()
    return
  }

  try {
    const backupDir = join(app.getPath('userData'), 'backups')
    mkdirSync(backupDir, { recursive: true })

    // A prior run could have been killed mid-backup, leaving a partial
    // "<dest>.db.tmp" behind. It is not a usable backup and pruning can't
    // tell it apart from a good one by name alone, so clear it up front.
    try {
      for (const f of readdirSync(backupDir)) {
        if (f.endsWith('.db.tmp')) unlinkSync(join(backupDir, f))
      }
    } catch (err) {
      console.error('Stale backup temp-file cleanup failed:', err)
    }

    const ts = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19)
    const dest = join(backupDir, `wellness-v${previous ?? 'pre-1.2.0'}-${ts}.db`)
    const tmp = `${dest}.tmp`
    pendingBackup = db
      .backup(tmp)
      .then(() => {
        // Only becomes the real backup file once fully written, so a reader
        // (pruning, the user, a future restore flow) never sees a partial one.
        renameSync(tmp, dest)
        stamp()
        pruneBackups(backupDir)
        console.log(`Database backed up before first run of v${current}: ${dest}`)
      })
      .catch((err) => console.error('Pre-update database backup failed:', err))
      .finally(() => {
        pendingBackup = null
      })
  } catch (err) {
    console.error('Pre-update backup setup failed:', err)
  }
}

/** Give an in-flight pre-update backup a moment to finish before the DB closes. */
export async function waitForPendingBackup(timeoutMs = 3000): Promise<void> {
  if (!pendingBackup) return
  await Promise.race([pendingBackup, new Promise<void>((resolve) => setTimeout(resolve, timeoutMs))])
}

function pruneBackups(backupDir: string): void {
  try {
    const backups = readdirSync(backupDir)
      .filter((f) => f.startsWith('wellness-') && f.endsWith('.db'))
      .map((f) => ({ f, mtime: statSync(join(backupDir, f)).mtimeMs }))
      .sort((a, b) => b.mtime - a.mtime)
    for (const { f } of backups.slice(MAX_DB_BACKUPS)) {
      unlinkSync(join(backupDir, f))
    }
  } catch (err) {
    console.error('Backup pruning failed:', err)
  }
}

// transport: ── wc-sync/4 pairing state (spec §2.8) ───────────────────────────
// Device keys, pending pairing slots and the pairing backoff all live as JSON
// blobs in the `settings` table, reached through a tiny get/set seam so the
// state machine can be unit-tested without a database (and so tests can never
// write a pairing into the user's live wellness.db).

export interface SettingsIO {
  get(key: string): string | null
  set(key: string, value: string): void
}

/** The production seam: the same `settings` table the db:getSetting IPC uses. */
export const dbSettingsIO: SettingsIO = {
  get(key: string): string | null {
    const row: any = getDatabase().prepare('SELECT value FROM settings WHERE key = ?').get(key)
    return row ? row.value : null
  },
  set(key: string, value: string): void {
    getDatabase()
      .prepare('INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)')
      .run(key, value)
  }
}

export const DEVICE_KEYS_SETTING = 'sync.device_keys'
export const PENDING_PAIRINGS_SETTING = 'sync.pending_pairings'
export const PAIR_BACKOFF_SETTING = 'sync.pair_backoff'

/**
 * Deliberately NOT the same number as MAX_CONNECTIONS: how many phones may stay
 * paired is a storage question, how many may talk at once is a resource
 * question. Conflating them evicted trusted devices whenever sockets got busy.
 */
export const MAX_STORED_DEVICE_KEYS = 8

/** Failures allowed before any lockout kicks in. */
export const PAIR_BACKOFF_FREE_ATTEMPTS = 3
export const PAIR_BACKOFF_BASE_MS = 30_000
export const PAIR_BACKOFF_MAX_MS = 30 * 60_000

export interface DeviceKeyRecord {
  keyId: string
  secret_b64: string
  label: string
  created: number
  lastSeen: number
}

export interface PendingPairing {
  secret_b64: string
  created: number
  ttlMs: number
}

export interface BackoffState {
  failures: number
  lockedUntil: number
  lockedOut: boolean
  lockedForMs: number
}

/** A corrupt or hand-edited blob must degrade to "no pairings", never throw. */
function readMap<T>(io: SettingsIO, key: string): Record<string, T> {
  const raw = io.get(key)
  if (!raw) return {}
  try {
    const parsed = JSON.parse(raw)
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return {}
    return parsed as Record<string, T>
  } catch {
    return {}
  }
}

function writeMap(io: SettingsIO, key: string, value: unknown): void {
  io.set(key, JSON.stringify(value))
}

export function getDeviceKeys(io: SettingsIO = dbSettingsIO): Record<string, DeviceKeyRecord> {
  return readMap<DeviceKeyRecord>(io, DEVICE_KEYS_SETTING)
}

/**
 * Upsert BY deviceId. A phone that re-pairs replaces its own slot, so a device
 * can never accumulate orphaned entries or leave a stale keyId behind. When the
 * cap is reached the least-recently-seen device is evicted.
 */
export function upsertDeviceKey(
  deviceId: string,
  record: DeviceKeyRecord,
  io: SettingsIO = dbSettingsIO
): void {
  const keys = getDeviceKeys(io)
  keys[deviceId] = record
  const ids = Object.keys(keys)
  if (ids.length > MAX_STORED_DEVICE_KEYS) {
    ids
      .sort((a, b) => (keys[a]?.lastSeen ?? 0) - (keys[b]?.lastSeen ?? 0))
      .slice(0, ids.length - MAX_STORED_DEVICE_KEYS)
      .forEach((id) => delete keys[id])
  }
  writeMap(io, DEVICE_KEYS_SETTING, keys)
}

export function removeDeviceKey(deviceId: string, io: SettingsIO = dbSettingsIO): boolean {
  const keys = getDeviceKeys(io)
  if (!keys[deviceId]) return false
  delete keys[deviceId]
  writeMap(io, DEVICE_KEYS_SETTING, keys)
  return true
}

/** Last resort: forget every paired device and every half-finished pairing. */
export function clearPairings(io: SettingsIO = dbSettingsIO): void {
  writeMap(io, DEVICE_KEYS_SETTING, {})
  writeMap(io, PENDING_PAIRINGS_SETTING, {})
}

/** Reads pending slots, pruning expired ones from storage as a side effect. */
export function getPending(
  now: number = Date.now(),
  io: SettingsIO = dbSettingsIO
): Record<string, PendingPairing> {
  const pending = readMap<PendingPairing>(io, PENDING_PAIRINGS_SETTING)
  const live: Record<string, PendingPairing> = {}
  let pruned = false
  for (const [keyId, slot] of Object.entries(pending)) {
    if (slot && now - slot.created <= slot.ttlMs) live[keyId] = slot
    else pruned = true
  }
  if (pruned) writeMap(io, PENDING_PAIRINGS_SETTING, live)
  return live
}

export function putPending(
  keyId: string,
  slot: PendingPairing,
  io: SettingsIO = dbSettingsIO
): void {
  const pending = getPending(slot.created, io)
  pending[keyId] = slot
  writeMap(io, PENDING_PAIRINGS_SETTING, pending)
}

/**
 * A completed first handshake promotes the pending slot to a permanent device
 * key. The secret is unchanged — it was delivered out-of-band and both sides
 * already held it, so there is no in-tunnel mint step to race or lose.
 */
export function bindPending(
  keyId: string,
  deviceId: string,
  label: string,
  now: number = Date.now(),
  io: SettingsIO = dbSettingsIO
): DeviceKeyRecord | null {
  const pending = getPending(now, io)
  const slot = pending[keyId]
  if (!slot) return null

  const record: DeviceKeyRecord = {
    keyId,
    secret_b64: slot.secret_b64,
    label,
    created: slot.created,
    lastSeen: now
  }
  upsertDeviceKey(deviceId, record, io)
  delete pending[keyId]
  writeMap(io, PENDING_PAIRINGS_SETTING, pending)
  return record
}

/**
 * Pairing backoff is persistent and global, not per socket: a per-socket
 * counter resets on reconnect, which is no throttle at all.
 */
export function getBackoff(now: number = Date.now(), io: SettingsIO = dbSettingsIO): BackoffState {
  const raw = io.get(PAIR_BACKOFF_SETTING)
  let failures = 0
  let lockedUntil = 0
  if (raw) {
    try {
      const parsed = JSON.parse(raw)
      failures = Number.isFinite(parsed?.failures) ? Number(parsed.failures) : 0
      lockedUntil = Number.isFinite(parsed?.lockedUntil) ? Number(parsed.lockedUntil) : 0
    } catch {
      /* corrupt blob: start clean rather than lock the user out forever */
    }
  }
  return {
    failures,
    lockedUntil,
    lockedOut: lockedUntil > now,
    lockedForMs: Math.max(0, lockedUntil - now)
  }
}

export function bumpBackoff(now: number = Date.now(), io: SettingsIO = dbSettingsIO): BackoffState {
  const failures = getBackoff(now, io).failures + 1
  const over = failures - PAIR_BACKOFF_FREE_ATTEMPTS
  const lockedUntil =
    over <= 0 ? 0 : now + Math.min(PAIR_BACKOFF_BASE_MS * 2 ** (over - 1), PAIR_BACKOFF_MAX_MS)
  io.set(PAIR_BACKOFF_SETTING, JSON.stringify({ failures, lockedUntil, lastFailure: now }))
  return getBackoff(now, io)
}

export function resetBackoff(io: SettingsIO = dbSettingsIO): void {
  io.set(PAIR_BACKOFF_SETTING, JSON.stringify({ failures: 0, lockedUntil: 0, lastFailure: 0 }))
}
// transport: ── end wc-sync/4 pairing state ─────────────────────────────────

export function registerDatabaseHandlers(): void {
  // Entries
  ipcMain.handle('db:getEntriesByDate', (_e, date: string) => {
    return db.prepare('SELECT * FROM entries WHERE date = ? ORDER BY timestamp DESC').all(date)
  })

  ipcMain.handle('db:getEntriesByDateAndCategory', (_e, date: string, category: string) => {
    return db.prepare('SELECT * FROM entries WHERE date = ? AND category = ? ORDER BY timestamp DESC').all(date, category)
  })

  ipcMain.handle('db:getEntriesByDateRange', (_e, startDate: string, endDate: string, category: string) => {
    return db.prepare('SELECT * FROM entries WHERE date BETWEEN ? AND ? AND category = ? ORDER BY timestamp ASC').all(startDate, endDate, category)
  })

  ipcMain.handle('db:getLoggedDates', (_e, category: string) => {
    return db.prepare('SELECT DISTINCT date FROM entries WHERE category = ? ORDER BY date DESC').all(category).map((r: any) => r.date)
  })

  ipcMain.handle('db:getAllDatesWithCounts', (_e, startDate: string, endDate: string) => {
    return db.prepare('SELECT date, COUNT(*) as count FROM entries WHERE date BETWEEN ? AND ? GROUP BY date').all(startDate, endDate)
  })

  ipcMain.handle('db:insertEntry', (_e, category: string, date: string, data: string) => {
    const now = Date.now()
    const id = uuidv4()
    const entry = { id, category, timestamp: now, date, data, version: 1, modified_at: now, synced: 0 }
    db.prepare('INSERT INTO entries (id, category, timestamp, date, data, version, modified_at, synced) VALUES (?, ?, ?, ?, ?, ?, ?, ?)').run(id, category, now, date, data, 1, now, 0)
    return entry
  })

  ipcMain.handle('db:updateEntry', (_e, id: string, data: string) => {
    const now = Date.now()
    db.prepare('UPDATE entries SET data = ?, version = version + 1, modified_at = ? WHERE id = ?').run(data, now, id)
  })

  ipcMain.handle('db:deleteEntry', (_e, id: string) => {
    db.prepare('DELETE FROM entries WHERE id = ?').run(id)
  })

  // Chore templates
  ipcMain.handle('db:getChoreTemplates', () => {
    return db.prepare('SELECT * FROM chore_templates ORDER BY name').all()
  })

  ipcMain.handle('db:addChoreTemplate', (_e, name: string, category: string | null, recurrence: string | null) => {
    const id = uuidv4()
    db.prepare('INSERT INTO chore_templates (id, name, category, recurrence, created_at) VALUES (?, ?, ?, ?, ?)').run(id, name, category, recurrence, Date.now())
  })

  ipcMain.handle('db:deleteChoreTemplate', (_e, id: string) => {
    db.prepare('DELETE FROM chore_templates WHERE id = ?').run(id)
  })

  // Hobbies
  ipcMain.handle('db:getHobbies', () => {
    return db.prepare('SELECT * FROM hobbies ORDER BY name').all()
  })

  ipcMain.handle('db:addHobby', (_e, name: string, color: string) => {
    const id = uuidv4()
    db.prepare('INSERT INTO hobbies (id, name, color, created_at) VALUES (?, ?, ?, ?)').run(id, name, color, Date.now())
  })

  ipcMain.handle('db:deleteHobby', (_e, id: string) => {
    db.prepare('DELETE FROM hobbies WHERE id = ?').run(id)
  })

  // People
  ipcMain.handle('db:getPeople', () => {
    return db.prepare('SELECT * FROM people WHERE deleted_at IS NULL ORDER BY name').all()
  })

  ipcMain.handle('db:addPerson', (_e, name: string) => {
    const id = uuidv4()
    db.prepare('INSERT INTO people (id, name, created_at) VALUES (?, ?, ?)').run(id, name, Date.now())
  })

  // Soft delete: the row becomes a tombstone so sync propagates the removal
  // instead of re-inserting the person from the peer on the next exchange.
  ipcMain.handle('db:deletePerson', (_e, id: string) => {
    db.prepare('UPDATE people SET deleted_at = ? WHERE id = ? AND deleted_at IS NULL').run(Date.now(), id)
  })

  // Settings
  ipcMain.handle('db:getSetting', (_e, key: string) => {
    const row: any = db.prepare('SELECT value FROM settings WHERE key = ?').get(key)
    return row ? row.value : null
  })

  ipcMain.handle('db:setSetting', (_e, key: string, value: string) => {
    db.prepare('INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)').run(key, value)
  })
}

// at-rest: SQLCipher-capable drop-in for better-sqlite3 (spec §4).
import Database from 'better-sqlite3-multiple-ciphers'
import { app, dialog, ipcMain, safeStorage } from 'electron'
// at-rest: copyFileSync/readFileSync/writeFileSync/openSync serve the key file,
// the magic-header check and the checkpoint+copy backup (§4.1-§4.4).
import {
  closeSync,
  copyFileSync,
  existsSync,
  mkdirSync,
  openSync,
  readdirSync,
  readFileSync,
  readSync,
  renameSync,
  statSync,
  unlinkSync,
  writeFileSync
} from 'fs'
import { randomBytes } from 'crypto'
import { join } from 'path'
import { v4 as uuidv4 } from 'uuid'

let db: Database.Database

export function initDatabase(): void {
  const userDataDir = app.getPath('userData')
  const dbPath = join(userDataDir, 'wellness.db')
  const hadExistingDb = existsSync(dbPath)

  // at-rest: the DB key must be resolved before the file is touched (§4.1).
  const keyOutcome = loadOrCreateDbKey(userDataDir, safeStorage as unknown as SafeStorageLike)
  if (keyOutcome.status === 'error') {
    failClosedOnKeyError(keyOutcome.reason, userDataDir)
    return
  }
  let key: Buffer | null = keyOutcome.status === 'ready' ? keyOutcome.key : null
  if (keyOutcome.status === 'unavailable') {
    setEncryptionState('unencrypted', keyOutcome.reason)
    console.warn(`Database encryption unavailable this session: ${keyOutcome.reason}`)
  }

  // at-rest: the header decides how to open — a flag in settings would be
  // inside the very file it describes, and desyncs after a manual restore.
  const plaintextOnDisk = hadExistingDb && isPlaintextSqliteFile(dbPath)
  const encryptedOnDisk = hadExistingDb && !plaintextOnDisk && fileSize(dbPath) > 0
  if (encryptedOnDisk && (key === null || keyOutcome.created)) {
    // An encrypted database with no key — or with a freshly minted one, which
    // means wellness.key was lost — cannot be opened. Stop while the user's
    // file is still exactly as they left it, rather than let SQLite create an
    // empty database over it.
    failClosedOnKeyError(
      'wellness.db is already encrypted but no matching wellness.key could be unwrapped',
      userDataDir
    )
    return
  }

  try {
    db = openDatabaseHandle(dbPath, plaintextOnDisk ? null : key)
    createTables()
    // Guard: this runs BEFORE backupOnVersionChange(), so today's additive-only
    // migrations are safe, but a future DESTRUCTIVE migration must snapshot the
    // db before migrating, not rely on the post-migration backup below to
    // protect it (see backupOnVersionChange's doc comment).
    migrateTables()
  } catch (err) {
    // at-rest: a key that unwraps but does not match the file (a restored db,
    // a swapped wellness.key) makes SQLite refuse every statement. That is an
    // explainable key error, not a crash — and the file is still intact,
    // because a database it cannot decrypt is one it cannot write to either.
    if (encryptedOnDisk) {
      try {
        if (db?.open) db.close()
      } catch (closeErr) {
        console.error('Closing the unreadable database failed:', closeErr)
      }
      failClosedOnKeyError(
        `wellness.db could not be opened with the stored key: ${describe(err)}`,
        userDataDir
      )
      return
    }
    throw err
  }

  // at-rest: plaintext -> encrypted, on its own copy, before the backup runs
  // (§4.4). The migration snapshots the file itself; it deliberately does not
  // lean on backupOnVersionChange(), which only runs after mutation.
  if (key && plaintextOnDisk) {
    db.pragma('wal_checkpoint(TRUNCATE)')
    db.close()
    const result = migrateToEncryptedIfNeeded(dbPath, key)
    if (result.status === 'migrated') {
      db = openDatabaseHandle(dbPath, key)
      setEncryptionState('encrypted', `migrated ${result.rows ?? 0} entries to an encrypted database`)
      console.log(`Database migrated to encrypted storage (${result.rows ?? 0} entries).`)
    } else {
      // Never brick: the original is untouched, so run on it unencrypted and
      // try again next launch.
      key = null
      db = openDatabaseHandle(dbPath, null)
      setEncryptionState('unencrypted', result.reason ?? 'encryption migration did not run')
      console.error(`Database encryption migration failed, running unencrypted: ${result.reason}`)
    }
  } else if (key) {
    setEncryptionState('encrypted', hadExistingDb ? 'opened encrypted' : 'created encrypted')
  }

  backupOnVersionChange(hadExistingDb, dbPath)
}

/** at-rest: open sequence per spec §4.2 (cipher, legacy, key, then WAL). */
function openDatabaseHandle(dbPath: string, key: Buffer | null): Database.Database {
  const handle = new Database(dbPath)
  if (key) {
    handle.pragma("cipher='sqlcipher'")
    handle.pragma('legacy=4') // SQLCipher-4 page format
    handle.key(key) // exactly 32 raw bytes -> no PBKDF2, the key is already uniform
  }
  handle.pragma('journal_mode = WAL')
  return handle
}

/**
 * at-rest: the one path that must never "repair" anything (§4.1). A key that
 * cannot be unwrapped means the encrypted database cannot be opened; the only
 * safe action is to say so and stop, because every alternative (open blind,
 * create a fresh db, re-mint a key) writes over data that is still perfectly
 * recoverable once the key or the Windows profile is restored.
 */
function failClosedOnKeyError(reason: string, userDataDir: string): void {
  setEncryptionState('error', reason)
  console.error(`Database key error, refusing to open wellness.db: ${reason}`)
  try {
    dialog.showErrorBox(
      'Wellness Companion — database key error',
      `${reason}\n\n` +
        'Your data has NOT been changed. The encrypted database and its key live in:\n' +
        `${userDataDir}\n\n` +
        'wellness.key can only be unwrapped by the same Windows user account that created it. ' +
        'Restore that account (or a matching wellness.key backup) and start the app again, or ' +
        'restore a copy from the backups folder.'
    )
  } catch (err) {
    console.error('Could not show the key-error dialog:', err)
  }
  app.exit(1)
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

// at-rest: ── encryption at rest (spec §4) ─────────────────────────────────
// The database key lives in wellness.key, NOT in the `settings` table: that
// table (device keys, pairing state, the version marker) is inside the very
// file the key protects, so storing it there would be circular.

const DB_KEY_FILE = 'wellness.key'
const DB_KEY_BYTES = 32
/** Every plaintext SQLite file starts with this; an encrypted one never does. */
const SQLITE_PLAINTEXT_MAGIC = Buffer.from('SQLite format 3\0', 'latin1')

/** The slice of Electron's safeStorage this module needs, so tests can supply it. */
export interface SafeStorageLike {
  isEncryptionAvailable(): boolean
  encryptString(plainText: string): Buffer
  decryptString(encrypted: Buffer): string
}

export type DbKeyOutcome =
  | { status: 'ready'; key: Buffer; created: boolean }
  /** No key material could be created; run unencrypted and retry next launch. */
  | { status: 'unavailable'; reason: string; created?: false }
  /** Key material exists but cannot be used: stop before touching the db. */
  | { status: 'error'; reason: string; created?: false }

function describe(err: unknown): string {
  return err instanceof Error ? err.message : String(err)
}

/**
 * Load the wrapped 256-bit database key, minting one on first run (§4.1).
 *
 * Fail-closed rules, in order of how much damage the alternative would do:
 *  - safeStorage unavailable with no key yet -> write NOTHING (an unwrapped key
 *    on disk is worse than no encryption, because it looks encrypted) and run
 *    unencrypted this session.
 *  - a key file that cannot be unwrapped -> error, never a silent plaintext
 *    fallback and never a re-mint over the existing key.
 */
export function loadOrCreateDbKey(userDataDir: string, ss: SafeStorageLike): DbKeyOutcome {
  const keyPath = join(userDataDir, DB_KEY_FILE)
  const tmpPath = `${keyPath}.tmp`

  if (existsSync(keyPath)) {
    if (!ss.isEncryptionAvailable()) {
      return {
        status: 'error',
        reason: 'wellness.key exists but Windows credential storage (DPAPI) is unavailable'
      }
    }
    try {
      const key = Buffer.from(ss.decryptString(readFileSync(keyPath)), 'base64')
      if (key.length !== DB_KEY_BYTES) {
        return {
          status: 'error',
          reason: `wellness.key unwrapped to ${key.length} bytes, expected ${DB_KEY_BYTES}`
        }
      }
      return { status: 'ready', key, created: false }
    } catch (err) {
      return { status: 'error', reason: `wellness.key could not be unwrapped: ${describe(err)}` }
    }
  }

  if (!ss.isEncryptionAvailable()) {
    return {
      status: 'unavailable',
      reason: 'Windows credential storage (DPAPI) is unavailable, so no database key was created'
    }
  }

  try {
    const key = randomBytes(DB_KEY_BYTES) // never derived, never hardcoded
    // tmp-then-rename: a half-written key file would be indistinguishable from
    // a corrupt one on the next launch.
    writeFileSync(tmpPath, ss.encryptString(key.toString('base64')))
    renameSync(tmpPath, keyPath)
    return { status: 'ready', key, created: true }
  } catch (err) {
    try {
      if (existsSync(tmpPath)) unlinkSync(tmpPath)
    } catch (cleanupErr) {
      console.error('Could not clean up a partial wellness.key.tmp:', cleanupErr)
    }
    // Nothing was encrypted yet, so continuing in plaintext is safe and retries.
    return { status: 'unavailable', reason: `database key could not be stored: ${describe(err)}` }
  }
}

/** Size of a file, or 0 when it cannot be stat'ed (missing, locked, gone). */
function fileSize(path: string): number {
  try {
    return statSync(path).size
  } catch {
    return 0
  }
}

/** Magic-header check (§4.4): cheap, and it cannot desync from the file. */
export function isPlaintextSqliteFile(path: string): boolean {
  let fd: number | null = null
  try {
    fd = openSync(path, 'r')
    const head = Buffer.alloc(SQLITE_PLAINTEXT_MAGIC.length)
    const read = readSync(fd, head, 0, head.length, 0)
    return read === head.length && head.equals(SQLITE_PLAINTEXT_MAGIC)
  } catch {
    return false
  } finally {
    if (fd !== null) {
      try {
        closeSync(fd)
      } catch {
        /* the fd is being dropped anyway */
      }
    }
  }
}

/** The handful of database methods the migration needs, so tests can fake it. */
export interface CipherDbHandle {
  pragma(source: string): unknown
  key(key: Buffer): number
  rekey(key: Buffer): number
  prepare(source: string): { get(...params: unknown[]): unknown }
  close(): void
}
export type CipherDbOpener = (path: string) => CipherDbHandle

const openCipherDb: CipherDbOpener = (path: string) =>
  new Database(path) as unknown as CipherDbHandle

export interface MigrationResult {
  status: 'not_needed' | 'migrated' | 'failed'
  rows?: number
  reason?: string
}

function removeIfPresent(path: string): void {
  try {
    if (existsSync(path)) unlinkSync(path)
  } catch (err) {
    console.error(`Could not remove ${path}:`, err)
  }
}

/**
 * Plaintext -> encrypted migration that never mutates the live file (§4.4).
 *
 * The whole conversion happens on `wellness.db.premigration.tmp`; the live file
 * is only renamed aside once a SEPARATE handle has reopened the copy under the
 * real key and actually read from `entries`. Any failure — including a stale
 * tmp from a killed run — deletes the working copy and leaves wellness.db
 * exactly as it was, so the app runs unencrypted this session and retries on
 * the next launch. One `wellness.db.plaintext.bak` cycle is kept.
 *
 * This library has no sqlcipher_export(), so encryption goes through
 * PRAGMA rekey — which requires a non-WAL journal first.
 */
export function migrateToEncryptedIfNeeded(
  dbPath: string,
  key: Buffer,
  open: CipherDbOpener = openCipherDb
): MigrationResult {
  if (!isPlaintextSqliteFile(dbPath)) return { status: 'not_needed' }

  const walPath = `${dbPath}-wal`
  try {
    if (existsSync(walPath) && statSync(walPath).size > 0) {
      // Copying the main file alone would silently drop whatever the WAL still
      // holds. The caller checkpoints and closes first; if that did not happen,
      // refuse rather than migrate a truncated snapshot.
      return {
        status: 'failed',
        reason: 'an uncheckpointed WAL is present next to wellness.db'
      }
    }
  } catch (err) {
    return { status: 'failed', reason: `could not inspect the WAL: ${describe(err)}` }
  }

  const tmpPath = `${dbPath}.premigration.tmp`
  const bakPath = `${dbPath}.plaintext.bak`
  const cleanupTmp = (): void => {
    removeIfPresent(tmpPath)
    removeIfPresent(`${tmpPath}-wal`)
    removeIfPresent(`${tmpPath}-shm`)
  }

  cleanupTmp() // a killed earlier attempt must not be mistaken for progress

  try {
    copyFileSync(dbPath, tmpPath)

    const work = open(tmpPath)
    try {
      work.pragma('journal_mode = DELETE') // rekey-from-plaintext needs non-WAL
      work.pragma("cipher='sqlcipher'")
      work.pragma('legacy=4')
      work.rekey(key) // encrypts the copy in place
      work.pragma('journal_mode = WAL')
    } finally {
      try {
        work.close()
      } catch (err) {
        console.error('Closing the migration working copy failed:', err)
      }
    }

    // Verification is a real keyed read on a fresh handle: anything less would
    // swap in a file we have never actually decrypted.
    const verify = open(tmpPath)
    let rows: number
    try {
      verify.pragma("cipher='sqlcipher'")
      verify.pragma('legacy=4')
      verify.key(key)
      const row = verify.prepare('SELECT count(*) FROM entries').get() as Record<string, unknown>
      rows = Number(Object.values(row ?? {})[0])
      if (!Number.isFinite(rows)) throw new Error('verification read returned no row count')
      verify.pragma('wal_checkpoint(TRUNCATE)')
    } finally {
      try {
        verify.close()
      } catch (err) {
        console.error('Closing the migration verification handle failed:', err)
      }
    }

    removeIfPresent(bakPath) // keep exactly one plaintext cycle
    renameSync(dbPath, bakPath)
    try {
      renameSync(tmpPath, dbPath)
    } catch (err) {
      // Put the original back rather than leave the app with no database.
      try {
        renameSync(bakPath, dbPath)
      } catch (restoreErr) {
        console.error('Restoring wellness.db after a failed swap failed:', restoreErr)
      }
      throw err
    }

    // A plaintext -wal/-shm left beside the now-encrypted file would be
    // replayed into it on the next open.
    removeIfPresent(`${dbPath}-wal`)
    removeIfPresent(`${dbPath}-shm`)

    return { status: 'migrated', rows }
  } catch (err) {
    cleanupTmp()
    return { status: 'failed', reason: describe(err) }
  }
}

export type EncryptionMode = 'encrypted' | 'unencrypted' | 'error'
let encryptionState: { mode: EncryptionMode; reason: string } = {
  mode: 'unencrypted',
  reason: 'database not initialised'
}

function setEncryptionState(mode: EncryptionMode, reason: string): void {
  encryptionState = { mode, reason }
}

/** Whether this session is running on an encrypted database, and why not. */
export function getEncryptionStatus(): { mode: EncryptionMode; reason: string } {
  return { ...encryptionState }
}
// at-rest: ── end encryption at rest ────────────────────────────────────────

/** Idempotent column adds for databases created before the column existed. */
function migrateTables(): void {
  const peopleCols = db.prepare('PRAGMA table_info(people)').all().map((c: any) => c.name)
  if (!peopleCols.includes('deleted_at')) {
    db.exec('ALTER TABLE people ADD COLUMN deleted_at INTEGER')
  }
}

const LAST_VERSION_KEY = 'app.last_run_version'
const MAX_DB_BACKUPS = 5

/**
 * at-rest: the pre-update snapshot, as a WAL checkpoint plus a raw file copy
 * (§4.3). db.backup() cannot be used on an encrypted database — it does not
 * produce a correctly-encrypted copy — whereas every page on disk, WAL
 * included, is already ciphertext, so copying the file is both correct and
 * cheaper. Preferred over VACUUM INTO, which would put the raw key into a SQL
 * string. Trade-off: this is not an *online* backup; it is safe here only
 * because the app holds a single connection and this runs at startup before
 * any writes.
 */
export function snapshotDatabaseFile(
  handle: { pragma(source: string): unknown },
  dbPath: string,
  tmpPath: string,
  destPath: string
): void {
  handle.pragma('wal_checkpoint(TRUNCATE)') // fold the WAL into wellness.db
  copyFileSync(dbPath, tmpPath)
  // Only becomes the real backup file once fully written, so a reader
  // (pruning, the user, a future restore flow) never sees a partial one.
  renameSync(tmpPath, destPath)
}

/**
 * Update safety net: the first launch after an app update snapshots the
 * database before normal use resumes, so a broken build or bad data writes
 * introduced by the new version can never take the only copy of the user's
 * data with it. This runs AFTER migrateTables(), so it does NOT protect
 * against the migration itself — a destructive migration would already have
 * mutated the live db by the time this snapshot is taken. Harmless today
 * because all migrations are additive (see the guard note on the
 * migrateTables() call in initDatabase). The version marker is only advanced
 * after a successful backup, so a failed backup retries on the next launch.
 *
 * at-rest: the copy is of the encrypted file, so the backup is ciphertext too
 * — and, like the live database, only openable by the Windows account whose
 * DPAPI wrapped wellness.key.
 *
 * Everything past the fresh-install early return is wrapped in try/catch:
 * this runs inside initDatabase(), which runs inside the un-caught
 * app.whenReady().then(...) chain in index.ts, so a synchronous throw here
 * (e.g. mkdirSync failing on a full disk or locked-down ACL) must never be
 * allowed to propagate and silently prevent the window from ever opening.
 */
function backupOnVersionChange(hadExistingDb: boolean, dbPath: string): void {
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
    // at-rest: checkpoint + copy replaces the async db.backup() (§4.3).
    snapshotDatabaseFile(db, dbPath, `${dest}.tmp`, dest)
    stamp()
    pruneBackups(backupDir)
    console.log(`Database backed up before first run of v${current}: ${dest}`)
  } catch (err) {
    console.error('Pre-update database backup failed:', err)
  }
}

/**
 * Kept as an awaited no-op for API stability: the pre-update snapshot is now a
 * synchronous checkpoint+copy (at-rest, §4.3), so nothing is ever in flight by
 * the time quit runs. index.ts still awaits this before closeDatabase().
 */
export async function waitForPendingBackup(_timeoutMs = 3000): Promise<void> {
  return
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

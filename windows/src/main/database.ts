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

  // at-rest: before ANY open — a missing wellness.db with migration leftovers
  // beside it is an interrupted swap, not a fresh install (§4.4, amended).
  const recovery = recoverInterruptedSwap(dbPath, key)
  if (recovery.status === 'unrecoverable') {
    failClosedOnKeyError(recovery.reason, userDataDir, SURVIVOR_GUIDANCE)
    return
  }

  // Computed AFTER recovery, so a recovered database counts as existing.
  const hadExistingDb = existsSync(dbPath)

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
      // at-rest: the reopen is the last step that can throw on its own; without
      // this guard it would escape into the uncaught app.whenReady() chain and
      // leave the user with no window and no explanation.
      if (!reopenOrFailClosed(dbPath, key, userDataDir)) return
      setEncryptionState('encrypted', `migrated ${result.rows ?? 0} entries to an encrypted database`)
      console.log(`Database migrated to encrypted storage (${result.rows ?? 0} entries).`)
    } else if (result.status === 'database_missing') {
      // at-rest: distinct from a plain failure — the swap left no wellness.db at
      // all. Opening anything here would create an empty database over the
      // crash state and orphan the survivors, so stop and say where the data is.
      failClosedOnKeyError(
        `the encryption migration left no wellness.db in place: ${result.reason}`,
        userDataDir,
        SURVIVOR_GUIDANCE
      )
      return
    } else {
      // Never brick: the original is untouched, so run on it unencrypted and
      // try again next launch.
      key = null
      if (!reopenOrFailClosed(dbPath, null, userDataDir)) return
      setEncryptionState('unencrypted', result.reason ?? 'encryption migration did not run')
      console.error(`Database encryption migration failed, running unencrypted: ${result.reason}`)
    }
  } else if (key) {
    setEncryptionState('encrypted', hadExistingDb ? 'opened encrypted' : 'created encrypted')
  }

  backupOnVersionChange(hadExistingDb, dbPath)
}

/**
 * at-rest: reopen after the migration, turning a throw into the same clear
 * error state as every other key failure. Returns false when it failed closed,
 * in which case the caller must return immediately.
 */
function reopenOrFailClosed(dbPath: string, key: Buffer | null, userDataDir: string): boolean {
  try {
    // fileMustExist: a REopen must never create. Without it, a swap that left
    // wellness.db missing would be "fixed" by SQLite conjuring an empty
    // database over the crash state, burying the real data in the survivors.
    db = openDatabaseHandle(dbPath, key, true)
    return true
  } catch (err) {
    failClosedOnKeyError(
      `wellness.db could not be reopened after the encryption migration: ${describe(err)}`,
      userDataDir,
      existsSync(dbPath) ? KEY_ERROR_GUIDANCE : SURVIVOR_GUIDANCE
    )
    return false
  }
}

/**
 * at-rest: open sequence per spec §4.2 (cipher, legacy, key, then WAL).
 * `mustExist` is for every open that is a REopen: only the very first open of a
 * launch is allowed to create the file.
 */
function openDatabaseHandle(
  dbPath: string,
  key: Buffer | null,
  mustExist = false
): Database.Database {
  const handle = mustExist ? new Database(dbPath, { fileMustExist: true }) : new Database(dbPath)
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
/**
 * Shown whenever wellness.db itself is missing. It must name BOTH survivors:
 * which one holds the data depends on how far the interrupted swap got, and the
 * user needs to be told their data still exists.
 */
const SURVIVOR_GUIDANCE =
  'Your data has NOT been lost: it is in wellness.db.premigration.tmp (encrypted) or ' +
  'wellness.db.plaintext.bak (unencrypted) in that folder. Neither file has been deleted. ' +
  'Rename the copy you want back to wellness.db and start the app again, or restore a copy ' +
  'from the backups folder.'

const KEY_ERROR_GUIDANCE =
  'Your data has NOT been changed. wellness.key can only be unwrapped by the same Windows ' +
  'user account that created it. Restore that account (or a matching wellness.key backup) ' +
  'and start the app again, or restore a copy from the backups folder.'

function failClosedOnKeyError(
  reason: string,
  userDataDir: string,
  guidance: string = KEY_ERROR_GUIDANCE
): void {
  setEncryptionState('error', reason)
  console.error(`Database error, refusing to open wellness.db: ${reason}`)
  try {
    dialog.showErrorBox(
      'Wellness Companion — database key error',
      `${reason}\n\nThe database and its key live in:\n${userDataDir}\n\n${guidance}`
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

  // at-rest: never mint a key next to a database that is ALREADY ciphertext.
  // The minted key cannot open it, and persisting it would make the next launch
  // read it back as an established key (created:false), slipping past the
  // "freshly minted" guard in initDatabase and leaving nothing but SQLite's
  // refusal to decrypt between the user and a confusing failure.
  const dbPath = join(userDataDir, 'wellness.db')
  if (existsSync(dbPath) && fileSize(dbPath) > 0 && !isPlaintextSqliteFile(dbPath)) {
    return {
      status: 'error',
      reason: 'wellness.db is encrypted but wellness.key is missing, so it cannot be unlocked'
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
/** Seam for the swap renames, so the restore-on-failure branch is testable. */
export type RenameFn = (from: string, to: string) => void

const openCipherDb: CipherDbOpener = (path: string) =>
  new Database(path) as unknown as CipherDbHandle

export interface MigrationResult {
  /**
   * `failed` means the original is still in place and usable; `database_missing`
   * means the swap left no wellness.db at all and the data lives only in the
   * survivors. Collapsing the two is what let an empty database be created over
   * a half-finished swap, so they stay distinct.
   */
  status: 'not_needed' | 'migrated' | 'failed' | 'database_missing'
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
  open: CipherDbOpener = openCipherDb,
  rename: RenameFn = renameSync
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
    // A survivor is only ever junk while the live database is actually there.
    // With wellness.db missing, this working copy may be the only copy of the
    // newest state — deleting it is the difference between a bad launch and
    // permanent data loss.
    if (!existsSync(dbPath)) return
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

    // The rename below REPLACES any previous .plaintext.bak atomically, which
    // is why it is not deleted first: a pre-delete buys nothing and only widens
    // the window in which neither file exists.
    // The two renames below are the one window in which wellness.db does not
    // exist. Nothing is lost if the process dies here — the data is in the
    // .premigration.tmp and the .plaintext.bak — but the next launch must not
    // mistake the gap for a fresh install, which is what
    // recoverInterruptedSwap() is for.
    rename(dbPath, bakPath)
    try {
      rename(tmpPath, dbPath)
    } catch (err) {
      // Put the original back rather than leave the app with no database.
      try {
        rename(bakPath, dbPath)
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
    // cleanupTmp() is a no-op while wellness.db is missing, so a failed swap
    // whose restore also failed keeps BOTH survivors for the next launch.
    cleanupTmp()
    if (!existsSync(dbPath)) {
      return {
        status: 'database_missing',
        reason: `${describe(err)} (wellness.db.premigration.tmp and/or wellness.db.plaintext.bak hold the data)`
      }
    }
    return { status: 'failed', reason: describe(err) }
  }
}

export type SwapRecovery =
  | { status: 'not_needed' }
  | { status: 'recovered'; from: 'premigration' | 'plaintext_backup'; rows?: number }
  | { status: 'unrecoverable'; reason: string }

/**
 * at-rest: recover from a migration swap that was interrupted (§4.4, amended).
 *
 * Between the two renames wellness.db does not exist. A process death there —
 * or a failed swap whose restore also failed — leaves the data in
 * wellness.db.premigration.tmp (encrypted) and/or wellness.db.plaintext.bak
 * (plaintext), with no wellness.db at all. Without this check the next launch
 * would read that as a fresh install and cheerfully create an empty encrypted
 * database, stamp the version marker and show the user zero entries.
 *
 * Runs BEFORE the database is opened. Prefers the .premigration.tmp when it
 * actually opens under the key (it is the newer, already-verified copy), falls
 * back to restoring the .plaintext.bak, and reports `unrecoverable` when a
 * survivor exists but neither route works — the caller then fails closed with
 * a real message instead of starting empty.
 */
export function recoverInterruptedSwap(
  dbPath: string,
  key: Buffer | null,
  open: CipherDbOpener = openCipherDb,
  rename: RenameFn = renameSync
): SwapRecovery {
  // Gate the early return on the file being a REAL database, not merely a file:
  // a zero-length wellness.db is what an interrupted create leaves behind, and
  // treating it as "nothing to do" is exactly how recovery gets skipped forever.
  // (A deeper content check is deliberately NOT done: an empty-but-initialised
  // database is indistinguishable from a user who deleted all their entries,
  // and restoring a backup over that would resurrect deleted data. Nothing can
  // create such a file over a half-finished swap any more — every reopen passes
  // fileMustExist — so the 0-byte case is the only ambiguity left.)
  if (existsSync(dbPath) && fileSize(dbPath) > 0) return { status: 'not_needed' }

  const tmpPath = `${dbPath}.premigration.tmp`
  const bakPath = `${dbPath}.plaintext.bak`
  const hasTmp = existsSync(tmpPath) && fileSize(tmpPath) > 0
  const hasBak = existsSync(bakPath) && fileSize(bakPath) > 0
  if (!hasTmp && !hasBak) return { status: 'not_needed' } // a genuine first run

  const failures: string[] = []

  if (hasTmp && key) {
    try {
      const handle = open(tmpPath)
      let rows: number
      try {
        handle.pragma("cipher='sqlcipher'")
        handle.pragma('legacy=4')
        handle.key(key)
        const row = handle.prepare('SELECT count(*) FROM entries').get() as Record<string, unknown>
        rows = Number(Object.values(row ?? {})[0])
        if (!Number.isFinite(rows)) throw new Error('recovery read returned no row count')
      } finally {
        try {
          handle.close()
        } catch (err) {
          console.error('Closing the recovery handle failed:', err)
        }
      }
      rename(tmpPath, dbPath)
      console.log(`Recovered an interrupted migration from the encrypted copy (${rows} entries).`)
      return { status: 'recovered', from: 'premigration', rows }
    } catch (err) {
      // Fall through to the plaintext backup: the tmp stays on disk untouched.
      failures.push(`premigration copy: ${describe(err)}`)
    }
  } else if (hasTmp) {
    failures.push('premigration copy: no database key is available to verify it')
  }

  if (hasBak) {
    try {
      rename(bakPath, dbPath)
      console.log('Recovered an interrupted migration by restoring the plaintext backup.')
      return { status: 'recovered', from: 'plaintext_backup' }
    } catch (err) {
      failures.push(`plaintext backup: ${describe(err)}`)
    }
  }

  return {
    status: 'unrecoverable',
    reason: `wellness.db is missing after an interrupted migration and could not be recovered (${failures.join('; ')})`
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
  // db:getSetting / db:setSetting are a general-purpose renderer-facing channel over the
  // `settings` table. That table also holds sync credentials under `sync.*` keys
  // (DEVICE_KEYS_SETTING, PENDING_PAIRINGS_SETTING, PAIR_BACKOFF_SETTING) -- raw device
  // pairing secrets and pairing state -- and internal bookkeeping like
  // `app.last_run_version` (LAST_VERSION_KEY), which gates the pre-update database
  // snapshot in backupOnVersionChange(). A compromised renderer (XSS, a bad bundled dep)
  // must not be able to read or write ANY of that: exfiltrate a paired phone's long-term
  // secret via getSetting, implant a rogue device key via setSetting to bypass the pairing
  // ceremony, or stamp app.last_run_version to the current version to suppress the backup
  // that would otherwise run before a migration.
  //
  // This is an ALLOWLIST, not a denylist of known-sensitive prefixes: only the exact key
  // shapes the renderer legitimately uses today are permitted, so a future sensitive key
  // is unreachable by construction instead of depending on someone remembering to add it
  // to a blocklist. Verified against every getSetting/setSetting call site under
  // windows/src/renderer and windows/src/preload -- the only caller is BadHabitsPage.tsx,
  // which reads/writes exactly:
  //   badhabits:<date>:alcohol:level
  //   badhabits:<date>:weed:level
  // <date> comes from useDateNav's yyyy-MM-dd string but is matched generically here
  // (any run of non-colon characters) rather than re-validated as a date, since this guard
  // is about which *keys* are reachable, not about revalidating renderer-owned data shape.
  const ALLOWED_SETTING_KEY = /^badhabits:[^:]+:(?:alcohol|weed):level$/

  const assertSettingKeyAllowed = (key: string): void => {
    if (!ALLOWED_SETTING_KEY.test(key)) {
      throw new Error('Access to this setting is not permitted via this channel')
    }
  }

  ipcMain.handle('db:getSetting', (_e, key: string) => {
    assertSettingKeyAllowed(key)
    const row: any = db.prepare('SELECT value FROM settings WHERE key = ?').get(key)
    return row ? row.value : null
  })

  ipcMain.handle('db:setSetting', (_e, key: string, value: string) => {
    assertSettingKeyAllowed(key)
    db.prepare('INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)').run(key, value)
  })
}

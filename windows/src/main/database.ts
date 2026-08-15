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

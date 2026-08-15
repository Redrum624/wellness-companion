/**
 * At-rest encryption: key wrapping, cipher-open sequencing, the checkpoint+copy
 * backup and the plaintext -> encrypted migration (spec §4.1-§4.4).
 *
 * The SQLCipher binding is compiled against Electron's ABI (NODE_MODULE_VERSION
 * 130), so plain-Node Jest cannot construct it — the same limitation that made
 * Task 1 introduce the SettingsIO seam. Two seams keep these tests honest
 * without it:
 *   - `loadOrCreateDbKey()` takes a SafeStorageLike, so the whole key lifecycle
 *     (generate / re-open / unavailable / unwrap failure) runs for real against
 *     real files in a temp dir.
 *   - `migrateToEncryptedIfNeeded()` and `snapshotDatabaseFile()` take a
 *     database opener, so every file-level guarantee (never mutate the live
 *     file, tmp-then-swap, delete the tmp on failure, keep .plaintext.bak) is
 *     asserted against the real filesystem with a fake cipher engine.
 * The real cipher round-trip is proven separately by the packaged smoke, which
 * runs the shipped binding under Electron.
 *
 * Nothing here touches the user's live wellness.db: every path is under a fresh
 * mkdtemp directory.
 */
jest.mock('electron', () => ({
  app: { getPath: (): string => '', getVersion: (): string => '0.0.0' },
  ipcMain: { handle: jest.fn(), removeHandler: jest.fn() },
  safeStorage: {
    isEncryptionAvailable: (): boolean => false,
    encryptString: (): Buffer => {
      throw new Error('safeStorage must not be used directly in unit tests')
    },
    decryptString: (): string => {
      throw new Error('safeStorage must not be used directly in unit tests')
    }
  }
}))
jest.mock('better-sqlite3-multiple-ciphers', () => ({
  __esModule: true,
  default: class {
    constructor() {
      throw new Error('the native SQLite binding must not be constructed in unit tests')
    }
  }
}))

import { createHash } from 'crypto'
import { existsSync, mkdtempSync, readFileSync, renameSync, rmSync, writeFileSync } from 'fs'
import { tmpdir } from 'os'
import { basename, join } from 'path'

import * as DB from '../src/main/database'

const SQLITE_MAGIC = Buffer.from('SQLite format 3\0', 'latin1')

let dir: string
beforeEach(() => {
  dir = mkdtempSync(join(tmpdir(), 'wc-at-rest-'))
})
afterEach(() => {
  rmSync(dir, { recursive: true, force: true })
})

// ── safeStorage doubles ─────────────────────────────────────────────────────
/** DPAPI stand-in: a reversible transform, so "wrapped" is never the raw key. */
function workingSafeStorage(): DB.SafeStorageLike {
  return {
    isEncryptionAvailable: () => true,
    encryptString: (plain: string) => Buffer.from(`wrapped:${plain}`, 'utf8'),
    decryptString: (blob: Buffer) => {
      const s = blob.toString('utf8')
      if (!s.startsWith('wrapped:')) throw new Error('not a DPAPI blob')
      return s.slice('wrapped:'.length)
    }
  }
}

function unavailableSafeStorage(): DB.SafeStorageLike {
  return {
    isEncryptionAvailable: () => false,
    encryptString: () => {
      throw new Error('encryption is not available')
    },
    decryptString: () => {
      throw new Error('encryption is not available')
    }
  }
}

// ── fake cipher engine ──────────────────────────────────────────────────────
// A file is "plaintext" when it starts with the SQLite magic; rekey() rewrites
// it with a key-derived header, so ciphertext/plaintext is a real property of
// the bytes on disk rather than a flag the test sets.
interface FakeFile {
  rows: number
  keyHex: string | null
}

function writePlainDb(path: string, rows: number): void {
  writeFileSync(path, Buffer.concat([SQLITE_MAGIC, Buffer.from(JSON.stringify({ rows }), 'utf8')]))
}

function readFake(path: string): FakeFile {
  const buf = readFileSync(path)
  if (buf.subarray(0, SQLITE_MAGIC.length).equals(SQLITE_MAGIC)) {
    return { ...JSON.parse(buf.subarray(SQLITE_MAGIC.length).toString('utf8')), keyHex: null }
  }
  const body = JSON.parse(buf.subarray(16).toString('utf8'))
  return { rows: body.rows, keyHex: body.keyHex }
}

function writeCipherDb(path: string, rows: number, keyHex: string): void {
  const header = createHash('sha256').update(keyHex).digest().subarray(0, 16)
  writeFileSync(path, Buffer.concat([header, Buffer.from(JSON.stringify({ rows, keyHex }), 'utf8')]))
}

type FailPoint = 'open' | 'rekey' | 'verify-open' | 'verify-read' | null

class FakeDb {
  readonly calls: string[] = []
  private unlocked: boolean
  private state: FakeFile

  constructor(
    private readonly path: string,
    private readonly failAt: FailPoint,
    private readonly phase: 'migrate' | 'verify'
  ) {
    this.state = readFake(path)
    this.unlocked = this.state.keyHex === null
  }

  pragma(source: string): unknown {
    this.calls.push(`pragma:${source}`)
    return []
  }

  rekey(key: Buffer): number {
    this.calls.push('rekey')
    if (this.failAt === 'rekey') throw new Error('simulated rekey failure')
    writeCipherDb(this.path, this.state.rows, key.toString('hex'))
    this.state = readFake(this.path)
    this.unlocked = true
    return 0
  }

  key(key: Buffer): number {
    this.calls.push('key')
    this.unlocked = this.state.keyHex === key.toString('hex')
    return 0
  }

  prepare(sql: string): { get: () => unknown } {
    this.calls.push(`prepare:${sql}`)
    return {
      get: () => {
        if (this.phase === 'verify' && this.failAt === 'verify-read') {
          throw new Error('simulated verification read failure')
        }
        if (!this.unlocked) throw new Error('file is not a database')
        return { c: this.state.rows }
      }
    }
  }

  close(): void {
    this.calls.push('close')
  }
}

function fakeOpener(failAt: FailPoint = null): {
  open: DB.CipherDbOpener
  opened: FakeDb[]
} {
  const opened: FakeDb[] = []
  const open: DB.CipherDbOpener = (path: string) => {
    const phase = opened.length === 0 ? 'migrate' : 'verify'
    if (failAt === 'open' && phase === 'migrate') throw new Error('simulated open failure')
    if (failAt === 'verify-open' && phase === 'verify') throw new Error('simulated verify-open failure')
    const db = new FakeDb(path, failAt, phase)
    opened.push(db)
    return db as unknown as ReturnType<DB.CipherDbOpener>
  }
  return { open, opened }
}

// ── §4.1 key management ─────────────────────────────────────────────────────
describe('loadOrCreateDbKey (spec §4.1)', () => {
  test('a first run mints a 32-byte key and stores it WRAPPED beside the db', () => {
    const ss = workingSafeStorage()
    const outcome = DB.loadOrCreateDbKey(dir, ss)

    expect(outcome.status).toBe('ready')
    if (outcome.status !== 'ready') return
    expect(outcome.key).toHaveLength(32)
    expect(outcome.created).toBe(true)

    const keyPath = join(dir, 'wellness.key')
    expect(existsSync(keyPath)).toBe(true)
    const stored = readFileSync(keyPath)
    // The key file is outside wellness.db on purpose (the settings table that
    // holds the device keys lives inside the db this key protects), and what
    // lands there must be the wrapped blob, never the raw key.
    expect(stored.includes(outcome.key)).toBe(false)
    expect(stored.toString('utf8').startsWith('wrapped:')).toBe(true)
    expect(existsSync(join(dir, 'wellness.key.tmp'))).toBe(false)
  })

  test('a later launch unwraps the SAME key', () => {
    const ss = workingSafeStorage()
    const first = DB.loadOrCreateDbKey(dir, ss)
    const second = DB.loadOrCreateDbKey(dir, ss)

    expect(first.status).toBe('ready')
    expect(second.status).toBe('ready')
    if (first.status !== 'ready' || second.status !== 'ready') return
    expect(second.key.toString('hex')).toBe(first.key.toString('hex'))
    expect(second.created).toBe(false)
  })

  test('isEncryptionAvailable()===false on a first run writes NO key and runs unencrypted', () => {
    const outcome = DB.loadOrCreateDbKey(dir, unavailableSafeStorage())

    expect(outcome.status).toBe('unavailable')
    // Fail closed on key material: an unwrapped key on disk would be worse than
    // no encryption at all, because it looks encrypted.
    expect(existsSync(join(dir, 'wellness.key'))).toBe(false)
    expect(existsSync(join(dir, 'wellness.key.tmp'))).toBe(false)
  })

  test('an unwrap failure is an ERROR state — it never falls back to plaintext', () => {
    const ss = workingSafeStorage()
    DB.loadOrCreateDbKey(dir, ss)
    const keyPath = join(dir, 'wellness.key')
    const before = readFileSync(keyPath)

    const dbPath = join(dir, 'wellness.db')
    writePlainDb(dbPath, 3)
    const dbBefore = readFileSync(dbPath)

    const broken: DB.SafeStorageLike = {
      ...ss,
      decryptString: () => {
        throw new Error('DPAPI: the data is invalid for this user profile')
      }
    }
    const outcome = DB.loadOrCreateDbKey(dir, broken)

    expect(outcome.status).toBe('error')
    // Nothing is repaired by force: the key file and the database are exactly
    // as they were, so a fixed profile (or a restored key) recovers next launch.
    expect(readFileSync(keyPath).equals(before)).toBe(true)
    expect(readFileSync(dbPath).equals(dbBefore)).toBe(true)
  })

  test('a key file that cannot be unwrapped because safeStorage is gone is an ERROR, not "unavailable"', () => {
    DB.loadOrCreateDbKey(dir, workingSafeStorage())
    const outcome = DB.loadOrCreateDbKey(dir, unavailableSafeStorage())

    // A wellness.key on disk means the db is (or is about to be) encrypted;
    // silently continuing unencrypted would open an encrypted file blind.
    expect(outcome.status).toBe('error')
    expect(existsSync(join(dir, 'wellness.key'))).toBe(true)
  })

  test('key material of the wrong length is an ERROR state', () => {
    const ss = workingSafeStorage()
    DB.loadOrCreateDbKey(dir, ss)
    const short: DB.SafeStorageLike = {
      ...ss,
      decryptString: () => Buffer.alloc(8).toString('base64')
    }
    expect(DB.loadOrCreateDbKey(dir, short).status).toBe('error')
  })

  test('NO key is minted beside an already-encrypted database (finding 3)', () => {
    // wellness.key was lost but wellness.db is ciphertext. Minting would persist
    // a key that cannot open it, and the next launch would read that key back as
    // an established one — slipping past the "freshly minted" guard.
    const dbPath = join(dir, 'wellness.db')
    writeCipherDb(dbPath, 831, 'bb'.repeat(32))
    const before = readFileSync(dbPath)

    const outcome = DB.loadOrCreateDbKey(dir, workingSafeStorage())

    expect(outcome.status).toBe('error')
    expect(existsSync(join(dir, 'wellness.key'))).toBe(false)
    expect(existsSync(join(dir, 'wellness.key.tmp'))).toBe(false)
    expect(readFileSync(dbPath).equals(before)).toBe(true)
  })

  test('a key IS minted beside a plaintext database (migration must still run)', () => {
    writePlainDb(join(dir, 'wellness.db'), 5)
    expect(DB.loadOrCreateDbKey(dir, workingSafeStorage()).status).toBe('ready')
    expect(existsSync(join(dir, 'wellness.key'))).toBe(true)
  })
})

// ── §4.4 header detection ───────────────────────────────────────────────────
describe('isPlaintextSqliteFile (spec §4.4 — magic header, not a flag)', () => {
  test('detects a plaintext database, an encrypted one and a missing file', () => {
    const plain = join(dir, 'plain.db')
    const enc = join(dir, 'enc.db')
    writePlainDb(plain, 1)
    writeCipherDb(enc, 1, 'aa'.repeat(32))

    expect(DB.isPlaintextSqliteFile(plain)).toBe(true)
    expect(DB.isPlaintextSqliteFile(enc)).toBe(false)
    expect(DB.isPlaintextSqliteFile(join(dir, 'nope.db'))).toBe(false)
  })

  test('an empty (freshly created) file is not treated as plaintext to migrate', () => {
    const empty = join(dir, 'empty.db')
    writeFileSync(empty, Buffer.alloc(0))
    expect(DB.isPlaintextSqliteFile(empty)).toBe(false)
  })
})

// ── §4.4 migration ──────────────────────────────────────────────────────────
describe('migrateToEncryptedIfNeeded (spec §4.4)', () => {
  const key = Buffer.alloc(32, 7)

  test('migrates a plaintext db on a COPY, verifies a keyed read, then swaps', () => {
    const dbPath = join(dir, 'wellness.db')
    writePlainDb(dbPath, 831)
    const original = readFileSync(dbPath)

    const { open, opened } = fakeOpener()
    const result = DB.migrateToEncryptedIfNeeded(dbPath, key, open)

    expect(result.status).toBe('migrated')
    expect(result.rows).toBe(831)

    // The live path is now ciphertext and still readable under the key.
    expect(DB.isPlaintextSqliteFile(dbPath)).toBe(false)
    expect(readFake(dbPath)).toEqual({ rows: 831, keyHex: key.toString('hex') })

    // One .plaintext.bak cycle is kept, byte-identical to the original.
    const bak = join(dir, 'wellness.db.plaintext.bak')
    expect(existsSync(bak)).toBe(true)
    expect(readFileSync(bak).equals(original)).toBe(true)

    // No working file survives.
    expect(existsSync(join(dir, 'wellness.db.premigration.tmp'))).toBe(false)

    // Order per spec: non-WAL before rekey, WAL after, and the verification is
    // a real keyed read against a SEPARATE handle.
    const migrate = opened[0].calls
    expect(migrate.indexOf('pragma:journal_mode = DELETE')).toBeLessThan(migrate.indexOf('rekey'))
    expect(migrate.indexOf("pragma:cipher='sqlcipher'")).toBeLessThan(migrate.indexOf('rekey'))
    expect(migrate.indexOf('pragma:legacy=4')).toBeLessThan(migrate.indexOf('rekey'))
    expect(migrate.indexOf('rekey')).toBeLessThan(migrate.indexOf('pragma:journal_mode = WAL'))
    expect(opened).toHaveLength(2)
    expect(opened[1].calls).toEqual(
      expect.arrayContaining(['key', 'prepare:SELECT count(*) FROM entries'])
    )
  })

  test('an already-encrypted database is left completely alone', () => {
    const dbPath = join(dir, 'wellness.db')
    writeCipherDb(dbPath, 12, key.toString('hex'))
    const before = readFileSync(dbPath)

    const { open, opened } = fakeOpener()
    expect(DB.migrateToEncryptedIfNeeded(dbPath, key, open).status).toBe('not_needed')
    expect(opened).toHaveLength(0)
    expect(readFileSync(dbPath).equals(before)).toBe(true)
    expect(existsSync(join(dir, 'wellness.db.plaintext.bak'))).toBe(false)
  })

  test('a missing database (fresh install) needs no migration', () => {
    const { open } = fakeOpener()
    expect(DB.migrateToEncryptedIfNeeded(join(dir, 'wellness.db'), key, open).status).toBe(
      'not_needed'
    )
  })

  for (const failAt of ['open', 'rekey', 'verify-open', 'verify-read'] as const) {
    test(`a failure at ${failAt} leaves the ORIGINAL plaintext db intact and readable`, () => {
      const dbPath = join(dir, 'wellness.db')
      writePlainDb(dbPath, 42)
      const original = readFileSync(dbPath)

      const { open } = fakeOpener(failAt)
      const result = DB.migrateToEncryptedIfNeeded(dbPath, key, open)

      expect(result.status).toBe('failed')
      expect(result.reason).toBeTruthy()

      // The live file is byte-for-byte what it was: still plaintext, still the
      // user's data. The app runs unencrypted this session and retries later.
      expect(readFileSync(dbPath).equals(original)).toBe(true)
      expect(DB.isPlaintextSqliteFile(dbPath)).toBe(true)
      expect(readFake(dbPath).rows).toBe(42)

      // The half-migrated working copy is removed, never left to be mistaken
      // for a backup.
      expect(existsSync(join(dir, 'wellness.db.premigration.tmp'))).toBe(false)
      expect(existsSync(join(dir, 'wellness.db.plaintext.bak'))).toBe(false)
    })
  }

  test('a failed SWAP renames the original back, intact and readable (finding 2)', () => {
    const dbPath = join(dir, 'wellness.db')
    const tmpPath = join(dir, 'wellness.db.premigration.tmp')
    const bakPath = join(dir, 'wellness.db.plaintext.bak')
    writePlainDb(dbPath, 77)
    const original = readFileSync(dbPath)

    // Only the tmp -> live rename fails; the live -> bak rename and the restore
    // both run for real, so the test actually enters the post-rename world.
    const attempted: string[] = []
    const rename: DB.RenameFn = (from, to) => {
      attempted.push(`${basename(from)} -> ${basename(to)}`)
      if (from === tmpPath && to === dbPath) throw new Error('simulated swap failure')
      renameSync(from, to)
    }

    const { open } = fakeOpener()
    const result = DB.migrateToEncryptedIfNeeded(dbPath, key, open, rename)

    expect(result.status).toBe('failed')
    expect(attempted).toEqual([
      'wellness.db -> wellness.db.plaintext.bak',
      'wellness.db.premigration.tmp -> wellness.db',
      'wellness.db.plaintext.bak -> wellness.db'
    ])

    // The original is back where it belongs, byte-identical and still readable.
    expect(existsSync(dbPath)).toBe(true)
    expect(readFileSync(dbPath).equals(original)).toBe(true)
    expect(DB.isPlaintextSqliteFile(dbPath)).toBe(true)
    expect(readFake(dbPath).rows).toBe(77)

    // No leftovers: neither the working copy nor a half-cycle backup.
    expect(existsSync(tmpPath)).toBe(false)
    expect(existsSync(bakPath)).toBe(false)
  })

  test('a swap that fails AND cannot restore reports database_missing and keeps BOTH survivors', () => {
    const dbPath = join(dir, 'wellness.db')
    const tmpPath = join(dir, 'wellness.db.premigration.tmp')
    const bakPath = join(dir, 'wellness.db.plaintext.bak')
    writePlainDb(dbPath, 9)

    const rename: DB.RenameFn = (from, to) => {
      if (from === tmpPath && to === dbPath) throw new Error('simulated swap failure')
      if (from === bakPath && to === dbPath) throw new Error('simulated restore failure')
      renameSync(from, to)
    }

    const { open } = fakeOpener()
    const result = DB.migrateToEncryptedIfNeeded(dbPath, key, open, rename)

    // Distinct from 'failed': there is no usable database at dbPath, so the
    // caller must NOT reopen (and therefore create) anything there.
    expect(result.status).toBe('database_missing')
    expect(result.reason).toContain('premigration')

    expect(existsSync(dbPath)).toBe(false)
    expect(existsSync(tmpPath)).toBe(true)
    expect(existsSync(bakPath)).toBe(true)
    expect(readFake(tmpPath)).toEqual({ rows: 9, keyHex: key.toString('hex') })
    expect(readFake(bakPath).rows).toBe(9)
  })

  test('the launch AFTER a database_missing swap recovers instead of starting empty', () => {
    const dbPath = join(dir, 'wellness.db')
    const tmpPath = join(dir, 'wellness.db.premigration.tmp')
    const bakPath = join(dir, 'wellness.db.plaintext.bak')
    writePlainDb(dbPath, 314)

    const rename: DB.RenameFn = (from, to) => {
      if (from === tmpPath && to === dbPath) throw new Error('simulated swap failure')
      if (from === bakPath && to === dbPath) throw new Error('simulated restore failure')
      renameSync(from, to)
    }
    expect(DB.migrateToEncryptedIfNeeded(dbPath, key, fakeOpener().open, rename).status).toBe(
      'database_missing'
    )

    // Next launch: recovery runs BEFORE any open and reinstates the encrypted
    // copy with every row intact — no empty database is ever created.
    const recovery = DB.recoverInterruptedSwap(dbPath, key, fakeOpener().open)
    expect(recovery).toMatchObject({ status: 'recovered', from: 'premigration', rows: 314 })
    expect(readFake(dbPath)).toEqual({ rows: 314, keyHex: key.toString('hex') })
    expect(existsSync(bakPath)).toBe(true)
  })

  test('the .plaintext.bak is REPLACED by the rename, never pre-deleted', () => {
    const dbPath = join(dir, 'wellness.db')
    const bakPath = join(dir, 'wellness.db.plaintext.bak')
    writePlainDb(dbPath, 21)
    writePlainDb(bakPath, 1) // a previous cycle

    // Assert the old backup is still on disk at the moment the rename happens:
    // a pre-delete would have removed it and widened the window.
    let bakPresentAtRename: boolean | null = null
    const rename: DB.RenameFn = (from, to) => {
      if (from === dbPath && to === bakPath) bakPresentAtRename = existsSync(bakPath)
      renameSync(from, to)
    }

    expect(DB.migrateToEncryptedIfNeeded(dbPath, key, fakeOpener().open, rename).status).toBe(
      'migrated'
    )
    expect(bakPresentAtRename).toBe(true)
    // One cycle kept, and it is the database that was just migrated.
    expect(readFake(bakPath).rows).toBe(21)
  })

  test('a stale .premigration.tmp from a killed run is replaced, not appended to', () => {
    const dbPath = join(dir, 'wellness.db')
    const tmpPath = join(dir, 'wellness.db.premigration.tmp')
    writePlainDb(dbPath, 5)
    writeFileSync(tmpPath, Buffer.from('garbage from a killed migration'))

    const { open } = fakeOpener()
    expect(DB.migrateToEncryptedIfNeeded(dbPath, key, open).status).toBe('migrated')
    expect(existsSync(tmpPath)).toBe(false)
    expect(readFake(dbPath).rows).toBe(5)
  })
})

// ── §4.4 interrupted-swap recovery (finding 1) ──────────────────────────────
describe('recoverInterruptedSwap (spec §4.4 amended)', () => {
  const key = Buffer.alloc(32, 7)
  const dbPathOf = (): string => join(dir, 'wellness.db')

  test('an ordinary launch with wellness.db present does nothing', () => {
    writePlainDb(dbPathOf(), 3)
    const { open, opened } = fakeOpener()
    expect(DB.recoverInterruptedSwap(dbPathOf(), key, open).status).toBe('not_needed')
    expect(opened).toHaveLength(0)
  })

  test('a ZERO-BYTE wellness.db does not count as a database — recovery still runs', () => {
    // The artifact of an interrupted create. Early-returning on existsSync alone
    // would skip recovery forever and leave the user staring at an empty app.
    writeFileSync(dbPathOf(), Buffer.alloc(0))
    writeCipherDb(`${dbPathOf()}.premigration.tmp`, 831, key.toString('hex'))

    const result = DB.recoverInterruptedSwap(dbPathOf(), key, fakeOpener().open)

    expect(result).toMatchObject({ status: 'recovered', from: 'premigration', rows: 831 })
    expect(readFake(dbPathOf())).toEqual({ rows: 831, keyHex: key.toString('hex') })
  })

  test('a zero-byte wellness.db with NO survivors is left alone (aborted first run)', () => {
    writeFileSync(dbPathOf(), Buffer.alloc(0))
    expect(DB.recoverInterruptedSwap(dbPathOf(), key, fakeOpener().open).status).toBe('not_needed')
    expect(existsSync(dbPathOf())).toBe(true)
  })

  test('a genuine fresh install (nothing on disk) is not mistaken for a crash', () => {
    const { open } = fakeOpener()
    expect(DB.recoverInterruptedSwap(dbPathOf(), key, open).status).toBe('not_needed')
    expect(existsSync(dbPathOf())).toBe(false)
  })

  test('prefers the encrypted .premigration.tmp when it opens under the key', () => {
    // The process died between the two renames: no wellness.db, both survivors.
    const tmpPath = `${dbPathOf()}.premigration.tmp`
    const bakPath = `${dbPathOf()}.plaintext.bak`
    writeCipherDb(tmpPath, 831, key.toString('hex'))
    writePlainDb(bakPath, 831)

    const { open } = fakeOpener()
    const result = DB.recoverInterruptedSwap(dbPathOf(), key, open)

    expect(result).toMatchObject({ status: 'recovered', from: 'premigration', rows: 831 })
    expect(existsSync(dbPathOf())).toBe(true)
    expect(DB.isPlaintextSqliteFile(dbPathOf())).toBe(false)
    expect(readFake(dbPathOf())).toEqual({ rows: 831, keyHex: key.toString('hex') })
    expect(existsSync(tmpPath)).toBe(false)
    // The plaintext cycle is left exactly where it was.
    expect(existsSync(bakPath)).toBe(true)
  })

  test('falls back to the .plaintext.bak when the tmp cannot be read', () => {
    const tmpPath = `${dbPathOf()}.premigration.tmp`
    const bakPath = `${dbPathOf()}.plaintext.bak`
    // A tmp encrypted under a DIFFERENT key: verification must fail.
    writeCipherDb(tmpPath, 12, 'cc'.repeat(32))
    writePlainDb(bakPath, 831)

    const result = DB.recoverInterruptedSwap(dbPathOf(), key, fakeOpener().open)

    expect(result).toMatchObject({ status: 'recovered', from: 'plaintext_backup' })
    expect(DB.isPlaintextSqliteFile(dbPathOf())).toBe(true)
    expect(readFake(dbPathOf()).rows).toBe(831)
    // The unreadable tmp is left alone rather than deleted.
    expect(existsSync(tmpPath)).toBe(true)
  })

  test('restores the .plaintext.bak when there is no tmp at all', () => {
    writePlainDb(`${dbPathOf()}.plaintext.bak`, 44)
    expect(DB.recoverInterruptedSwap(dbPathOf(), key, fakeOpener().open)).toMatchObject({
      status: 'recovered',
      from: 'plaintext_backup'
    })
    expect(readFake(dbPathOf()).rows).toBe(44)
  })

  test('an unreadable tmp with NO backup is unrecoverable — never a fresh start', () => {
    const tmpPath = `${dbPathOf()}.premigration.tmp`
    writeCipherDb(tmpPath, 5, 'dd'.repeat(32))

    const result = DB.recoverInterruptedSwap(dbPathOf(), key, fakeOpener().open)

    expect(result.status).toBe('unrecoverable')
    // The caller fails closed on this; nothing was created or destroyed.
    expect(existsSync(dbPathOf())).toBe(false)
    expect(existsSync(tmpPath)).toBe(true)
  })

  test('a tmp with no key available is unrecoverable rather than silently empty', () => {
    writeCipherDb(`${dbPathOf()}.premigration.tmp`, 5, key.toString('hex'))
    const result = DB.recoverInterruptedSwap(dbPathOf(), null, fakeOpener().open)
    expect(result.status).toBe('unrecoverable')
    expect(existsSync(dbPathOf())).toBe(false)
  })

  test('reports unrecoverable when even the restore rename fails', () => {
    writePlainDb(`${dbPathOf()}.plaintext.bak`, 44)
    const rename: DB.RenameFn = () => {
      throw new Error('simulated rename failure')
    }
    const result = DB.recoverInterruptedSwap(dbPathOf(), key, fakeOpener().open, rename)
    expect(result.status).toBe('unrecoverable')
    expect(existsSync(dbPathOf())).toBe(false)
  })
})

// ── §4.3 backup ─────────────────────────────────────────────────────────────
describe('snapshotDatabaseFile (spec §4.3 — checkpoint + copy replaces db.backup())', () => {
  test('checkpoints first, then copies the raw (already-encrypted) file via a tmp', () => {
    const dbPath = join(dir, 'wellness.db')
    const key = Buffer.alloc(32, 3)
    writeCipherDb(dbPath, 100, key.toString('hex'))
    const source = readFileSync(dbPath)

    const calls: string[] = []
    const handle = {
      pragma: (sql: string): unknown => {
        calls.push(sql)
        // The copy must see a checkpointed file, so the pragma runs first.
        expect(existsSync(join(dir, 'backup.db'))).toBe(false)
        return []
      }
    }

    const dest = join(dir, 'backup.db')
    DB.snapshotDatabaseFile(handle, dbPath, `${dest}.tmp`, dest)

    expect(calls).toEqual(['wal_checkpoint(TRUNCATE)'])
    expect(readFileSync(dest).equals(source)).toBe(true)
    // The backup of an encrypted database is itself ciphertext (this is the
    // whole reason db.backup() had to go).
    expect(readFileSync(dest).subarray(0, 16).equals(SQLITE_MAGIC)).toBe(false)
    expect(existsSync(`${dest}.tmp`)).toBe(false)
  })

  test('waitForPendingBackup still resolves (the copy is synchronous now)', async () => {
    await expect(DB.waitForPendingBackup(50)).resolves.toBeUndefined()
  })
})

/**
 * Device-key / pending-pairing / pairing-backoff persistence (spec §2.8).
 *
 * database.ts pulls in electron and the native SQLite binding (built for
 * Electron's ABI, not plain Node), so both are stubbed here: these helpers
 * are exercised against an in-memory SettingsIO, which is exactly the seam the
 * production code uses over the `settings` table. Nothing here touches the
 * user's live wellness.db.
 */
jest.mock('electron', () => ({
  app: { getPath: (): string => '', getVersion: (): string => '0.0.0' },
  ipcMain: { handle: jest.fn(), removeHandler: jest.fn() }
}))
jest.mock('better-sqlite3-multiple-ciphers', () => ({
  __esModule: true,
  default: class {
    constructor() {
      throw new Error('the native SQLite binding must not be constructed in unit tests')
    }
  }
}))

import * as DB from '../src/main/database'

function memIO(): DB.SettingsIO & { map: Map<string, string> } {
  const map = new Map<string, string>()
  return {
    map,
    get: (key: string) => (map.has(key) ? (map.get(key) as string) : null),
    set: (key: string, value: string) => void map.set(key, value)
  }
}

const secret = (n: number): string => Buffer.alloc(16, n).toString('base64')

function device(n: number, lastSeen: number): DB.DeviceKeyRecord {
  return {
    keyId: `key-${n}`,
    secret_b64: secret(n),
    label: `Phone ${n}`,
    created: 1000,
    lastSeen
  }
}

describe('device_keys store', () => {
  test('starts empty and tolerates an absent or corrupt blob', () => {
    const io = memIO()
    expect(DB.getDeviceKeys(io)).toEqual({})
    io.set(DB.DEVICE_KEYS_SETTING, 'not json{')
    expect(DB.getDeviceKeys(io)).toEqual({})
    io.set(DB.DEVICE_KEYS_SETTING, '[1,2,3]')
    expect(DB.getDeviceKeys(io)).toEqual({})
  })

  test('is upserted BY deviceId — a re-pair replaces the slot, never appends', () => {
    const io = memIO()
    DB.upsertDeviceKey('dev-a', device(1, 5000), io)
    DB.upsertDeviceKey('dev-a', { ...device(2, 9000), label: 'Renamed' }, io)

    const keys = DB.getDeviceKeys(io)
    expect(Object.keys(keys)).toEqual(['dev-a'])
    expect(keys['dev-a'].keyId).toBe('key-2')
    expect(keys['dev-a'].secret_b64).toBe(secret(2))
    expect(keys['dev-a'].label).toBe('Renamed')
  })

  test('a second device gets its own slot', () => {
    const io = memIO()
    DB.upsertDeviceKey('dev-a', device(1, 5000), io)
    DB.upsertDeviceKey('dev-b', device(2, 6000), io)
    expect(Object.keys(DB.getDeviceKeys(io)).sort()).toEqual(['dev-a', 'dev-b'])
  })

  test('caps at MAX_STORED_DEVICE_KEYS by evicting the least-recently-seen', () => {
    const io = memIO()
    expect(DB.MAX_STORED_DEVICE_KEYS).toBe(8)
    for (let i = 0; i < DB.MAX_STORED_DEVICE_KEYS; i++) {
      DB.upsertDeviceKey(`dev-${i}`, device(i, 1000 + i), io)
    }
    expect(Object.keys(DB.getDeviceKeys(io)).length).toBe(DB.MAX_STORED_DEVICE_KEYS)

    DB.upsertDeviceKey('dev-new', device(99, 99999), io)
    const keys = DB.getDeviceKeys(io)
    expect(Object.keys(keys).length).toBe(DB.MAX_STORED_DEVICE_KEYS)
    expect(keys['dev-0']).toBeUndefined() // oldest lastSeen evicted
    expect(keys['dev-new']).toBeDefined()
    expect(keys['dev-7']).toBeDefined()
  })

  test('removeDeviceKey revokes exactly one device', () => {
    const io = memIO()
    DB.upsertDeviceKey('dev-a', device(1, 5000), io)
    DB.upsertDeviceKey('dev-b', device(2, 6000), io)
    expect(DB.removeDeviceKey('dev-a', io)).toBe(true)
    expect(DB.removeDeviceKey('dev-a', io)).toBe(false)
    expect(Object.keys(DB.getDeviceKeys(io))).toEqual(['dev-b'])
  })

  test('clearPairings is the last-resort forget-everything path', () => {
    const io = memIO()
    DB.upsertDeviceKey('dev-a', device(1, 5000), io)
    DB.putPending('key-x', { secret_b64: secret(3), created: 0, ttlMs: 60_000 }, io)
    DB.clearPairings(io)
    expect(DB.getDeviceKeys(io)).toEqual({})
    expect(DB.getPending(1000, io)).toEqual({})
  })
})

describe('pending pairing slots', () => {
  test('a pending slot is readable inside its TTL and gone after it', () => {
    const io = memIO()
    DB.putPending('key-x', { secret_b64: secret(4), created: 10_000, ttlMs: 300_000 }, io)
    expect(DB.getPending(10_000, io)['key-x'].secret_b64).toBe(secret(4))
    expect(DB.getPending(309_999, io)['key-x']).toBeDefined()
    expect(DB.getPending(310_001, io)['key-x']).toBeUndefined()
  })

  test('reading live slots writes NOTHING (an unauthenticated hs1 costs no write)', () => {
    const io = memIO()
    DB.putPending('key-x', { secret_b64: secret(4), created: 0, ttlMs: 300_000 }, io)
    const writes: string[] = []
    const counting = { get: io.get, set: (k: string, v: string) => void (writes.push(k), io.set(k, v)) }

    DB.getPending(1000, counting)
    DB.getPending(2000, counting)
    DB.getDeviceKeys(counting)
    expect(writes).toEqual([])
  })

  test('expired slots are pruned from storage, not just filtered on read', () => {
    const io = memIO()
    DB.putPending('key-x', { secret_b64: secret(4), created: 0, ttlMs: 1000 }, io)
    DB.getPending(5000, io)
    expect(JSON.parse(io.map.get(DB.PENDING_PAIRINGS_SETTING) as string)).toEqual({})
  })

  test('bindPending promotes the slot to a permanent device key with the SAME secret', () => {
    const io = memIO()
    DB.putPending('key-x', { secret_b64: secret(5), created: 0, ttlMs: 300_000 }, io)

    const bound = DB.bindPending('key-x', 'dev-a', 'Pixel 7', 1000, io)
    expect(bound).not.toBeNull()
    expect(bound?.secret_b64).toBe(secret(5))
    expect(bound?.keyId).toBe('key-x')
    expect(bound?.label).toBe('Pixel 7')

    // The pending slot is consumed, and the device is now permanent.
    expect(DB.getPending(1000, io)['key-x']).toBeUndefined()
    expect(DB.getDeviceKeys(io)['dev-a'].secret_b64).toBe(secret(5))
  })

  test('bindPending on an unknown or expired keyId returns null and stores nothing', () => {
    const io = memIO()
    expect(DB.bindPending('nope', 'dev-a', 'Phone', 1000, io)).toBeNull()

    DB.putPending('key-x', { secret_b64: secret(6), created: 0, ttlMs: 1000 }, io)
    expect(DB.bindPending('key-x', 'dev-a', 'Phone', 50_000, io)).toBeNull()
    expect(DB.getDeviceKeys(io)).toEqual({})
  })

  test('re-pairing the same phone replaces its slot rather than orphaning it', () => {
    const io = memIO()
    DB.putPending('key-1', { secret_b64: secret(7), created: 0, ttlMs: 300_000 }, io)
    DB.bindPending('key-1', 'dev-a', 'Phone', 100, io)
    DB.putPending('key-2', { secret_b64: secret(8), created: 0, ttlMs: 300_000 }, io)
    DB.bindPending('key-2', 'dev-a', 'Phone', 200, io)

    const keys = DB.getDeviceKeys(io)
    expect(Object.keys(keys)).toEqual(['dev-a'])
    expect(keys['dev-a'].keyId).toBe('key-2')
    expect(keys['dev-a'].secret_b64).toBe(secret(8))
  })
})

describe('persistent pairing backoff', () => {
  test('is global and survives a restart (it lives in settings, not a socket)', () => {
    const io = memIO()
    for (let i = 0; i < 5; i++) DB.bumpBackoff(1_000_000, io)
    // A "restart" is just a fresh read of the same store.
    const state = DB.getBackoff(1_000_000, io)
    expect(state.failures).toBe(5)
    expect(state.lockedOut).toBe(true)
    expect(state.lockedForMs).toBeGreaterThan(0)
  })

  test('the first few failures do not lock out, then lockout grows exponentially', () => {
    const io = memIO()
    expect(DB.bumpBackoff(0, io).lockedOut).toBe(false)
    expect(DB.bumpBackoff(0, io).lockedOut).toBe(false)
    expect(DB.bumpBackoff(0, io).lockedOut).toBe(false)
    const first = DB.bumpBackoff(0, io)
    expect(first.lockedOut).toBe(true)
    const second = DB.bumpBackoff(0, io)
    expect(second.lockedForMs).toBeGreaterThan(first.lockedForMs)
  })

  test('the lockout expires with the clock and is capped', () => {
    const io = memIO()
    for (let i = 0; i < 30; i++) DB.bumpBackoff(0, io)
    const locked = DB.getBackoff(0, io)
    expect(locked.lockedForMs).toBeLessThanOrEqual(DB.PAIR_BACKOFF_MAX_MS)
    expect(DB.getBackoff(locked.lockedUntil + 1, io).lockedOut).toBe(false)
  })

  test('a successful pairing resets it', () => {
    const io = memIO()
    for (let i = 0; i < 6; i++) DB.bumpBackoff(0, io)
    expect(DB.getBackoff(0, io).lockedOut).toBe(true)
    DB.resetBackoff(io)
    expect(DB.getBackoff(0, io)).toEqual({
      failures: 0,
      lockedUntil: 0,
      lockedOut: false,
      lockedForMs: 0
    })
  })
})

/**
 * Fail-closed downgrade suite (spec §2.9 / P-F2, I-1, I-6).
 *
 * These tests drive the exported frame handler against a fake socket, so the
 * whole state machine runs without a real WebSocket, without Electron and
 * without a database. `getDatabase()` in the mock THROWS by default: any
 * plaintext path that reached the ingest pipeline would fail loudly rather
 * than quietly moving rows.
 */
jest.mock('electron', () => ({
  ipcMain: { handle: jest.fn(), removeHandler: jest.fn() },
  BrowserWindow: { getAllWindows: (): unknown[] => [] }
}))

jest.mock('../src/main/database', () => {
  let deviceKeys: Record<string, any> = {}
  let pending: Record<string, any> = {}
  let backoff = { failures: 0, lockedUntil: 0, lockedOut: false, lockedForMs: 0 }
  let db: any = null
  const dbCalls: string[] = []
  return {
    __reset: (): void => {
      deviceKeys = {}
      pending = {}
      backoff = { failures: 0, lockedUntil: 0, lockedOut: false, lockedForMs: 0 }
      db = null
      dbCalls.length = 0
    },
    __setDeviceKey: (deviceId: string, rec: any): void => void (deviceKeys[deviceId] = rec),
    __setPending: (keyId: string, slot: any): void => void (pending[keyId] = slot),
    __setBackoff: (b: any): void => void (backoff = b),
    __setDb: (fake: any): void => void (db = fake),
    __dbCalls: dbCalls,
    getDatabase: (): unknown => {
      dbCalls.push('getDatabase')
      if (!db) throw new Error('getDatabase() reached from a path that must never touch data')
      return db
    },
    getDeviceKeys: (): unknown => deviceKeys,
    upsertDeviceKey: (deviceId: string, rec: any): void => void (deviceKeys[deviceId] = rec),
    removeDeviceKey: (deviceId: string): boolean => delete deviceKeys[deviceId],
    clearPairings: (): void => {
      deviceKeys = {}
      pending = {}
    },
    getPending: (): unknown => pending,
    putPending: (keyId: string, slot: any): void => void (pending[keyId] = slot),
    bindPending: (keyId: string, deviceId: string, label: string, now: number): unknown => {
      const slot = pending[keyId]
      if (!slot) return null
      const rec = { keyId, secret_b64: slot.secret_b64, label, created: slot.created, lastSeen: now }
      deviceKeys[deviceId] = rec
      delete pending[keyId]
      return rec
    },
    getBackoff: (): unknown => backoff,
    // Mirrors the real policy's SHAPE (a few free attempts, then a lockout) so
    // the scoping tests below are meaningful; the real thresholds are covered
    // in device-keys.test.ts.
    bumpBackoff: (): unknown => {
      const failures = backoff.failures + 1
      const lockedOut = failures > 3
      backoff = {
        failures,
        lockedUntil: lockedOut ? Date.now() + 60_000 : 0,
        lockedOut,
        lockedForMs: lockedOut ? 60_000 : 0
      }
      return backoff
    },
    resetBackoff: (): void => void (backoff = {
      failures: 0,
      lockedUntil: 0,
      lockedOut: false,
      lockedForMs: 0
    })
  }
})

import { randomBytes } from 'node:crypto'
import * as sync from '../src/main/sync-server'
import * as X from '../src/main/sync-crypto'

const dbMock = jest.requireMock('../src/main/database') as any

interface FakeSocket {
  readyState: number
  sent: Array<{ binary: boolean; data: any }>
  closes: Array<{ code?: number; reason?: string }>
  send(data: string | Buffer): void
  close(code?: number, reason?: string): void
}

function fakeSocket(): FakeSocket {
  return {
    readyState: 1,
    sent: [],
    closes: [],
    send(data: string | Buffer): void {
      this.sent.push({ binary: Buffer.isBuffer(data), data })
    },
    close(code?: number, reason?: string): void {
      this.closes.push({ code, reason })
      this.readyState = 3
    }
  }
}

const text = (obj: unknown): Buffer => Buffer.from(JSON.stringify(obj), 'utf8')
const textFrames = (ws: FakeSocket): any[] => ws.sent.filter((f) => !f.binary)
const binaryFrames = (ws: FakeSocket): any[] => ws.sent.filter((f) => f.binary)
const lastText = (ws: FakeSocket): any => JSON.parse(textFrames(ws).slice(-1)[0].data)

// keyId is 4 random bytes as 8 lowercase hex; deviceId stays a phone-side UUID.
const KEY_ID = '1a2b3c4d'
const DEVICE_ID = '22222222-2222-4222-8222-222222222222'

/** Drives the phone half of the handshake, so this doubles as an interop check. */
function completeHandshake(
  ws: FakeSocket,
  conn: any,
  secret: Buffer,
  keyId: string = KEY_ID
): { kC2S: Buffer; kS2C: Buffer } {
  const helloJson: string = textFrames(ws)[0].data
  const hello = JSON.parse(helloJson)
  expect(hello.type).toBe('hello')
  expect(hello.version).toBe(4)
  expect(hello.crypto).toEqual(['x1'])
  expect(Buffer.from(hello.nonce_s, 'base64').length).toBe(32)

  const eph = X.generateEphemeral()
  const hs1Json = JSON.stringify({
    type: 'hs1',
    proto: 'x1',
    keyId,
    deviceId: DEVICE_ID,
    pub_c: eph.publicSpkiB64,
    nonce_c: randomBytes(32).toString('base64')
  })
  sync.handleFrame(conn, Buffer.from(hs1Json, 'utf8'), false)

  const hs2 = lastText(ws)
  expect(hs2.type).toBe('hs2')

  // Same transcript rule as the server: uint32-BE length-prefixed WIRE bytes,
  // pub_s as its base64 TEXT.
  const th = X.transcriptHash(
    Buffer.from(helloJson, 'utf8'),
    Buffer.from(hs1Json, 'utf8'),
    Buffer.from(hs2.pub_s, 'ascii')
  )
  const ss = X.ecdhSharedSecret(eph.priv, hs2.pub_s)
  const keys = X.deriveSession(ss, X.pskFromSecret(secret), th)
  expect(hs2.mac_s).toBe(X.macTag(keys.km, 'srv', th).toString('base64'))

  sync.handleFrame(
    conn,
    text({ type: 'hs3', mac_c: X.macTag(keys.km, 'cli', th).toString('base64') }),
    false
  )
  return { kC2S: keys.kC2S, kS2C: keys.kS2C }
}

function pairedSocket(): { ws: FakeSocket; conn: any; secret: Buffer } {
  const secret = X.randomPairingSecret()
  dbMock.__setPending(KEY_ID, { secret_b64: secret.toString('base64'), created: Date.now(), ttlMs: 300_000 })
  const ws = fakeSocket()
  const conn = sync.createConnection(ws as any)
  return { ws, conn, secret }
}

beforeEach(() => {
  dbMock.__reset()
})

describe('v3 plaintext tombstone', () => {
  test('plaintext auth is a tombstone: 0 rows, close 4005, token never validated', () => {
    const ws = fakeSocket()
    const conn = sync.createConnection(ws as any)

    sync.handleFrame(conn, text({ type: 'auth', token: 'ABCD2345' }), false)

    expect(lastText(ws)).toEqual({ type: 'error', code: 'update_required' })
    expect(ws.closes.slice(-1)[0].code).toBe(4005)
    // Nothing was read or written: no settings lookup, no ingest, no reply that
    // distinguishes a right code from a wrong one.
    expect(dbMock.__dbCalls).toEqual([])
    expect(textFrames(ws).some((f) => JSON.parse(f.data).type === 'auth_ok')).toBe(false)
  })

  test('a WRONG and a RIGHT-looking token get byte-identical treatment', () => {
    const a = fakeSocket()
    const b = fakeSocket()
    sync.handleFrame(sync.createConnection(a as any), text({ type: 'auth', token: 'AAAAAAAA' }), false)
    sync.handleFrame(sync.createConnection(b as any), text({ type: 'auth', token: '' }), false)
    expect(lastText(a)).toEqual(lastText(b))
    expect(a.closes.slice(-1)[0].code).toBe(b.closes.slice(-1)[0].code)
  })

  test('text full_sync after text auth ingests zero rows (the v3 data path is gone)', () => {
    const ws = fakeSocket()
    const conn = sync.createConnection(ws as any)

    sync.handleFrame(conn, text({ type: 'auth', token: 'ABCD2345' }), false)
    sync.handleFrame(
      conn,
      text({
        type: 'full_sync',
        since: 0,
        entries: [
          {
            id: 'evil-1',
            category: 'water',
            timestamp: 1,
            date: '2026-08-15',
            data: '{}',
            modified_at: 1
          }
        ]
      }),
      false
    )

    expect(dbMock.__dbCalls).toEqual([])
    expect(textFrames(ws).some((f) => JSON.parse(f.data).type === 'full_sync_response')).toBe(false)
    expect(binaryFrames(ws).length).toBe(0)
  })

  test.each(['full_sync', 'push', 'pull'])(
    'a bare plaintext %s is a tombstone too, with no ingest',
    (type) => {
      const ws = fakeSocket()
      const conn = sync.createConnection(ws as any)
      sync.handleFrame(conn, text({ type, since: 0, entries: [{ id: 'x' }] }), false)
      expect(lastText(ws)).toEqual({ type: 'error', code: 'update_required' })
      expect(ws.closes.slice(-1)[0].code).toBe(4005)
      expect(dbMock.__dbCalls).toEqual([])
    }
  )

  test('frames after the tombstone close are ignored entirely', () => {
    const ws = fakeSocket()
    const conn = sync.createConnection(ws as any)
    sync.handleFrame(conn, text({ type: 'auth', token: 'X' }), false)
    const after = ws.sent.length
    sync.handleFrame(conn, text({ type: 'full_sync', entries: [] }), false)
    sync.handleFrame(conn, text({ type: 'hs1' }), false)
    expect(ws.sent.length).toBe(after)
    expect(dbMock.__dbCalls).toEqual([])
  })
})

describe('channel gate', () => {
  test('a binary record before hs3 is rejected (no channel)', () => {
    const ws = fakeSocket()
    const conn = sync.createConnection(ws as any)
    const frame = X.sealRecord(randomBytes(32), 0n, X.DIR_C2S, { type: 'full_sync', entries: [] })

    sync.handleFrame(conn, frame, true)

    expect(conn.channelEstablished).toBe(false)
    expect(ws.closes.length).toBe(1)
    // Wrong channel for the current state: closed with NO reply.
    expect(textFrames(ws).length).toBe(1) // the hello only
    expect(dbMock.__dbCalls).toEqual([])
  })

  test('hs3 without hs1 cannot establish a channel', () => {
    const ws = fakeSocket()
    const conn = sync.createConnection(ws as any)
    sync.handleFrame(conn, text({ type: 'hs3', mac_c: Buffer.alloc(32).toString('base64') }), false)
    expect(conn.channelEstablished).toBe(false)
    expect(ws.closes.length).toBe(1)
  })

  test('an oversized handshake frame is dropped before any crypto', () => {
    const ws = fakeSocket()
    const conn = sync.createConnection(ws as any)
    sync.handleFrame(conn, Buffer.alloc(X.MAX_HANDSHAKE_FRAME_BYTES + 1, 0x20), false)
    expect(ws.closes.length).toBe(1)
    expect(conn.channelEstablished).toBe(false)
  })

  test('an unknown keyId is recoverable, not fatal: 4006 repair_required', () => {
    const ws = fakeSocket()
    const conn = sync.createConnection(ws as any)
    const eph = X.generateEphemeral()
    sync.handleFrame(
      conn,
      text({
        type: 'hs1',
        proto: 'x1',
        keyId: 'unknown-key',
        deviceId: DEVICE_ID,
        pub_c: eph.publicSpkiB64,
        nonce_c: randomBytes(32).toString('base64')
      }),
      false
    )
    expect(lastText(ws)).toEqual({ type: 'error', code: 'repair_required' })
    expect(ws.closes.slice(-1)[0].code).toBe(4006)
  })

  test('a mismatched device_key gets the SAME 4006 recovery as an unknown key', () => {
    const { ws, conn } = pairedSocket()
    const eph = X.generateEphemeral()
    sync.handleFrame(
      conn,
      text({
        type: 'hs1',
        proto: 'x1',
        keyId: KEY_ID,
        deviceId: DEVICE_ID,
        pub_c: eph.publicSpkiB64,
        nonce_c: randomBytes(32).toString('base64')
      }),
      false
    )
    // Confirm with a MAC derived from the wrong secret.
    sync.handleFrame(conn, text({ type: 'hs3', mac_c: randomBytes(32).toString('base64') }), false)

    expect(conn.channelEstablished).toBe(false)
    expect(lastText(ws)).toEqual({ type: 'error', code: 'repair_required' })
    expect(ws.closes.slice(-1)[0].code).toBe(4006)
  })

  test('an off-curve pub_c is rejected with no reply', () => {
    const v = require('../../shared/crypto-vectors.json')
    const { ws, conn } = pairedSocket()
    sync.handleFrame(
      conn,
      text({
        type: 'hs1',
        proto: 'x1',
        keyId: KEY_ID,
        deviceId: DEVICE_ID,
        pub_c: v.offcurve_spki,
        nonce_c: randomBytes(32).toString('base64')
      }),
      false
    )
    expect(conn.channelEstablished).toBe(false)
    expect(ws.closes.length).toBe(1)
    expect(textFrames(ws).length).toBe(1) // hello only
  })

  test('a lockout refuses further UNKNOWN-key attempts without evaluating them', () => {
    dbMock.__setBackoff({
      failures: 9,
      lockedUntil: Date.now() + 60_000,
      lockedOut: true,
      lockedForMs: 60_000
    })
    const ws = fakeSocket()
    const conn = sync.createConnection(ws as any)
    const eph = X.generateEphemeral()
    sync.handleFrame(
      conn,
      text({
        type: 'hs1',
        proto: 'x1',
        keyId: 'unknown-key',
        deviceId: 'attacker',
        pub_c: eph.publicSpkiB64,
        nonce_c: randomBytes(32).toString('base64')
      }),
      false
    )
    expect(lastText(ws).code).toBe('locked_out')
    expect(lastText(ws).retryAfterMs).toBeGreaterThan(0)
    expect(ws.closes.slice(-1)[0].code).toBe(4002)
    // Refused without evaluating: the counter is not advanced any further.
    expect(dbMock.getBackoff().failures).toBe(9)
  })
})

describe('pairing lockout is scoped to the pairing path (not the whole handshake)', () => {
  function noiseAttempt(keyId: string): void {
    const ws = fakeSocket()
    const conn = sync.createConnection(ws as any)
    const eph = X.generateEphemeral()
    sync.handleFrame(
      conn,
      text({
        type: 'hs1',
        proto: 'x1',
        keyId,
        deviceId: 'attacker-device',
        pub_c: eph.publicSpkiB64,
        nonce_c: randomBytes(32).toString('base64')
      }),
      false
    )
  }

  function bindDevice(): Buffer {
    const secret = X.randomPairingSecret()
    dbMock.__setDeviceKey(DEVICE_ID, {
      keyId: KEY_ID,
      secret_b64: secret.toString('base64'),
      label: 'Pixel 7',
      created: 1,
      lastSeen: 1
    })
    return secret
  }

  test('LAN noise cannot lock out an already-paired phone', () => {
    const secret = bindDevice()
    // Any host on the LAN sprays bogus keyIds — enough to trip the lockout.
    for (let i = 0; i < 6; i++) noiseAttempt(`bogus-${i}`)
    expect(dbMock.getBackoff().lockedOut).toBe(true)

    // The correctly-keyed phone still completes hs3 and gets its channel.
    const ws = fakeSocket()
    const conn = sync.createConnection(ws as any)
    completeHandshake(ws, conn, secret)
    expect(conn.channelEstablished).toBe(true)
    // A successful pairing clears the lockout the noise created.
    expect(dbMock.getBackoff().lockedOut).toBe(false)
  })

  test('the throttle still bites the attempts that caused it', () => {
    bindDevice()
    for (let i = 0; i < 4; i++) noiseAttempt(`bogus-${i}`)
    expect(dbMock.getBackoff().lockedOut).toBe(true)

    const ws = fakeSocket()
    const conn = sync.createConnection(ws as any)
    const eph = X.generateEphemeral()
    sync.handleFrame(
      conn,
      text({
        type: 'hs1',
        proto: 'x1',
        keyId: 'bogus-99',
        deviceId: 'attacker-device',
        pub_c: eph.publicSpkiB64,
        nonce_c: randomBytes(32).toString('base64')
      }),
      false
    )
    expect(lastText(ws).code).toBe('locked_out')
  })

  test('a malformed ephemeral key from a KNOWN keyId does not touch the backoff', () => {
    const v = require('../../shared/crypto-vectors.json')
    bindDevice()
    const ws = fakeSocket()
    const conn = sync.createConnection(ws as any)
    sync.handleFrame(
      conn,
      text({
        type: 'hs1',
        proto: 'x1',
        keyId: KEY_ID,
        deviceId: DEVICE_ID,
        pub_c: v.offcurve_spki,
        nonce_c: randomBytes(32).toString('base64')
      }),
      false
    )
    expect(conn.channelEstablished).toBe(false)
    expect(dbMock.getBackoff().failures).toBe(0)
  })

  test('a mac_c failure DOES advance the backoff', () => {
    bindDevice()
    const ws = fakeSocket()
    const conn = sync.createConnection(ws as any)
    const eph = X.generateEphemeral()
    sync.handleFrame(
      conn,
      text({
        type: 'hs1',
        proto: 'x1',
        keyId: KEY_ID,
        deviceId: DEVICE_ID,
        pub_c: eph.publicSpkiB64,
        nonce_c: randomBytes(32).toString('base64')
      }),
      false
    )
    sync.handleFrame(conn, text({ type: 'hs3', mac_c: randomBytes(32).toString('base64') }), false)
    expect(dbMock.getBackoff().failures).toBe(1)
    expect(lastText(ws)).toEqual({ type: 'error', code: 'repair_required' })
  })
})

describe('pairing mint', () => {
  test('mints ONE code carrying both the keyId and a 128-bit secret', () => {
    const a = sync.createPairing()
    const b = sync.createPairing()
    expect(a.keyId).not.toBe(b.keyId)
    expect(a.keyId).toMatch(/^[0-9a-f]{8}$/)
    expect(a.code.replace(/-/g, '').length).toBe(X.PAIRING_CODE_LENGTH)
    // The old 8-character (~40-bit) code must never come back.
    expect(a.code.replace(/-/g, '').length).toBeGreaterThan(8)
    expect(a.expiresAt).toBeGreaterThan(Date.now())

    // The user copies the code and nothing else: it yields the keyId too.
    const decoded = X.decodePairingCode(a.code)
    expect(decoded.keyId).toBe(a.keyId)
    expect(decoded.secret.length).toBe(16)

    // The pending slot holds exactly the secret the code encodes.
    const pending = dbMock.getPending()[a.keyId]
    expect(pending.secret_b64).toBe(decoded.secret.toString('base64'))
  })

  test('a minted keyId never collides with a pending or bound one', () => {
    dbMock.__setDeviceKey('other-device', {
      keyId: 'aaaaaaaa',
      secret_b64: X.randomPairingSecret().toString('base64'),
      label: 'Other',
      created: 1,
      lastSeen: 1
    })
    const minted = new Set<string>()
    for (let i = 0; i < 50; i++) {
      const { keyId } = sync.createPairing()
      expect(keyId).not.toBe('aaaaaaaa')
      expect(minted.has(keyId)).toBe(false)
      minted.add(keyId)
    }
    expect(minted.size).toBe(50)
  })

  test('the code the phone types resolves to the pending slot it was minted for', () => {
    const { keyId, code } = sync.createPairing()
    // Exactly what the phone does: strip dashes, upper-case, decode, use both.
    const typed = X.decodePairingCode(code.replace(/-/g, '').toLowerCase())
    expect(typed.keyId).toBe(keyId)

    const ws = fakeSocket()
    const conn = sync.createConnection(ws as any)
    completeHandshake(ws, conn, typed.secret, typed.keyId)
    expect(conn.channelEstablished).toBe(true)
    expect(dbMock.getDeviceKeys()[DEVICE_ID].keyId).toBe(keyId)
  })

  test('minting a pairing clears a lockout left by failed attempts', () => {
    dbMock.__setBackoff({
      failures: 9,
      lockedUntil: Date.now() + 60_000,
      lockedOut: true,
      lockedForMs: 60_000
    })
    sync.createPairing()
    expect(dbMock.getBackoff().lockedOut).toBe(false)
  })
})

describe('established channel', () => {
  test('a full handshake establishes the channel and binds the pending slot', () => {
    const { ws, conn, secret } = pairedSocket()
    completeHandshake(ws, conn, secret)

    expect(conn.channelEstablished).toBe(true)
    const bound = dbMock.getDeviceKeys()[DEVICE_ID]
    expect(bound.keyId).toBe(KEY_ID)
    expect(bound.secret_b64).toBe(secret.toString('base64'))
  })

  test('no text frame is emitted after channelEstablished', () => {
    const { ws, conn, secret } = pairedSocket()
    const textBefore = textFrames(ws).length
    const keys = completeHandshake(ws, conn, secret)

    // hello + hs2 only; auth_ok arrives as a BINARY record.
    expect(textFrames(ws).length).toBe(textBefore + 1)
    const authOk = X.openRecord(keys.kS2C, 0n, X.DIR_S2C, binaryFrames(ws)[0].data)
    expect(authOk).toEqual({ type: 'auth_ok' })

    // Every provocation that used to produce a plaintext reply must now
    // produce a close, never a text frame.
    dbMock.__setDb(null)
    const textCount = textFrames(ws).length
    sync.handleFrame(conn, text({ type: 'auth', token: 'X' }), false)
    expect(textFrames(ws).length).toBe(textCount)
    expect(ws.closes.length).toBe(1)
  })

  test('a corrupted record fails closed with no reply and no plaintext consumed', () => {
    const { ws, conn, secret } = pairedSocket()
    const keys = completeHandshake(ws, conn, secret)
    dbMock.__setDb(null)

    const frame = X.sealRecord(keys.kC2S, 0n, X.DIR_C2S, { type: 'full_sync', entries: [] })
    frame[frame.length - 1] ^= 0x01
    const before = ws.sent.length
    sync.handleFrame(conn, frame, true)

    expect(ws.sent.length).toBe(before) // no reply at all
    expect(ws.closes.length).toBe(1)
    expect(dbMock.__dbCalls).toEqual([])
  })

  test('a replayed record is rejected by the counter check', () => {
    const { ws, conn, secret } = pairedSocket()
    const keys = completeHandshake(ws, conn, secret)
    dbMock.__setDb({
      prepare: () => ({ get: () => undefined, all: () => [], run: () => ({ changes: 0 }) }),
      transaction: (fn: any) => (): any => fn()
    })

    const frame = X.sealRecord(keys.kC2S, 0n, X.DIR_C2S, { type: 'pull', since: 0 })
    sync.handleFrame(conn, frame, true)
    expect(binaryFrames(ws).length).toBe(2) // auth_ok + pull_response

    sync.handleFrame(conn, frame, true) // same counter again
    expect(binaryFrames(ws).length).toBe(2)
    expect(ws.closes.length).toBe(1)
  })

  test('the encrypted data plane answers in binary records only', () => {
    const { ws, conn, secret } = pairedSocket()
    const keys = completeHandshake(ws, conn, secret)
    dbMock.__setDb({
      prepare: () => ({ get: () => undefined, all: () => [], run: () => ({ changes: 0 }) }),
      transaction: (fn: any) => (): any => fn()
    })

    const textCount = textFrames(ws).length
    sync.handleFrame(conn, X.sealRecord(keys.kC2S, 0n, X.DIR_C2S, { type: 'pull', since: 0 }), true)

    const reply: any = X.openRecord(keys.kS2C, 1n, X.DIR_S2C, binaryFrames(ws)[1].data)
    expect(reply.type).toBe('pull_response')
    expect(textFrames(ws).length).toBe(textCount)
  })

  test('per-device Remove revokes exactly that phone and drops its socket', () => {
    const { ws, conn, secret } = pairedSocket()
    completeHandshake(ws, conn, secret)
    const other = fakeSocket()
    sync.createConnection(other as any)

    expect(sync.listDevices().map((d: any) => d.deviceId)).toEqual([DEVICE_ID])
    expect(sync.removeDevice(DEVICE_ID)).toBe(true)

    expect(sync.listDevices()).toEqual([])
    expect(ws.closes.slice(-1)[0].code).toBe(4001)
    expect(other.closes.length).toBe(0) // an unrelated socket is untouched
  })

  test('two connections never reuse the ephemeral key (no GCM nonce reuse)', () => {
    const a = fakeSocket()
    const b = fakeSocket()
    const connA = sync.createConnection(a as any)
    const connB = sync.createConnection(b as any)
    expect(connA.ephemeral?.publicSpkiB64).toBeDefined()
    expect(connA.ephemeral?.publicSpkiB64).not.toBe(connB.ephemeral?.publicSpkiB64)
    expect(JSON.parse(textFrames(a)[0].data).nonce_s).not.toBe(
      JSON.parse(textFrames(b)[0].data).nonce_s
    )
  })
})

import { WebSocketServer, WebSocket } from 'ws'
import { Bonjour } from 'bonjour-service'
import { ipcMain, BrowserWindow } from 'electron'
// at-rest: type-only import follows the encrypted-DB binding swap (spec §4.2).
import type Database from 'better-sqlite3-multiple-ciphers'
import { networkInterfaces } from 'os'
import { randomBytes } from 'crypto'
// transport: wc-sync/4 replaces the plaintext v3 protocol wholesale.
import {
  getDatabase,
  getDeviceKeys,
  upsertDeviceKey,
  removeDeviceKey,
  clearPairings,
  getPending,
  putPending,
  bindPending,
  getBackoff,
  bumpBackoff,
  resetBackoff
} from './database'
import * as X from './sync-crypto'

let wss: WebSocketServer | null = null
// InstanceType<> rather than `Bonjour` directly: the package resolves to a
// types entry where Bonjour is only a value under the test tsconfig's
// node-style module resolution, and to a class+type under the build's.
let bonjour: InstanceType<typeof Bonjour> | null = null
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

// transport: a pairing code is valid for one first handshake within this window.
const PAIRING_TTL_MS = 5 * 60_000

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

// transport: ── close-code contract (mirrored by the phone) ─────────────────
//
// Every close code this server can emit, and what the phone must show for it.
// The phone maps each to a specific, actionable string — never the generic
// "Connection closed".
//
//   4001 pairing_changed   The desktop forgot this device ("Remove", or
//                          "forget all devices"). Phone: user-gated re-pair.
//   4002 locked_out        Refused without evaluating: too many failed pairing
//                          attempts recently. The reply carries `retryAfterMs`.
//                          Phone: "Try again in N minutes", keep the key.
//   4003 too_many_conns    Connection cap reached. Phone: retry later.
//   4005 update_required   A legacy v3 frame arrived. Nothing was validated and
//                          no data moved. Phone: "Update the app."
//   4006 repair_required   Unknown keyId, or a key whose secret does not match
//                          (deliberately the SAME code for both, so neither
//                          bricks). Phone: user-gated re-pair, KEEP the key
//                          until the user completes a new pairing.
//
// Standard RFC 6455 codes are also used for protocol violations that carry no
// user-facing meaning: 1002 (wrong channel / malformed frame), 1008 (record
// failed its tag or counter check), 1009 (handshake frame over the 8 KB cap),
// 1011 (internal error). The phone treats all of these as "sync failed, retry".
//
// transport: ── connection state machine ────────────────────────────────────

/**
 * The subset of `ws` the protocol actually uses. Structural, so the state
 * machine can be driven by a fake peer in tests without a real socket.
 */
export interface SyncSocket {
  readyState: number
  send(data: string | Buffer): void
  close(code?: number, reason?: string): void
  ping?(): void
  terminate?(): void
}

/**
 * Per-connection state. There is deliberately NO `authed` flag any more:
 * `channelEstablished` is the only established state, and only a verified
 * `hs3` sets it.
 */
export interface SyncConn {
  ws: SyncSocket
  isAlive: boolean
  closed: boolean
  channelEstablished: boolean
  /** Fresh per connection — reuse across a reconnect is the GCM nonce-reuse bug. */
  ephemeral: X.EphemeralKey | null
  /** The exact bytes we sent / received, hashed into the transcript verbatim. */
  helloBytes: Buffer | null
  hs1Bytes: Buffer | null
  expectedMacC: Buffer | null
  kC2S: Buffer | null
  kS2C: Buffer | null
  ctrIn: bigint
  ctrOut: bigint
  keyId: string | null
  deviceId: string | null
  label: string
  fromPending: boolean
}

const peers = new Map<SyncSocket, SyncConn>()

// `code` is an optional machine-readable companion to `detail`: the renderer
// can match on it instead of parsing English prose, so a copy edit to `detail`
// never silently breaks a UI decision keyed off the wording.
function broadcastSyncStatus(status: string, detail?: string, code?: string): void {
  BrowserWindow.getAllWindows().forEach((win) => {
    if (!win.isDestroyed()) win.webContents.send('sync:status', { status, detail, code })
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

function isOpen(conn: SyncConn): boolean {
  return !conn.closed && conn.ws.readyState === WebSocket.OPEN
}

/**
 * Emitter #1 of exactly two: TEXT frames, handshake JSON only. It refuses to
 * run once a channel exists, so a stray error reply can never egress as
 * plaintext after `hs3`. Returns the exact string sent, because the transcript
 * must hash the bytes that actually went on the wire.
 */
function sendHandshake(conn: SyncConn, obj: unknown): string {
  if (conn.channelEstablished) {
    throw new Error('sendHandshake called after the encrypted channel was established')
  }
  const json = JSON.stringify(obj)
  if (isOpen(conn)) conn.ws.send(json)
  return json
}

/**
 * Emitter #2 of exactly two: BINARY GCM records. This is the ONLY way an
 * `auth_ok` / `full_sync_response` / `push_ack` / `pull_response` can be
 * produced.
 */
function sendEncrypted(conn: SyncConn, obj: unknown): void {
  if (!conn.channelEstablished || !conn.kS2C) {
    throw new Error('sendEncrypted called before the encrypted channel was established')
  }
  if (!isOpen(conn)) return
  const frame = X.sealRecord(conn.kS2C, conn.ctrOut, X.DIR_S2C, obj)
  conn.ctrOut += 1n
  conn.ws.send(frame)
}

/** Close and forget every key this connection derived. */
function dropConnection(conn: SyncConn, code: number, reason: string): void {
  if (conn.closed) return
  conn.closed = true
  conn.ephemeral = null
  conn.kC2S = null
  conn.kS2C = null
  conn.expectedMacC = null
  conn.channelEstablished = false
  try {
    conn.ws.close(code, reason)
  } catch {
    /* already gone */
  }
  peers.delete(conn.ws)
}

/**
 * Set up a connection and speak first: the server sends `hello` so the phone
 * never fires a frame before it knows which protocol it is talking to.
 */
export function createConnection(ws: SyncSocket): SyncConn {
  const conn: SyncConn = {
    ws,
    isAlive: true,
    closed: false,
    channelEstablished: false,
    ephemeral: X.generateEphemeral(),
    helloBytes: null,
    hs1Bytes: null,
    expectedMacC: null,
    kC2S: null,
    kS2C: null,
    ctrIn: 0n,
    ctrOut: 0n,
    keyId: null,
    deviceId: null,
    label: 'Phone',
    fromPending: false
  }
  peers.set(ws, conn)
  const hello = sendHandshake(conn, {
    type: 'hello',
    version: 4,
    minVersion: 4,
    crypto: [X.CRYPTO],
    nonce_s: randomBytes(32).toString('base64')
  })
  conn.helloBytes = Buffer.from(hello, 'utf8')
  return conn
}

function toBuffer(data: Buffer | ArrayBuffer | Buffer[] | string): Buffer {
  if (typeof data === 'string') return Buffer.from(data, 'utf8')
  if (Array.isArray(data)) return Buffer.concat(data)
  return Buffer.isBuffer(data) ? data : Buffer.from(new Uint8Array(data as ArrayBuffer))
}

/**
 * The single entry point for every inbound frame, split by channel:
 * pre-handshake accepts TEXT handshake JSON only, established accepts BINARY
 * records only. A frame on the wrong channel for the current state is closed
 * with no reply — there is no code path by which a text `full_sync` can reach
 * the ingest pipeline.
 */
export function handleFrame(
  conn: SyncConn,
  data: Buffer | ArrayBuffer | Buffer[] | string,
  isBinary: boolean
): void {
  if (conn.closed) return

  if (conn.channelEstablished) {
    if (!isBinary) return dropConnection(conn, 1002, 'text frame on an encrypted channel')
    return handleRecord(conn, toBuffer(data))
  }

  if (isBinary) return dropConnection(conn, 1002, 'binary frame before the handshake')

  const raw = toBuffer(data)
  // Size cap BEFORE any parsing or crypto (handshake-junk DoS).
  if (raw.length > X.MAX_HANDSHAKE_FRAME_BYTES) {
    return dropConnection(conn, 1009, 'handshake frame too large')
  }

  let msg: any
  try {
    msg = JSON.parse(raw.toString('utf8'))
  } catch {
    return dropConnection(conn, 1002, 'invalid handshake JSON')
  }
  if (!msg || typeof msg !== 'object') {
    return dropConnection(conn, 1002, 'invalid handshake frame')
  }

  switch (msg.type) {
    case 'hs1':
      return handleHs1(conn, raw, msg)
    case 'hs3':
      return handleHs3(conn, msg)
    // The v3 plaintext data path. It validates nothing and moves zero rows.
    case 'auth':
    case 'full_sync':
    case 'push':
    case 'pull':
      return tombstoneLegacyFrame(conn)
    default:
      return dropConnection(conn, 1002, 'unexpected handshake frame')
  }
}

/**
 * Pure tombstone for every legacy v3 frame: no token comparison, no database
 * handle, no ingest — just "update the phone" and a close. The reply is
 * identical for a right and a wrong code, so it is not an oracle either.
 */
function tombstoneLegacyFrame(conn: SyncConn): void {
  sendHandshake(conn, { type: 'error', code: 'update_required' })
  broadcastSyncStatus('error', 'Phone app is outdated — update it to sync.', 'update_required')
  dropConnection(conn, X.CLOSE_UPDATE_REQUIRED, 'update_required')
}

function isSaneString(value: unknown, max: number): value is string {
  return typeof value === 'string' && value.length > 0 && value.length <= max
}

/**
 * The phone supplies its own display name and it is rendered in the desktop UI,
 * so keep it short and printable — control characters would corrupt the list.
 */
function sanitizeLabel(value: unknown): string {
  if (typeof value !== 'string') return ''
  return Array.from(value.slice(0, 64), (ch) => ch.charCodeAt(0))
    .filter((ch) => ch >= 0x20 && ch !== 0x7f)
    .map((code) => String.fromCharCode(code))
    .join('')
}

/**
 * A failure on the PAIRING control plane — an unknown `keyId`, or a `mac_c`
 * that does not verify. This is the ONLY place the global backoff is read or
 * advanced — which makes it a throttle on failed attempts, not a gate in
 * front of the handshake: `lockedOut` is only ever consulted here, and this
 * function only runs once the lookup/MAC check has already failed. A
 * connection presenting the correct `keyId` and a valid `mac_c` returns
 * normally from `handleHs1`/`handleHs3` and never calls this function at
 * all — so a correct guess that happens to arrive while a lockout is in
 * effect still succeeds; the lockout does not block it. That is acceptable
 * rather than a bug: the throttle is defense-in-depth layered on top of a
 * 128-bit pairing secret, not the primary defense against guessing it.
 * Landing the right 128 bits is already computationally infeasible before
 * the lockout is ever consulted — the lockout's job is to slow down noisy or
 * hostile scanning across many wrong guesses, not to add a hard stop that a
 * correct answer could still slip past.
 *
 * Scope matters too: the counter is persistent and global, so consulting it
 * before the key lookup meant a handful of bogus `hs1` frames from any host on
 * the LAN locked out EVERY handshake, including an already-paired phone
 * presenting a valid MAC — a free, persistent sync kill-switch. A device that
 * proves it holds the pairing secret is never refused because of unrelated
 * noise; only attempts that fail a key check are throttled (spec §2.8:
 * "code/secret-scoped failure counter").
 */
function failPairingAttempt(conn: SyncConn, detail: string): void {
  const backoff = getBackoff()
  if (backoff.lockedOut) {
    // Already locked: refuse without evaluating, and do not advance further.
    sendHandshake(conn, {
      type: 'error',
      code: 'locked_out',
      retryAfterMs: backoff.lockedForMs
    })
    broadcastSyncStatus(
      'error',
      `Pairing locked for ${Math.ceil(backoff.lockedForMs / 60_000)} min after repeated failures`
    )
    return dropConnection(conn, X.CLOSE_TOO_MANY_ATTEMPTS, 'locked out')
  }
  bumpBackoff()
  sendHandshake(conn, { type: 'error', code: 'repair_required' })
  broadcastSyncStatus('error', detail)
  return dropConnection(conn, X.CLOSE_REPAIR_REQUIRED, 'repair_required')
}

/** The pairing secret for this keyId: a pending slot, or the bound device's key. */
function lookupSecret(
  keyId: string,
  deviceId: string
): { secret: Buffer; fromPending: boolean; label: string | null } | null {
  const pending = getPending()[keyId]
  if (pending) {
    return { secret: Buffer.from(pending.secret_b64, 'base64'), fromPending: true, label: null }
  }
  const bound = getDeviceKeys()[deviceId]
  if (bound && bound.keyId === keyId) {
    return { secret: Buffer.from(bound.secret_b64, 'base64'), fromPending: false, label: bound.label }
  }
  return null
}

function handleHs1(conn: SyncConn, wireBytes: Buffer, msg: any): void {
  if (conn.hs1Bytes || !conn.ephemeral || !conn.helloBytes) {
    return dropConnection(conn, 1002, 'unexpected hs1')
  }
  if (msg.proto !== X.CRYPTO) {
    sendHandshake(conn, { type: 'error', code: 'update_required' })
    broadcastSyncStatus('error', 'Phone app is outdated — update it to sync.', 'update_required')
    return dropConnection(conn, X.CLOSE_UPDATE_REQUIRED, 'unsupported crypto')
  }
  if (
    !isSaneString(msg.keyId, 128) ||
    !isSaneString(msg.deviceId, 128) ||
    !isSaneString(msg.pub_c, 1024) ||
    !isSaneString(msg.nonce_c, 128)
  ) {
    return dropConnection(conn, 1002, 'malformed hs1')
  }

  const found = lookupSecret(msg.keyId, msg.deviceId)
  if (!found) {
    // Unknown keyId and a mismatched key resolve to the SAME recoverable state.
    return failPairingAttempt(conn, 'A device tried to sync with an unknown pairing key')
  }

  let ss: Buffer
  try {
    // Throws on an off-curve / malformed / wrong-curve point. Deliberately NOT
    // counted against the pairing backoff: the keyId was valid, so this is a
    // malformed frame, not a guess at the pairing control plane.
    ss = X.ecdhSharedSecret(conn.ephemeral.priv, msg.pub_c)
  } catch {
    return dropConnection(conn, 1002, 'bad ephemeral key')
  }

  conn.hs1Bytes = wireBytes
  const pubS = conn.ephemeral.publicSpkiB64
  // th covers the exact wire bytes of hello and hs1 (so version, minVersion,
  // crypto[], both nonces and pub_c are all bound) plus pub_s as base64 TEXT.
  const th = X.transcriptHash(conn.helloBytes, wireBytes, Buffer.from(pubS, 'ascii'))
  const keys = X.deriveSession(ss, X.pskFromSecret(found.secret), th)

  conn.kC2S = keys.kC2S
  conn.kS2C = keys.kS2C
  conn.expectedMacC = X.macTag(keys.km, 'cli', th)
  conn.keyId = msg.keyId
  conn.deviceId = msg.deviceId
  conn.fromPending = found.fromPending
  conn.label = sanitizeLabel(msg.deviceName) || found.label || 'Phone'

  sendHandshake(conn, {
    type: 'hs2',
    pub_s: pubS,
    mac_s: X.macTag(keys.km, 'srv', th).toString('base64')
  })
}

function handleHs3(conn: SyncConn, msg: any): void {
  if (!conn.expectedMacC || !conn.deviceId || !conn.keyId) {
    return dropConnection(conn, 1002, 'hs3 before hs1')
  }
  const presented = Buffer.from(typeof msg.mac_c === 'string' ? msg.mac_c : '', 'base64')
  if (!X.constantTimeEqual(presented, conn.expectedMacC)) {
    // The other pairing-plane failure: a present keyId whose secret is wrong.
    return failPairingAttempt(
      conn,
      'A device failed to authenticate — re-pair it if this was you'
    )
  }

  const now = Date.now()
  if (conn.fromPending) {
    // First handshake against a pending slot: bind it, permanently.
    if (!bindPending(conn.keyId, conn.deviceId, conn.label, now)) {
      sendHandshake(conn, { type: 'error', code: 'repair_required' })
      return dropConnection(conn, X.CLOSE_REPAIR_REQUIRED, 'pairing expired')
    }
  } else {
    const existing = getDeviceKeys()[conn.deviceId]
    if (existing) {
      upsertDeviceKey(conn.deviceId, { ...existing, label: conn.label, lastSeen: now })
    }
  }
  resetBackoff()

  conn.channelEstablished = true
  conn.expectedMacC = null
  sendEncrypted(conn, { type: 'auth_ok' })
  broadcastSyncStatus('connected', `${conn.label} paired`)
}

function handleRecord(conn: SyncConn, frame: Buffer): void {
  if (!conn.kC2S) return dropConnection(conn, 1002, 'no channel')
  let msg: any
  try {
    // One-shot: the counter is checked first and the plaintext is only ever
    // observable after the tag verifies.
    msg = X.openRecord(conn.kC2S, conn.ctrIn, X.DIR_C2S, frame)
  } catch {
    // No reply — any reply here would have to be a plaintext text frame.
    return dropConnection(conn, 1008, 'record rejected')
  }
  conn.ctrIn += 1n
  if (!msg || typeof msg !== 'object') return dropConnection(conn, 1002, 'malformed record')

  try {
    handleAppMessage(conn, msg)
  } catch (err) {
    console.error('Sync handler error:', err)
    if (conn.channelEstablished) sendEncrypted(conn, { type: 'error', message: 'Internal error' })
  }
}

/** The data plane. Every entry into it asserts the channel exists. */
function handleAppMessage(conn: SyncConn, msg: any): void {
  if (!conn.channelEstablished) {
    throw new Error('data-plane message reached the handler without an established channel')
  }
  const db = getDatabase()

  switch (msg.type) {
    case 'push': {
      const { inserted, updated, rejected } = ingestEntries(db, msg.entries)
      ingestAux(db, msg)
      sendEncrypted(conn, { type: 'push_ack', inserted, updated, rejected })
      broadcastSyncStatus('synced', `Received: ${inserted} new, ${updated} updated`)
      break
    }

    case 'pull': {
      sendEncrypted(conn, { type: 'pull_response', ...readSince(db, msg.since) })
      broadcastSyncStatus('synced', 'Sent updates to the phone')
      break
    }

    case 'full_sync': {
      const { inserted, updated, rejected } = ingestEntries(db, msg.entries)
      ingestAux(db, msg)
      // `since` keeps this incremental. It used to SELECT * FROM entries and
      // stringify the whole history on every sync, so peak memory grew with
      // the log. A client that has never synced still sends 0 and gets all.
      sendEncrypted(conn, {
        type: 'full_sync_response',
        ...readSince(db, msg.since),
        received: { inserted, updated, rejected }
      })
      broadcastSyncStatus('synced', `Full sync: +${inserted} new, ${updated} updated`)
      break
    }

    default:
      sendEncrypted(conn, { type: 'error', message: `Unknown type: ${msg.type}` })
  }
}

// transport: ── end connection state machine ────────────────────────────────

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
        ws.close(X.CLOSE_TOO_MANY_CONNECTIONS, 'too many connections')
      } catch {
        /* ignore */
      }
      return
    }

    const conn = createConnection(ws as unknown as SyncSocket)
    broadcastSyncStatus('pairing', 'Device connecting…')

    ws.on('pong', () => {
      conn.isAlive = true
    })

    // transport: (data, isBinary) split — a single raw.toString()+JSON.parse
    // for every frame breaks on binary records and leaks a cleartext error per
    // encrypted frame.
    ws.on('message', (data: Buffer | ArrayBuffer | Buffer[], isBinary: boolean) => {
      try {
        handleFrame(conn, data, isBinary)
      } catch (err) {
        console.error('Sync frame error:', err)
        dropConnection(conn, 1011, 'internal error')
      }
    })

    ws.on('close', () => {
      conn.closed = true
      conn.ephemeral = null
      conn.kC2S = null
      conn.kS2C = null
      peers.delete(conn.ws)
      broadcastSyncStatus('listening', 'Device disconnected')
    })

    ws.on('error', (err) => {
      console.error('Sync WebSocket error:', err)
      dropConnection(conn, 1011, 'socket error')
    })
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
    peers.forEach((conn, ws) => {
      if (!conn.isAlive) {
        try {
          ws.terminate?.()
        } catch {
          /* ignore */
        }
        peers.delete(ws)
        return
      }
      conn.isAlive = false
      try {
        ws.ping?.()
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
      // transport: advisory only — the transcript, not this, is the enforcement.
      txt: { app: 'wellness-companion', version: '4', proto: X.SYNC_PROTO }
    })
    console.log(`mDNS: advertising ${SERVICE_NAME} on port ${SYNC_PORT}`)
  } catch (err) {
    console.error('mDNS publish failed:', err)
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
  // The WHERE guard compares against the INCOMING write's modified_at, not the existing row's —
  // binding the existing row's own value here made the guard compare it to itself, so it was
  // always false and the UPDATE never applied.
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
        updateStmt.run(e.data, e.date, e.version ?? 1, e.modified_at, e.id, e.modified_at)
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

// transport: ── pairing + device management ─────────────────────────────────

/**
 * Mint a pairing: a random 128-bit secret pre-registered against a fresh keyId
 * for 5 minutes. The user types the rendered code into the phone once; that
 * secret IS the long-term device key, so there is no in-tunnel mint step to
 * race, and nothing low-entropy ever appears on the wire.
 */
function mintKeyId(): string {
  // 32 bits over <= MAX_STORED_DEVICE_KEYS keys makes a collision vanishingly
  // rare, and a collision is harmless anyway (the wrong secret fails the MAC
  // into 4006 repair_required) — but retrying is one line, so retry.
  const pending = getPending()
  const bound = new Set(Object.values(getDeviceKeys()).map((rec) => rec.keyId))
  for (let attempt = 0; attempt < 64; attempt++) {
    const keyId = X.randomKeyId()
    if (!pending[keyId] && !bound.has(keyId)) return keyId
  }
  throw new Error('could not mint a unique pairing keyId')
}

export function createPairing(): { keyId: string; code: string; expiresAt: number } {
  const keyId = mintKeyId()
  const secret = X.randomPairingSecret()
  const created = Date.now()
  putPending(keyId, { secret_b64: secret.toString('base64'), created, ttlMs: PAIRING_TTL_MS })
  // Minting a pairing is an explicit user action, so it clears any lockout an
  // attacker's failed attempts left behind — otherwise a LAN nuisance could
  // keep the owner permanently unable to pair.
  resetBackoff()
  broadcastSyncStatus('pairing', 'Enter the pairing code on your phone')
  // ONE string for the user: the code already carries the keyId. `keyId` is
  // returned alongside for the desktop's own display/logging, NOT as a second
  // thing to transcribe.
  return { keyId, code: X.encodePairingCode(keyId, secret), expiresAt: created + PAIRING_TTL_MS }
}

export function listDevices(): Array<{
  deviceId: string
  keyId: string
  label: string
  lastSeen: number
}> {
  return Object.entries(getDeviceKeys())
    .map(([deviceId, rec]) => ({
      deviceId,
      keyId: rec.keyId,
      label: rec.label,
      lastSeen: rec.lastSeen
    }))
    .sort((a, b) => b.lastSeen - a.lastSeen)
}

/** Revoke exactly one phone. Others keep syncing. */
export function removeDevice(deviceId: string): boolean {
  const removed = removeDeviceKey(deviceId)
  peers.forEach((conn) => {
    if (conn.deviceId === deviceId) dropConnection(conn, X.CLOSE_PAIRING_CHANGED, 'pairing removed')
  })
  return removed
}

/**
 * Last resort: forget ALL devices and every pending pairing, and drop every
 * socket. Per-device Remove is the normal revocation path.
 */
export function regeneratePairingToken(): void {
  clearPairings()
  resetBackoff()
  peers.forEach((conn) => dropConnection(conn, X.CLOSE_PAIRING_CHANGED, 'pairing changed'))
  broadcastSyncStatus('listening', 'All devices forgotten — pair again to sync')
}

const SYNC_IPC_CHANNELS = [
  'sync:getStatus',
  'sync:getPort',
  'sync:getLocalIp',
  'sync:createPairing',
  'sync:listDevices',
  'sync:removeDevice',
  'sync:regeneratePairingToken'
]

export function registerSyncHandlers(): void {
  ipcMain.handle('sync:getStatus', () => (wss ? 'listening' : 'stopped'))
  ipcMain.handle('sync:getPort', () => SYNC_PORT)
  ipcMain.handle('sync:getLocalIp', () => getLocalIp())
  ipcMain.handle('sync:createPairing', () => createPairing())
  ipcMain.handle('sync:listDevices', () => listDevices())
  ipcMain.handle('sync:removeDevice', (_e, deviceId: string) => removeDevice(deviceId))
  ipcMain.handle('sync:regeneratePairingToken', () => regeneratePairingToken())
}

export function unregisterSyncHandlers(): void {
  SYNC_IPC_CHANNELS.forEach((channel) => ipcMain.removeHandler(channel))
}

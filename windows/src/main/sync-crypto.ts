/**
 * wc-sync/4 (`crypto:'x1'`) session crypto — the desktop half of the wire
 * contract. Every byte layout in here is pinned by `shared/crypto-vectors.json`
 * and mirrored by the Android client; a change on one side without the other is
 * a total sync outage, so treat this file as a wire format, not as utilities.
 *
 * Zero runtime dependencies — Node built-ins only.
 */
import {
  createCipheriv,
  createDecipheriv,
  createHash,
  createHmac,
  createPublicKey,
  diffieHellman,
  generateKeyPairSync,
  hkdfSync,
  randomBytes,
  timingSafeEqual,
  type KeyObject
} from 'node:crypto'

/** Advertised protocol id. mDNS TXT carries the version half of this. */
export const SYNC_PROTO = 'wc-sync/4'
/** The single ciphersuite. There is deliberately nothing to negotiate. */
export const CRYPTO = 'x1'

/** Close codes shared with the phone (spec §2.8/§2.9). */
export const CLOSE_PAIRING_CHANGED = 4001
export const CLOSE_TOO_MANY_ATTEMPTS = 4002
export const CLOSE_TOO_MANY_CONNECTIONS = 4003
export const CLOSE_UPDATE_REQUIRED = 4005
export const CLOSE_REPAIR_REQUIRED = 4006

/** Reject any text handshake frame larger than this BEFORE any crypto (impl-9). */
export const MAX_HANDSHAKE_FRAME_BYTES = 8 * 1024

const CURVE = 'prime256v1'
const SS_BYTES = 32
/** Record type/version tag — byte 0 of every binary frame. */
export const RECORD_TAG = 0x01
export const GCM_TAG_BYTES = 16
/** byte0 ‖ counter(uint64 BE) */
export const RECORD_HEADER_BYTES = 9
export const NONCE_BYTES = 12
export const PAIRING_SECRET_BYTES = 16

/**
 * Nonce direction constants: the 4-byte prefix of every GCM nonce, ASCII label
 * zero-padded to 4. Pinned by the `gcm_record` vector (`633273 00…`).
 */
export const DIR_C2S = Buffer.from([0x63, 0x32, 0x73, 0x00]) // 'c2s\0'
export const DIR_S2C = Buffer.from([0x73, 0x32, 0x63, 0x00]) // 's2c\0'

export type Role = 'srv' | 'cli'

export interface EphemeralKey {
  publicSpkiB64: string
  priv: KeyObject
}

export interface SessionKeys {
  km: Buffer
  kC2S: Buffer
  kS2C: Buffer
}

/**
 * Fresh P-256 keypair. MUST be called per connection (impl-2): reusing one
 * across a reconnect restarts the record counters at 0 under the same key,
 * which is the GCM nonce-reuse catastrophe.
 */
export function generateEphemeral(): EphemeralKey {
  const { publicKey, privateKey } = generateKeyPairSync('ec', { namedCurve: CURVE })
  const der = publicKey.export({ type: 'spki', format: 'der' })
  return { publicSpkiB64: der.toString('base64'), priv: privateKey }
}

/**
 * ECDH against a peer's SPKI-DER public key (base64 in JSON). `createPublicKey`
 * performs the on-curve check for us — a crafted off-curve point fails to
 * import, which is why no hand-rolled point validation lives here (impl-8).
 * The result is the fixed-width 32-byte big-endian X; a leading zero byte is
 * NEVER stripped (that would silently diverge from the Android side).
 */
export function ecdhSharedSecret(priv: KeyObject, peerSpkiB64: string): Buffer {
  if (typeof peerSpkiB64 !== 'string' || peerSpkiB64.length === 0) {
    throw new Error('peer public key missing')
  }
  const der = Buffer.from(peerSpkiB64, 'base64')
  // Throws "Failed to read asymmetric key" for an off-curve or malformed point.
  const publicKey = createPublicKey({ key: der, format: 'der', type: 'spki' })
  if (publicKey.asymmetricKeyType !== 'ec') {
    throw new Error(`peer key is ${publicKey.asymmetricKeyType}, expected ec`)
  }
  const namedCurve = publicKey.asymmetricKeyDetails?.namedCurve
  if (namedCurve !== CURVE) {
    throw new Error(`peer key curve ${namedCurve}, expected ${CURVE}`)
  }
  const ss = diffieHellman({ privateKey: priv, publicKey })
  if (ss.length > SS_BYTES) throw new Error(`shared secret is ${ss.length} bytes`)
  if (ss.length === SS_BYTES) return ss
  return Buffer.concat([Buffer.alloc(SS_BYTES - ss.length), ss], SS_BYTES)
}

/**
 * HKDF-SHA256 with the argument order FIXED as (ikm, salt, info, len).
 *
 * Node's own `hkdfSync(digest, ikm, salt, info, keylen)` takes two adjacent
 * Buffers — transposing ikm and salt still returns 32 plausible bytes, still
 * passes any Node-only round-trip test, and fails only against the Android
 * peer, at which point the bug looks like a transport problem. Every call site
 * goes through this helper so the order is stated once.
 */
export function hkdf(ikm: Buffer, salt: Buffer, info: string | Buffer, len: number): Buffer {
  const infoBuf = typeof info === 'string' ? Buffer.from(info, 'utf8') : info
  return Buffer.from(hkdfSync('sha256', ikm, salt, infoBuf, len))
}

/**
 * SHA-256 over the exact wire bytes each side sent/received, each element
 * prefixed with its length as a uint32 BIG-ENDIAN (impl-3). Never hash
 * re-serialized objects: key order or whitespace drift breaks both MACs.
 * Order is hello ‖ hs1 ‖ pub_s_b64, and `pub_s_b64` contributes its ASCII
 * base64 TEXT bytes, not the decoded DER.
 */
export function transcriptHash(...wireElements: Buffer[]): Buffer {
  const h = createHash('sha256')
  const lenPrefix = Buffer.alloc(4)
  for (const el of wireElements) {
    lenPrefix.writeUInt32BE(el.length, 0)
    h.update(lenPrefix)
    h.update(el)
  }
  return h.digest()
}

/**
 * prk = HKDF-Extract(salt=th, ikm = ss ‖ psk); then Expand for each label.
 * Node's hkdfSync combines Extract+Expand, so the PRK is never materialised —
 * the fixture's `prk` value is cross-checked in vectors.test.ts instead.
 */
export function deriveSession(ss: Buffer, psk: Buffer, th: Buffer): SessionKeys {
  const ikm = Buffer.concat([ss, psk])
  return {
    km: hkdf(ikm, th, 'confirm', 32),
    kC2S: hkdf(ikm, th, 'c2s', 32),
    kS2C: hkdf(ikm, th, 's2c', 32)
  }
}

/** HMAC-SHA256(km, role ‖ th). The role label blocks MAC reflection. */
export function macTag(km: Buffer, role: Role, th: Buffer): Buffer {
  return createHmac('sha256', km).update(Buffer.from(role, 'ascii')).update(th).digest()
}

/** Length-safe constant-time compare (timingSafeEqual throws on length mismatch). */
export function constantTimeEqual(a: Buffer, b: Buffer): boolean {
  if (a.length !== b.length) return false
  return timingSafeEqual(a, b)
}

/** The 128-bit pairing secret is promoted to the long-term PSK by one HKDF. */
export function pskFromSecret(secret: Buffer): Buffer {
  return hkdf(secret, Buffer.alloc(0), 'wc-psk', 32)
}

function recordHeader(counter: bigint): Buffer {
  if (counter < 0n || counter > 0xffffffffffffffffn) throw new Error('record counter out of range')
  const header = Buffer.alloc(RECORD_HEADER_BYTES)
  header[0] = RECORD_TAG
  header.writeBigUInt64BE(counter, 1)
  return header
}

function nonceFor(dir: Buffer, header: Buffer): Buffer {
  if (dir.length !== 4) throw new Error('direction constant must be 4 bytes')
  const nonce = Buffer.concat([dir, header.subarray(1)], NONCE_BYTES)
  return nonce
}

/**
 * Seal one application object into a binary record:
 *
 *   byte 0      0x01
 *   bytes 1..8  counter, uint64 big-endian, per direction, from 0 each conn
 *   bytes 9..N  GCM ciphertext ‖ tag(16B)      <- Android-native ct‖tag layout
 *
 * Nonce = dir(4B) ‖ counter(8B); `byte0 ‖ counter` is also the GCM AAD, which
 * binds the framing to the ciphertext.
 */
export function sealRecord(key: Buffer, counter: bigint, dir: Buffer, obj: unknown): Buffer {
  if (key.length !== 32) throw new Error('record key must be 32 bytes')
  const header = recordHeader(counter)
  const cipher = createCipheriv('aes-256-gcm', key, nonceFor(dir, header), {
    authTagLength: GCM_TAG_BYTES
  })
  cipher.setAAD(header)
  const plaintext = Buffer.from(JSON.stringify(obj), 'utf8')
  const ct = Buffer.concat([cipher.update(plaintext), cipher.final()])
  const tag = cipher.getAuthTag()
  if (tag.length !== GCM_TAG_BYTES) throw new Error(`unexpected tag length ${tag.length}`)
  return Buffer.concat([header, ct, tag])
}

/**
 * Open one binary record. Counter monotonicity is checked before any crypto,
 * the tag is installed BEFORE `final()`, and the plaintext is only ever
 * observable after `final()` has verified it (the one-shot discipline —
 * `update()`'s output is never consumed on its own).
 */
export function openRecord(
  key: Buffer,
  expectedCounter: bigint,
  dir: Buffer,
  frame: Buffer
): unknown {
  if (key.length !== 32) throw new Error('record key must be 32 bytes')
  if (!Buffer.isBuffer(frame)) throw new Error('record frame must be binary')
  if (frame.length < RECORD_HEADER_BYTES + GCM_TAG_BYTES) throw new Error('record too short')
  if (frame[0] !== RECORD_TAG) throw new Error(`unknown record tag 0x${frame[0].toString(16)}`)

  const counter = frame.readBigUInt64BE(1)
  if (counter !== expectedCounter) {
    throw new Error(`record counter ${counter} != expected ${expectedCounter}`)
  }

  const header = frame.subarray(0, RECORD_HEADER_BYTES)
  const body = frame.subarray(RECORD_HEADER_BYTES)
  const ct = body.subarray(0, body.length - GCM_TAG_BYTES)
  const tag = body.subarray(body.length - GCM_TAG_BYTES)

  const decipher = createDecipheriv('aes-256-gcm', key, nonceFor(dir, header), {
    authTagLength: GCM_TAG_BYTES
  })
  decipher.setAAD(header)
  decipher.setAuthTag(tag)
  const plaintext = Buffer.concat([decipher.update(ct), decipher.final()])
  return JSON.parse(plaintext.toString('utf8'))
}

/**
 * Pairing-code alphabet: 31 glyphs, excluding 0/O/1/I/L — the pairs people
 * misread copying a code off a screen. NOT a secret; the entropy is in the
 * 16 random bytes it encodes.
 */
export const PAIRING_ALPHABET = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789' // gitleaks:allow
const PAIRING_RADIX = BigInt(PAIRING_ALPHABET.length)

/**
 * The pairing code carries `keyId(4) ‖ secret(16)` — ONE string for the user,
 * never a lookup handle plus a secret to transcribe separately. (The original
 * design paired a 36-char UUID with a 26-char secret: ~62 characters for the
 * single interaction that makes sync work at all.)
 *
 * `keyId` is a public lookup handle with no entropy requirement; a collision is
 * harmless because the wrong secret simply fails the MAC into the existing
 * `4006 repair_required` recovery. The SECRET is still 128 bits — that floor
 * does not move.
 */
export const PAIRING_KEY_ID_BYTES = 4
export const PAIRING_CODE_BYTES = PAIRING_KEY_ID_BYTES + PAIRING_SECRET_BYTES // 20
/** ceil(160 / log2(31)) = 33 glyphs carry all 20 bytes (31^33 > 2^160). */
export const PAIRING_CODE_LENGTH = 33
const PAIRING_GROUP = 5
const MAX_CODE_VALUE = 1n << BigInt(PAIRING_CODE_BYTES * 8)

export interface PairingCodeParts {
  /** 8 lowercase hex characters — the `keyId` exactly as it travels in `hs1`. */
  keyId: string
  /** 16 bytes / 128 bits. */
  secret: Buffer
}

/** 128 bits. The old ~40-bit 8-character code is gone and must not come back. */
export function randomPairingSecret(): Buffer {
  return randomBytes(PAIRING_SECRET_BYTES)
}

/** 4 random bytes as 8 lowercase hex — the wire form of `keyId`. */
export function randomKeyId(): string {
  return randomBytes(PAIRING_KEY_ID_BYTES).toString('hex')
}

/**
 * Render `keyId ‖ secret` as one fixed-width, dash-grouped base-31 code:
 * big-endian over the 20 bytes, 33 glyphs, grouped 5-5-5-5-5-5-3.
 */
export function encodePairingCode(keyId: string, secret: Buffer): string {
  if (!/^[0-9a-f]{8}$/.test(keyId)) {
    throw new Error('keyId must be 8 lowercase hex characters')
  }
  if (secret.length !== PAIRING_SECRET_BYTES) {
    throw new Error(`pairing secret must be ${PAIRING_SECRET_BYTES} bytes`)
  }
  let n = BigInt(`0x${keyId}${secret.toString('hex')}`)
  const glyphs: string[] = []
  for (let i = 0; i < PAIRING_CODE_LENGTH; i++) {
    glyphs.unshift(PAIRING_ALPHABET[Number(n % PAIRING_RADIX)])
    n /= PAIRING_RADIX
  }
  /* istanbul ignore next — 31^33 > 2^160, so this is unreachable by construction */
  if (n !== 0n) throw new Error('pairing code overflowed its length')

  const groups: string[] = []
  const flat = glyphs.join('')
  for (let i = 0; i < flat.length; i += PAIRING_GROUP) {
    groups.push(flat.slice(i, i + PAIRING_GROUP))
  }
  // 33 = 6*5 + 3, so no group is ever a lone glyph; fold anyway if that changes.
  if (groups.length > 1 && groups[groups.length - 1].length === 1) {
    groups[groups.length - 2] += groups.pop()
  }
  return groups.join('-')
}

/** Inverse of encodePairingCode. Dashes, whitespace and case are ignored. */
export function decodePairingCode(code: string): PairingCodeParts {
  const cleaned = String(code).trim().toUpperCase().replace(/[\s-]/g, '')
  if (cleaned.length !== PAIRING_CODE_LENGTH) {
    throw new Error(`pairing code must be ${PAIRING_CODE_LENGTH} characters`)
  }
  let n = 0n
  for (const ch of cleaned) {
    const idx = PAIRING_ALPHABET.indexOf(ch)
    if (idx < 0) throw new Error(`invalid pairing-code character ${JSON.stringify(ch)}`)
    n = n * PAIRING_RADIX + BigInt(idx)
  }
  if (n >= MAX_CODE_VALUE) throw new Error('pairing code out of range')
  const bytes = Buffer.from(n.toString(16).padStart(PAIRING_CODE_BYTES * 2, '0'), 'hex')
  return {
    keyId: bytes.subarray(0, PAIRING_KEY_ID_BYTES).toString('hex'),
    secret: bytes.subarray(PAIRING_KEY_ID_BYTES)
  }
}

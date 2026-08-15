/**
 * wc-sync/4 client-side crypto, re-implemented from the SPEC (§2.3–2.7) in
 * plain JS so the hostile peer is an INDEPENDENT implementation, not a re-use
 * of the desktop's own module. If these two agree, the wire contract is real.
 */
const crypto = require('node:crypto')

const CRYPTO = 'x1'
const CURVE = 'prime256v1'
const RECORD_TAG = 0x01
const GCM_TAG_BYTES = 16
const RECORD_HEADER_BYTES = 9
const DIR_C2S = Buffer.from([0x63, 0x32, 0x73, 0x00])
const DIR_S2C = Buffer.from([0x73, 0x32, 0x63, 0x00])

function generateEphemeral() {
  const { publicKey, privateKey } = crypto.generateKeyPairSync('ec', { namedCurve: CURVE })
  return {
    publicSpkiB64: publicKey.export({ type: 'spki', format: 'der' }).toString('base64'),
    priv: privateKey
  }
}

function ecdhSharedSecret(priv, peerSpkiB64) {
  const publicKey = crypto.createPublicKey({
    key: Buffer.from(peerSpkiB64, 'base64'),
    format: 'der',
    type: 'spki'
  })
  const ss = crypto.diffieHellman({ privateKey: priv, publicKey })
  if (ss.length === 32) return ss
  return Buffer.concat([Buffer.alloc(32 - ss.length), ss], 32)
}

function hkdf(ikm, salt, info, len) {
  return Buffer.from(crypto.hkdfSync('sha256', ikm, salt, Buffer.from(info, 'utf8'), len))
}

function transcriptHash(...els) {
  const h = crypto.createHash('sha256')
  const p = Buffer.alloc(4)
  for (const el of els) {
    p.writeUInt32BE(el.length, 0)
    h.update(p)
    h.update(el)
  }
  return h.digest()
}

function deriveSession(ss, psk, th) {
  const ikm = Buffer.concat([ss, psk])
  return {
    km: hkdf(ikm, th, 'confirm', 32),
    kC2S: hkdf(ikm, th, 'c2s', 32),
    kS2C: hkdf(ikm, th, 's2c', 32)
  }
}

const macTag = (km, role, th) =>
  crypto.createHmac('sha256', km).update(Buffer.from(role, 'ascii')).update(th).digest()

const pskFromSecret = (secret) => hkdf(secret, Buffer.alloc(0), 'wc-psk', 32)

function header(counter) {
  const h = Buffer.alloc(RECORD_HEADER_BYTES)
  h[0] = RECORD_TAG
  h.writeBigUInt64BE(BigInt(counter), 1)
  return h
}
const nonceFor = (dir, hdr) => Buffer.concat([dir, hdr.subarray(1)], 12)

function sealRecord(key, counter, dir, obj) {
  const hdr = header(counter)
  const c = crypto.createCipheriv('aes-256-gcm', key, nonceFor(dir, hdr), { authTagLength: 16 })
  c.setAAD(hdr)
  const pt = Buffer.from(JSON.stringify(obj), 'utf8')
  const ct = Buffer.concat([c.update(pt), c.final()])
  return Buffer.concat([hdr, ct, c.getAuthTag()])
}

function openRecord(key, expectedCounter, dir, frame) {
  if (frame[0] !== RECORD_TAG) throw new Error(`unknown record tag 0x${frame[0].toString(16)}`)
  const counter = frame.readBigUInt64BE(1)
  if (counter !== BigInt(expectedCounter)) {
    throw new Error(`record counter ${counter} != expected ${expectedCounter}`)
  }
  const hdr = frame.subarray(0, RECORD_HEADER_BYTES)
  const body = frame.subarray(RECORD_HEADER_BYTES)
  const d = crypto.createDecipheriv('aes-256-gcm', key, nonceFor(dir, hdr), { authTagLength: 16 })
  d.setAAD(hdr)
  d.setAuthTag(body.subarray(body.length - GCM_TAG_BYTES))
  const pt = Buffer.concat([d.update(body.subarray(0, body.length - GCM_TAG_BYTES)), d.final()])
  return JSON.parse(pt.toString('utf8'))
}

// ── pairing code (base31 over keyId(4) || secret(16)) ─────────────────────────
const ALPHABET = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789'
const RADIX = BigInt(ALPHABET.length)

function decodePairingCode(code) {
  const cleaned = String(code).trim().toUpperCase().replace(/[\s-]/g, '')
  if (cleaned.length !== 33) throw new Error(`pairing code must be 33 characters, got ${cleaned.length}`)
  let n = 0n
  for (const ch of cleaned) {
    const i = ALPHABET.indexOf(ch)
    if (i < 0) throw new Error(`invalid character ${JSON.stringify(ch)}`)
    n = n * RADIX + BigInt(i)
  }
  const bytes = Buffer.from(n.toString(16).padStart(40, '0'), 'hex')
  return { keyId: bytes.subarray(0, 4).toString('hex'), secret: bytes.subarray(4) }
}

module.exports = {
  CRYPTO, DIR_C2S, DIR_S2C, RECORD_HEADER_BYTES, GCM_TAG_BYTES,
  generateEphemeral, ecdhSharedSecret, hkdf, transcriptHash, deriveSession,
  macTag, pskFromSecret, sealRecord, openRecord, decodePairingCode
}

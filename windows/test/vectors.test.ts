import { createHash, createHmac, hkdfSync } from 'node:crypto'
import vectors from '../../shared/crypto-vectors.json'

function hex(buf: ArrayBuffer | Buffer): string {
  return Buffer.from(buf as ArrayBuffer).toString('hex')
}

test('fixture has all sections', () => {
  expect(vectors.handshake.km).toMatch(/^[0-9a-f]{64}$/)
  expect(vectors.hkdf_rfc5869.length).toBeGreaterThanOrEqual(3)
  expect(vectors.gcm_record.ct_tag.length).toBeGreaterThan(vectors.gcm_record.plaintext.length)
  // Both directions are pinned, so a mirrored implementation cannot get the
  // s2c constant wrong and only discover it at the interop gate.
  expect(vectors.gcm_record_s2c.nonce.slice(0, 8)).toBe('73326300')
  expect(vectors.gcm_record.nonce.slice(0, 8)).toBe('63327300')
  expect(vectors.gcm_record_s2c.counter).toBe(1)
  expect(vectors.ecdh_leading_zero_x.expected_ss).toMatch(/^00[0-9a-f]{62}$/)
  expect(typeof vectors.offcurve_spki).toBe('string')
  expect(vectors.offcurve_spki.length).toBeGreaterThan(0)
  expect(vectors.th_wire_bytes.expected_th).toMatch(/^[0-9a-f]{64}$/)
  expect(vectors.th_wire_bytes.elements_hex.length).toBe(3)
  // The pairing code is encoded on the desktop and decoded on the phone, so
  // its format is pinned here too.
  expect(vectors.pairing_code.code_length).toBe(33)
  expect(vectors.pairing_code.grouping).toEqual([5, 5, 5, 5, 5, 5, 3])
  expect(vectors.pairing_code.vectors.length).toBeGreaterThanOrEqual(3)
})

describe('RFC 5869 HKDF-SHA256 vectors (Node crypto.hkdfSync)', () => {
  // Each vector carries its OWN `info` (per RFC 5869 Appendix A) — deliberately
  // NOT passed as an empty string, since a test that hardcodes info:'' would
  // silently pass even for an implementation that mishandles the info param.
  for (const v of vectors.hkdf_rfc5869) {
    test(`L=${v.L} info=${v.info || '(empty)'}`, () => {
      const okm = hkdfSync(
        'sha256',
        Buffer.from(v.IKM, 'hex'),
        Buffer.from(v.salt, 'hex'),
        Buffer.from(v.info, 'hex'),
        v.L
      )
      expect(hex(okm)).toBe(v.OKM)
    })
  }
})

describe('handshake derivation contract (Node crypto)', () => {
  // prk = HKDF-Extract(salt=th, ikm = ss || psk)
  // km  = HKDF-Expand(prk, "confirm", 32)   -- verified via combined hkdfSync
  // mac_s = HMAC-SHA256(km, "srv" || th); mac_c = HMAC-SHA256(km, "cli" || th)
  // k_c2s = HKDF-Expand(prk, "c2s", 32); k_s2c = HKDF-Expand(prk, "s2c", 32)
  const h = vectors.handshake
  const ss = Buffer.from(h.ss, 'hex')
  const psk = Buffer.from(h.psk, 'hex')
  const th = Buffer.from(h.th, 'hex')
  const ikm = Buffer.concat([ss, psk])

  test('prk = HKDF-Extract(salt=th, ikm=ss||psk)', () => {
    // Extract is a single HMAC(salt, ikm) call, exposed directly by Node.
    const prk = createHmac('sha256', th).update(ikm).digest()
    expect(hex(prk)).toBe(h.prk)
  })

  test('km/k_c2s/k_s2c = HKDF-Expand(prk, label, 32), fixed arg order (digest, ikm, salt, info, keylen)', () => {
    // Node's hkdfSync(digest, ikm, salt, info, keylen) combines extract+expand;
    // called with (ikm=ss||psk, salt=th) it reproduces the same prk internally,
    // so this also cross-checks against the fixture's independently-computed values.
    const km = hkdfSync('sha256', ikm, th, Buffer.from('confirm'), 32)
    expect(hex(km)).toBe(h.km)

    const k_c2s = hkdfSync('sha256', ikm, th, Buffer.from('c2s'), 32)
    expect(hex(k_c2s)).toBe(h.k_c2s)

    const k_s2c = hkdfSync('sha256', ikm, th, Buffer.from('s2c'), 32)
    expect(hex(k_s2c)).toBe(h.k_s2c)
  })

  test('mac_s / mac_c = HMAC-SHA256(km, dir || th), domain-separated', () => {
    const km = Buffer.from(h.km, 'hex')
    const mac_s = createHmac('sha256', km).update(Buffer.concat([Buffer.from('srv'), th])).digest()
    const mac_c = createHmac('sha256', km).update(Buffer.concat([Buffer.from('cli'), th])).digest()
    expect(hex(mac_s)).toBe(h.mac_s)
    expect(hex(mac_c)).toBe(h.mac_c)
    expect(h.mac_s).not.toBe(h.mac_c)
  })
})

describe('th (transcript hash) wire-byte construction (spec §2.3)', () => {
  // th = SHA256(len-prefixed wire bytes: hello || hs1 || pub_s_b64), each
  // element prefixed with its length as a uint32 BIG-ENDIAN integer, hashed
  // in the order hello, hs1, pub_s_b64. This is a SECOND, independent
  // implementation of the same length-prefix rule the Python generator used
  // (Buffer.writeUInt32BE + Buffer.concat + node:crypto's Hash, not the
  // generator's Python int.to_bytes/hashlib) -- an agreement here means the
  // rule itself, not just one implementation of it, is unambiguous.
  const t = vectors.th_wire_bytes

  function lengthPrefixed(elem: Buffer): Buffer {
    const lenPrefix = Buffer.alloc(4)
    lenPrefix.writeUInt32BE(elem.length, 0)
    return Buffer.concat([lenPrefix, elem])
  }

  test('recompute expected_th from elements_hex using the uint32BE-prefix rule', () => {
    const elements = t.elements_hex.map((h) => Buffer.from(h, 'hex'))
    const transcript = Buffer.concat(elements.map(lengthPrefixed))
    const th = createHash('sha256').update(transcript).digest()
    expect(hex(th)).toBe(t.expected_th)
  })

  test('elements_hex matches independently re-encoding hello_json/hs1_json/pub_s_b64', () => {
    // pub_s_b64 contributes its ASCII/base64 TEXT bytes (not decoded DER) --
    // the #1 way Node/Android implementations could silently diverge.
    const helloBytes = Buffer.from(t.hello_json, 'utf8')
    const hs1Bytes = Buffer.from(t.hs1_json, 'utf8')
    const pubSBytes = Buffer.from(t.pub_s_b64, 'ascii')

    expect(hex(helloBytes)).toBe(t.elements_hex[0])
    expect(hex(hs1Bytes)).toBe(t.elements_hex[1])
    expect(hex(pubSBytes)).toBe(t.elements_hex[2])

    const transcript = Buffer.concat([helloBytes, hs1Bytes, pubSBytes].map(lengthPrefixed))
    const th = createHash('sha256').update(transcript).digest()
    expect(hex(th)).toBe(t.expected_th)
  })
})

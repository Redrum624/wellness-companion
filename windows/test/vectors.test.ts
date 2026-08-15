import { createHmac, hkdfSync } from 'node:crypto'
import vectors from '../../shared/crypto-vectors.json'

function hex(buf: ArrayBuffer | Buffer): string {
  return Buffer.from(buf as ArrayBuffer).toString('hex')
}

test('fixture has all sections', () => {
  expect(vectors.handshake.km).toMatch(/^[0-9a-f]{64}$/)
  expect(vectors.hkdf_rfc5869.length).toBeGreaterThanOrEqual(3)
  expect(vectors.gcm_record.ct_tag.length).toBeGreaterThan(vectors.gcm_record.plaintext.length)
  expect(vectors.ecdh_leading_zero_x.expected_ss).toMatch(/^00[0-9a-f]{62}$/)
  expect(typeof vectors.offcurve_spki).toBe('string')
  expect(vectors.offcurve_spki.length).toBeGreaterThan(0)
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

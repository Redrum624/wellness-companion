import { createPrivateKey } from 'node:crypto'
import * as C from '../src/main/sync-crypto'
import v from '../../shared/crypto-vectors.json'

const hex = (b: Buffer): string => b.toString('hex')
const H = (s: string): Buffer => Buffer.from(s, 'hex')

describe('hkdf (fixed arg order: ikm, salt, info, len)', () => {
  // The vectors carry `info` as HEX, so it is passed as raw bytes here. Passing
  // info:'' would let an implementation that mishandles info pass anyway.
  for (const t of v.hkdf_rfc5869) {
    test(`RFC 5869 L=${t.L} info=${t.info || '(empty)'}`, () => {
      expect(hex(C.hkdf(H(t.IKM), H(t.salt), H(t.info), t.L))).toBe(t.OKM)
    })
  }

  test('ikm/salt transposition produces a DIFFERENT key (the footgun is detectable)', () => {
    const t = v.hkdf_rfc5869[0]
    const right = C.hkdf(H(t.IKM), H(t.salt), H(t.info), t.L)
    const wrong = C.hkdf(H(t.salt), H(t.IKM), H(t.info), t.L)
    expect(hex(right)).not.toBe(hex(wrong))
  })
})

describe('handshake derivation', () => {
  test('session derivation matches fixture', () => {
    const d = C.deriveSession(H(v.handshake.ss), H(v.handshake.psk), H(v.handshake.th))
    expect(hex(d.km)).toBe(v.handshake.km)
    expect(hex(d.kC2S)).toBe(v.handshake.k_c2s)
    expect(hex(d.kS2C)).toBe(v.handshake.k_s2c)
  })

  test('mac tags match and are domain-separated', () => {
    const km = H(v.handshake.km)
    const th = H(v.handshake.th)
    expect(hex(C.macTag(km, 'srv', th))).toBe(v.handshake.mac_s)
    expect(hex(C.macTag(km, 'cli', th))).toBe(v.handshake.mac_c)
    expect(v.handshake.mac_s).not.toBe(v.handshake.mac_c)
  })

  test('pskFromSecret matches the pinned psk_from_secret vector', () => {
    // Pinned by the shared fixture (independent Python), not by this file, and
    // asserted identically in the Android suite: `handshake` takes `psk` as an
    // input, so this was the last derivation step where each platform could
    // only check its own HKDF against its own HKDF.
    expect(hex(C.pskFromSecret(H(v.psk_from_secret.secret_hex)))).toBe(
      v.psk_from_secret.expected_psk_hex
    )
  })

  test('pskFromSecret is HKDF(secret, "", "wc-psk", 32) and is deterministic', () => {
    const secret = Buffer.alloc(16, 0xab)
    const psk = C.pskFromSecret(secret)
    expect(psk.length).toBe(32)
    expect(hex(psk)).toBe(hex(C.hkdf(secret, Buffer.alloc(0), 'wc-psk', 32)))
    expect(hex(psk)).not.toBe(hex(C.pskFromSecret(Buffer.alloc(16, 0xac))))
  })
})

describe('transcriptHash (uint32-BE length-prefixed wire bytes)', () => {
  const t = v.th_wire_bytes

  test('matches expected_th for the pinned hello/hs1/pub_s_b64 wire bytes', () => {
    const th = C.transcriptHash(
      Buffer.from(t.hello_json, 'utf8'),
      Buffer.from(t.hs1_json, 'utf8'),
      // pub_s contributes its ASCII/base64 TEXT bytes, never the decoded DER.
      Buffer.from(t.pub_s_b64, 'ascii')
    )
    expect(hex(th)).toBe(t.expected_th)
  })

  test('matches expected_th when fed the pinned elements_hex directly', () => {
    const th = C.transcriptHash(...t.elements_hex.map((h) => H(h)))
    expect(hex(th)).toBe(t.expected_th)
  })

  test('length-prefixing is not concatenation (element boundaries are bound)', () => {
    const a = C.transcriptHash(Buffer.from('ab'), Buffer.from('c'))
    const b = C.transcriptHash(Buffer.from('a'), Buffer.from('bc'))
    expect(hex(a)).not.toBe(hex(b))
  })

  test('decoded DER for pub_s would NOT match (the interop trap is pinned)', () => {
    const th = C.transcriptHash(
      Buffer.from(t.hello_json, 'utf8'),
      Buffer.from(t.hs1_json, 'utf8'),
      Buffer.from(t.pub_s_b64, 'base64')
    )
    expect(hex(th)).not.toBe(t.expected_th)
  })
})

describe('ECDH over SPKI DER', () => {
  test('leading-zero X is preserved as a 32-byte shared secret', () => {
    const priv = createPrivateKey({
      key: Buffer.from(v.ecdh_leading_zero_x.priv_a, 'base64'),
      format: 'der',
      type: 'pkcs8'
    })
    const ss = C.ecdhSharedSecret(priv, v.ecdh_leading_zero_x.pub_b_spki)
    expect(ss.length).toBe(32)
    expect(hex(ss)).toBe(v.ecdh_leading_zero_x.expected_ss)
  })

  test('off-curve SPKI import throws', () => {
    const { priv } = C.generateEphemeral()
    expect(() => C.ecdhSharedSecret(priv, v.offcurve_spki)).toThrow()
  })

  test('garbage SPKI throws', () => {
    const { priv } = C.generateEphemeral()
    expect(() => C.ecdhSharedSecret(priv, 'bm90LWEta2V5')).toThrow()
  })

  test('generateEphemeral produces a fresh keypair every call', () => {
    const a = C.generateEphemeral()
    const b = C.generateEphemeral()
    expect(a.publicSpkiB64).not.toBe(b.publicSpkiB64)
    expect(Buffer.from(a.publicSpkiB64, 'base64').length).toBeGreaterThan(64)
  })

  test('both sides derive the same shared secret', () => {
    const a = C.generateEphemeral()
    const b = C.generateEphemeral()
    expect(hex(C.ecdhSharedSecret(a.priv, b.publicSpkiB64))).toBe(
      hex(C.ecdhSharedSecret(b.priv, a.publicSpkiB64))
    )
  })
})

describe('GCM record framing', () => {
  const r = v.gcm_record
  // The pinned nonce is dir(4B) || counter(8B); the fixture's dir is 'c2s\0'.
  const dir = H(r.nonce).subarray(0, 4)

  test('sealRecord reproduces the pinned ct||tag and byte layout', () => {
    const obj = JSON.parse(Buffer.from(r.plaintext, 'hex').toString('utf8'))
    const frame = C.sealRecord(H(r.key), 0n, dir, obj)
    expect(frame[0]).toBe(0x01)
    expect(hex(frame.subarray(0, 9))).toBe(r.aad)
    expect(hex(frame.subarray(9))).toBe(r.ct_tag)
    expect(frame.length).toBe(9 + r.ct_tag.length / 2)
  })

  test('the pinned dir constant is c2s / s2c padded to 4 bytes', () => {
    expect(hex(C.DIR_C2S)).toBe(hex(dir))
    expect(hex(C.DIR_S2C)).toBe(hex(H(v.gcm_record_s2c.nonce).subarray(0, 4)))
    expect(hex(C.DIR_C2S)).not.toBe(hex(C.DIR_S2C))
  })

  test('sealRecord reproduces the pinned s2c record (direction + non-zero counter)', () => {
    // The s2c direction constant and the uint64 BE counter encoding are pinned
    // by the fixture, not just by a literal in this file — Task 2 mirrors the
    // wire format from the fixture alone.
    const s = v.gcm_record_s2c
    const obj = JSON.parse(Buffer.from(s.plaintext, 'hex').toString('utf8'))
    const frame = C.sealRecord(H(s.key), BigInt(s.counter), C.DIR_S2C, obj)

    expect(hex(frame.subarray(0, 9))).toBe(s.aad)
    expect(frame.readBigUInt64BE(1)).toBe(BigInt(s.counter))
    expect(hex(frame.subarray(9))).toBe(s.ct_tag)
    expect(C.openRecord(H(s.key), BigInt(s.counter), C.DIR_S2C, frame)).toEqual(obj)
  })

  test('the two directions are not interchangeable', () => {
    const s = v.gcm_record_s2c
    const obj = JSON.parse(Buffer.from(s.plaintext, 'hex').toString('utf8'))
    const frame = C.sealRecord(H(s.key), BigInt(s.counter), C.DIR_S2C, obj)
    // Same key, same counter, wrong direction constant -> tag fails.
    expect(() => C.openRecord(H(s.key), BigInt(s.counter), C.DIR_C2S, frame)).toThrow()
    expect(hex(C.sealRecord(H(s.key), BigInt(s.counter), C.DIR_C2S, obj).subarray(9))).not.toBe(
      s.ct_tag
    )
  })

  test('openRecord round-trips a sealed record', () => {
    const key = H(r.key)
    const frame = C.sealRecord(key, 7n, C.DIR_S2C, { type: 'push_ack', inserted: 3 })
    expect(C.openRecord(key, 7n, C.DIR_S2C, frame)).toEqual({ type: 'push_ack', inserted: 3 })
  })

  test('a flipped ciphertext byte fails the tag (no plaintext returned)', () => {
    const key = H(r.key)
    const frame = C.sealRecord(key, 0n, C.DIR_S2C, { type: 'auth_ok' })
    frame[10] ^= 0x01
    expect(() => C.openRecord(key, 0n, C.DIR_S2C, frame)).toThrow()
  })

  test('a flipped tag byte fails', () => {
    const key = H(r.key)
    const frame = C.sealRecord(key, 0n, C.DIR_S2C, { type: 'auth_ok' })
    frame[frame.length - 1] ^= 0x80
    expect(() => C.openRecord(key, 0n, C.DIR_S2C, frame)).toThrow()
  })

  test('the header is authenticated as AAD (counter tamper fails the tag)', () => {
    const key = H(r.key)
    const frame = C.sealRecord(key, 5n, C.DIR_S2C, { type: 'auth_ok' })
    frame.writeBigUInt64BE(6n, 1)
    // Ask for 6 so the monotonicity check passes and only the AAD can fail.
    expect(() => C.openRecord(key, 6n, C.DIR_S2C, frame)).toThrow()
  })

  test('a replayed / out-of-order counter is rejected before decryption', () => {
    const key = H(r.key)
    const frame = C.sealRecord(key, 0n, C.DIR_S2C, { type: 'auth_ok' })
    expect(() => C.openRecord(key, 1n, C.DIR_S2C, frame)).toThrow(/counter/i)
  })

  test('the wrong direction key/constant fails', () => {
    const key = H(r.key)
    const frame = C.sealRecord(key, 0n, C.DIR_C2S, { type: 'auth_ok' })
    expect(() => C.openRecord(key, 0n, C.DIR_S2C, frame)).toThrow()
  })

  test('a truncated or mistagged frame is rejected', () => {
    const key = H(r.key)
    const frame = C.sealRecord(key, 0n, C.DIR_S2C, { type: 'auth_ok' })
    expect(() => C.openRecord(key, 0n, C.DIR_S2C, frame.subarray(0, 12))).toThrow()
    const wrongTag = Buffer.from(frame)
    wrongTag[0] = 0x02
    expect(() => C.openRecord(key, 0n, C.DIR_S2C, wrongTag)).toThrow()
  })
})

describe('pairing code — ONE string carrying keyId(4) + secret(16)', () => {
  test('encodes 20 bytes as 33 glyphs from the 31-glyph alphabet', () => {
    expect(C.PAIRING_ALPHABET.length).toBe(31)
    expect(/[01ILO]/.test(C.PAIRING_ALPHABET)).toBe(false)
    expect(C.PAIRING_KEY_ID_BYTES).toBe(4)
    expect(C.PAIRING_CODE_BYTES).toBe(20)

    const code = C.encodePairingCode('ffffffff', Buffer.alloc(16, 0xff))
    expect(code.replace(/-/g, '').length).toBe(C.PAIRING_CODE_LENGTH)
    expect(C.PAIRING_CODE_LENGTH).toBe(33)
    for (const ch of code.replace(/-/g, '')) expect(C.PAIRING_ALPHABET).toContain(ch)
  })

  test('is grouped 5-5-5-5-5-5-3 for reading aloud', () => {
    const code = C.encodePairingCode(C.randomKeyId(), C.randomPairingSecret())
    expect(code.split('-').map((g) => g.length)).toEqual([5, 5, 5, 5, 5, 5, 3])
    expect(code.length).toBe(33 + 6)
  })

  test('round-trips mint -> render -> decode to the same keyId and secret', () => {
    for (let i = 0; i < 50; i++) {
      const keyId = C.randomKeyId()
      const secret = C.randomPairingSecret()
      expect(keyId).toMatch(/^[0-9a-f]{8}$/)
      expect(secret.length).toBe(16)

      const decoded = C.decodePairingCode(C.encodePairingCode(keyId, secret))
      expect(decoded.keyId).toBe(keyId)
      expect(hex(decoded.secret)).toBe(hex(secret))
    }
  })

  test('dashes, whitespace and case are ignored on decode', () => {
    const keyId = C.randomKeyId()
    const secret = C.randomPairingSecret()
    const code = C.encodePairingCode(keyId, secret)
    const flat = code.replace(/-/g, '')

    for (const variant of [flat, flat.toLowerCase(), `  ${code.toLowerCase()}  `, code]) {
      const decoded = C.decodePairingCode(variant)
      expect(decoded.keyId).toBe(keyId)
      expect(hex(decoded.secret)).toBe(hex(secret))
    }
  })

  test('the all-zero code round-trips (fixed width, no truncation)', () => {
    const code = C.encodePairingCode('00000000', Buffer.alloc(16, 0))
    expect(code.replace(/-/g, '').length).toBe(33)
    const decoded = C.decodePairingCode(code)
    expect(decoded.keyId).toBe('00000000')
    expect(hex(decoded.secret)).toBe('00'.repeat(16))
  })

  test('the keyId occupies the LEADING bytes (big-endian over keyId||secret)', () => {
    const a = C.decodePairingCode(C.encodePairingCode('deadbeef', Buffer.alloc(16, 0)))
    expect(a.keyId).toBe('deadbeef')
    expect(hex(a.secret)).toBe('00'.repeat(16))
    const b = C.decodePairingCode(C.encodePairingCode('00000000', Buffer.alloc(16, 0xff)))
    expect(b.keyId).toBe('00000000')
    expect(hex(b.secret)).toBe('ff'.repeat(16))
  })

  test('a malformed code is rejected', () => {
    expect(() => C.decodePairingCode('TOO-SHORT')).toThrow()
    expect(() => C.decodePairingCode('!'.repeat(33))).toThrow()
    // 26 glyphs was the OLD length — it must not decode as if it were valid.
    expect(() => C.decodePairingCode('A'.repeat(26))).toThrow()
    expect(() => C.encodePairingCode('ABCDEF12', Buffer.alloc(16))).toThrow() // not lowercase hex
    expect(() => C.encodePairingCode('deadbeef', Buffer.alloc(8))).toThrow() // short secret
  })

  describe('matches the shared fixture (encoded on desktop, decoded on the phone)', () => {
    const p = v.pairing_code

    test('the codec constants match the pinned contract', () => {
      expect(C.PAIRING_ALPHABET).toBe(p.alphabet)
      expect(C.PAIRING_CODE_LENGTH).toBe(p.code_length)
      expect(C.PAIRING_KEY_ID_BYTES).toBe(p.key_id_bytes)
      expect(C.PAIRING_SECRET_BYTES).toBe(p.secret_bytes)
      expect(C.PAIRING_CODE_BYTES).toBe(p.key_id_bytes + p.secret_bytes)
    })

    for (const t of p.vectors) {
      test(`keyId ${t.keyId_hex} + secret ${t.secret_hex.slice(0, 8)}… -> ${t.code_grouped.slice(0, 11)}…`, () => {
        // Encode side (this is the side the desktop owns).
        const code = C.encodePairingCode(t.keyId_hex, H(t.secret_hex))
        expect(code).toBe(t.code_grouped)
        expect(code.replace(/-/g, '')).toBe(t.code_bare)
        expect(code.split('-').map((g) => g.length)).toEqual(p.grouping)

        // Decode side (this is the side the phone owns — same normalization).
        for (const variant of [
          t.code_grouped,
          t.code_bare,
          t.code_bare.toLowerCase(),
          `  ${t.code_grouped.toLowerCase()}  `
        ]) {
          const decoded = C.decodePairingCode(variant)
          expect(decoded.keyId).toBe(t.keyId_hex)
          expect(hex(decoded.secret)).toBe(t.secret_hex)
        }
      })
    }

    test('the pinned codes only use alphabet glyphs and are exactly 33 long', () => {
      for (const t of p.vectors) {
        expect(t.code_bare.length).toBe(p.code_length)
        for (const ch of t.code_bare) expect(p.alphabet).toContain(ch)
      }
    })
  })

  test('the secret is still 128 bits — the floor did not move', () => {
    const seen = new Set<string>()
    for (let i = 0; i < 100; i++) seen.add(hex(C.randomPairingSecret()))
    expect(seen.size).toBe(100)
    expect(C.randomPairingSecret().length * 8).toBe(128)
  })
})

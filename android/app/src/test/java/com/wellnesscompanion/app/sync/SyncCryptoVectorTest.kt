package com.wellnesscompanion.app.sync

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStreamReader
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

/**
 * The Android half of the `wc-sync/4` wire contract, locked to the SAME
 * `shared/crypto-vectors.json` fixture the desktop's Jest suite asserts
 * against. Every value here was produced independently of this code; an
 * agreement means the two platforms will interoperate byte-for-byte, and a
 * disagreement is a total sync outage caught here instead of at the interop
 * gate.
 *
 * Gson (not org.json) parses the fixture: org.json is an empty Android
 * platform stub on the pure-JVM unit-test classpath.
 */
class SyncCryptoVectorTest {

    private companion object {
        fun loadVectors(): JsonObject {
            val stream = SyncCryptoVectorTest::class.java.classLoader
                ?.getResourceAsStream("crypto-vectors.json")
                ?: error("crypto-vectors.json not found on the unit-test classpath")
            return InputStreamReader(stream, Charsets.UTF_8).use { JsonParser.parseReader(it).asJsonObject }
        }

        fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

        fun unhex(s: String): ByteArray = ByteArray(s.length / 2) {
            ((Character.digit(s[it * 2], 16) shl 4) or Character.digit(s[it * 2 + 1], 16)).toByte()
        }

        val V: JsonObject = loadVectors()

        fun section(name: String): JsonObject = V.getAsJsonObject(name)

        fun str(o: JsonObject, key: String): String = o.get(key).asString
    }

    // ── HKDF (hand-rolled RFC 5869) ────────────────────────────────────────

    @Test
    fun `hkdf matches every RFC 5869 vector, extract and expand separately`() {
        val vectors = V.getAsJsonArray("hkdf_rfc5869")
        assertTrue("expected at least 3 RFC 5869 vectors", vectors.size() >= 3)
        for (element in vectors) {
            val v = element.asJsonObject
            val ikm = unhex(str(v, "IKM"))
            val salt = unhex(str(v, "salt"))
            val info = unhex(str(v, "info"))
            val l = v.get("L").asInt

            val prk = SyncCrypto.hkdfExtract(ikm, salt)
            assertEquals("PRK for L=$l", str(v, "PRK"), hex(prk))
            assertEquals("OKM for L=$l", str(v, "OKM"), hex(SyncCrypto.hkdfExpand(prk, info, l)))
            assertEquals("hkdf() for L=$l", str(v, "OKM"), hex(SyncCrypto.hkdf(ikm, salt, info, l)))
        }
    }

    @Test
    fun `transposing ikm and salt produces a different key (the footgun is detectable)`() {
        val v = V.getAsJsonArray("hkdf_rfc5869")[0].asJsonObject
        val ikm = unhex(str(v, "IKM"))
        val salt = unhex(str(v, "salt"))
        val info = unhex(str(v, "info"))
        val l = v.get("L").asInt
        assertNotEquals(hex(SyncCrypto.hkdf(ikm, salt, info, l)), hex(SyncCrypto.hkdf(salt, ikm, info, l)))
    }

    // ── Handshake derivation ───────────────────────────────────────────────

    @Test
    fun `deriveSession reproduces prk, km, k_c2s and k_s2c`() {
        val h = section("handshake")
        val ss = unhex(str(h, "ss"))
        val psk = unhex(str(h, "psk"))
        val th = unhex(str(h, "th"))

        // prk = HKDF-Extract(salt = th, ikm = ss || psk)
        assertEquals(str(h, "prk"), hex(SyncCrypto.hkdfExtract(ss + psk, th)))

        val keys = SyncCrypto.deriveSession(ss, psk, th)
        assertEquals(str(h, "km"), hex(keys.km))
        assertEquals(str(h, "k_c2s"), hex(keys.kC2S))
        assertEquals(str(h, "k_s2c"), hex(keys.kS2C))
        assertEquals(32, keys.kC2S.size)
        assertEquals(32, keys.kS2C.size)
    }

    @Test
    fun `macTag matches mac_s and mac_c and is domain-separated`() {
        val h = section("handshake")
        val km = unhex(str(h, "km"))
        val th = unhex(str(h, "th"))
        assertEquals(str(h, "mac_s"), hex(SyncCrypto.macTag(km, SyncCrypto.ROLE_SERVER, th)))
        assertEquals(str(h, "mac_c"), hex(SyncCrypto.macTag(km, SyncCrypto.ROLE_CLIENT, th)))
        assertNotEquals(str(h, "mac_s"), str(h, "mac_c"))
    }

    @Test
    fun `pskFromSecret is HKDF(secret, empty, wc-psk, 32) and is deterministic`() {
        val secret = ByteArray(16) { 0xAB.toByte() }
        val psk = SyncCrypto.pskFromSecret(secret)
        assertEquals(32, psk.size)
        assertArrayEquals(SyncCrypto.hkdf(secret, ByteArray(0), "wc-psk".toByteArray(Charsets.US_ASCII), 32), psk)
        assertNotEquals(hex(psk), hex(SyncCrypto.pskFromSecret(ByteArray(16) { 0xAC.toByte() })))
    }

    @Test
    fun `constantTimeEqual compares content, not identity, and rejects length mismatch`() {
        assertTrue(SyncCrypto.constantTimeEqual(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 3)))
        assertFalse(SyncCrypto.constantTimeEqual(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 4)))
        assertFalse(SyncCrypto.constantTimeEqual(byteArrayOf(1, 2, 3), byteArrayOf(1, 2)))
    }

    // ── Transcript hash over exact wire bytes ──────────────────────────────

    @Test
    fun `transcriptHash matches expected_th from the pinned hello, hs1 and pub_s_b64`() {
        val t = section("th_wire_bytes")
        val th = SyncCrypto.transcriptHash(
            str(t, "hello_json").toByteArray(Charsets.UTF_8),
            str(t, "hs1_json").toByteArray(Charsets.UTF_8),
            // pub_s contributes its ASCII/base64 TEXT bytes, never the decoded DER.
            str(t, "pub_s_b64").toByteArray(Charsets.US_ASCII)
        )
        assertEquals(str(t, "expected_th"), hex(th))
    }

    @Test
    fun `transcriptHash matches expected_th when fed the pinned elements_hex directly`() {
        val t = section("th_wire_bytes")
        val elements = t.getAsJsonArray("elements_hex").map { unhex(it.asString) }
        assertEquals(3, elements.size)
        assertEquals(str(t, "expected_th"), hex(SyncCrypto.transcriptHash(*elements.toTypedArray())))
    }

    @Test
    fun `feeding pub_s as decoded DER would NOT match (the interop trap is pinned)`() {
        val t = section("th_wire_bytes")
        val th = SyncCrypto.transcriptHash(
            str(t, "hello_json").toByteArray(Charsets.UTF_8),
            str(t, "hs1_json").toByteArray(Charsets.UTF_8),
            Base64.getDecoder().decode(str(t, "pub_s_b64"))
        )
        assertNotEquals(str(t, "expected_th"), hex(th))
    }

    @Test
    fun `length-prefixing binds element boundaries (it is not concatenation)`() {
        val a = SyncCrypto.transcriptHash("ab".toByteArray(), "c".toByteArray())
        val b = SyncCrypto.transcriptHash("a".toByteArray(), "bc".toByteArray())
        assertNotEquals(hex(a), hex(b))
    }

    // ── ECDH over SPKI DER ─────────────────────────────────────────────────

    @Test
    fun `leading-zero X is preserved as a 32-byte shared secret`() {
        val e = section("ecdh_leading_zero_x")
        val priv = KeyFactory.getInstance("EC")
            .generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(str(e, "priv_a"))))
        val ss = SyncCrypto.ecdhSharedSecret(priv, str(e, "pub_b_spki"))
        assertEquals(32, ss.size)
        assertEquals(str(e, "expected_ss"), hex(ss))
        assertEquals("the leading zero byte must never be stripped", 0, ss[0].toInt())
    }

    @Test
    fun `an off-curve SPKI is rejected`() {
        val priv = SyncCrypto.generateEphemeral().priv
        assertThrows(Exception::class.java) {
            SyncCrypto.ecdhSharedSecret(priv, V.get("offcurve_spki").asString)
        }
    }

    @Test
    fun `a garbage or empty peer key is rejected`() {
        val priv = SyncCrypto.generateEphemeral().priv
        assertThrows(Exception::class.java) { SyncCrypto.ecdhSharedSecret(priv, "bm90LWEta2V5") }
        assertThrows(Exception::class.java) { SyncCrypto.ecdhSharedSecret(priv, "") }
        assertThrows(Exception::class.java) { SyncCrypto.ecdhSharedSecret(priv, "!!!not base64!!!") }
    }

    @Test
    fun `generateEphemeral produces a fresh keypair every call and both sides agree`() {
        val a = SyncCrypto.generateEphemeral()
        val b = SyncCrypto.generateEphemeral()
        assertNotEquals(a.publicSpkiB64, b.publicSpkiB64)
        assertTrue(Base64.getDecoder().decode(a.publicSpkiB64).size > 64)
        assertEquals(
            hex(SyncCrypto.ecdhSharedSecret(a.priv, b.publicSpkiB64)),
            hex(SyncCrypto.ecdhSharedSecret(b.priv, a.publicSpkiB64))
        )
    }

    // ── GCM record framing ─────────────────────────────────────────────────

    @Test
    fun `the direction constants are the pinned nonce prefixes, read from the fixture`() {
        val c2sPrefix = unhex(str(section("gcm_record"), "nonce")).copyOfRange(0, 4)
        val s2cPrefix = unhex(str(section("gcm_record_s2c"), "nonce")).copyOfRange(0, 4)
        assertArrayEquals(c2sPrefix, SyncCrypto.DIR_C2S)
        assertArrayEquals(s2cPrefix, SyncCrypto.DIR_S2C)
        assertNotEquals(hex(SyncCrypto.DIR_C2S), hex(SyncCrypto.DIR_S2C))
    }

    @Test
    fun `sealRecord reproduces the pinned c2s record byte-for-byte`() {
        val r = section("gcm_record")
        val frame = SyncCrypto.sealRecord(unhex(str(r, "key")), 0L, SyncCrypto.DIR_C2S, unhex(str(r, "plaintext")))
        assertEquals(SyncCrypto.RECORD_TAG, frame[0])
        assertEquals(str(r, "aad"), hex(frame.copyOfRange(0, SyncCrypto.RECORD_HEADER_BYTES)))
        assertEquals(str(r, "ct_tag"), hex(frame.copyOfRange(SyncCrypto.RECORD_HEADER_BYTES, frame.size)))
        assertEquals(SyncCrypto.RECORD_HEADER_BYTES + str(r, "ct_tag").length / 2, frame.size)
    }

    @Test
    fun `sealRecord reproduces the pinned s2c record (direction plus a non-zero counter)`() {
        val s = section("gcm_record_s2c")
        val counter = s.get("counter").asLong
        val key = unhex(str(s, "key"))
        val plaintext = unhex(str(s, "plaintext"))
        val frame = SyncCrypto.sealRecord(key, counter, SyncCrypto.DIR_S2C, plaintext)

        assertEquals(str(s, "aad"), hex(frame.copyOfRange(0, SyncCrypto.RECORD_HEADER_BYTES)))
        assertEquals(str(s, "ct_tag"), hex(frame.copyOfRange(SyncCrypto.RECORD_HEADER_BYTES, frame.size)))
        // The counter is a uint64 big-endian in bytes 1..8.
        assertEquals(counter, SyncCrypto.recordCounter(frame))
        assertArrayEquals(plaintext, SyncCrypto.openRecord(key, counter, SyncCrypto.DIR_S2C, frame))
    }

    @Test
    fun `the two directions are not interchangeable`() {
        val s = section("gcm_record_s2c")
        val counter = s.get("counter").asLong
        val key = unhex(str(s, "key"))
        val plaintext = unhex(str(s, "plaintext"))
        val frame = SyncCrypto.sealRecord(key, counter, SyncCrypto.DIR_S2C, plaintext)
        assertThrows(Exception::class.java) {
            SyncCrypto.openRecord(key, counter, SyncCrypto.DIR_C2S, frame)
        }
        assertNotEquals(
            str(s, "ct_tag"),
            hex(
                SyncCrypto.sealRecord(key, counter, SyncCrypto.DIR_C2S, plaintext)
                    .copyOfRange(SyncCrypto.RECORD_HEADER_BYTES, frame.size)
            )
        )
    }

    @Test
    fun `openRecord round-trips a sealed record`() {
        val key = unhex(str(section("gcm_record"), "key"))
        val pt = """{"type":"push_ack","inserted":3}""".toByteArray(Charsets.UTF_8)
        val frame = SyncCrypto.sealRecord(key, 7L, SyncCrypto.DIR_S2C, pt)
        assertArrayEquals(pt, SyncCrypto.openRecord(key, 7L, SyncCrypto.DIR_S2C, frame))
    }

    @Test
    fun `a flipped ciphertext or tag byte fails the tag, and no plaintext is returned`() {
        val key = unhex(str(section("gcm_record"), "key"))
        val pt = """{"type":"auth_ok"}""".toByteArray(Charsets.UTF_8)

        val flippedCt = SyncCrypto.sealRecord(key, 0L, SyncCrypto.DIR_S2C, pt)
        flippedCt[SyncCrypto.RECORD_HEADER_BYTES] = (flippedCt[SyncCrypto.RECORD_HEADER_BYTES].toInt() xor 0x01).toByte()
        assertThrows(Exception::class.java) { SyncCrypto.openRecord(key, 0L, SyncCrypto.DIR_S2C, flippedCt) }

        val flippedTag = SyncCrypto.sealRecord(key, 0L, SyncCrypto.DIR_S2C, pt)
        flippedTag[flippedTag.size - 1] = (flippedTag[flippedTag.size - 1].toInt() xor 0x80).toByte()
        assertThrows(Exception::class.java) { SyncCrypto.openRecord(key, 0L, SyncCrypto.DIR_S2C, flippedTag) }
    }

    @Test
    fun `the record header is authenticated as AAD (a counter tamper fails the tag)`() {
        val key = unhex(str(section("gcm_record"), "key"))
        val frame = SyncCrypto.sealRecord(key, 5L, SyncCrypto.DIR_S2C, "x".toByteArray())
        // Rewrite the counter to 6 and ask for 6, so monotonicity passes and only the AAD can fail.
        frame[8] = 6
        assertThrows(Exception::class.java) { SyncCrypto.openRecord(key, 6L, SyncCrypto.DIR_S2C, frame) }
    }

    @Test
    fun `a replayed or out-of-order counter is rejected before decryption`() {
        val key = unhex(str(section("gcm_record"), "key"))
        val frame = SyncCrypto.sealRecord(key, 0L, SyncCrypto.DIR_S2C, "x".toByteArray())
        val err = assertThrows(Exception::class.java) {
            SyncCrypto.openRecord(key, 1L, SyncCrypto.DIR_S2C, frame)
        }
        assertTrue("expected a counter error, got: ${err.message}", err.message.orEmpty().contains("counter"))
    }

    @Test
    fun `a truncated frame or an unknown record tag is rejected`() {
        val key = unhex(str(section("gcm_record"), "key"))
        val frame = SyncCrypto.sealRecord(key, 0L, SyncCrypto.DIR_S2C, "x".toByteArray())
        assertThrows(Exception::class.java) {
            SyncCrypto.openRecord(key, 0L, SyncCrypto.DIR_S2C, frame.copyOfRange(0, 12))
        }
        val badTag = frame.copyOf()
        badTag[0] = 0x02
        assertThrows(Exception::class.java) { SyncCrypto.openRecord(key, 0L, SyncCrypto.DIR_S2C, badTag) }
    }

    // ── Pairing code (128-bit, base-31 big-endian, 26 glyphs) ──────────────

    @Test
    fun `the pairing alphabet is the 31 confusable-free glyphs and the code is 26 long`() {
        assertEquals(31, SyncCrypto.PAIRING_ALPHABET.length)
        assertFalse(SyncCrypto.PAIRING_ALPHABET.any { it in "01ILO" })
        // The abolished ~40-bit 8-character code must never come back.
        assertEquals(26, SyncCrypto.PAIRING_CODE_LENGTH)
    }

    @Test
    fun `decodePairingCode matches codes produced by the desktop encoder`() {
        // Known answers emitted by the desktop's encodePairingSecret (sync-crypto.ts)
        // — a genuine cross-implementation check, not a self-consistent round trip.
        val known = listOf(
            "00000000000000000000000000000000" to "AAAAA-AAAAA-AAAAA-AAAAA-AAAAAA",
            "ffffffffffffffffffffffffffffffff" to "UYR35-K9VDG-DTTBG-YZ4J7-62BWRH",
            "000102030405060708090a0b0c0d0e0f" to "AAAJD-J3F49-Z2Q3C-KTCG7-ZC3F3X",
            "a3f19c7d2b4e60815fca9d3e7b02c418" to "NMHYJ-8HJR5-WDX3M-RFY38-CWXNF4"
        )
        for ((secretHex, code) in known) {
            val decoded = SyncCrypto.decodePairingCode(code)
            assertEquals("code $code", secretHex, hex(decoded))
            assertEquals(SyncCrypto.PAIRING_SECRET_BYTES, decoded.size)
        }
    }

    @Test
    fun `dashes, whitespace and case are ignored on input`() {
        val expected = "a3f19c7d2b4e60815fca9d3e7b02c418"
        assertEquals(expected, hex(SyncCrypto.decodePairingCode("NMHYJ8HJR5WDX3MRFY38CWXNF4")))
        assertEquals(expected, hex(SyncCrypto.decodePairingCode("nmhyj-8hjr5-wdx3m-rfy38-cwxnf4")))
        assertEquals(expected, hex(SyncCrypto.decodePairingCode("  NMHYJ 8HJR5 WDX3M RFY38 CWXNF4  ")))
    }

    @Test
    fun `a malformed pairing code is rejected`() {
        assertThrows(Exception::class.java) { SyncCrypto.decodePairingCode("TOO-SHORT") }
        assertThrows(Exception::class.java) { SyncCrypto.decodePairingCode("!".repeat(26)) }
        // Valid glyphs, valid length, but a value >= 2^128.
        assertThrows(Exception::class.java) { SyncCrypto.decodePairingCode("9".repeat(26)) }
        // The abolished 8-character code is not accepted.
        assertThrows(Exception::class.java) { SyncCrypto.decodePairingCode("ABCD2345") }
    }
}

package com.wellnesscompanion.app.sync

import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * `wc-sync/4` (`crypto:'x1'`) session crypto — the ANDROID half of the wire
 * contract. Every byte layout here is pinned by `shared/crypto-vectors.json`
 * and mirrored by `windows/src/main/sync-crypto.ts`; changing one side without
 * the other is a total sync outage, so treat this file as a wire format, not
 * as utilities.
 *
 * Zero runtime dependencies — JCA primitives available at minSdk 26 only:
 * EC `secp256r1` (P-256, because `XDH`/X25519 is API 33+), `KeyAgreement`
 * ECDH, `KeyFactory` + `X509EncodedKeySpec` for peer SPKI import, a
 * hand-rolled RFC 5869 HKDF over `HmacSHA256`, and `AES/GCM/NoPadding`.
 *
 * Nothing in here ever logs: every argument is key material.
 */
object SyncCrypto {

    /** Advertised protocol id; mDNS TXT carries the version half of this. */
    const val SYNC_PROTO = "wc-sync/4"
    /** The single ciphersuite. There is deliberately nothing to negotiate. */
    const val CRYPTO = "x1"
    /** The only protocol version this client speaks. Anything else fails closed. */
    const val PROTOCOL_VERSION = 4

    // ── Close codes shared with the desktop (spec §2.8/§2.9) ───────────────
    const val CLOSE_PAIRING_CHANGED = 4001
    const val CLOSE_LOCKED_OUT = 4002
    const val CLOSE_TOO_MANY_CONNECTIONS = 4003
    const val CLOSE_UPDATE_REQUIRED = 4005
    const val CLOSE_REPAIR_REQUIRED = 4006

    /** Reject any text handshake frame larger than this BEFORE any parsing (impl-9). */
    const val MAX_HANDSHAKE_FRAME_BYTES = 8 * 1024

    private const val CURVE = "secp256r1"
    private const val HMAC = "HmacSHA256"
    private const val SHA256 = "SHA-256"
    private const val AES_GCM = "AES/GCM/NoPadding"
    private const val SS_BYTES = 32
    private const val HASH_BYTES = 32
    private const val P256_FIELD_BITS = 256

    /** Record type/version tag — byte 0 of every binary frame. */
    const val RECORD_TAG: Byte = 0x01
    const val GCM_TAG_BYTES = 16
    private const val GCM_TAG_BITS = GCM_TAG_BYTES * 8
    /** byte0 ‖ counter(uint64 BE) */
    const val RECORD_HEADER_BYTES = 9
    const val NONCE_BYTES = 12
    const val PAIRING_SECRET_BYTES = 16

    /**
     * Nonce direction constants: the 4-byte prefix of every GCM nonce, the
     * ASCII label zero-padded to 4. Pinned by the fixture's two `gcm_record`
     * sections (`63327300…` / `73326300…`).
     */
    val DIR_C2S: ByteArray = directionConstant("c2s")
    val DIR_S2C: ByteArray = directionConstant("s2c")

    /** MAC domain separators. `"srv"` authenticates the desktop, `"cli"` the phone. */
    const val ROLE_SERVER = "srv"
    const val ROLE_CLIENT = "cli"

    private fun directionConstant(label: String): ByteArray {
        val out = ByteArray(4)
        val ascii = label.toByteArray(Charsets.US_ASCII)
        require(ascii.size < out.size) { "direction label too long" }
        ascii.copyInto(out)
        return out
    }

    // ── Ephemeral key agreement ────────────────────────────────────────────

    /** A per-connection P-256 keypair. `priv` never leaves the process. */
    class EphemeralKey(val publicSpkiB64: String, val priv: PrivateKey)

    /**
     * Fresh P-256 keypair. MUST be called per connection (impl-2): reusing one
     * across an OkHttp re-dial restarts the record counters at 0 under the same
     * key, which is the GCM nonce-reuse catastrophe.
     */
    fun generateEphemeral(): EphemeralKey {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec(CURVE), SecureRandom())
        val pair = generator.generateKeyPair()
        // getEncoded() on an EC public key is X.509 SubjectPublicKeyInfo DER,
        // which is exactly what the desktop exports and imports.
        return EphemeralKey(Base64.getEncoder().encodeToString(pair.public.encoded), pair.private)
    }

    /**
     * Import a peer's SPKI-DER public key (base64 in JSON). The provider parses
     * and validates the encoded point, so a crafted off-curve point fails here
     * or at [KeyAgreement.doPhase] rather than producing a usable secret —
     * which is why no hand-rolled point arithmetic lives in this file (impl-8).
     */
    private fun importPeerPublicKey(peerSpkiB64: String): PublicKey {
        require(peerSpkiB64.isNotEmpty()) { "peer public key missing" }
        val der = Base64.getDecoder().decode(peerSpkiB64)
        val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(der))
        val ec = key as? ECPublicKey ?: throw IllegalArgumentException("peer key is not an EC key")
        val bits = ec.params.curve.field.fieldSize
        require(bits == P256_FIELD_BITS) { "peer key is not on a 256-bit curve" }
        return key
    }

    /**
     * ECDH against a peer's SPKI-DER public key. The result is the fixed-width
     * 32-byte big-endian X; a leading zero byte is NEVER stripped (a provider
     * that returns a short array would otherwise silently diverge from the
     * desktop, which the `ecdh_leading_zero_x` vector pins).
     */
    fun ecdhSharedSecret(priv: PrivateKey, peerSpkiB64: String): ByteArray {
        val peer = importPeerPublicKey(peerSpkiB64)
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(priv)
        agreement.doPhase(peer, true)
        val raw = agreement.generateSecret()
        require(raw.size <= SS_BYTES) { "shared secret is ${raw.size} bytes" }
        return leftPad(raw, SS_BYTES)
    }

    private fun leftPad(value: ByteArray, width: Int): ByteArray {
        if (value.size == width) return value
        val out = ByteArray(width)
        value.copyInto(out, width - value.size)
        return out
    }

    // ── HKDF (RFC 5869, hand-rolled over HmacSHA256) ───────────────────────

    /** `PRK = HMAC-SHA256(salt, IKM)`. An absent salt is HashLen zero bytes. */
    fun hkdfExtract(ikm: ByteArray, salt: ByteArray): ByteArray {
        val effectiveSalt = if (salt.isEmpty()) ByteArray(HASH_BYTES) else salt
        val mac = Mac.getInstance(HMAC)
        mac.init(SecretKeySpec(effectiveSalt, HMAC))
        return mac.doFinal(ikm)
    }

    /** `OKM = T(1) ‖ T(2) ‖ …`, `T(n) = HMAC(PRK, T(n-1) ‖ info ‖ n)`. */
    fun hkdfExpand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length > 0 && length <= 255 * HASH_BYTES) { "invalid HKDF output length" }
        val mac = Mac.getInstance(HMAC)
        mac.init(SecretKeySpec(prk, HMAC))
        val out = ByteArray(length)
        var previous = ByteArray(0)
        var written = 0
        var counter = 1
        while (written < length) {
            mac.update(previous)
            mac.update(info)
            mac.update(counter.toByte())
            previous = mac.doFinal()
            val take = minOf(previous.size, length - written)
            previous.copyInto(out, written, 0, take)
            written += take
            counter++
        }
        return out
    }

    /**
     * HKDF-SHA256 with the argument order FIXED as (ikm, salt, info, len).
     * Every call site goes through this helper so the order is stated once —
     * transposing ikm and salt still yields 32 plausible bytes and fails only
     * against the desktop peer (impl-7).
     */
    fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray =
        hkdfExpand(hkdfExtract(ikm, salt), info, length)

    /** Session keys for one connection. `km` only ever confirms the handshake. */
    class SessionKeys(val km: ByteArray, val kC2S: ByteArray, val kS2C: ByteArray)

    /** `prk = HKDF-Extract(salt = th, ikm = ss ‖ psk)`, then Expand per label. */
    fun deriveSession(ss: ByteArray, psk: ByteArray, th: ByteArray): SessionKeys {
        val prk = hkdfExtract(ss + psk, th)
        return SessionKeys(
            km = hkdfExpand(prk, "confirm".toByteArray(Charsets.US_ASCII), 32),
            kC2S = hkdfExpand(prk, "c2s".toByteArray(Charsets.US_ASCII), 32),
            kS2C = hkdfExpand(prk, "s2c".toByteArray(Charsets.US_ASCII), 32)
        )
    }

    /** The 128-bit pairing secret is promoted to the long-term PSK by one HKDF. */
    fun pskFromSecret(secret: ByteArray): ByteArray =
        hkdf(secret, ByteArray(0), "wc-psk".toByteArray(Charsets.US_ASCII), 32)

    /** `HMAC-SHA256(km, role ‖ th)`. The role label blocks MAC reflection. */
    fun macTag(km: ByteArray, role: String, th: ByteArray): ByteArray {
        val mac = Mac.getInstance(HMAC)
        mac.init(SecretKeySpec(km, HMAC))
        mac.update(role.toByteArray(Charsets.US_ASCII))
        mac.update(th)
        return mac.doFinal()
    }

    /** Length-safe constant-time compare. */
    fun constantTimeEqual(a: ByteArray, b: ByteArray): Boolean = MessageDigest.isEqual(a, b)

    // ── Transcript ─────────────────────────────────────────────────────────

    /**
     * SHA-256 over the exact wire bytes each side sent/received, each element
     * prefixed with its length as a uint32 BIG-ENDIAN (impl-3). Never hash a
     * re-serialised object: key order or whitespace drift breaks both MACs.
     * The order is hello ‖ hs1 ‖ pub_s_b64, and `pub_s_b64` contributes its
     * ASCII base64 TEXT bytes, not the decoded DER.
     */
    fun transcriptHash(vararg wireElements: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance(SHA256)
        for (element in wireElements) {
            digest.update(uint32BE(element.size))
            digest.update(element)
        }
        return digest.digest()
    }

    private fun uint32BE(value: Int): ByteArray = byteArrayOf(
        (value ushr 24).toByte(),
        (value ushr 16).toByte(),
        (value ushr 8).toByte(),
        value.toByte()
    )

    // ── Binary record framing ──────────────────────────────────────────────

    private fun recordHeader(counter: Long): ByteArray {
        require(counter >= 0) { "record counter out of range" }
        val header = ByteArray(RECORD_HEADER_BYTES)
        header[0] = RECORD_TAG
        for (i in 0 until 8) {
            header[1 + i] = (counter ushr (8 * (7 - i))).toByte()
        }
        return header
    }

    /** The uint64-BE counter carried in bytes 1..8 of a record. */
    fun recordCounter(frame: ByteArray): Long {
        require(frame.size >= RECORD_HEADER_BYTES) { "record too short" }
        var counter = 0L
        for (i in 1..8) counter = (counter shl 8) or (frame[i].toLong() and 0xFF)
        return counter
    }

    private fun nonceFor(dir: ByteArray, header: ByteArray): ByteArray {
        require(dir.size == 4) { "direction constant must be 4 bytes" }
        val nonce = ByteArray(NONCE_BYTES)
        dir.copyInto(nonce)
        header.copyInto(nonce, 4, 1, RECORD_HEADER_BYTES)
        return nonce
    }

    /**
     * Seal one application payload into a binary record:
     *
     *   byte 0      0x01
     *   bytes 1..8  counter, uint64 big-endian, per direction, from 0 each conn
     *   bytes 9..N  GCM ciphertext ‖ tag(16B)     <- Android-native ct‖tag layout
     *
     * Nonce = dir(4B) ‖ counter(8B); `byte0 ‖ counter` is also the GCM AAD,
     * which binds the framing to the ciphertext.
     */
    fun sealRecord(key: ByteArray, counter: Long, dir: ByteArray, plaintext: ByteArray): ByteArray {
        require(key.size == 32) { "record key must be 32 bytes" }
        val header = recordHeader(counter)
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, nonceFor(dir, header))
        )
        cipher.updateAAD(header)
        // JCA appends the 16-byte tag to the ciphertext, which IS the wire layout.
        val ctTag = cipher.doFinal(plaintext)
        return header + ctTag
    }

    /**
     * Open one binary record. The counter is checked before any crypto, and the
     * plaintext is produced by a single [Cipher.doFinal] — never `update()`,
     * whose output JCA releases before the tag is verified (impl-5).
     */
    fun openRecord(key: ByteArray, expectedCounter: Long, dir: ByteArray, frame: ByteArray): ByteArray {
        require(key.size == 32) { "record key must be 32 bytes" }
        require(frame.size >= RECORD_HEADER_BYTES + GCM_TAG_BYTES) { "record too short" }
        require(frame[0] == RECORD_TAG) { "unknown record tag" }

        val counter = recordCounter(frame)
        require(counter == expectedCounter) { "record counter $counter != expected $expectedCounter" }

        val header = frame.copyOfRange(0, RECORD_HEADER_BYTES)
        val body = frame.copyOfRange(RECORD_HEADER_BYTES, frame.size)

        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, nonceFor(dir, header))
        )
        cipher.updateAAD(header)
        return cipher.doFinal(body)
    }

    // ── Pairing code ───────────────────────────────────────────────────────

    /**
     * 31 glyphs, excluding 0/O/1/I/L — the pairs people misread copying a code
     * off a screen. NOT a secret; the entropy is in the 16 random bytes it
     * carries.
     */
    const val PAIRING_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789" // gitleaks:allow

    /**
     * The pairing code carries `keyId(4) ‖ secret(16)` — ONE string for the
     * user, never a lookup handle plus a secret to transcribe separately. (The
     * first cut of this design paired a 36-char UUID keyId with a 26-char
     * secret: ~62 characters for the single interaction that makes sync work.)
     *
     * `keyId` is a public lookup handle with no entropy requirement; a
     * collision is harmless because the wrong secret simply fails the MAC into
     * the existing `4006 repair_required` recovery. The SECRET is still 128
     * bits — that floor does not move, and the abolished ~40-bit code must
     * never come back.
     */
    const val PAIRING_KEY_ID_BYTES = 4
    const val PAIRING_CODE_BYTES = PAIRING_KEY_ID_BYTES + PAIRING_SECRET_BYTES // 20
    /** ceil(160 / log2(31)) = 33 glyphs carry all 20 bytes (31^33 > 2^160). */
    const val PAIRING_CODE_LENGTH = 33
    private const val PAIRING_GROUP = 5

    private val PAIRING_RADIX = BigInteger.valueOf(PAIRING_ALPHABET.length.toLong())
    private val MAX_CODE_VALUE = BigInteger.ONE.shiftLeft(PAIRING_CODE_BYTES * 8)
    private val SEPARATORS = Regex("[\\s-]")
    private val KEY_ID_FORMAT = Regex("^[0-9a-f]{8}$")

    /** What one typed code resolves to. `keyId` is the wire form used in `hs1`. */
    class PairingCodeParts(val keyId: String, val secret: ByteArray)

    /**
     * Render `keyId ‖ secret` as one fixed-width, dash-grouped base-31 code:
     * big-endian over the 20 bytes, 33 glyphs, grouped 5-5-5-5-5-5-3. The phone
     * never shows a code to the user; this exists so the decoder can be pinned
     * against the desktop's rendering, byte-identically, in the vector suite.
     */
    fun encodePairingCode(keyId: String, secret: ByteArray): String {
        require(KEY_ID_FORMAT.matches(keyId)) { "keyId must be 8 lowercase hex characters" }
        require(secret.size == PAIRING_SECRET_BYTES) {
            "pairing secret must be $PAIRING_SECRET_BYTES bytes"
        }
        var value = BigInteger(keyId + secret.joinToString("") { "%02x".format(it) }, 16)
        val glyphs = CharArray(PAIRING_CODE_LENGTH)
        for (i in PAIRING_CODE_LENGTH - 1 downTo 0) {
            val divRem = value.divideAndRemainder(PAIRING_RADIX)
            glyphs[i] = PAIRING_ALPHABET[divRem[1].toInt()]
            value = divRem[0]
        }
        check(value.signum() == 0) { "pairing code overflowed its length" }
        return String(glyphs).chunked(PAIRING_GROUP).joinToString("-")
    }

    /**
     * Decode a typed pairing code back to the desktop's `keyId` and 16 random
     * bytes. Dashes, whitespace and case are ignored. Error messages
     * deliberately never quote the offending character — the code is secret
     * material.
     */
    fun decodePairingCode(code: String): PairingCodeParts {
        val cleaned = code.trim().uppercase().replace(SEPARATORS, "")
        require(cleaned.length == PAIRING_CODE_LENGTH) {
            "The pairing code is $PAIRING_CODE_LENGTH characters"
        }
        var value = BigInteger.ZERO
        for (ch in cleaned) {
            val index = PAIRING_ALPHABET.indexOf(ch)
            require(index >= 0) { "The pairing code has a character that is not on the code chart" }
            value = value.multiply(PAIRING_RADIX).add(BigInteger.valueOf(index.toLong()))
        }
        require(value < MAX_CODE_VALUE) { "That is not a valid pairing code" }
        // Fixed width: an all-zero code must yield 20 zero bytes, never a short
        // array that would silently shift keyId and secret into each other.
        val magnitude = value.toByteArray() // may carry a leading 0x00 sign byte
        val take = minOf(magnitude.size, PAIRING_CODE_BYTES)
        val bytes = ByteArray(PAIRING_CODE_BYTES)
        magnitude.copyInto(bytes, PAIRING_CODE_BYTES - take, magnitude.size - take, magnitude.size)
        return PairingCodeParts(
            keyId = bytes.copyOfRange(0, PAIRING_KEY_ID_BYTES).joinToString("") { "%02x".format(it) },
            secret = bytes.copyOfRange(PAIRING_KEY_ID_BYTES, PAIRING_CODE_BYTES)
        )
    }
}

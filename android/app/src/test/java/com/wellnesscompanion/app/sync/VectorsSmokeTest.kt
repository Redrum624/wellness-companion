package com.wellnesscompanion.app.sync

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStreamReader

/**
 * Smoke test for `shared/crypto-vectors.json`: proves the fixture resolves on
 * the pure-JVM unit-test classpath and every top-level section parses with
 * the expected shape. Deliberately shape-only -- Task 2 implements and tests
 * the actual RFC 5869 / handshake derivation contract in JCA against these
 * same vectors.
 *
 * JSON parsing uses Gson (already an `implementation` dependency of :app, so
 * this adds nothing new to the dependency graph) rather than org.json:
 * org.json ships as an Android-platform stub on the pure-JVM unit-test
 * classpath and throws unless `testOptions.unitTests.isReturnDefaultValues`
 * papers over every call -- Gson sidesteps that footgun entirely.
 */
class VectorsSmokeTest {

    private fun loadVectors(): JsonObject {
        val stream = javaClass.classLoader?.getResourceAsStream("crypto-vectors.json")
            ?: error("crypto-vectors.json not found on the unit-test classpath")
        return InputStreamReader(stream, Charsets.UTF_8).use { reader ->
            JsonParser.parseReader(reader).asJsonObject
        }
    }

    @Test
    fun `fixture has all five sections and they parse with the expected shape`() {
        val vectors = loadVectors()

        val hkdfArray = vectors.getAsJsonArray("hkdf_rfc5869")
        assertTrue("expected at least 3 RFC 5869 vectors, got ${hkdfArray.size()}", hkdfArray.size() >= 3)

        val handshake = vectors.getAsJsonObject("handshake")
        val km = handshake.get("km").asString
        assertTrue("km must be 64 lowercase hex chars, got '$km'", km.matches(Regex("^[0-9a-f]{64}$")))

        val gcmRecord = vectors.getAsJsonObject("gcm_record")
        val ctTag = gcmRecord.get("ct_tag").asString
        val plaintext = gcmRecord.get("plaintext").asString
        assertTrue(
            "ct_tag hex must be longer than plaintext hex (16B GCM tag appended)",
            ctTag.length > plaintext.length
        )

        val ecdh = vectors.getAsJsonObject("ecdh_leading_zero_x")
        val expectedSs = ecdh.get("expected_ss").asString
        assertTrue(
            "expected_ss must be a 32-byte hex string with a leading zero byte, got '$expectedSs'",
            expectedSs.matches(Regex("^00[0-9a-f]{62}$"))
        )
        assertTrue(ecdh.get("priv_a").asString.isNotEmpty())
        assertTrue(ecdh.get("pub_b_spki").asString.isNotEmpty())

        val offcurveSpki = vectors.get("offcurve_spki").asString
        assertTrue(offcurveSpki.isNotEmpty())

        // th_wire_bytes: shape-only here -- Task 2 owns the full JCA recompute
        // of the uint32BE-length-prefix rule (mirrors the Jest test's second
        // independent implementation of the same rule).
        val thWireBytes = vectors.getAsJsonObject("th_wire_bytes")
        val expectedTh = thWireBytes.get("expected_th").asString
        assertTrue(
            "expected_th must be 64 lowercase hex chars, got '$expectedTh'",
            expectedTh.matches(Regex("^[0-9a-f]{64}$"))
        )
        val elementsHex = thWireBytes.getAsJsonArray("elements_hex")
        assertTrue("th_wire_bytes.elements_hex must have exactly 3 elements (hello, hs1, pub_s_b64)", elementsHex.size() == 3)
        assertTrue(thWireBytes.get("hello_json").asString.isNotEmpty())
        assertTrue(thWireBytes.get("hs1_json").asString.isNotEmpty())
        assertTrue(thWireBytes.get("pub_s_b64").asString.isNotEmpty())
    }
}

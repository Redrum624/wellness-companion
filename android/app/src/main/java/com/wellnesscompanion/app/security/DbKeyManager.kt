package com.wellnesscompanion.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * at-rest: Keystore-wrapped Room/SQLCipher passphrase (spec §5.1).
 *
 * The DB passphrase is 32 `SecureRandom` bytes -- never derived, never hardcoded.
 * It is wrapped with a one-time, non-exportable AndroidKeyStore AES-256-GCM key
 * and the resulting `iv‖wrapped` blob is persisted as a single Base64 string in
 * plain SharedPreferences. That is safe precisely because the blob is already
 * opaque AES-GCM ciphertext -- layering EncryptedSharedPreferences on top would
 * add nothing but deprecated (1.1.0) surface (spec explicitly calls this out).
 *
 * `setUserAuthenticationRequired` is deliberately left at its `false` default:
 * the app has no lock-screen gate and syncs from a background Worker, so a
 * lock-bound key would make the passphrase unreadable exactly when the sync
 * worker needs it. Documented trade-off, spec §6.
 *
 * Never log the passphrase, the wrapping key, or the wrapped blob.
 */
object DbKeyManager {

    private const val TAG = "DbKeyManager"
    private const val PREFS_NAME = "wellness_db_key"
    private const val PREF_KEY_BLOB = "wellness_db_key"
    private const val KEYSTORE_ALIAS = "wellness_db_key_wrap"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_IV_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private const val PASSPHRASE_BYTES = 32

    /**
     * Returns the 32-byte DB passphrase, minting and persisting a wrapped one on
     * first run. Every subsequent call on this device returns the same bytes.
     */
    fun getOrCreatePassphrase(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existingBlob = prefs.getString(PREF_KEY_BLOB, null)
        if (existingBlob != null) {
            return unwrap(existingBlob)
        }

        val passphrase = ByteArray(PASSPHRASE_BYTES).also { SecureRandom().nextBytes(it) }
        val blob = wrap(passphrase)
        // commit() is synchronous: a process death right after this call can never
        // leave a passphrase minted in memory but unpersisted on disk, which would
        // otherwise re-mint a DIFFERENT key next launch against an already-keyed db.
        val saved = prefs.edit().putString(PREF_KEY_BLOB, blob).commit()
        check(saved) { "could not persist the wrapped database passphrase" }
        Log.i(TAG, "Minted a new database passphrase (Keystore-wrapped).")
        return passphrase
    }

    private fun wrappingKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false) // deliberate -- see class doc
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private fun wrap(passphrase: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey())
        val iv = cipher.iv
        check(iv.size == GCM_IV_BYTES) { "unexpected GCM IV length from AndroidKeyStore" }
        val wrapped = cipher.doFinal(passphrase)
        return Base64.encodeToString(iv + wrapped, Base64.NO_WRAP)
    }

    private fun unwrap(blob: String): ByteArray {
        val bytes = Base64.decode(blob, Base64.NO_WRAP)
        require(bytes.size > GCM_IV_BYTES) { "wrapped database key blob is too short" }
        val iv = bytes.copyOfRange(0, GCM_IV_BYTES)
        val wrapped = bytes.copyOfRange(GCM_IV_BYTES, bytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        val passphrase = cipher.doFinal(wrapped)
        check(passphrase.size == PASSPHRASE_BYTES) {
            "unwrapped database passphrase had unexpected length ${passphrase.size}"
        }
        return passphrase
    }
}

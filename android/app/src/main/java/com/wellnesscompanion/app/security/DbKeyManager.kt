package com.wellnesscompanion.app.security

import android.content.Context
import android.content.SharedPreferences
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
    /**
     * Every wrapped blob that [mintFreshPassphrase] has ever replaced, newline
     * separated, oldest first (a Base64 NO_WRAP blob never contains a newline,
     * so the join is unambiguous). Append-only: a blob is NEVER dropped from
     * this list, because the file it decrypts (`wellness.db.keylost*.bak`) is
     * never deleted either.
     */
    private const val PREF_KEY_BLOB_PREVIOUS = "wellness_db_key.previous"
    private const val KEYSTORE_ALIAS = "wellness_db_key_wrap"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_IV_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private const val PASSPHRASE_BYTES = 32

    /**
     * Returns the 32-byte DB passphrase, minting and persisting a wrapped one on
     * first run. Every subsequent call on this device returns the same bytes.
     * Throws if an existing wrapped blob can no longer be unwrapped (corrupt
     * blob, lost Keystore alias) -- callers must not treat that as "mint a new
     * one" (see [existingWrappingKey]); `AppModule` handles that recovery via
     * `DbEncryptionMigrator.recoverFromLostKey` + [mintFreshPassphrase].
     */
    fun getOrCreatePassphrase(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existingBlob = prefs.getString(PREF_KEY_BLOB, null)
        if (existingBlob != null) {
            return unwrap(existingBlob)
        }
        return mintAndPersist(prefs, "Minted a new database passphrase (Keystore-wrapped).")
    }

    /**
     * Mints and persists a brand-new passphrase unconditionally, replacing the
     * existing (unusable) wrapped blob. Only for the lost-key recovery path in
     * `AppModule` -- [getOrCreatePassphrase] must never silently do this on its
     * own, which would risk stranding data still under the old key before a
     * caller gets a chance to recover it via a `.plaintext.bak`.
     *
     * The blob being replaced is PRESERVED first, under
     * [PREF_KEY_BLOB_PREVIOUS]. That is what keeps the recovery reversible: the
     * lost-key path renames the undecryptable `wellness.db` aside to
     * `wellness.db.keylost*.bak` and rolls back to the plaintext backup, so
     * every entry written since the migration lives ONLY in that keylost file.
     * The unwrap failure that triggered all this may be transient (the Keystore
     * alias briefly unavailable, a locked-device hiccup) -- and if the only
     * blob that can ever decrypt the keylost file were overwritten here, a
     * transient failure would be silently promoted to permanent data loss.
     * Preserving it costs one string and leaves the door open.
     */
    fun mintFreshPassphrase(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        preserveCurrentBlob(prefs)
        return mintAndPersist(prefs, "Minted a fresh database passphrase after the previous key became unusable.")
    }

    /**
     * Appends the blob currently in [PREF_KEY_BLOB] to the append-only
     * [PREF_KEY_BLOB_PREVIOUS] list, committed synchronously BEFORE anything
     * overwrites it. Appends rather than overwrites so a second key loss cannot
     * discard the blob belonging to the first keylost file. Never logs a blob.
     *
     * Nothing reads these back automatically: an automatic "try every old key"
     * pass would have to decide where to put what it found, and this file's
     * discipline is that a recovery path never guesses. They stay in
     * `shared_prefs/wellness_db_key.xml` for as long as the app is installed so
     * the keylost files remain decryptable AT ALL -- by a later deliberate
     * recovery flow, not by a silent one.
     */
    private fun preserveCurrentBlob(prefs: SharedPreferences) {
        val current = prefs.getString(PREF_KEY_BLOB, null) ?: return
        val existing = prefs.getString(PREF_KEY_BLOB_PREVIOUS, null)
        val kept = existing?.split('\n')?.filter { it.isNotEmpty() } ?: emptyList()
        if (kept.contains(current)) return
        val saved = prefs.edit()
            .putString(PREF_KEY_BLOB_PREVIOUS, (kept + current).joinToString("\n"))
            .commit()
        check(saved) { "could not preserve the previous wrapped database passphrase" }
        Log.i(TAG, "Preserved the previous wrapped database key (${kept.size + 1} kept).")
    }

    private fun mintAndPersist(prefs: SharedPreferences, logMessage: String): ByteArray {
        val passphrase = ByteArray(PASSPHRASE_BYTES).also { SecureRandom().nextBytes(it) }
        val blob = wrap(passphrase)
        // commit() is synchronous: a process death right after this call can never
        // leave a passphrase minted in memory but unpersisted on disk, which would
        // otherwise re-mint a DIFFERENT key next launch against an already-keyed db.
        val saved = prefs.edit().putString(PREF_KEY_BLOB, blob).commit()
        check(saved) { "could not persist the wrapped database passphrase" }
        Log.i(TAG, logMessage)
        return passphrase
    }

    /** Mints the wrap key on first use -- fine for [wrap]: a fresh mint there just means a fresh passphrase gets a fresh wrap. */
    private fun getOrCreateWrappingKey(): SecretKey {
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

    /**
     * The wrap key for [unwrap] ONLY -- must never mint (M1, post-review fix).
     * Minting here would silently swap in a NEW alias key that cannot decrypt
     * a blob wrapped under the OLD (now-missing) key, and unwrap would then
     * fail with a confusing GCM "bad tag" instead of surfacing the real
     * problem -- the alias itself is gone -- to the caller that decides
     * whether the lost-key recovery path applies.
     */
    private fun existingWrappingKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey
            ?: throw IllegalStateException("the AndroidKeyStore wrapping key '$KEYSTORE_ALIAS' no longer exists")
    }

    private fun wrap(passphrase: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateWrappingKey())
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
        cipher.init(Cipher.DECRYPT_MODE, existingWrappingKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        val passphrase = cipher.doFinal(wrapped)
        check(passphrase.size == PASSPHRASE_BYTES) {
            "unwrapped database passphrase had unexpected length ${passphrase.size}"
        }
        return passphrase
    }
}

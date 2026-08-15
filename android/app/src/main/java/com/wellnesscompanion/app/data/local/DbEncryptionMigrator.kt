package com.wellnesscompanion.app.data.local

import android.util.Log
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File
import java.io.RandomAccessFile

/**
 * at-rest: plaintext -> encrypted Room DB migration via `sqlcipher_export()`
 * (spec §5.3). Never mutates the live `wellness.db` before a real keyed read on
 * the encrypted copy has succeeded; any failure leaves the original exactly as
 * it was and the app runs unencrypted this session, retrying on the next
 * launch. Mirrors the desktop's `migrateToEncryptedIfNeeded` /
 * `recoverInterruptedSwap` discipline (`windows/src/main/database.ts`).
 *
 * Divergence from the brief (load-bearing, see task-4-report.md): the pinned
 * artifact `net.zetetic:sqlcipher-android:4.17.0` is the NEW rewritten
 * "SQLCipher for Android" (github.com/sqlcipher/sqlcipher-android), not the
 * classic `net.sqlcipher.database` wrapper the brief/spec snippets were written
 * against. Its package is `net.zetetic.database.sqlcipher`, there is no
 * `SQLiteDatabase.loadLibs(Context)` -- `System.loadLibrary("sqlcipher")` must
 * be called directly -- and the Room factory class is `SupportOpenHelperFactory`,
 * not `SupportFactory`. `SQLCipherUtils` does not ship in this artifact either,
 * confirming the brief's warning; the header check below is the local
 * reimplementation it anticipated.
 */
object DbEncryptionMigrator {

    private const val TAG = "DbEncryptionMigrator"
    private const val TMP_SUFFIX = ".encrypting.tmp"
    private const val BAK_SUFFIX = ".plaintext.bak"
    private val PLAINTEXT_MAGIC = byteArrayOf(
        'S'.code.toByte(), 'Q'.code.toByte(), 'L'.code.toByte(), 'i'.code.toByte(),
        't'.code.toByte(), 'e'.code.toByte(), ' '.code.toByte(), 'f'.code.toByte(),
        'o'.code.toByte(), 'r'.code.toByte(), 'm'.code.toByte(), 'a'.code.toByte(),
        't'.code.toByte(), ' '.code.toByte(), '3'.code.toByte(), 0
    )

    @Volatile private var nativeLibLoaded = false

    /** Idempotent: safe to call from both the migrator and Room wiring. */
    @Synchronized
    fun ensureNativeLibraryLoaded() {
        if (!nativeLibLoaded) {
            System.loadLibrary("sqlcipher")
            nativeLibLoaded = true
        }
    }

    /**
     * The raw 32-byte passphrase, unmodified -- this is what every C-API entry
     * point in this library (`SupportOpenHelperFactory(byte[])`,
     * `SQLiteDatabase.openDatabase(path, byte[], ...)`) expects: it hands the
     * buffer straight to the native keying call and uses it verbatim as raw key
     * material, no PBKDF2, no `x'...'` escape needed (measured on-device: an
     * ASCII `x'<hex>'`-encoded buffer here does NOT decode back to the original
     * bytes -- it derives a different key and the reopen fails with
     * `SQLITE_NOTADB`). Exists as a named seam (not a bare `passphrase` pass-through)
     * so the C-API call sites read as deliberate, and so AppModule doesn't
     * duplicate this reasoning.
     */
    fun rawKeyBytes(passphrase: ByteArray): ByteArray = passphrase

    /**
     * The passphrase as SQLCipher's raw-key SQL literal (`x'<64 lowercase hex
     * chars>'`) for embedding directly in ATTACH/PRAGMA statement text -- the
     * SQL layer's BLOB literal syntax, which decodes back to the same 32 raw
     * bytes on the SQL side (unlike the C-API path above, which wants those 32
     * bytes as-is, not this ASCII form).
     */
    private fun hexKeyLiteral(passphrase: ByteArray): String = "x'${hexOf(passphrase)}'"

    private fun hexOf(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) sb.append(String.format("%02x", b))
        return sb.toString()
    }

    sealed class MigrationResult {
        object NotNeeded : MigrationResult()
        data class Migrated(val rows: Int) : MigrationResult()
        data class Failed(val reason: String) : MigrationResult()
    }

    sealed class RecoveryResult {
        object NotNeeded : RecoveryResult()
        data class Recovered(val detail: String) : RecoveryResult()
        data class Unrecoverable(val reason: String) : RecoveryResult()
    }

    /** Magic-header check (spec §5.3): cheap, and it cannot desync from the file. */
    fun isPlaintextSqliteFile(file: File): Boolean {
        if (!file.exists() || file.length() < PLAINTEXT_MAGIC.size) return false
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val head = ByteArray(PLAINTEXT_MAGIC.size)
                raf.readFully(head)
                head.contentEquals(PLAINTEXT_MAGIC)
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun tmpFile(dbFile: File) = File(dbFile.parentFile, dbFile.name + TMP_SUFFIX)
    private fun bakFile(dbFile: File) = File(dbFile.parentFile, dbFile.name + BAK_SUFFIX)

    private fun removeIfPresent(file: File) {
        try {
            if (file.exists() && !file.delete()) {
                Log.e(TAG, "Could not remove ${file.name}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not remove ${file.name}: ${e.message}")
        }
    }

    private fun cleanupTmp(dbFile: File) {
        val tmp = tmpFile(dbFile)
        removeIfPresent(tmp)
        removeIfPresent(File(tmp.path + "-wal"))
        removeIfPresent(File(tmp.path + "-shm"))
        removeIfPresent(File(tmp.path + "-journal"))
    }

    private fun removeSidecars(dbFile: File) {
        removeIfPresent(File(dbFile.path + "-wal"))
        removeIfPresent(File(dbFile.path + "-shm"))
        removeIfPresent(File(dbFile.path + "-journal"))
    }

    /**
     * Recovers from a swap interrupted mid-rename. MUST run before Room (or
     * anything else) opens `dbFile`. Between the two renames in
     * [migrateIfNeeded], `dbFile` briefly does not exist; a process death there
     * would otherwise read as a fresh install and let Room create an empty
     * database over data that is still intact in the tmp/backup copy.
     */
    fun recoverInterruptedSwap(dbFile: File, passphrase: ByteArray): RecoveryResult {
        if (dbFile.exists()) return RecoveryResult.NotNeeded

        val tmp = tmpFile(dbFile)
        val bak = bakFile(dbFile)
        val hasTmp = tmp.exists() && tmp.length() > 0
        val hasBak = bak.exists() && bak.length() > 0
        if (!hasTmp && !hasBak) return RecoveryResult.NotNeeded // genuine first run

        val failures = mutableListOf<String>()

        if (hasTmp) {
            try {
                ensureNativeLibraryLoaded()
                val rows = verifyEncryptedReadable(tmp, passphrase)
                if (tmp.renameTo(dbFile)) {
                    Log.i(TAG, "Recovered an interrupted migration from the encrypted copy ($rows entries).")
                    return RecoveryResult.Recovered("premigration copy, $rows entries")
                }
                failures.add("could not rename the encrypted tmp copy into place")
            } catch (e: Exception) {
                // Leave the tmp on disk untouched; fall through to the backup.
                failures.add("premigration copy: ${e.message}")
            }
        }

        if (hasBak) {
            if (bak.renameTo(dbFile)) {
                Log.i(TAG, "Recovered an interrupted migration by restoring the plaintext backup.")
                return RecoveryResult.Recovered("plaintext backup")
            }
            failures.add("could not restore the plaintext backup")
        }

        return RecoveryResult.Unrecoverable(
            "wellness.db is missing after an interrupted migration and could not be recovered " +
                "(${failures.joinToString("; ")})"
        )
    }

    /**
     * Plaintext -> encrypted migration via `sqlcipher_export()` (spec §5.3).
     * Detects the need by magic header, not a flag. The whole export happens
     * against a brand-new `<dbName>.encrypting.tmp`; `dbFile` itself is only
     * opened read/write to serve as the `sqlcipher_export` SOURCE (writes land
     * on the attached tmp, never on `main`), so its bytes never change until the
     * verified swap. Any failure deletes the tmp and leaves `dbFile` untouched.
     */
    fun migrateIfNeeded(dbFile: File, passphrase: ByteArray): MigrationResult {
        if (!isPlaintextSqliteFile(dbFile)) return MigrationResult.NotNeeded

        ensureNativeLibraryLoaded()
        val tmp = tmpFile(dbFile)
        val bak = bakFile(dbFile)
        cleanupTmp(dbFile) // a killed earlier attempt must not be mistaken for progress
        // This library's ATTACH ... KEY refuses to create a brand-new target file
        // (measured on-device: SQLITE_CANTOPEN/ENOENT from the VFS open inside
        // ATTACH) and expects the path to already exist. Pre-create an empty
        // placeholder so ATTACH only has to open, not create, it.
        try {
            tmp.createNewFile()
        } catch (e: Exception) {
            cleanupTmp(dbFile)
            return MigrationResult.Failed("could not create the tmp file for export: ${e.message}")
        }

        var plainDb: SQLiteDatabase? = null
        try {
            plainDb = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            // The tmp path is embedded as an escaped SQL string literal (our own
            // File.absolutePath, not attacker-controlled); the key is SQLCipher's
            // raw-key BLOB literal x'<hex>' (hexKeyLiteral doc) -- ATTACH...KEY
            // needs the SQL-side encoding, which is NOT the same encoding the
            // C-API paths (verifyEncryptedReadable, Room's SupportOpenHelperFactory
            // in AppModule) need for the identical key material -- see both
            // functions' docs.
            val escapedTmpPath = tmp.absolutePath.replace("'", "''")
            plainDb.rawExecSQL("ATTACH DATABASE '$escapedTmpPath' AS encrypted KEY ${hexKeyLiteral(passphrase)}")
            plainDb.rawExecSQL("SELECT sqlcipher_export('encrypted')")
            plainDb.rawExecSQL("DETACH DATABASE encrypted")
        } catch (e: Exception) {
            try { plainDb?.close() } catch (closeErr: Exception) {
                Log.e(TAG, "Closing the plaintext handle after a failed export failed: ${closeErr.message}")
            }
            cleanupTmp(dbFile)
            return MigrationResult.Failed("export failed: ${e.message}")
        }
        try {
            plainDb.close()
        } catch (e: Exception) {
            Log.e(TAG, "Closing the plaintext handle failed: ${e.message}")
        }

        // Verification is a real keyed read on a fresh handle: anything less
        // would swap in a file we have never actually decrypted.
        val rows: Int
        try {
            rows = verifyEncryptedReadable(tmp, passphrase)
        } catch (e: Exception) {
            cleanupTmp(dbFile)
            return MigrationResult.Failed("verification read failed: ${e.message}")
        }

        removeIfPresent(bak) // keep exactly one plaintext cycle
        // The two renames below are the one window in which dbFile does not
        // exist. Nothing is lost if the process dies here -- the data is in the
        // tmp and the .plaintext.bak -- but the next launch must not mistake the
        // gap for a fresh install, which is what recoverInterruptedSwap() is for.
        if (!dbFile.renameTo(bak)) {
            cleanupTmp(dbFile)
            return MigrationResult.Failed("could not rename the original database aside")
        }
        if (!tmp.renameTo(dbFile)) {
            // Put the original back rather than leave the app with no database.
            if (!bak.renameTo(dbFile)) {
                // Truly stuck: leave both survivors on disk for
                // recoverInterruptedSwap() on the next launch instead of
                // deleting anything.
                return MigrationResult.Failed(
                    "swap failed and the original could not be restored -- data is safe in " +
                        "${bak.name} / ${tmp.name}, will recover on next launch"
                )
            }
            cleanupTmp(dbFile)
            return MigrationResult.Failed("could not rename the encrypted copy into place; original restored")
        }

        // A plaintext -wal/-shm left beside the now-encrypted file would be
        // replayed into it on the next open.
        removeSidecars(dbFile)

        return MigrationResult.Migrated(rows)
    }

    private fun verifyEncryptedReadable(file: File, passphrase: ByteArray): Int {
        // Same raw-key literal encoding used everywhere else (rawKeyBytes doc) --
        // this is the exact byte[] Room's SupportOpenHelperFactory is given too,
        // so a successful open here is real evidence Room will open it as well.
        val db = SQLiteDatabase.openDatabase(
            file.absolutePath, rawKeyBytes(passphrase), null, SQLiteDatabase.OPEN_READWRITE, null
        )
        try {
            db.rawQuery("SELECT count(*) FROM entries", null).use { cursor ->
                check(cursor.moveToFirst()) { "verification read returned no row" }
                return cursor.getInt(0)
            }
        } finally {
            try { db.close() } catch (e: Exception) {
                Log.e(TAG, "Closing the verification handle failed: ${e.message}")
            }
        }
    }
}

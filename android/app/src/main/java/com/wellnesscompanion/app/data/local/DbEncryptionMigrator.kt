package com.wellnesscompanion.app.data.local

import android.util.Log
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Seam for a real keyed row-count read, injectable so JVM unit tests (no
 * native SQLCipher library on that classpath) can exercise the swap and
 * recovery logic without it. Mirrors the desktop's `CipherDbOpener`
 * (`database.ts:554-558`).
 */
internal typealias VerifyFn = (file: File, passphrase: ByteArray) -> Int

/** Seam for the swap/restore renames. Mirrors the desktop's `RenameFn` (`database.ts:558`). */
internal typealias RenameFn = (from: File, to: File) -> Boolean

/**
 * at-rest: plaintext -> encrypted Room DB migration via `sqlcipher_export()`
 * (spec §5.3). Never mutates the live `wellness.db` before a real keyed read on
 * the encrypted copy has succeeded; any failure leaves the original exactly as
 * it was and the app runs unencrypted this session, retrying on the next
 * launch. Mirrors the desktop's `migrateToEncryptedIfNeeded` /
 * `recoverInterruptedSwap` discipline (`windows/src/main/database.ts`).
 *
 * Divergence from the brief (load-bearing): the pinned
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
    private const val KEYLOST_SUFFIX = ".keylost.bak"
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
        /** Original plaintext still on disk (or restored); safe to open unencrypted this session. */
        data class Failed(val reason: String) : MigrationResult()
        /**
         * Post-review fix (C1): the swap failed AND the restore-back also
         * failed, so `dbFile` does not exist even though the migration was
         * otherwise successful (a verified encrypted copy exists at the tmp
         * path, the original at the backup path). This is deliberately NOT
         * `Failed` -- `AppModule` used to treat every `Failed` as "plaintext
         * still on disk, open unencrypted," which let Room silently create a
         * brand-new EMPTY database over two intact survivors. Callers MUST
         * fail closed on this result, never open Room.
         */
        data class DatabaseMissing(val reason: String) : MigrationResult()
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

    /**
     * A fresh, non-colliding name for the "key was lost, this file can no
     * longer be decrypted" copy. It NEVER returns a name that is already taken:
     * an earlier `.keylost` file is data-bearing (it may be the only copy of
     * everything written since the migration), and deleting one to reuse its
     * name on a data-loss-recovery path is exactly the mistake this whole file
     * exists to avoid. Second and later cycles get a timestamp suffix
     * (`wellness.db.keylost-20260815-142530.bak`, `-1`, `-2`, ... on the same
     * second) so every generation survives side by side.
     */
    private fun freshKeylostFile(dbFile: File): File {
        val base = File(dbFile.parentFile, dbFile.name + KEYLOST_SUFFIX)
        if (!base.exists()) return base
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        var candidate = File(dbFile.parentFile, "${dbFile.name}.keylost-$stamp.bak")
        var n = 1
        while (candidate.exists()) {
            candidate = File(dbFile.parentFile, "${dbFile.name}.keylost-$stamp-$n.bak")
            n++
        }
        return candidate
    }

    /**
     * True when a migration survivor -- the verified encrypted
     * `.encrypting.tmp` or the pre-migration `.plaintext.bak` -- is on disk and
     * non-empty.
     *
     * This is the qualifier the caller's "wellness.db is missing" guard needs.
     * A missing `wellness.db` on its own is the completely normal FIRST LAUNCH
     * of a new install: nothing creates the file before Room does. It is only
     * an emergency when a survivor is sitting beside the hole, which is the
     * interrupted-swap state (spec §4.4 Amendment 2026-08-15b (b)). The desktop
     * relies on the same invariant -- it computes `hadExistingDb` AFTER
     * recovery and only fails closed on a missing file when a survivor exists
     * (`windows/src/main/database.ts:51`, `SURVIVOR_GUIDANCE`).
     */
    fun hasSurvivors(dbFile: File): Boolean {
        val tmp = tmpFile(dbFile)
        val bak = bakFile(dbFile)
        return (tmp.exists() && tmp.length() > 0) || (bak.exists() && bak.length() > 0)
    }

    private fun removeIfPresent(file: File) {
        try {
            if (file.exists() && !file.delete()) {
                Log.e(TAG, "Could not remove ${file.name}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not remove ${file.name}: ${e.message}")
        }
    }

    /**
     * Deletes the working copy left by an earlier attempt -- but ONLY while the
     * live database is actually on disk. With `wellness.db` missing (or
     * zero-length, which is what an interrupted create leaves behind) this
     * working copy may be the only copy of the newest state, and deleting it is
     * the difference between a bad launch and permanent data loss. The guard
     * lives HERE, not at the call sites, so requirement (d) of spec §4.4
     * Amendment 2026-08-15b holds by construction for every present and future
     * caller -- exactly as the desktop does it (`database.ts:486-495`).
     */
    internal fun cleanupTmp(dbFile: File) {
        if (!dbFile.exists() || dbFile.length() == 0L) return
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

    private val defaultVerify: VerifyFn = ::verifyEncryptedReadable
    private val defaultRename: RenameFn = { from, to -> from.renameTo(to) }

    /**
     * Recovers from a swap interrupted mid-rename. MUST run before Room (or
     * anything else) opens `dbFile`. Between the two renames in
     * [migrateIfNeeded], `dbFile` briefly does not exist; a process death there
     * would otherwise read as a fresh install and let Room create an empty
     * database over data that is still intact in the tmp/backup copy.
     */
    fun recoverInterruptedSwap(
        dbFile: File,
        passphrase: ByteArray,
        verify: VerifyFn = defaultVerify,
        rename: RenameFn = defaultRename
    ): RecoveryResult {
        // Gate the early return on the file being a REAL database, not merely a
        // file: a zero-length wellness.db is what an interrupted create leaves
        // behind, and treating it as "nothing to do" is exactly how recovery
        // gets skipped -- permanently, since the empty file never grows a header
        // and every later launch takes the same early return while both
        // survivors sit beside it. Same gate the desktop uses
        // (`database.ts:613`). A deeper content check is deliberately NOT done:
        // an empty-but-initialised database is indistinguishable from a user who
        // deleted all their entries, and restoring a backup over that would
        // resurrect deleted data.
        if (dbFile.exists() && dbFile.length() > 0) return RecoveryResult.NotNeeded

        val tmp = tmpFile(dbFile)
        val bak = bakFile(dbFile)
        val hasTmp = tmp.exists() && tmp.length() > 0
        val hasBak = bak.exists() && bak.length() > 0
        if (!hasTmp && !hasBak) return RecoveryResult.NotNeeded // genuine first run

        val failures = mutableListOf<String>()

        if (hasTmp) {
            try {
                val rows = verify(tmp, passphrase)
                if (rename(tmp, dbFile)) {
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
            if (rename(bak, dbFile)) {
                Log.i(TAG, "Recovered an interrupted migration by restoring the plaintext backup.")
                return RecoveryResult.Recovered("plaintext backup")
            }
            failures.add("could not restore the plaintext backup")
        }

        return RecoveryResult.Unrecoverable(
            "wellness.db is missing after an interrupted migration and could not be recovered " +
                "(${failures.joinToString("; ")}). Your data is safe -- check for " +
                "${bak.name} and ${tmp.name} in the app's data directory."
        )
    }

    /**
     * Recovery for a DB encryption key that can no longer be unwrapped (a
     * corrupt blob, or the AndroidKeyStore alias is gone) when a
     * pre-encryption `wellness.db.plaintext.bak` still exists (I2, spec §6
     * residual). The now-undecryptable `wellness.db` is preserved (renamed
     * aside to a fresh, never-reused `wellness.db.keylost*.bak` name, and never
     * deleted -- see [freshKeylostFile]) and the plaintext
     * backup takes `wellness.db`'s place, so a freshly minted passphrase
     * (`DbKeyManager.mintFreshPassphrase`) and [migrateIfNeeded] can
     * re-encrypt it on this same launch -- instead of the app refusing to
     * start on every subsequent launch with no way for the user to recover.
     */
    fun recoverFromLostKey(dbFile: File, rename: RenameFn = defaultRename): RecoveryResult {
        val bak = bakFile(dbFile)
        if (!bak.exists() || bak.length() <= 0) {
            return RecoveryResult.Unrecoverable(
                "the database encryption key could not be unwrapped and no ${bak.name} backup " +
                    "exists to recover from"
            )
        }

        // A previous .keylost copy is never deleted to make room for this one
        // (see freshKeylostFile): it may hold everything written between two
        // key losses, and this is a data-LOSS recovery path -- the one place
        // where deleting a data-bearing file is least excusable.
        val keylost = freshKeylostFile(dbFile)
        if (dbFile.exists()) {
            if (!rename(dbFile, keylost)) {
                return RecoveryResult.Unrecoverable(
                    "the database encryption key could not be unwrapped and the unreadable " +
                        "${dbFile.name} could not be preserved aside to recover from ${bak.name}"
                )
            }
        }

        if (!rename(bak, dbFile)) {
            // Try to put the unreadable original back rather than leave no db at all.
            rename(keylost, dbFile)
            return RecoveryResult.Unrecoverable(
                "the database encryption key could not be unwrapped and ${bak.name} could not be restored"
            )
        }

        return RecoveryResult.Recovered(
            "restored ${bak.name} after the encryption key became unusable; the unreadable prior " +
                "database is preserved at ${keylost.name}"
        )
    }

    /**
     * Plaintext -> encrypted migration via `sqlcipher_export()` (spec §5.3).
     * Detects the need by magic header, not a flag. The whole export happens
     * against a brand-new `<dbName>.encrypting.tmp`; `dbFile` itself is only
     * opened read/write to serve as the `sqlcipher_export` SOURCE (writes land
     * on the attached tmp, never on `main`), so its bytes never change until the
     * verified swap. Any failure deletes the tmp and leaves `dbFile` untouched
     * (except the [MigrationResult.DatabaseMissing] case -- see its doc).
     */
    fun migrateIfNeeded(
        dbFile: File,
        passphrase: ByteArray,
        verify: VerifyFn = defaultVerify,
        rename: RenameFn = defaultRename
    ): MigrationResult {
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
            rows = verify(tmp, passphrase)
        } catch (e: Exception) {
            cleanupTmp(dbFile)
            return MigrationResult.Failed("verification read failed: ${e.message}")
        }

        return performSwap(dbFile, tmp, bak, rows, rename)
    }

    /**
     * The swap itself, extracted from [migrateIfNeeded] so the failure
     * branches (C1) are directly unit-testable on the JVM: no native library
     * needed, only `File` renames. Called only after [tmp] has already been
     * verified as a real, keyed-readable database holding [rows] rows.
     *
     * Post-review fix (C1): `bak` is never pre-deleted here -- `File.renameTo`
     * on Android/Linux replaces the destination atomically (POSIX `rename()`),
     * so deleting first only widens a window for no benefit, and it violated
     * the task's explicit never-delete-a-`.plaintext.bak` constraint (I1).
     */
    internal fun performSwap(
        dbFile: File,
        tmp: File,
        bak: File,
        rows: Int,
        rename: RenameFn = defaultRename
    ): MigrationResult {
        // The two renames below are the one window in which dbFile does not
        // exist. Nothing is lost if the process dies here -- the data is in the
        // tmp and the .plaintext.bak -- but the next launch must not mistake the
        // gap for a fresh install, which is what recoverInterruptedSwap() is for.
        if (!rename(dbFile, bak)) {
            cleanupTmp(dbFile)
            return MigrationResult.Failed("could not rename the original database aside")
        }
        if (!rename(tmp, dbFile)) {
            // Put the original back rather than leave the app with no database.
            if (!rename(bak, dbFile)) {
                // Truly stuck: dbFile does not exist. Never delete either
                // survivor here (I1) -- recoverInterruptedSwap() on the next
                // launch recovers from exactly this state (C1).
                return MigrationResult.DatabaseMissing(
                    "wellness.db is missing after a failed migration swap; the verified encrypted " +
                        "copy is at ${tmp.name} and the original plaintext is at ${bak.name} -- both " +
                        "are intact and will be recovered automatically on the next launch"
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
        ensureNativeLibraryLoaded()
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

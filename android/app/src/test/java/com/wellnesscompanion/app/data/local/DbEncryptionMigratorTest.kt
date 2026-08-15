package com.wellnesscompanion.app.data.local

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Pure-JVM coverage for the swap/recovery *file logic* in
 * [DbEncryptionMigrator] -- deliberately independent of the native SQLCipher
 * library (unavailable on the `testDebugUnitTest` classpath) via the
 * [DbEncryptionMigrator]-internal `VerifyFn`/`RenameFn` seams, mirroring the
 * desktop's `CipherDbOpener`/`RenameFn` (`database.ts:554-558`).
 *
 * Post-review fix for C1 (Critical -- total data loss): when a migration swap
 * fails AND the restore-back also fails, `wellness.db` ends up missing while
 * both the verified encrypted tmp copy and the original plaintext backup
 * survive on disk. The bug was that callers treated this the same as an
 * ordinary `Failed` (which means "the original is still there, open it
 * unencrypted"), which let Room silently create a brand-new EMPTY database
 * over the two intact survivors -- and the next launch's `cleanupTmp`/
 * `removeIfPresent(bak)` calls would then have destroyed both of them. These
 * tests pin the fix: a distinct `DatabaseMissing` result, both survivors kept
 * untouched, and `recoverInterruptedSwap` actually recovering from them.
 */
class DbEncryptionMigratorTest {

    private lateinit var dir: File
    private lateinit var dbFile: File
    private lateinit var tmp: File
    private lateinit var bak: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("dbmigrator-test").toFile()
        dbFile = File(dir, "wellness.db")
        tmp = File(dir, "wellness.db.encrypting.tmp")
        bak = File(dir, "wellness.db.plaintext.bak")
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `failed swap restores the original byte-identical and cleans up the tmp`() {
        val originalContent = "PLAINTEXT-ORIGINAL-CONTENT".toByteArray()
        val encryptedContent = "ENCRYPTED-TMP-CONTENT".toByteArray()
        dbFile.writeBytes(originalContent)
        tmp.writeBytes(encryptedContent)

        // tmp -> dbFile fails (simulating e.g. cross-filesystem or permission
        // failure); every other rename in the call is real File.renameTo.
        val rename: RenameFn = { from, to ->
            if (from == tmp && to == dbFile) false else from.renameTo(to)
        }

        val result = DbEncryptionMigrator.performSwap(dbFile, tmp, bak, rows = 5, rename = rename)

        assertTrue(
            "expected Failed (original restored), got $result",
            result is DbEncryptionMigrator.MigrationResult.Failed
        )
        assertTrue("the original must be restored at dbFile", dbFile.exists())
        assertArrayEquals(
            "the restored dbFile content must be byte-identical to the original",
            originalContent,
            dbFile.readBytes()
        )
        assertFalse("the tmp must be cleaned up after a failed-but-restored swap", tmp.exists())
    }

    @Test
    fun `failed swap and failed restore reports DatabaseMissing, keeps both survivors, and recovers on next launch`() {
        val originalContent = "PLAINTEXT-ORIGINAL-CONTENT".toByteArray()
        val encryptedContent = "ENCRYPTED-TMP-CONTENT".toByteArray()
        dbFile.writeBytes(originalContent)
        tmp.writeBytes(encryptedContent)

        // The first rename (dbFile -> bak) succeeds for real; both the
        // tmp -> dbFile swap AND the bak -> dbFile restore-back fail --
        // exactly the "truly stuck" scenario C1 was reported against.
        val renameAlwaysFailsAfterFirst: RenameFn = { from, to ->
            if (from == dbFile && to == bak) from.renameTo(to) else false
        }

        val result = DbEncryptionMigrator.performSwap(
            dbFile, tmp, bak, rows = 5, rename = renameAlwaysFailsAfterFirst
        )

        assertTrue(
            "expected DatabaseMissing, got $result",
            result is DbEncryptionMigrator.MigrationResult.DatabaseMissing
        )
        assertFalse("dbFile must not exist -- and must NEVER be silently recreated", dbFile.exists())
        assertTrue("the encrypted tmp survivor must be KEPT, never deleted", tmp.exists())
        assertArrayEquals(encryptedContent, tmp.readBytes())
        assertTrue("the plaintext backup survivor must be KEPT, never deleted", bak.exists())
        assertArrayEquals(originalContent, bak.readBytes())

        // Next launch: recoverInterruptedSwap must recover from exactly these
        // survivors rather than let a caller (AppModule) start with an empty
        // database. Prefers the tmp (verified, newer) over the bak.
        var verifyCalls = 0
        val fakeVerify: VerifyFn = { file, _ ->
            verifyCalls++
            assertEquals("recovery must verify the tmp copy, not the bak", tmp, file)
            7
        }

        val recovery = DbEncryptionMigrator.recoverInterruptedSwap(
            dbFile, passphrase = ByteArray(32), verify = fakeVerify
        )

        assertTrue("expected Recovered, got $recovery", recovery is DbEncryptionMigrator.RecoveryResult.Recovered)
        assertEquals("verify must be called exactly once", 1, verifyCalls)
        assertTrue("dbFile must exist again after recovery", dbFile.exists())
        assertArrayEquals(
            "the recovered dbFile must be the verified encrypted copy, not the plaintext backup",
            encryptedContent,
            dbFile.readBytes()
        )
    }

    @Test
    fun `a normal successful swap does not need bak pre-deleted`() {
        // I1 regression guard: performSwap must not require (or perform) a
        // pre-delete of an existing bak -- File.renameTo replaces the
        // destination atomically on Android/Linux, and deleting first only
        // widens a window and risks violating the never-delete-a-
        // plaintext-bak constraint. This test's `rename` fake mimics that
        // real POSIX/Android replace-on-rename semantics explicitly, since
        // java.io.File.renameTo on Windows (this dev machine) does NOT
        // overwrite an existing destination -- a host-OS quirk unrelated to
        // the behavior under test, which is "performSwap issues no separate
        // delete of bak before renaming onto it."
        val originalContent = "PLAINTEXT-ORIGINAL-CONTENT".toByteArray()
        val encryptedContent = "ENCRYPTED-TMP-CONTENT".toByteArray()
        val staleBakContent = "STALE-OLDER-BACKUP-CYCLE".toByteArray()
        dbFile.writeBytes(originalContent)
        tmp.writeBytes(encryptedContent)
        bak.writeBytes(staleBakContent) // an old bak already sitting there

        val replaceOnRename: RenameFn = { from, to ->
            if (to.exists()) to.delete()
            from.renameTo(to)
        }
        val result = DbEncryptionMigrator.performSwap(dbFile, tmp, bak, rows = 3, rename = replaceOnRename)

        assertTrue("expected Migrated, got $result", result is DbEncryptionMigrator.MigrationResult.Migrated)
        assertEquals(3, (result as DbEncryptionMigrator.MigrationResult.Migrated).rows)
        assertArrayEquals("dbFile must be the (renamed) encrypted copy", encryptedContent, dbFile.readBytes())
        assertTrue("bak must exist (atomically replaced by the old original)", bak.exists())
        assertArrayEquals(
            "bak must hold the CURRENT cycle's original, not the stale one",
            originalContent,
            bak.readBytes()
        )
        assertFalse("tmp must be gone after a successful swap", tmp.exists())
    }
}

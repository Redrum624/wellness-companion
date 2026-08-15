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

        // The load-bearing observation is made AT the rename instant, not at
        // the end: the end state of "delete bak, then rename onto it" is
        // byte-identical to "rename onto it", so an end-state-only assertion
        // cannot tell the two apart and would not regression-guard anything.
        // Recording what bak looked like at the moment performSwap asked for
        // the dbFile -> bak rename does: a pre-delete makes it absent there.
        val bakAtSwapInstant = mutableListOf<String>()
        val replaceOnRename: RenameFn = { from, to ->
            if (from == dbFile && to == bak) {
                bakAtSwapInstant.add(if (bak.exists()) String(bak.readBytes()) else "<ABSENT>")
            }
            if (to.exists()) to.delete()
            from.renameTo(to)
        }
        val result = DbEncryptionMigrator.performSwap(dbFile, tmp, bak, rows = 3, rename = replaceOnRename)

        assertEquals(
            "the stale bak must still be present, untouched, at the instant of the swap rename " +
                "-- performSwap must issue no delete of its own",
            listOf(String(staleBakContent)),
            bakAtSwapInstant
        )
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

    // ------------------------------------------------------------------
    // Fix round 2
    // ------------------------------------------------------------------

    /**
     * The crash that round 1 introduced: `AppModule` failed closed on a bare
     * `!dbFile.exists()`, which is the state EVERY new install is in on its
     * first launch -- nothing creates wellness.db before Room does. The guard
     * has to be qualified by the survivor state, and this is that qualifier.
     */
    @Test
    fun `a genuine fresh install has no survivors so the missing-db guard must not fire`() {
        assertFalse("nothing on disk at all: this is a first launch, not an emergency", dbFile.exists())
        assertFalse(DbEncryptionMigrator.hasSurvivors(dbFile))

        // A zero-length leftover is not a survivor either -- failing closed on
        // one would brick a fresh install just as badly.
        tmp.writeBytes(ByteArray(0))
        bak.writeBytes(ByteArray(0))
        assertFalse("zero-length leftovers hold no data", DbEncryptionMigrator.hasSurvivors(dbFile))
    }

    @Test
    fun `either survivor alone qualifies the missing-db guard`() {
        tmp.writeBytes("ENCRYPTED-TMP-CONTENT".toByteArray())
        assertTrue("the encrypted tmp alone is a survivor", DbEncryptionMigrator.hasSurvivors(dbFile))

        tmp.delete()
        bak.writeBytes("PLAINTEXT-ORIGINAL-CONTENT".toByteArray())
        assertTrue("the plaintext backup alone is a survivor", DbEncryptionMigrator.hasSurvivors(dbFile))
    }

    /**
     * A zero-length wellness.db beside intact survivors is the interrupted-create
     * state. Gating recovery on bare existence makes it skip FOREVER: the empty
     * file never grows a header, so every later launch takes the same early
     * return. Desktop gates on `exists && size > 0` (`database.ts:613`).
     */
    @Test
    fun `a zero-length dbFile does not count as a database and recovery still runs`() {
        val encryptedContent = "ENCRYPTED-TMP-CONTENT".toByteArray()
        dbFile.writeBytes(ByteArray(0)) // 0 bytes, but it EXISTS
        tmp.writeBytes(encryptedContent)

        // Replace-on-rename, as POSIX/Android does it: java.io.File.renameTo on
        // this Windows dev machine will not overwrite an existing destination,
        // and the destination here (the 0-byte file) exists by construction.
        val replaceOnRename: RenameFn = { from, to ->
            if (to.exists()) to.delete()
            from.renameTo(to)
        }
        val recovery = DbEncryptionMigrator.recoverInterruptedSwap(
            dbFile,
            passphrase = ByteArray(32),
            verify = { _, _ -> 11 },
            rename = replaceOnRename
        )

        assertTrue(
            "expected Recovered over the 0-byte placeholder, got $recovery",
            recovery is DbEncryptionMigrator.RecoveryResult.Recovered
        )
        assertArrayEquals(
            "the 0-byte placeholder must be replaced by the verified encrypted survivor",
            encryptedContent,
            dbFile.readBytes()
        )
    }

    @Test
    fun `a zero-length dbFile with no survivors is still a first run, not an error`() {
        dbFile.writeBytes(ByteArray(0))

        val recovery = DbEncryptionMigrator.recoverInterruptedSwap(dbFile, passphrase = ByteArray(32))

        assertTrue(
            "no survivor exists, so there is nothing to recover and nothing to fail on: got $recovery",
            recovery is DbEncryptionMigrator.RecoveryResult.NotNeeded
        )
    }

    /**
     * Requirement (d) of spec §4.4 Amendment 2026-08-15b, enforced inside
     * cleanupTmp itself rather than at each call site (as the desktop does,
     * `database.ts:486-495`) so it cannot be lost by a future caller.
     */
    @Test
    fun `cleanupTmp never deletes the working copy while the live database is missing`() {
        val encryptedContent = "ENCRYPTED-TMP-CONTENT".toByteArray()
        tmp.writeBytes(encryptedContent)
        File(dir, "wellness.db.encrypting.tmp-wal").writeBytes("WAL".toByteArray())

        DbEncryptionMigrator.cleanupTmp(dbFile) // wellness.db absent

        assertTrue("the tmp may be the only copy of the newest state -- never delete it", tmp.exists())
        assertArrayEquals(encryptedContent, tmp.readBytes())

        dbFile.writeBytes(ByteArray(0))
        DbEncryptionMigrator.cleanupTmp(dbFile) // a 0-byte wellness.db is not a database either
        assertTrue("a zero-length wellness.db does not make the tmp junk", tmp.exists())

        dbFile.writeBytes("REAL-LIVE-DATABASE".toByteArray())
        DbEncryptionMigrator.cleanupTmp(dbFile)
        assertFalse("with a real live database present the tmp IS junk and must go", tmp.exists())
        assertFalse(File(dir, "wellness.db.encrypting.tmp-wal").exists())
    }

    /**
     * The lost-key path renames the undecryptable wellness.db aside. A second
     * key loss must not delete the first keylost copy to reuse its name: that
     * file may hold every entry written between the two losses, and this is the
     * one code path whose entire purpose is not losing data.
     */
    @Test
    fun `a second lost-key recovery keeps the first keylost copy under a fresh name`() {
        val firstUnreadable = "UNREADABLE-CIPHERTEXT-ONE".toByteArray()
        val firstBackup = "PLAINTEXT-BACKUP-ONE".toByteArray()
        dbFile.writeBytes(firstUnreadable)
        bak.writeBytes(firstBackup)

        val first = DbEncryptionMigrator.recoverFromLostKey(dbFile)
        assertTrue("expected Recovered, got $first", first is DbEncryptionMigrator.RecoveryResult.Recovered)
        val keylostOne = File(dir, "wellness.db.keylost.bak")
        assertTrue("the unreadable database must be preserved aside", keylostOne.exists())
        assertArrayEquals(firstUnreadable, keylostOne.readBytes())
        assertArrayEquals("the plaintext backup takes wellness.db's place", firstBackup, dbFile.readBytes())

        // Second cycle: a new (re-encrypted, now also unreadable) database and a
        // new backup, and the key is lost again.
        val secondUnreadable = "UNREADABLE-CIPHERTEXT-TWO".toByteArray()
        val secondBackup = "PLAINTEXT-BACKUP-TWO".toByteArray()
        dbFile.writeBytes(secondUnreadable)
        bak.writeBytes(secondBackup)

        val second = DbEncryptionMigrator.recoverFromLostKey(dbFile)
        assertTrue("expected Recovered, got $second", second is DbEncryptionMigrator.RecoveryResult.Recovered)

        assertTrue("the FIRST keylost copy must still exist, untouched", keylostOne.exists())
        assertArrayEquals(
            "the first keylost copy's bytes must be unchanged",
            firstUnreadable,
            keylostOne.readBytes()
        )
        val keylostFiles = dir.listFiles { f -> f.name.startsWith("wellness.db.keylost") }!!
        assertEquals(
            "each lost-key cycle gets its own file: ${keylostFiles.map { it.name }}",
            2,
            keylostFiles.size
        )
        val keylostTwo = keylostFiles.first { it.name != keylostOne.name }
        assertArrayEquals(
            "the second cycle's unreadable database is preserved too",
            secondUnreadable,
            keylostTwo.readBytes()
        )
        assertArrayEquals(secondBackup, dbFile.readBytes())
    }
}

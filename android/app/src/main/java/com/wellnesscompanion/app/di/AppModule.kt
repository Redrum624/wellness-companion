package com.wellnesscompanion.app.di

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.google.gson.Gson
import com.wellnesscompanion.app.data.local.DbEncryptionMigrator
import com.wellnesscompanion.app.data.local.WellnessDatabase
import com.wellnesscompanion.app.data.local.dao.ChoreTemplateDao
import com.wellnesscompanion.app.data.local.dao.EntryDao
import com.wellnesscompanion.app.data.local.dao.HobbyDao
import com.wellnesscompanion.app.data.local.dao.PersonDao
import com.wellnesscompanion.app.data.local.dao.SettingsDao
import com.wellnesscompanion.app.security.DbKeyManager
import com.wellnesscompanion.app.sync.SyncManager
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private const val TAG = "AppModule"

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * I6 (post-review fix): a single unwrap exception must not be treated as
     * permanent key loss. `DbKeyManager`'s own doc on [mintFreshPassphrase]
     * already flags that the failure "may be transient (the Keystore alias
     * briefly unavailable, a locked-device hiccup)" -- but before this,
     * `provideDatabase` acted on the very first exception, so one transient
     * `KeyStoreException` silently reverted the user to migration-era data
     * via `recoverFromLostKey`. Retry a couple of times with a short backoff
     * before letting the caller treat the failure as real key loss. Every
     * existing guarantee is untouched: this only decides whether
     * `recoverFromLostKey` runs at all, never how it behaves.
     */
    private fun getPassphraseWithRetry(context: Context): ByteArray {
        val backoffMs = longArrayOf(150, 300)
        var lastError: Exception? = null
        for (attempt in 0..backoffMs.size) {
            try {
                return DbKeyManager.getOrCreatePassphrase(context)
            } catch (e: Exception) {
                lastError = e
                if (attempt < backoffMs.size) {
                    Log.w(TAG, "Passphrase unwrap failed (attempt ${attempt + 1}), retrying: ${e.message}")
                    try {
                        Thread.sleep(backoffMs[attempt])
                    } catch (interrupted: InterruptedException) {
                        // Restore the interrupt flag rather than swallow it, but don't let
                        // InterruptedException itself propagate: that would skip lastError
                        // and drop the real unwrap error from the log while still being
                        // treated as permanent key loss by the caller.
                        Thread.currentThread().interrupt()
                        throw lastError!!
                    }
                }
            }
        }
        throw lastError!!
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): WellnessDatabase {
        // at-rest: SQLCipher via Room's SupportFactory (spec §5.2). Encryption
        // is wired here only -- DAOs, entities and queries are untouched, and
        // the sync layer keeps reading/writing through the normal DAOs.
        DbEncryptionMigrator.ensureNativeLibraryLoaded()

        val dbFile = context.getDatabasePath("wellness.db")

        // I2 (post-review fix): an unwrap failure (corrupt blob, lost Keystore
        // alias) must not crash "Wellness Companion keeps stopping" forever --
        // the user on Android has no run-as recourse and clearing app data
        // would destroy the .plaintext.bak too. If a pre-encryption backup is
        // still on disk, recover from it and mint a fresh key; otherwise fail
        // closed with a message that names the backup rather than a raw
        // Keystore stack trace.
        val passphrase: ByteArray = try {
            getPassphraseWithRetry(context)
        } catch (e: Exception) {
            Log.e(TAG, "Database key could not be unwrapped after retries: ${e.message}")
            val keyRecovery = DbEncryptionMigrator.recoverFromLostKey(dbFile)
            if (keyRecovery is DbEncryptionMigrator.RecoveryResult.Recovered) {
                Log.i(TAG, "Recovered from a lost database key: ${keyRecovery.detail}")
                DbKeyManager.mintFreshPassphrase(context)
            } else {
                val reason = (keyRecovery as? DbEncryptionMigrator.RecoveryResult.Unrecoverable)?.reason
                    ?: "the database key could not be unwrapped: ${e.message}"
                Log.e(TAG, "Database error, refusing to open wellness.db: $reason")
                error(reason)
            }
        }

        // Recover a swap interrupted mid-rename BEFORE anything else touches
        // dbFile (mirrors the desktop's recoverInterruptedSwap, spec §5.3).
        val recovery = DbEncryptionMigrator.recoverInterruptedSwap(dbFile, passphrase)
        if (recovery is DbEncryptionMigrator.RecoveryResult.Unrecoverable) {
            Log.e(TAG, "Database error, refusing to open wellness.db: ${recovery.reason}")
            error(recovery.reason)
        }

        // Plaintext -> encrypted, detected by magic header, never a flag. ANY
        // failure leaves dbFile exactly as it was; open it unencrypted this
        // session and retry next launch -- never brick the user's data.
        val migration = DbEncryptionMigrator.migrateIfNeeded(dbFile, passphrase)
        when (migration) {
            is DbEncryptionMigrator.MigrationResult.Failed ->
                Log.e(TAG, "Database encryption migration failed, running unencrypted: ${migration.reason}")
            is DbEncryptionMigrator.MigrationResult.Migrated ->
                Log.i(TAG, "Database migrated to encrypted storage (${migration.rows} entries).")
            is DbEncryptionMigrator.MigrationResult.DatabaseMissing -> {
                // C1 (post-review fix, Critical): a swap failed AND the
                // restore-back also failed, so dbFile does not exist even
                // though a verified encrypted copy and the original plaintext
                // both survive on disk. NEVER fall through to Room here --
                // Room's default opener would happily create a brand-new
                // EMPTY database over both survivors.
                Log.e(TAG, "Database error, refusing to open wellness.db: ${migration.reason}")
                error(migration.reason)
            }
            DbEncryptionMigrator.MigrationResult.NotNeeded -> {}
        }

        // Defense in depth: the branches above should already have failed
        // closed on any path that leaves dbFile missing, but Room must never
        // be allowed to create a fresh database over an inconsistent state.
        //
        // The guard is QUALIFIED by hasSurvivors on purpose. A missing
        // wellness.db on its own is not an emergency -- it is the completely
        // normal first launch of a new install: getDatabasePath only builds a
        // path, recoverInterruptedSwap returns NotNeeded (no survivors) and
        // migrateIfNeeded returns NotNeeded (a missing file is not plaintext),
        // so nothing has created the file yet and Room is supposed to create it
        // below. An unqualified check here crashed every new user on first
        // launch. It is only an emergency when a survivor is sitting beside the
        // hole (spec §4.4 Amendment 2026-08-15b (b)); the desktop leans on the
        // same invariant -- it fails closed on a missing db only with
        // SURVIVOR_GUIDANCE, and otherwise lets a fresh database be created
        // (database.ts:51, :111-120).
        if (!dbFile.exists() && DbEncryptionMigrator.hasSurvivors(dbFile)) {
            val reason = "wellness.db is unexpectedly missing; check for wellness.db.plaintext.bak " +
                "and wellness.db.encrypting.tmp in the app's data directory -- your data is safe there"
            Log.e(TAG, reason)
            error(reason)
        }

        val openEncrypted = migration !is DbEncryptionMigrator.MigrationResult.Failed

        val builder = Room.databaseBuilder(
            context,
            WellnessDatabase::class.java,
            "wellness.db"
        )
            .addMigrations(WellnessDatabase.MIGRATION_1_2, WellnessDatabase.MIGRATION_2_3)

        if (openEncrypted) {
            // Goes through the same named seam the migrator's own C-API opens use
            // (DbEncryptionMigrator.rawKeyBytes) -- the raw 32-byte passphrase,
            // unmodified, is exactly what SupportOpenHelperFactory expects, and
            // it must be byte-identical to the key the migration wrote with.
            builder.openHelperFactory(SupportOpenHelperFactory(DbEncryptionMigrator.rawKeyBytes(passphrase)))
        }
        // else: migration failed and the file is still plaintext on disk --
        // fall through to Room's default (unencrypted) opener so the app keeps
        // working this session; the header check retries next launch.

        return builder.build()
    }

    @Provides
    fun provideEntryDao(db: WellnessDatabase): EntryDao = db.entryDao()

    @Provides
    fun provideSettingsDao(db: WellnessDatabase): SettingsDao = db.settingsDao()

    @Provides
    fun provideChoreTemplateDao(db: WellnessDatabase): ChoreTemplateDao = db.choreTemplateDao()

    @Provides
    fun provideHobbyDao(db: WellnessDatabase): HobbyDao = db.hobbyDao()

    @Provides
    fun providePersonDao(db: WellnessDatabase): PersonDao = db.personDao()

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    /**
     * One client for the whole app. A fresh OkHttpClient was previously built
     * per SyncViewModel, and each carries its own Dispatcher thread pool and
     * ConnectionPool that nothing ever shut down.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        // Upgraded web sockets disable read timeouts, so without a ping a peer
        // that vanishes mid-sync would hold the socket open indefinitely.
        .pingInterval(20, TimeUnit.SECONDS)
        // transport: a silent OkHttp re-dial would open a SECOND wc-sync/4
        // connection while SyncManager still holds the first connection's
        // ephemeral key and record counters — the counters would restart at 0
        // under a key that had already sealed records, which is the GCM
        // nonce-reuse catastrophe (spec §2.5, I-2). Fail the call instead.
        .retryOnConnectionFailure(false)
        .build()

    @Provides
    @Singleton
    fun provideSyncManager(
        @ApplicationContext context: Context,
        db: WellnessDatabase,
        gson: Gson,
        client: OkHttpClient
    ): SyncManager = SyncManager(context, db, gson, client)
}

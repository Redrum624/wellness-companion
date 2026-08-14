package com.wellnesscompanion.app.di

import android.content.Context
import androidx.room.Room
import com.google.gson.Gson
import com.wellnesscompanion.app.data.local.WellnessDatabase
import com.wellnesscompanion.app.data.local.dao.ChoreTemplateDao
import com.wellnesscompanion.app.data.local.dao.EntryDao
import com.wellnesscompanion.app.data.local.dao.HobbyDao
import com.wellnesscompanion.app.data.local.dao.PersonDao
import com.wellnesscompanion.app.data.local.dao.SettingsDao
import com.wellnesscompanion.app.sync.SyncManager
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): WellnessDatabase {
        return Room.databaseBuilder(
            context,
            WellnessDatabase::class.java,
            "wellness.db"
        )
            .addMigrations(WellnessDatabase.MIGRATION_1_2, WellnessDatabase.MIGRATION_2_3)
            .build()
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

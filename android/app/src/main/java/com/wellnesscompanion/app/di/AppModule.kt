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
            .addMigrations(WellnessDatabase.MIGRATION_1_2)
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
}

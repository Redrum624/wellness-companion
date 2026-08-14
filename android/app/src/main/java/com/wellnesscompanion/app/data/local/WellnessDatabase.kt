package com.wellnesscompanion.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.wellnesscompanion.app.data.local.dao.ChoreTemplateDao
import com.wellnesscompanion.app.data.local.dao.EntryDao
import com.wellnesscompanion.app.data.local.dao.HobbyDao
import com.wellnesscompanion.app.data.local.dao.PersonDao
import com.wellnesscompanion.app.data.local.dao.SettingsDao
import com.wellnesscompanion.app.data.local.entity.ChoreTemplateEntity
import com.wellnesscompanion.app.data.local.entity.EntryEntity
import com.wellnesscompanion.app.data.local.entity.HobbyEntity
import com.wellnesscompanion.app.data.local.entity.PersonEntity
import com.wellnesscompanion.app.data.local.entity.SettingEntity

@Database(
    entities = [
        EntryEntity::class,
        SettingEntity::class,
        ChoreTemplateEntity::class,
        HobbyEntity::class,
        PersonEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class WellnessDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
    abstract fun settingsDao(): SettingsDao
    abstract fun choreTemplateDao(): ChoreTemplateDao
    abstract fun hobbyDao(): HobbyDao
    abstract fun personDao(): PersonDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS chore_templates (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        category TEXT,
                        recurrence TEXT,
                        created_at INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS hobbies (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        color TEXT NOT NULL,
                        created_at INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS people (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        created_at INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE people ADD COLUMN deleted_at INTEGER")
            }
        }
    }
}

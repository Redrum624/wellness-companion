package com.wellnesscompanion.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wellnesscompanion.app.data.local.entity.SettingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {

    @Query("SELECT value FROM settings WHERE `key` = :key")
    suspend fun getSetting(key: String): String?

    @Query("SELECT value FROM settings WHERE `key` = :key")
    fun getSettingFlow(key: String): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSetting(setting: SettingEntity)

    /**
     * Write several rows in ONE transaction (Room wraps a list `@Insert` in
     * `beginTransaction`/`endTransaction`). Pairing needs this: writing
     * `sync.key_id`, `sync.device_key` and the cursor reset as three separate
     * statements leaves a crash window in which a NEW key id is stored against
     * the OLD secret — recoverable via `4006`, but a pointless way to strand
     * a user mid-pairing.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSettings(settings: List<SettingEntity>)
}

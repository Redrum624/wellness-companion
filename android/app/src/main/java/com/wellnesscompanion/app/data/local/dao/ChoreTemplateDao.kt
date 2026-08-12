package com.wellnesscompanion.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wellnesscompanion.app.data.local.entity.ChoreTemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChoreTemplateDao {

    @Query("SELECT * FROM chore_templates ORDER BY name ASC")
    fun getAll(): Flow<List<ChoreTemplateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(template: ChoreTemplateEntity)

    /**
     * Non-suspending insert for the sync path: the WebSocket listener delivers
     * frames on OkHttp's reader thread, which is not a coroutine scope.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSync(template: ChoreTemplateEntity)

    @Query("SELECT * FROM chore_templates ORDER BY name ASC")
    fun getAllSync(): List<ChoreTemplateEntity>

    @Delete
    suspend fun delete(template: ChoreTemplateEntity)
}

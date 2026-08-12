package com.wellnesscompanion.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wellnesscompanion.app.data.local.entity.HobbyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HobbyDao {

    @Query("SELECT * FROM hobbies ORDER BY name ASC")
    fun getAll(): Flow<List<HobbyEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(hobby: HobbyEntity)

    /**
     * Non-suspending insert for the sync path: the WebSocket listener delivers
     * frames on OkHttp's reader thread, which is not a coroutine scope.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSync(hobby: HobbyEntity)

    @Query("SELECT * FROM hobbies ORDER BY name ASC")
    fun getAllSync(): List<HobbyEntity>

    @Delete
    suspend fun delete(hobby: HobbyEntity)
}

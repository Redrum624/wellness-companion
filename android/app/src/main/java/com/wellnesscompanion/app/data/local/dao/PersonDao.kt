package com.wellnesscompanion.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wellnesscompanion.app.data.local.entity.PersonEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {

    @Query("SELECT * FROM people WHERE deleted_at IS NULL ORDER BY name ASC")
    fun getAll(): Flow<List<PersonEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(person: PersonEntity)

    /**
     * Non-suspending insert for the sync path: the WebSocket listener delivers
     * frames on OkHttp's reader thread, which is not a coroutine scope.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSync(person: PersonEntity)

    /** Sync must see tombstones too, so this stays unfiltered. */
    @Query("SELECT * FROM people ORDER BY name ASC")
    fun getAllSync(): List<PersonEntity>

    @Query("SELECT * FROM people WHERE id = :id")
    fun getByIdSync(id: String): PersonEntity?

    /** Grow-only tombstone: only ever sets deleted_at, never clears it. */
    @Query("UPDATE people SET deleted_at = :deletedAt WHERE id = :id AND deleted_at IS NULL")
    fun markDeletedSync(id: String, deletedAt: Long)

    @Delete
    suspend fun delete(person: PersonEntity)
}

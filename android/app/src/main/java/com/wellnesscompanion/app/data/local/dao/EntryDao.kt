package com.wellnesscompanion.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.wellnesscompanion.app.data.local.entity.EntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {

    @Query("SELECT * FROM entries WHERE date = :date ORDER BY timestamp DESC")
    fun getEntriesByDate(date: String): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE date = :date AND category = :category ORDER BY timestamp DESC")
    fun getEntriesByDateAndCategory(date: String, category: String): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE date BETWEEN :startDate AND :endDate AND category = :category ORDER BY timestamp ASC")
    fun getEntriesByDateRange(startDate: String, endDate: String, category: String): Flow<List<EntryEntity>>

    @Query("SELECT DISTINCT date FROM entries WHERE category = :category ORDER BY date DESC")
    fun getLoggedDates(category: String): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: EntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSync(entry: EntryEntity)

    @Update
    suspend fun update(entry: EntryEntity)

    @Update
    fun updateSync(entry: EntryEntity)

    @Delete
    suspend fun delete(entry: EntryEntity)

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM entries ORDER BY timestamp DESC")
    fun getAllEntriesSync(): List<EntryEntity>

    @Query("SELECT * FROM entries WHERE id = :id")
    fun getByIdSync(id: String): EntryEntity?
}

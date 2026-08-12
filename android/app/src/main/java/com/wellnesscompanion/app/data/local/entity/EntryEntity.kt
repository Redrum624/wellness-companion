package com.wellnesscompanion.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "entries")
data class EntryEntity(
    @PrimaryKey val id: String,
    val category: String,
    val timestamp: Long,
    val date: String,
    val data: String,
    val version: Int = 1,
    @ColumnInfo(name = "modified_at") val modifiedAt: Long,
    val synced: Int = 0
)

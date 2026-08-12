package com.wellnesscompanion.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hobbies")
data class HobbyEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: String, // Hex color
    @ColumnInfo(name = "created_at") val createdAt: Long
)

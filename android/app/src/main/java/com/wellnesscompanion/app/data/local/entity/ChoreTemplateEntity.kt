package com.wellnesscompanion.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chore_templates")
data class ChoreTemplateEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String? = null,
    val recurrence: String? = null, // "daily", "weekdays", "weekly", "monthly"
    @ColumnInfo(name = "created_at") val createdAt: Long
)

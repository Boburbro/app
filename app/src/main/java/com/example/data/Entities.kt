package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val colorHex: String, // Hex string representable color, e.g., "#FF5733"
    val totalFocusMinutes: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
) : Serializable

@Entity(tableName = "focus_sessions")
data class FocusSession(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val taskId: Int?, // Linked task if any
    val startTime: Long,
    val endTime: Long,
    val durationSeconds: Int,
    val completed: Boolean,
    val type: String, // "FOCUS", "SHORT_BREAK", "LONG_BREAK"
    val distractions: Int = 0 // Count of distractions
) : Serializable

@Entity(tableName = "daily_goals")
data class DailyGoal(
    @PrimaryKey val date: String, // LocalDate formatted as "yyyy-MM-dd"
    val targetSessions: Int = 4,
    val targetMinutes: Int = 100
) : Serializable

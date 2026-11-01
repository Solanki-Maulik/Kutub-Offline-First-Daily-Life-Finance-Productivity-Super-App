package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Subtask(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val isCompleted: Boolean = false
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "tasks")
data class TaskItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val description: String = "",
    val priority: String = "MEDIUM", // "LOW", "MEDIUM", "HIGH", "URGENT"
    val category: String = "Personal", // "Personal", "Work", "Finance", "Bills", "Health", "Shopping", "Home"
    val dueDateTime: Long? = null,
    val status: String = "TODO", // "TODO", "DOING", "DONE"
    val repeatFrequency: String = "NONE", // "NONE", "DAILY", "WEEKLY", "MONTHLY", "CUSTOM"
    val subtasksJson: String = "[]",
    // Linked to Money
    val isLinkedToMoney: Boolean = false,
    val linkedAmount: Double? = null,
    val linkedAccount: String? = null,
    val linkedCategory: String? = null,
    val linkedTransactionId: Long? = null,
    // Reminder
    val reminderEnabled: Boolean = false,
    val reminderTime: Long? = null,
    val completedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "schedule_events")
data class ScheduleEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val description: String = "",
    val startTime: Long,
    val endTime: Long,
    val location: String = "",
    val category: String = "Meeting", // "Meeting", "Personal", "Reminder", "Doctor", "Work", "Other"
    val reminderEnabled: Boolean = false,
    val reminderOffsetMinutes: Int = 15,
    val createdAt: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "task_streaks")
data class TaskStreak(
    @PrimaryKey val id: Int = 1,
    val currentStreak: Int = 0,
    val maxStreak: Int = 0,
    val lastCompletedDate: String = "", // "YYYY-MM-DD"
    val totalCompletedTasks: Int = 0
)

data class TaskStats(
    val totalTasks: Int,
    val pendingTasks: Int,
    val doingTasks: Int,
    val completedTasks: Int,
    val completionRatePercent: Int,
    val currentStreak: Int,
    val maxStreak: Int,
    val completedTodayCount: Int
)

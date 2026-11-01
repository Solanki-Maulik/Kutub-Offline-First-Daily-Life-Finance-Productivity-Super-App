package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val iconName: String = "SelfImprovement",
    val colorHex: String = "#2E7D32",
    // Frequency: "DAILY", "SPECIFIC_DAYS", "TIMES_PER_WEEK"
    val frequencyType: String = "DAILY",
    // Comma-separated ISO day of week 1=Mon..7=Sun, e.g. "1,2,3,4,5"
    val scheduledDaysCsv: String = "1,2,3,4,5,6,7",
    val timesPerWeek: Int = 7,
    // Target: 1 for boolean check-off, >1 for count-based (e.g., 8 glasses)
    val targetCount: Int = 1,
    val unitLabel: String = "", // e.g., "glasses", "mins", "pages"
    // Reminder
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 9,
    val reminderMinute: Int = 0,
    // Optional Money-Linked Rule: "NONE", "NO_SPEND_DAY", "SPEND_UNDER_LIMIT", "SAVED_TODAY"
    val moneyRuleType: String = "NONE",
    val moneyThreshold: Double = 0.0,
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
@Entity(
    tableName = "habit_logs",
    indices = [Index(value = ["habitId", "dateIso"], unique = true)]
)
data class HabitLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val habitId: Long,
    val dateIso: String, // "yyyy-MM-dd"
    val currentCount: Int = 0,
    // "PENDING", "COMPLETED", "SKIPPED" (Rest Day)
    val status: String = "PENDING",
    val note: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

data class HabitComputedStats(
    val habit: Habit,
    val selectedDateIso: String,
    val selectedDayLog: HabitLog?,
    val selectedDayCount: Int,
    val selectedDayStatus: String, // "PENDING", "COMPLETED", "SKIPPED"
    val isAutoMoneySatisfied: Boolean,
    val autoMoneySummary: String?,
    val currentStreak: Int,
    val longestStreak: Int,
    val weekCompletionPercent: Int,
    val monthCompletionPercent: Int,
    val yearCompletionPercent: Int,
    // Map of "yyyy-MM-dd" -> status ("COMPLETED", "SKIPPED", "PARTIAL", "NONE") for heatmap
    val heatmapMap: Map<String, String>
)

data class OverallHabitDashboardStats(
    val overallConsistencyPercent: Int,
    val completedTodayCount: Int,
    val totalScheduledToday: Int,
    val bestActiveStreak: Int,
    val habitStats: List<HabitComputedStats>
)

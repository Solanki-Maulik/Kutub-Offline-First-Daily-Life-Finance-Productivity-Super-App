package com.example.utils

import com.example.data.Subtask
import com.example.data.TaskItem
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object TaskUtils {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val subtaskListType = Types.newParameterizedType(List::class.java, Subtask::class.java)
    private val subtaskAdapter = moshi.adapter<List<Subtask>>(subtaskListType)

    private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun parseSubtasks(json: String?): List<Subtask> {
        if (json.isNullOrBlank() || json == "[]") return emptyList()
        return try {
            subtaskAdapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun serializeSubtasks(subtasks: List<Subtask>): String {
        return try {
            subtaskAdapter.toJson(subtasks)
        } catch (e: Exception) {
            "[]"
        }
    }

    fun getTodayIsoDate(): String {
        return isoDateFormat.format(Date())
    }

    fun getIsoDate(millis: Long): String {
        return isoDateFormat.format(Date(millis))
    }

    fun isToday(millis: Long?): Boolean {
        if (millis == null || millis <= 0) return false
        val cal1 = Calendar.getInstance().apply { timeInMillis = millis }
        val cal2 = Calendar.getInstance()
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    fun isOverdue(millis: Long?): Boolean {
        if (millis == null || millis <= 0) return false
        val cal1 = Calendar.getInstance().apply { timeInMillis = millis }
        val cal2 = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal1.timeInMillis < cal2.timeInMillis
    }

    fun isSameDay(millis1: Long, millis2: Long): Boolean {
        val cal1 = Calendar.getInstance().apply { timeInMillis = millis1 }
        val cal2 = Calendar.getInstance().apply { timeInMillis = millis2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    fun calculateNewStreak(lastDate: String?, currentStreak: Int, maxStreak: Int): Pair<Int, Int> {
        val today = getTodayIsoDate()
        if (lastDate == today) {
            val ensuredStreak = maxOf(1, currentStreak)
            return Pair(ensuredStreak, maxOf(maxStreak, ensuredStreak))
        }

        val yesterdayCal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -1)
        }
        val yesterday = isoDateFormat.format(yesterdayCal.time)

        val newStreak = if (lastDate == yesterday) {
            currentStreak + 1
        } else {
            1
        }
        val newMax = maxOf(maxStreak, newStreak)
        return Pair(newStreak, newMax)
    }

    fun computeStreakFromTasks(
        allTasks: List<TaskItem>,
        previousMaxStreak: Int = 0
    ): Triple<Int, Int, String> {
        val doneTasks = allTasks.filter { it.status == "DONE" }
        if (doneTasks.isEmpty()) {
            return Triple(0, previousMaxStreak, "")
        }
        val completedDates = doneTasks.map { task ->
            val ts = task.completedAt ?: task.dueDateTime ?: task.createdAt
            isoDateFormat.format(Date(ts))
        }.toSortedSet()

        val today = getTodayIsoDate()
        val yesterdayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val yesterday = isoDateFormat.format(yesterdayCal.time)
        val latestDate = completedDates.lastOrNull() ?: ""

        val cal = Calendar.getInstance()
        if (today !in completedDates) {
            if (yesterday in completedDates) {
                cal.add(Calendar.DAY_OF_YEAR, -1)
            } else {
                return Triple(0, maxOf(previousMaxStreak, 1), latestDate)
            }
        }

        var streak = 0
        while (true) {
            val iso = isoDateFormat.format(cal.time)
            if (iso in completedDates) {
                streak++
                cal.add(Calendar.DAY_OF_YEAR, -1)
            } else {
                break
            }
        }
        val best = maxOf(previousMaxStreak, streak)
        return Triple(streak, best, latestDate)
    }

    fun calculateNextDueDate(baseTime: Long, repeatFrequency: String): Long {
        val normalizedFreq = when (repeatFrequency.uppercase(Locale.US)) {
            "DAILY", "WEEKLY", "MONTHLY", "YEARLY" -> repeatFrequency.uppercase(Locale.US)
            else -> "DAILY"
        }
        return FormatUtils.advanceRecurringDate(baseTime, normalizedFreq)
    }
}

package com.example.utils

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.data.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object HabitUtils {

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val displayDayFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val shortLabelFormat = SimpleDateFormat("EEE, dd MMM", Locale.getDefault())

    val HABIT_ICONS: List<Pair<String, ImageVector>> = listOf(
        "SelfImprovement" to Icons.Default.SelfImprovement,
        "WaterDrop" to Icons.Default.WaterDrop,
        "FitnessCenter" to Icons.Default.FitnessCenter,
        "MenuBook" to Icons.Default.MenuBook,
        "Savings" to Icons.Default.Savings,
        "MoneyOff" to Icons.Default.MoneyOff,
        "DirectionsRun" to Icons.Default.DirectionsRun,
        "Bedtime" to Icons.Default.Bedtime,
        "LocalCafe" to Icons.Default.LocalCafe,
        "CheckCircle" to Icons.Default.CheckCircle
    )

    val HABIT_COLORS: List<Pair<String, String>> = listOf(
        "#2E7D32" to "Emerald",
        "#1565C0" to "Ocean Blue",
        "#7B1FA2" to "Royal Purple",
        "#E65100" to "Sunrise Orange",
        "#00897B" to "Teal",
        "#C2185B" to "Berry",
        "#D32F2F" to "Crimson"
    )

    fun getIcon(name: String): ImageVector {
        return HABIT_ICONS.firstOrNull { it.first.equals(name, ignoreCase = true) }?.second
            ?: Icons.Default.SelfImprovement
    }

    fun parseColor(hex: String): Color {
        return AccountUtils.parseColor(hex, Color(0xFF2E7D32))
    }

    fun getTodayIso(): String = isoFormat.format(Date())

    fun getIsoFromMillis(millis: Long): String = isoFormat.format(Date(millis))

    fun getIsoDaysAgo(daysAgo: Int, baseTimeMillis: Long = System.currentTimeMillis()): String {
        val cal = Calendar.getInstance().apply {
            timeInMillis = baseTimeMillis
            add(Calendar.DAY_OF_YEAR, -daysAgo)
        }
        return isoFormat.format(cal.time)
    }

    fun getLastNDaysIso(days: Int, baseTimeMillis: Long = System.currentTimeMillis()): List<String> {
        return (0 until days).map { getIsoDaysAgo(it, baseTimeMillis) }
    }

    fun formatIsoToIndianDate(dateIso: String): String {
        return try {
            val d = isoFormat.parse(dateIso) ?: return dateIso
            displayDayFormat.format(d)
        } catch (e: Exception) {
            dateIso
        }
    }

    fun formatIsoToShortLabel(dateIso: String): String {
        val today = getTodayIso()
        val yesterday = getIsoDaysAgo(1)
        return when (dateIso) {
            today -> "Today (${formatIsoToIndianDate(dateIso)})"
            yesterday -> "Yesterday (${formatIsoToIndianDate(dateIso)})"
            else -> try {
                val d = isoFormat.parse(dateIso) ?: return dateIso
                shortLabelFormat.format(d)
            } catch (e: Exception) {
                dateIso
            }
        }
    }

    fun isScheduledOnDate(habit: Habit, dateIso: String): Boolean {
        if (habit.frequencyType != "SPECIFIC_DAYS") return true
        return try {
            val d = isoFormat.parse(dateIso) ?: return true
            val cal = Calendar.getInstance().apply { time = d }
            // Convert Calendar.SUNDAY(1)..SATURDAY(7) to ISO 1=Mon..7=Sun
            val calDow = cal.get(Calendar.DAY_OF_WEEK)
            val isoDow = if (calDow == Calendar.SUNDAY) 7 else calDow - 1
            val scheduledSet = habit.scheduledDaysCsv
                .split(",")
                .mapNotNull { it.trim().toIntOrNull() }
                .toSet()
            if (scheduledSet.isEmpty()) true else scheduledSet.contains(isoDow)
        } catch (e: Exception) {
            true
        }
    }

    /**
     * Evaluates money-linked habits ("NO_SPEND_DAY", "SPEND_UNDER_LIMIT", "SAVED_TODAY")
     * directly from the user's transactions and goal contributions on the given dateIso ("yyyy-MM-dd").
     */
    fun evaluateMoneyRule(
        habit: Habit,
        dateIso: String,
        transactions: List<Transaction>,
        goalContributions: List<GoalContribution> = emptyList()
    ): Pair<Boolean, String?> {
        if (habit.moneyRuleType == "NONE" || habit.moneyRuleType.isBlank()) {
            return false to null
        }

        val dayTransactions = transactions.filter { getIsoFromMillis(it.timestamp) == dateIso }
        val dayExpenses = dayTransactions.filter {
            !it.isIncome && it.category != "Transfer" && it.category != "Adjustment" && it.category != "Udhaar"
        }
        val totalSpent = dayExpenses.sumOf { it.amount }

        return when (habit.moneyRuleType) {
            "NO_SPEND_DAY" -> {
                val satisfied = totalSpent <= 0.0
                val summary = if (satisfied) {
                    "Auto-verified: ₹0 spent on ${formatIsoToIndianDate(dateIso)} ✓"
                } else {
                    "Spent ${FormatUtils.formatInr(totalSpent)} on ${formatIsoToIndianDate(dateIso)}"
                }
                satisfied to summary
            }
            "SPEND_UNDER_LIMIT" -> {
                val limit = habit.moneyThreshold.coerceAtLeast(1.0)
                val satisfied = totalSpent <= limit
                val summary = "Spent ${FormatUtils.formatInr(totalSpent)} / limit ${FormatUtils.formatInr(limit)}"
                satisfied to summary
            }
            "SAVED_TODAY" -> {
                val target = habit.moneyThreshold.coerceAtLeast(1.0)
                val contribSaved = goalContributions
                    .filter { getIsoFromMillis(it.date) == dateIso }
                    .sumOf { it.amount }
                val txSaved = dayTransactions
                    .filter { it.category.contains("Saving", ignoreCase = true) || it.category.contains("Investment", ignoreCase = true) || it.category.contains("SIP", ignoreCase = true) }
                    .sumOf { it.amount }
                val totalSaved = contribSaved + txSaved
                val satisfied = totalSaved >= target
                val summary = "Saved ${FormatUtils.formatInr(totalSaved)} / target ${FormatUtils.formatInr(target)}"
                satisfied to summary
            }
            else -> false to null
        }
    }

    /**
     * Resolves effective status of a habit on a given date ("COMPLETED", "SKIPPED", "PARTIAL", "NONE")
     */
    fun getEffectiveDayStatus(
        habit: Habit,
        dateIso: String,
        logMap: Map<String, HabitLog>,
        transactions: List<Transaction>,
        goalContributions: List<GoalContribution>
    ): String {
        val log = logMap[dateIso]
        if (log != null) {
            if (log.status == "SKIPPED") return "SKIPPED"
            if (log.status == "COMPLETED" || log.currentCount >= habit.targetCount.coerceAtLeast(1)) {
                return "COMPLETED"
            }
            if (log.currentCount > 0) return "PARTIAL"
        }
        val createdIso = getIsoFromMillis(habit.createdAt)
        if (dateIso < createdIso) {
            return "NONE"
        }
        // Check if money-linked habit is satisfied on this date (only from habit creation date up to today)
        if (habit.moneyRuleType != "NONE") {
            if (dateIso <= getTodayIso()) {
                val (satisfied, _) = evaluateMoneyRule(habit, dateIso, transactions, goalContributions)
                if (satisfied) return "COMPLETED"
            }
        }
        return "NONE"
    }

    fun computeHabitStats(
        habit: Habit,
        selectedDateIso: String,
        logsForHabit: List<HabitLog>,
        transactions: List<Transaction>,
        goalContributions: List<GoalContribution> = emptyList()
    ): HabitComputedStats {
        val logMap = logsForHabit.associateBy { it.dateIso }
        val selectedLog = logMap[selectedDateIso]
        val createdIso = getIsoFromMillis(habit.createdAt)
        val (autoMoneyOk, autoMoneyMsg) = evaluateMoneyRule(habit, selectedDateIso, transactions, goalContributions)

        val effectiveSelectedStatus = when {
            selectedLog?.status == "SKIPPED" -> "SKIPPED"
            selectedLog?.status == "COMPLETED" || (selectedLog?.currentCount ?: 0) >= habit.targetCount.coerceAtLeast(1) -> "COMPLETED"
            selectedDateIso >= createdIso && habit.moneyRuleType != "NONE" && autoMoneyOk -> "COMPLETED"
            else -> "PENDING"
        }

        val effectiveSelectedCount = when {
            effectiveSelectedStatus == "COMPLETED" -> maxOf(selectedLog?.currentCount ?: 0, habit.targetCount.coerceAtLeast(1))
            else -> selectedLog?.currentCount ?: 0
        }

        // Build 90-day heatmap & calculate streaks
        val last90Days = getLastNDaysIso(90)
        val heatmap = mutableMapOf<String, String>()
        last90Days.forEach { dIso ->
            heatmap[dIso] = getEffectiveDayStatus(habit, dIso, logMap, transactions, goalContributions)
        }

        // Current streak (walking backwards from today, never walking before creation date unless explicitly logged)
        var currentStreak = 0
        for (i in 0 until 365) {
            val dIso = getIsoDaysAgo(i)
            val hasExplicitLog = logMap.containsKey(dIso)
            if (dIso < createdIso && !hasExplicitLog) {
                break
            }
            val status = heatmap[dIso] ?: getEffectiveDayStatus(habit, dIso, logMap, transactions, goalContributions)
            val scheduled = isScheduledOnDate(habit, dIso)

            if (status == "COMPLETED") {
                currentStreak++
            } else if (status == "SKIPPED" || !scheduled) {
                // Rest day or unscheduled weekday (after creation date): does not break streak
                continue
            } else {
                // If today (i == 0) is not completed yet, don't break streak if yesterday was completed
                if (i == 0) continue
                break
            }
        }

        // Longest streak over last 365 days
        var longestStreak = 0
        var runningStreak = 0
        for (i in 364 downTo 0) {
            val dIso = getIsoDaysAgo(i)
            val hasExplicitLog = logMap.containsKey(dIso)
            if (dIso < createdIso && !hasExplicitLog) {
                continue
            }
            val status = heatmap[dIso] ?: getEffectiveDayStatus(habit, dIso, logMap, transactions, goalContributions)
            val scheduled = isScheduledOnDate(habit, dIso)
            if (status == "COMPLETED") {
                runningStreak++
                if (runningStreak > longestStreak) longestStreak = runningStreak
            } else if (status == "SKIPPED" || !scheduled) {
                // Rest day preserves runningStreak
                continue
            } else {
                if (i != 0) runningStreak = 0
            }
        }
        longestStreak = maxOf(longestStreak, currentStreak)

        // Completion % for Week (7d), Month (30d), Year (up to 365d since creation)
        fun calcCompletionPct(days: Int): Int {
            var completed = 0
            var eligible = 0
            for (i in 0 until days) {
                val dIso = getIsoDaysAgo(i)
                val status = heatmap[dIso] ?: getEffectiveDayStatus(habit, dIso, logMap, transactions, goalContributions)
                if (status == "SKIPPED") continue
                if (!isScheduledOnDate(habit, dIso)) continue
                eligible++
                if (status == "COMPLETED") completed++
            }
            if (eligible == 0) return 100
            return ((completed.toDouble() / eligible.toDouble()) * 100.0).toInt().coerceIn(0, 100)
        }

        val weekPct = calcCompletionPct(7)
        val monthPct = calcCompletionPct(30)
        val daysSinceCreated = maxOf(1, ((System.currentTimeMillis() - habit.createdAt) / (24L * 60 * 60 * 1000)).toInt() + 1).coerceAtMost(365)
        val yearPct = calcCompletionPct(maxOf(7, daysSinceCreated))

        return HabitComputedStats(
            habit = habit,
            selectedDateIso = selectedDateIso,
            selectedDayLog = selectedLog,
            selectedDayCount = effectiveSelectedCount,
            selectedDayStatus = effectiveSelectedStatus,
            isAutoMoneySatisfied = autoMoneyOk,
            autoMoneySummary = autoMoneyMsg,
            currentStreak = currentStreak,
            longestStreak = longestStreak,
            weekCompletionPercent = weekPct,
            monthCompletionPercent = monthPct,
            yearCompletionPercent = yearPct,
            heatmapMap = heatmap
        )
    }
}

package com.example.utils

import com.example.data.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object ReportUtils {

    fun getDateRangeBounds(
        range: ReportDateRange,
        customStart: Long? = null,
        customEnd: Long? = null
    ): Pair<Long, Long> {
        val now = Calendar.getInstance()
        return when (range) {
            ReportDateRange.TODAY -> {
                val start = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val end = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }.timeInMillis
                Pair(start, end)
            }
            ReportDateRange.THIS_WEEK -> {
                val start = Calendar.getInstance().apply {
                    firstDayOfWeek = Calendar.MONDAY
                    set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                Pair(start, now.timeInMillis)
            }
            ReportDateRange.THIS_MONTH -> {
                val start = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                Pair(start, now.timeInMillis)
            }
            ReportDateRange.THIS_YEAR -> {
                val start = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_YEAR, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                Pair(start, now.timeInMillis)
            }
            ReportDateRange.CUSTOM -> {
                val rawStart = customStart ?: (now.timeInMillis - (30L * 24 * 60 * 60 * 1000))
                val rawEnd = customEnd ?: now.timeInMillis
                val s = minOf(rawStart, rawEnd)
                val eRaw = maxOf(rawStart, rawEnd)
                // If customEnd was picked at midnight (00:00:00.000), extend to 23:59:59.999 of that day
                val calEnd = Calendar.getInstance().apply { timeInMillis = eRaw }
                val e = if (
                    calEnd.get(Calendar.HOUR_OF_DAY) == 0 &&
                    calEnd.get(Calendar.MINUTE) == 0 &&
                    calEnd.get(Calendar.SECOND) == 0 &&
                    calEnd.get(Calendar.MILLISECOND) == 0
                ) {
                    FormatUtils.endOfDayMillis(eRaw)
                } else {
                    eRaw
                }
                Pair(s, e)
            }
        }
    }

    fun filterTransactions(
        transactions: List<Transaction>,
        startTime: Long,
        endTime: Long,
        accountFilter: String = "All",
        categoryFilter: String = "All"
    ): List<Transaction> {
        val lo = minOf(startTime, endTime)
        val hi = maxOf(startTime, endTime)
        return transactions.filter { tx ->
            val matchesTime = tx.timestamp in lo..hi
            val matchesAccount = if (accountFilter == "All") true else tx.account.equals(accountFilter, ignoreCase = true)
            val matchesCategory = if (categoryFilter == "All") true else tx.category.equals(categoryFilter, ignoreCase = true)
            matchesTime && matchesAccount && matchesCategory
        }
    }

    /**
     * Single source of truth for non-operating categories that must never be counted
     * as Income or Expense in reports, budgets, or history income/expense filters.
     */
    fun isExcludedFromIncomeExpense(category: String): Boolean {
        return category.equals("Transfer", ignoreCase = true) ||
            category.equals("Adjustment", ignoreCase = true) ||
            category.equals("Udhaar", ignoreCase = true) ||
            category.equals("Investment", ignoreCase = true)
    }

    fun calculateSummary(
        filteredTransactions: List<Transaction>,
        startTime: Long = 0L,
        endTime: Long = System.currentTimeMillis()
    ): ReportSummary {
        var totalIncome = 0.0
        var totalExpense = 0.0
        val dayFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val expensesByDay = HashMap<String, Double>()

        for (tx in filteredTransactions) {
            if (!tx.amount.isFinite() || tx.amount < 0.0) continue
            if (isExcludedFromIncomeExpense(tx.category)) {
                continue
            }
            if (tx.isIncome) {
                totalIncome += tx.amount
            } else {
                totalExpense += tx.amount
                val dayKey = dayFormat.format(Date(tx.timestamp))
                expensesByDay[dayKey] = (expensesByDay[dayKey] ?: 0.0) + tx.amount
            }
        }

        totalIncome = FormatUtils.round2(totalIncome)
        totalExpense = FormatUtils.round2(totalExpense)
        val netSavings = FormatUtils.round2(totalIncome - totalExpense)
        val savingsPercent = if (totalIncome > 0.0) ((netSavings / totalIncome) * 100.0).toInt() else 0

        val lo = minOf(startTime, endTime)
        val hi = maxOf(startTime, endTime)
        val daysDiff = maxOf(1, ((hi - lo) / (24L * 60 * 60 * 1000)).toInt() + 1)
        val avgDailySpend = FormatUtils.round2(totalExpense / daysDiff)

        val highestDayEntry = expensesByDay.maxByOrNull { it.value }
        val highestDay = highestDayEntry?.key ?: "None"
        val highestAmount = FormatUtils.round2(highestDayEntry?.value ?: 0.0)

        return ReportSummary(
            totalIncome = totalIncome,
            totalExpense = totalExpense,
            netSavings = netSavings,
            savingsPercent = savingsPercent,
            avgDailySpend = avgDailySpend,
            highestExpenseDay = highestDay,
            highestExpenseDayAmount = highestAmount,
            totalDays = daysDiff
        )
    }

    fun calculateMonthComparison(transactions: List<Transaction>): MonthComparison {
        val now = Calendar.getInstance()

        // Current Month bounds
        val curStart = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val curEnd = now.timeInMillis

        // Previous Month bounds
        val prevStartCal = Calendar.getInstance().apply {
            add(Calendar.MONTH, -1)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val prevStart = prevStartCal.timeInMillis

        val prevEndCal = Calendar.getInstance().apply {
            add(Calendar.MONTH, -1)
            val maxDay = getActualMaximum(Calendar.DAY_OF_MONTH)
            set(Calendar.DAY_OF_MONTH, maxDay)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        val prevEnd = prevEndCal.timeInMillis

        val curTxs = transactions.filter {
            it.timestamp in curStart..curEnd &&
            it.category != "Transfer" && it.category != "Adjustment" && it.category != "Udhaar" && it.category != "Investment"
        }
        val prevTxs = transactions.filter {
            it.timestamp in prevStart..prevEnd &&
            it.category != "Transfer" && it.category != "Adjustment" && it.category != "Udhaar" && it.category != "Investment"
        }

        val curIncome = curTxs.filter { it.isIncome }.sumOf { it.amount }
        val curExpense = curTxs.filter { !it.isIncome }.sumOf { it.amount }
        val curSavings = curIncome - curExpense

        val prevIncome = prevTxs.filter { it.isIncome }.sumOf { it.amount }
        val prevExpense = prevTxs.filter { !it.isIncome }.sumOf { it.amount }
        val prevSavings = prevIncome - prevExpense

        fun calcChange(cur: Double, prev: Double): Double {
            if (prev == 0.0) return if (cur > 0) 100.0 else 0.0
            return ((cur - prev) / prev) * 100.0
        }

        return MonthComparison(
            currentMonthIncome = curIncome,
            previousMonthIncome = prevIncome,
            incomeChangePercent = calcChange(curIncome, prevIncome),
            currentMonthExpense = curExpense,
            previousMonthExpense = prevExpense,
            expenseChangePercent = calcChange(curExpense, prevExpense),
            currentMonthSavings = curSavings,
            previousMonthSavings = prevSavings,
            savingsChangePercent = calcChange(curSavings, prevSavings)
        )
    }

    fun calculateCashBankFlow(
        transactions: List<Transaction>,
        startTime: Long,
        endTime: Long
    ): CashBankFlow {
        val rangeTxs = transactions.filter { it.timestamp in startTime..endTime }

        var cashIn = 0.0
        var cashOut = 0.0
        var bankIn = 0.0
        var bankOut = 0.0

        rangeTxs.forEach { tx ->
            val isCash = tx.account.contains("cash", ignoreCase = true)
            if (isCash) {
                if (tx.isIncome) cashIn += tx.amount else cashOut += tx.amount
            } else {
                if (tx.isIncome) bankIn += tx.amount else bankOut += tx.amount
            }
        }

        return CashBankFlow(
            cashInflow = cashIn,
            cashOutflow = cashOut,
            bankInflow = bankIn,
            bankOutflow = bankOut
        )
    }

    data class TopCategoryStat(
        val category: String,
        val totalAmount: Double,
        val transactionCount: Int,
        val percentageOfTotal: Double
    )

    fun calculateTopCategories(
        transactions: List<Transaction>,
        startTime: Long,
        endTime: Long
    ): List<TopCategoryStat> {
        val expenses = transactions.filter {
            it.timestamp in startTime..endTime &&
            !it.isIncome &&
            it.category != "Transfer" && it.category != "Adjustment" && it.category != "Udhaar" && it.category != "Investment"
        }
        val total = expenses.sumOf { it.amount }
        if (total <= 0.0) return emptyList()

        return expenses.groupBy { it.category }
            .map { (cat, list) ->
                val sum = list.sumOf { it.amount }
                TopCategoryStat(
                    category = cat,
                    totalAmount = sum,
                    transactionCount = list.size,
                    percentageOfTotal = (sum / total) * 100.0
                )
            }
            .sortedByDescending { it.totalAmount }
            .take(5)
    }

    data class MonthBarData(
        val monthLabel: String,
        val income: Double,
        val expense: Double
    )

    fun calculateLast6MonthsFlow(transactions: List<Transaction>): List<MonthBarData> {
        val list = mutableListOf<MonthBarData>()
        val monthFormat = SimpleDateFormat("MMM", Locale.getDefault())

        for (i in 5 downTo 0) {
            val calStart = Calendar.getInstance().apply {
                add(Calendar.MONTH, -i)
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val calEnd = Calendar.getInstance().apply {
                timeInMillis = calStart.timeInMillis
                val maxDay = getActualMaximum(Calendar.DAY_OF_MONTH)
                set(Calendar.DAY_OF_MONTH, maxDay)
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }

            val monthTxs = transactions.filter {
                it.timestamp in calStart.timeInMillis..calEnd.timeInMillis &&
                it.category != "Transfer" && it.category != "Adjustment" && it.category != "Udhaar" && it.category != "Investment"
            }
            val inc = monthTxs.filter { it.isIncome }.sumOf { it.amount }
            val exp = monthTxs.filter { !it.isIncome }.sumOf { it.amount }
            val label = monthFormat.format(calStart.time)
            list.add(MonthBarData(label, inc, exp))
        }

        return list
    }

    data class TrendPoint(
        val label: String,
        val amount: Double
    )

    fun calculateDailyTrend(
        transactions: List<Transaction>,
        startTime: Long,
        endTime: Long
    ): List<TrendPoint> {
        val expenses = transactions.filter {
            it.timestamp in startTime..endTime &&
            !it.isIncome &&
            it.category != "Transfer" && it.category != "Adjustment" && it.category != "Udhaar" && it.category != "Investment"
        }
        val days = maxOf(1, ((endTime - startTime) / (24L * 60 * 60 * 1000)).toInt() + 1)
        val dateFormat = SimpleDateFormat("dd MMM", Locale.getDefault())

        // Group into at most 14 points for clean rendering
        val step = maxOf(1, days / 14)
        val points = mutableListOf<TrendPoint>()

        var currentStart = startTime
        while (currentStart <= endTime) {
            val currentEnd = currentStart + (step * 24L * 60 * 60 * 1000) - 1
            val sliceExpenses = expenses.filter { it.timestamp in currentStart..currentEnd }.sumOf { it.amount }
            val label = dateFormat.format(Date(currentStart))
            points.add(TrendPoint(label, sliceExpenses))
            currentStart += (step * 24L * 60 * 60 * 1000)
        }

        return points
    }
}

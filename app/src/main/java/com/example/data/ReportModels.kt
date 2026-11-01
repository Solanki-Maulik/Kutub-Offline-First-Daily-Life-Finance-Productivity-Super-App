package com.example.data

enum class ReportDateRange(val label: String) {
    TODAY("Today"),
    THIS_WEEK("This Week"),
    THIS_MONTH("This Month"),
    THIS_YEAR("This Year"),
    CUSTOM("Custom Range")
}

data class ReportSummary(
    val totalIncome: Double,
    val totalExpense: Double,
    val netSavings: Double,
    val savingsPercent: Int,
    val avgDailySpend: Double,
    val highestExpenseDay: String,
    val highestExpenseDayAmount: Double,
    val totalDays: Int
)

data class MonthComparison(
    val currentMonthIncome: Double,
    val previousMonthIncome: Double,
    val incomeChangePercent: Double,

    val currentMonthExpense: Double,
    val previousMonthExpense: Double,
    val expenseChangePercent: Double,

    val currentMonthSavings: Double,
    val previousMonthSavings: Double,
    val savingsChangePercent: Double
)

data class CashBankFlow(
    val cashInflow: Double,
    val cashOutflow: Double,
    val bankInflow: Double,
    val bankOutflow: Double
)

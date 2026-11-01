package com.example.data

data class BudgetStatus(
    val category: String,
    val limitAmount: Double,
    val spentAmount: Double,
    val remainingAmount: Double,
    val progress: Float,
    val isWarning80: Boolean,
    val isExceeded100: Boolean
)

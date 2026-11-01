package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
@Entity(tableName = "recurring_transactions")
data class RecurringTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val amount: Double,
    val isIncome: Boolean,
    val category: String,
    val account: String,
    val frequency: String, // "DAILY", "WEEKLY", "MONTHLY", "YEARLY"
    val nextDueDate: Long,
    val autoCreate: Boolean = true,
    val pendingConfirmation: Boolean = false,
    val note: String = "",
    val isActive: Boolean = true,
    val lastExecutedDate: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

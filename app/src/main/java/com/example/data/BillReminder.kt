package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
@Entity(tableName = "bill_reminders")
data class BillReminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val amount: Double,
    val dueDate: Long,
    val category: String = "Bills",
    val account: String = "Bank Account",
    val isPaid: Boolean = false,
    val paidDate: Long? = null,
    val isRecurring: Boolean = false,
    val recurringFrequency: String? = "MONTHLY",
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

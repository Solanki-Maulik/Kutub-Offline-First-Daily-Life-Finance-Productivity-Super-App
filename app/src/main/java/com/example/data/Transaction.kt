package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val amount: Double,
    val isIncome: Boolean,
    val category: String,
    val subcategory: String = "",
    val account: String = "Cash in Hand",
    val paymentMethod: String = "UPI",
    val note: String = "",
    val tags: String = "",
    val photoUri: String? = null,
    val timestamp: Long
)

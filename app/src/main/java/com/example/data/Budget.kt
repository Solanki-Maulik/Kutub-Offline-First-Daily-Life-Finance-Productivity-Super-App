package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey val category: String,
    val limitAmount: Double
)

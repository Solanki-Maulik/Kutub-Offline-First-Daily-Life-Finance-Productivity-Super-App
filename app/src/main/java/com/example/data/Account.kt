package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey val name: String,
    val balance: Double = 0.0
)

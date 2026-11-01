package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val isIncome: Boolean = false,
    val colorHex: String = "#FF9800",
    val iconName: String = "Category",
    val isDefault: Boolean = false
)

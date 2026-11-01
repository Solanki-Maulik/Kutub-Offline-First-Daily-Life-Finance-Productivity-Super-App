package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
@Entity(
    tableName = "udhaar_repayments",
    foreignKeys = [
        ForeignKey(
            entity = UdhaarEntry::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["entryId"]), Index(value = ["personId"])]
)
data class UdhaarRepayment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: Long,
    val personId: Long,
    val amount: Double,
    val account: String = "Cash in Hand",
    val date: Long = System.currentTimeMillis(),
    val note: String = "",
    val transactionId: Int? = null
)

package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

enum class UdhaarType(val id: String, val title: String) {
    GAVE("GAVE", "I gave (Lent)"),
    TOOK("TOOK", "I took (Borrowed)");

    companion object {
        fun fromId(id: String): UdhaarType =
            if (id.equals("TOOK", ignoreCase = true)) TOOK else GAVE
    }
}

@JsonClass(generateAdapter = true)
@Entity(
    tableName = "udhaar_entries",
    foreignKeys = [
        ForeignKey(
            entity = Person::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["personId"])]
)
data class UdhaarEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val type: String, // "GAVE" or "TOOK"
    val amount: Double,
    val remainingAmount: Double,
    val account: String = "Cash in Hand",
    val date: Long = System.currentTimeMillis(),
    val dueDate: Long? = null,
    val note: String = "",
    val isSettled: Boolean = false,
    val transactionId: Int? = null
)

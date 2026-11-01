package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

enum class AccountType(val id: String, val displayName: String) {
    CASH("CASH", "Cash in Hand"),
    BANK("BANK", "Bank Account"),
    UPI("UPI", "UPI Wallet"),
    CREDIT_CARD("CREDIT_CARD", "Credit Card");

    companion object {
        fun fromId(id: String?): AccountType {
            if (id.isNullOrBlank()) return BANK
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
                ?: when {
                    id.contains("cash", ignoreCase = true) -> CASH
                    id.contains("upi", ignoreCase = true) || id.contains("online", ignoreCase = true) || id.contains("wallet", ignoreCase = true) -> UPI
                    id.contains("card", ignoreCase = true) || id.contains("credit", ignoreCase = true) -> CREDIT_CARD
                    else -> BANK
                }
        }
    }
}

@JsonClass(generateAdapter = true)
@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey val name: String,
    val balance: Double = 0.0, // Represents Opening Balance
    val type: String = "BANK", // CASH, BANK, UPI, CREDIT_CARD
    val colorHex: String = "#1565C0",
    val iconName: String = "AccountBalance"
)

package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MemberSplit(
    val memberName: String,
    val amount: Double,
    val percentage: Double = 0.0,
    val shares: Int = 1
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "split_groups")
data class SplitGroup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val type: String = "Trip", // "Trip", "Flatmates", "Dinner", "Event", "Other"
    val membersJson: String = "[\"Me\"]",
    val linkToUdhaar: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "split_expenses")
data class SplitExpense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val groupId: Long,
    val title: String,
    val totalAmount: Double,
    val category: String = "Food",
    val paidBy: String = "Me",
    val paymentAccount: String = "UPI Wallet",
    val splitType: String = "EQUALLY", // "EQUALLY", "EXACT", "PERCENTAGE", "SHARES"
    val splitsJson: String = "[]",
    val myShareAmount: Double = 0.0,
    val myExpenseTxId: Long? = null,
    val receivableTxId: Long? = null,
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "split_settlements")
data class SplitSettlement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val groupId: Long,
    val fromMember: String,
    val toMember: String,
    val amount: Double,
    val account: String = "UPI Wallet",
    val transactionId: Long? = null,
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class MemberBalance(
    val memberName: String,
    val totalPaid: Double,
    val totalShare: Double,
    val netBalance: Double // > 0 means gets back (owed to them), < 0 means owes others
)

data class SimplifiedDebt(
    val fromMember: String,
    val toMember: String,
    val amount: Double
)

data class SplitGroupSummary(
    val group: SplitGroup,
    val members: List<String>,
    val totalGroupSpend: Double,
    val myTotalPaid: Double,
    val myTotalShare: Double,
    val myNetBalance: Double, // > 0: I am owed overall, < 0: I owe overall
    val memberBalances: List<MemberBalance>,
    val simplifiedDebts: List<SimplifiedDebt>,
    val expensesCount: Int,
    val settlementsCount: Int
)

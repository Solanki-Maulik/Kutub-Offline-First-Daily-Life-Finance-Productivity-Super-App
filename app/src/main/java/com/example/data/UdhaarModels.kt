package com.example.data

data class PersonUdhaarSummary(
    val person: Person,
    val totalGave: Double,
    val totalTook: Double,
    val remainingToReceive: Double, // unsettled "GAVE" remaining
    val remainingToPay: Double,     // unsettled "TOOK" remaining
    val netBalance: Double,         // remainingToReceive - remainingToPay (>0: They owe me, <0: I owe them)
    val activeEntriesCount: Int,
    val hasOverdue: Boolean,
    val lastActivityDate: Long
)

sealed class DueStatus {
    object None : DueStatus()
    data class Overdue(val days: Long) : DueStatus()
    object DueToday : DueStatus()
    data class DueSoon(val days: Long) : DueStatus()
    data class DueLater(val dateString: String) : DueStatus()
}

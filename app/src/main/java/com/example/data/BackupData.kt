package com.example.data

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BackupData(
    val accounts: List<Account>,
    val budgets: List<Budget>,
    val debts: List<Debt>,
    val goals: List<Goal>,
    val moneyNotes: List<MoneyNote>,
    val transactions: List<Transaction>
)

package com.example.data

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BackupData(
    val accounts: List<Account> = emptyList(),
    val budgets: List<Budget> = emptyList(),
    val debts: List<Debt> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val moneyNotes: List<MoneyNote> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
    val categories: List<Category> = emptyList(),
    val people: List<Person> = emptyList(),
    val udhaarEntries: List<UdhaarEntry> = emptyList(),
    val udhaarRepayments: List<UdhaarRepayment> = emptyList(),
    val goalContributions: List<GoalContribution> = emptyList(),
    val recurringTransactions: List<RecurringTransaction> = emptyList(),
    val billReminders: List<BillReminder> = emptyList(),
    val tasks: List<TaskItem> = emptyList(),
    val scheduleEvents: List<ScheduleEvent> = emptyList(),
    val splitGroups: List<SplitGroup> = emptyList(),
    val splitExpenses: List<SplitExpense> = emptyList(),
    val splitSettlements: List<SplitSettlement> = emptyList(),
    val habits: List<Habit> = emptyList(),
    val habitLogs: List<HabitLog> = emptyList(),
    val investmentHoldings: List<InvestmentHolding> = emptyList(),
    val liabilityItems: List<LiabilityItem> = emptyList(),
    val vaultDocuments: List<VaultDocument> = emptyList(),
    val vaultFilePayloadsBase64: Map<String, String> = emptyMap(),
    val taskStreak: TaskStreak? = null,
    val userSettings: Map<String, String> = emptyMap(),
    val schemaVersion: Int = 2,
    val exportedAt: Long = System.currentTimeMillis(),
    val appName: String = "RupeeStory"
)

data class BackupPreviewSummary(
    val schemaVersion: Int,
    val exportedAt: Long,
    val appName: String,
    val transactionsCount: Int,
    val accountsCount: Int,
    val categoriesCount: Int,
    val budgetsCount: Int,
    val goalsCount: Int,
    val debtsAndUdhaarCount: Int,
    val recurringAndBillsCount: Int,
    val tasksAndEventsCount: Int,
    val notesCount: Int,
    val splitGroupsAndExpensesCount: Int,
    val habitsAndLogsCount: Int,
    val investmentsAndLiabilitiesCount: Int,
    val vaultDocumentsCount: Int,
    val totalRecordsCount: Int,
    val dateRangeLabel: String,
    val includesSettings: Boolean
)

data class BackupValidationResult(
    val isValid: Boolean,
    val errors: List<String>,
    val warnings: List<String>,
    val preview: BackupPreviewSummary?,
    val parsedData: BackupData?,
    val rawJson: String = ""
)

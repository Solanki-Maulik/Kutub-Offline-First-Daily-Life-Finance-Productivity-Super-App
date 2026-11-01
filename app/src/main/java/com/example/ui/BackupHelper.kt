package com.example.ui

import android.content.Context
import android.net.Uri
import com.example.data.BackupData
import com.example.data.BackupPreviewSummary
import com.example.data.BackupValidationResult
import com.example.utils.FormatUtils
import com.example.viewmodel.FinanceViewModel
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.InputStreamReader
import java.io.OutputStreamWriter

object BackupHelper {
    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    fun serializeBackupToJson(data: BackupData): String {
        val adapter = moshi.adapter(BackupData::class.java).indent("  ")
        return adapter.toJson(data)
    }

    fun buildPreviewSummary(data: BackupData): BackupPreviewSummary {
        val txDates = data.transactions.map { it.timestamp }.filter { it > 0L }
        val dateRangeLabel = if (txDates.isNotEmpty()) {
            val minDate = FormatUtils.formatDate(txDates.minOrNull()!!)
            val maxDate = FormatUtils.formatDate(txDates.maxOrNull()!!)
            "$minDate – $maxDate"
        } else {
            "No transactions"
        }

        val debtsAndUdhaar = data.debts.size + data.people.size + data.udhaarEntries.size + data.udhaarRepayments.size
        val recurringAndBills = data.recurringTransactions.size + data.billReminders.size
        val tasksAndEvents = data.tasks.size + data.scheduleEvents.size
        val splitCount = data.splitGroups.size + data.splitExpenses.size + data.splitSettlements.size
        val habitsCount = data.habits.size + data.habitLogs.size
        val investCount = data.investmentHoldings.size + data.liabilityItems.size

        val totalRecords = data.transactions.size +
            data.accounts.size +
            data.categories.size +
            data.budgets.size +
            data.goals.size +
            data.goalContributions.size +
            debtsAndUdhaar +
            recurringAndBills +
            tasksAndEvents +
            data.moneyNotes.size +
            splitCount +
            habitsCount +
            investCount +
            data.vaultDocuments.size

        return BackupPreviewSummary(
            schemaVersion = data.schemaVersion,
            exportedAt = data.exportedAt,
            appName = data.appName.ifBlank { "RupeeStory" },
            transactionsCount = data.transactions.size,
            accountsCount = data.accounts.size,
            categoriesCount = data.categories.size,
            budgetsCount = data.budgets.size,
            goalsCount = data.goals.size,
            debtsAndUdhaarCount = debtsAndUdhaar,
            recurringAndBillsCount = recurringAndBills,
            tasksAndEventsCount = tasksAndEvents,
            notesCount = data.moneyNotes.size,
            splitGroupsAndExpensesCount = splitCount,
            habitsAndLogsCount = habitsCount,
            investmentsAndLiabilitiesCount = investCount,
            vaultDocumentsCount = data.vaultDocuments.size,
            totalRecordsCount = totalRecords,
            dateRangeLabel = dateRangeLabel,
            includesSettings = data.userSettings.isNotEmpty()
        )
    }

    fun parseAndValidateBackupJson(json: String): BackupValidationResult {
        val trimmed = json.trim()
        if (trimmed.isEmpty()) {
            return BackupValidationResult(
                isValid = false,
                errors = listOf("Backup file is empty."),
                warnings = emptyList(),
                preview = null,
                parsedData = null,
                rawJson = json
            )
        }

        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        return try {
            val rootObj = JSONObject(trimmed)
            if (!rootObj.has("transactions") && !rootObj.has("accounts")) {
                errors.add("Missing required 'transactions' and 'accounts' sections in JSON.")
            }

            val adapter = moshi.adapter(BackupData::class.java)
            val parsed = adapter.fromJson(trimmed)
            if (parsed == null) {
                errors.add("Could not deserialize BackupData object from JSON.")
                return BackupValidationResult(
                    isValid = false,
                    errors = errors,
                    warnings = warnings,
                    preview = null,
                    parsedData = null,
                    rawJson = json
                )
            }

            // Integrity checks
            val invalidTxCount = parsed.transactions.count { it.amount < 0.0 || it.account.isBlank() }
            if (invalidTxCount > 0) {
                warnings.add("$invalidTxCount transaction(s) have blank account or negative amount and will be normalized.")
            }
            if (parsed.accounts.isEmpty()) {
                warnings.add("Backup contains 0 accounts; default accounts (Cash, Bank, UPI) will be preserved.")
            }
            if (parsed.vaultDocuments.isNotEmpty()) {
                warnings.add("${parsed.vaultDocuments.size} Document Vault metadata record(s) included. Local attachment files are device-specific unless restored on the same device.")
            }
            if (parsed.schemaVersion > 2) {
                warnings.add("Backup was created with a newer schema version (v${parsed.schemaVersion}).")
            }

            val preview = buildPreviewSummary(parsed)
            if (preview.totalRecordsCount == 0 && !preview.includesSettings) {
                warnings.add("Backup file contains 0 records across all modules.")
            }

            BackupValidationResult(
                isValid = errors.isEmpty(),
                errors = errors,
                warnings = warnings,
                preview = preview,
                parsedData = parsed,
                rawJson = trimmed
            )
        } catch (e: Exception) {
            BackupValidationResult(
                isValid = false,
                errors = listOf("Invalid JSON format: ${e.localizedMessage ?: "Parse error"}"),
                warnings = warnings,
                preview = null,
                parsedData = null,
                rawJson = json
            )
        }
    }

    suspend fun readAndValidateBackupUri(context: Context, uri: Uri): BackupValidationResult {
        return withContext(Dispatchers.IO) {
            try {
                val jsonText = context.contentResolver.openInputStream(uri)?.use { input ->
                    InputStreamReader(input).use { reader ->
                        reader.readText()
                    }
                } ?: ""
                parseAndValidateBackupJson(jsonText)
            } catch (e: Exception) {
                BackupValidationResult(
                    isValid = false,
                    errors = listOf("Failed to read file: ${e.localizedMessage ?: "IO error"}"),
                    warnings = emptyList(),
                    preview = null,
                    parsedData = null
                )
            }
        }
    }

    suspend fun exportData(context: Context, uri: Uri, data: BackupData): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val json = serializeBackupToJson(data)
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    OutputStreamWriter(output).use { writer ->
                        writer.write(json)
                    }
                }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    suspend fun importData(context: Context, uri: Uri, viewModel: FinanceViewModel): BackupValidationResult {
        val result = readAndValidateBackupUri(context, uri)
        if (result.isValid && result.parsedData != null) {
            viewModel.restoreBackup(result.parsedData, clearExistingFirst = false)
        }
        return result
    }
}

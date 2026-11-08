package com.example.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import com.example.data.*
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

private const val TAG = "GeminiAiService"
private const val MODEL_NAME = "gemini-3.5-flash-lite"
private const val AI_TIMEOUT_MS = 2_500L

const val AI_MISTAKES_DISCLAIMER =
    "AI can make mistakes. Please verify amounts, accounts, categories, and dates before saving or acting on financial insights."

sealed interface AiUiState<out T> {
    data object Idle : AiUiState<Nothing>
    data class Loading(val message: String = "Analyzing with Gemini AI...") : AiUiState<Nothing>
    data class Success<T>(
        val data: T,
        val minimalPayloadPreview: String = ""
    ) : AiUiState<T>
    data class Error(
        val message: String,
        val isOffline: Boolean = false,
        val isQuotaExceeded: Boolean = false
    ) : AiUiState<Nothing>
}

// ============================================================================
// 1. Structured Models for All 6 AI Features
// ============================================================================

data class SmartParsedEntry(
    val id: String = UUID.randomUUID().toString(),
    val entryKind: String, // "EXPENSE", "INCOME", "TRANSFER", "UDHAAR_GOT", "UDHAAR_GAVE", "TASK", "SCHEDULE", "HABIT", "NOTE"
    val amount: Double,
    val account: String,
    val toAccount: String = "",
    val category: String,
    val personName: String = "",
    val dateString: String, // DD/MM/YYYY
    val timestamp: Long,
    val note: String,
    val selected: Boolean = true,
    val validationWarning: String? = null
) {
    val requiresAmount: Boolean
        get() = entryKind.uppercase(Locale.ROOT) in setOf("EXPENSE", "INCOME", "TRANSFER", "UDHAAR_GOT", "UDHAAR_GAVE")

    val isReadyToSave: Boolean
        get() = selected && (!requiresAmount || amount > 0.0) && note.isNotBlank()
}

data class SmartEntryResult(
    val entries: List<SmartParsedEntry>,
    val summaryNote: String
)

data class ReceiptScanResult(
    val amount: Double,
    val dateString: String, // DD/MM/YYYY
    val timestamp: Long,
    val shopName: String,
    val category: String,
    val paymentMethod: String,
    val itemsSummary: String,
    val confidenceNote: String,
    val validationWarning: String? = null
)

data class AskMyDataResult(
    val question: String,
    val answer: String,
    val figuresCited: List<String>,
    val followUpSuggestions: List<String>
)

data class MonthlyInsightsResult(
    val headlineSummary: String,
    val healthScore: Int,
    val spendingPatterns: List<String>,
    val unusualExpenses: List<String>,
    val savingTips: List<String>,
    val budgetWarnings: List<String>
)

data class AiSuggestedTask(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val priority: String, // "HIGH", "MEDIUM", "LOW"
    val category: String,
    val dueDaysFromNow: Int,
    val selected: Boolean = true
)

data class NoteHelperResult(
    val summaryBullets: List<String>,
    val grammarFixedTitle: String,
    val grammarFixedContent: String,
    val extractedTasks: List<AiSuggestedTask>
)

data class AiSuggestedScheduleBlock(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val dayOffset: Int,
    val startHour: Int,
    val durationMinutes: Int,
    val notes: String,
    val selected: Boolean = true
)

data class TaskHelperResult(
    val goalTitle: String,
    val strategySummary: String,
    val priority: String,
    val category: String,
    val subtasks: List<String>,
    val scheduleBlocks: List<AiSuggestedScheduleBlock>
)

// ============================================================================
// 2. Minimum-Data Privacy Aggregator (Never sends full database!)
// ============================================================================

object AiDataSummaryBuilder {

    fun todayDateDdMmYyyy(nowMillis: Long = System.currentTimeMillis()): String {
        return SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(nowMillis))
    }

    fun parseDdMmYyyyOrToday(dateStr: String, fallbackMillis: Long = System.currentTimeMillis()): Pair<String, Long> {
        val cleaned = dateStr.trim()
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply { isLenient = false }
        return try {
            val parsed = sdf.parse(cleaned)
            if (parsed != null) {
                cleaned to parsed.time
            } else {
                todayDateDdMmYyyy(fallbackMillis) to fallbackMillis
            }
        } catch (_: Exception) {
            todayDateDdMmYyyy(fallbackMillis) to fallbackMillis
        }
    }

    /**
     * Builds the minimal context needed for Smart Entry: only today's date,
     * available account names, and available category names.
     */
    fun buildSmartEntryMinimalContext(
        accounts: List<Account>,
        categories: List<Category>,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        val accountNames = accounts.map { it.name }.ifEmpty { listOf("Cash", "Bank", "UPI") }.distinct()
        val categoryNames = (categories.map { it.name } + listOf(
            "Food", "Transport", "Shopping", "Bills", "Entertainment", "Health", "Groceries", "Rent", "Salary", "Other"
        )).distinct()

        return JSONObject().apply {
            put("todayDate", todayDateDdMmYyyy(nowMillis))
            put("currency", "INR (₹)")
            put("availableAccounts", JSONArray(accountNames))
            put("availableCategories", JSONArray(categoryNames))
        }.toString()
    }

    /**
     * Builds a compact, pre-computed statistical summary of the user's finances
     * for "Ask My Data" and "Monthly Insights".
     * NEVER includes raw database tables or full transaction rows.
     */
    fun buildComputedFinancialSummary(
        accounts: List<Account>,
        transactions: List<Transaction>,
        budgets: List<Budget>,
        goals: List<Goal>,
        bills: List<BillReminder>,
        overallToReceive: Double,
        overallToPay: Double,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }

        // Start of Today
        val startOfToday = (cal.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // Start of This Month
        val startOfThisMonthCal = (cal.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfThisMonth = startOfThisMonthCal.timeInMillis

        // Start & End of Last Month
        val startOfLastMonthCal = (startOfThisMonthCal.clone() as Calendar).apply {
            add(Calendar.MONTH, -1)
        }
        val startOfLastMonth = startOfLastMonthCal.timeInMillis
        val endOfLastMonth = startOfThisMonth - 1L

        fun isRealFlow(tx: Transaction): Boolean {
            return tx.category != "Transfer" &&
                tx.category != "Adjustment" &&
                tx.category != "Udhaar" &&
                tx.category != "Investment"
        }

        val realTx = transactions.filter { isRealFlow(it) }
        val thisMonthTx = realTx.filter { it.timestamp in startOfThisMonth..nowMillis }
        val lastMonthTx = realTx.filter { it.timestamp in startOfLastMonth..endOfLastMonth }
        val todayTx = realTx.filter { it.timestamp >= startOfToday }

        val thisMonthIncome = thisMonthTx.filter { it.isIncome }.sumOf { it.amount }
        val thisMonthExpense = thisMonthTx.filter { !it.isIncome }.sumOf { it.amount }
        val thisMonthNet = thisMonthIncome - thisMonthExpense

        val lastMonthIncome = lastMonthTx.filter { it.isIncome }.sumOf { it.amount }
        val lastMonthExpense = lastMonthTx.filter { !it.isIncome }.sumOf { it.amount }
        val lastMonthNet = lastMonthIncome - lastMonthExpense

        val momExpenseChangePct = if (lastMonthExpense > 0.0) {
            ((thisMonthExpense - lastMonthExpense) / lastMonthExpense) * 100.0
        } else 0.0

        val daysElapsedThisMonth = cal.get(Calendar.DAY_OF_MONTH).coerceAtLeast(1)
        val avgDailySpendThisMonth = thisMonthExpense / daysElapsedThisMonth

        // Category breakdown (This Month & Last Month)
        val thisMonthByCategory = thisMonthTx
            .filter { !it.isIncome }
            .groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
            .entries
            .sortedByDescending { it.value }

        val lastMonthByCategory = lastMonthTx
            .filter { !it.isIncome }
            .groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
            .entries
            .sortedByDescending { it.value }

        // Highest single-day spend this month
        val dayFmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val highestDayThisMonth = thisMonthTx
            .filter { !it.isIncome }
            .groupBy { dayFmt.format(Date(it.timestamp)) }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
            .maxByOrNull { it.value }

        // Highest single transaction this month (anonymized to category + amount + date)
        val largestSingleExpenseThisMonth = thisMonthTx
            .filter { !it.isIncome }
            .maxByOrNull { it.amount }

        // Budgets status
        val budgetSummaries = budgets.map { budget ->
            val spent = if (budget.category.equals("Overall", ignoreCase = true) || budget.category == "__OVERALL__") {
                thisMonthExpense
            } else {
                thisMonthByCategory.firstOrNull { it.key.equals(budget.category, ignoreCase = true) }?.value ?: 0.0
            }
            val pct = if (budget.limitAmount > 0) ((spent / budget.limitAmount) * 100).toInt() else 0
            val label = if (budget.category == "__OVERALL__") "Overall" else budget.category
            "$label: ₹${spent.toLong()} / ₹${budget.limitAmount.toLong()} ($pct%)"
        }

        val unpaidBills = bills.filter { !it.isPaid }
        val totalBalance = AccountUtils.calculateTotalBalance(accounts, transactions)
        val cashBalance = AccountUtils.calculateCashBalance(accounts, transactions)
        val bankBalance = AccountUtils.calculateBankBalance(accounts, transactions)

        return JSONObject().apply {
            put("todayDate", todayDateDdMmYyyy(nowMillis))
            put("currency", "INR (₹)")
            put("balances", JSONObject().apply {
                put("totalBalance", totalBalance.toLong())
                put("cashBalance", cashBalance.toLong())
                put("bankAndUpiBalance", bankBalance.toLong())
            })
            put("todaySummary", JSONObject().apply {
                put("todayExpense", todayTx.filter { !it.isIncome }.sumOf { it.amount }.toLong())
                put("todayIncome", todayTx.filter { it.isIncome }.sumOf { it.amount }.toLong())
            })
            put("thisMonthSummary", JSONObject().apply {
                put("totalIncome", thisMonthIncome.toLong())
                put("totalExpense", thisMonthExpense.toLong())
                put("netSavings", thisMonthNet.toLong())
                put("averageDailySpend", avgDailySpendThisMonth.toLong())
                put("highestDaySpend", highestDayThisMonth?.let { "${it.key}: ₹${it.value.toLong()}" } ?: "None")
                put("largestSingleExpense", largestSingleExpenseThisMonth?.let {
                    "${it.category}${if (it.note.isNotBlank()) " (${it.note.take(24)})" else ""}: ₹${it.amount.toLong()} on ${dayFmt.format(Date(it.timestamp))}"
                } ?: "None")
                put("categoryExpenses", JSONObject().apply {
                    thisMonthByCategory.forEach { (cat, amt) -> put(cat, amt.toLong()) }
                })
            })
            put("lastMonthSummary", JSONObject().apply {
                put("totalIncome", lastMonthIncome.toLong())
                put("totalExpense", lastMonthExpense.toLong())
                put("netSavings", lastMonthNet.toLong())
                put("monthOverMonthExpenseChangePercent", String.format(Locale.US, "%.1f%%", momExpenseChangePct))
                put("categoryExpenses", JSONObject().apply {
                    lastMonthByCategory.forEach { (cat, amt) -> put(cat, amt.toLong()) }
                })
            })
            put("budgetsStatus", JSONArray(budgetSummaries))
            put("udhaarSummary", JSONObject().apply {
                put("toReceiveFromOthers", overallToReceive.toLong())
                put("toPayToOthers", overallToPay.toLong())
            })
            put("upcomingBillsSummary", JSONObject().apply {
                put("unpaidCount", unpaidBills.size)
                put("unpaidTotalAmount", unpaidBills.sumOf { it.amount }.toLong())
            })
            put("savingsGoalsSummary", JSONObject().apply {
                put("activeGoalsCount", goals.size)
                put("totalTargetAmount", goals.sumOf { it.targetAmount }.toLong())
                put("totalSavedAmount", goals.sumOf { it.savedAmount }.toLong())
            })
        }.toString(2)
    }
}

// ============================================================================
// 3. Structured JSON Schemas & Validation Helpers
// ============================================================================

object AiSchemasAndValidators {

    val smartEntrySchema: Schema = Schema.obj(
        properties = mapOf(
            "summaryNote" to Schema.string(description = "Brief summary of the parsed entries"),
            "entries" to Schema.array(
                description = "List of parsed transactions, Udhaar entries, tasks, schedule events, habits, or notes",
                items = Schema.obj(
                    properties = mapOf(
                        "entryKind" to Schema.enumeration(
                            values = listOf(
                                "EXPENSE",
                                "INCOME",
                                "TRANSFER",
                                "UDHAAR_GOT",
                                "UDHAAR_GAVE",
                                "TASK",
                                "SCHEDULE",
                                "HABIT",
                                "NOTE"
                            ),
                            description = "Type of entry: EXPENSE, INCOME, TRANSFER, UDHAAR_GOT, UDHAAR_GAVE, TASK, SCHEDULE, HABIT, or NOTE."
                        ),
                        "amount" to Schema.double(description = "Numeric amount in INR (0 if TASK, SCHEDULE, HABIT, or NOTE)"),
                        "account" to Schema.string(description = "Account name such as Cash, Bank, or UPI"),
                        "toAccount" to Schema.string(description = "Destination account if entryKind is TRANSFER, else empty string"),
                        "category" to Schema.string(description = "Category name such as Food, Transport, Shopping, Bills, Salary, Udhaar, Personal, Work, Health, or Note"),
                        "personName" to Schema.string(description = "Person name if money was received from or given to a person, else empty string"),
                        "date" to Schema.string(description = "Date in DD/MM/YYYY format"),
                        "note" to Schema.string(description = "Title or description of the transaction, task, schedule, habit, or note")
                    ),
                    optionalProperties = listOf("toAccount", "personName")
                )
            )
        )
    )

    val receiptScanSchema: Schema = Schema.obj(
        properties = mapOf(
            "amount" to Schema.double(description = "Total final bill/receipt amount in INR"),
            "date" to Schema.string(description = "Receipt date in DD/MM/YYYY format"),
            "shopName" to Schema.string(description = "Name of the merchant, shop, or restaurant"),
            "category" to Schema.string(description = "Best matching expense category (e.g. Food, Groceries, Shopping, Bills, Transport, Health, Other)"),
            "paymentMethod" to Schema.enumeration(
                values = listOf("UPI", "Cash", "Card", "Bank"),
                description = "Detected payment method on receipt, default UPI"
            ),
            "itemsSummary" to Schema.string(description = "Comma-separated summary of main items on the bill"),
            "confidenceNote" to Schema.string(description = "Brief note on extraction clarity or any missing fields")
        )
    )

    val askMyDataSchema: Schema = Schema.obj(
        properties = mapOf(
            "answer" to Schema.string(description = "Clear, direct answer using ONLY the provided computed financial summaries in INR (₹)"),
            "figuresCited" to Schema.array(
                items = Schema.string(),
                description = "Exact figures from the provided summary used to answer the question"
            ),
            "followUpSuggestions" to Schema.array(
                items = Schema.string(),
                description = "2 to 3 short follow-up questions the user can ask next"
            )
        )
    )

    val monthlyInsightsSchema: Schema = Schema.obj(
        properties = mapOf(
            "headlineSummary" to Schema.string(description = "1-2 sentence executive summary of this month's financial performance"),
            "healthScore" to Schema.integer(description = "Financial discipline and savings score from 0 to 100"),
            "spendingPatterns" to Schema.array(
                items = Schema.string(),
                description = "Key spending trends and category comparisons (this month vs last month)"
            ),
            "unusualExpenses" to Schema.array(
                items = Schema.string(),
                description = "Category spikes, highest single-day spend, or unusual outflows noticed in the summary"
            ),
            "savingTips" to Schema.array(
                items = Schema.string(),
                description = "Actionable, specific tips to save more money based on top categories"
            ),
            "budgetWarnings" to Schema.array(
                items = Schema.string(),
                description = "Warnings for budgets near or above 80% / 100%, or upcoming unpaid bills"
            )
        )
    )

    val noteHelperSchema: Schema = Schema.obj(
        properties = mapOf(
            "summaryBullets" to Schema.array(
                items = Schema.string(),
                description = "Concise bullet points summarizing the note"
            ),
            "grammarFixedTitle" to Schema.string(description = "Corrected and polished note title"),
            "grammarFixedContent" to Schema.string(description = "Grammar-corrected and well-structured markdown content"),
            "extractedTasks" to Schema.array(
                description = "Actionable tasks extracted from the note",
                items = Schema.obj(
                    properties = mapOf(
                        "title" to Schema.string(description = "Actionable task title"),
                        "priority" to Schema.enumeration(
                            values = listOf("HIGH", "MEDIUM", "LOW"),
                            description = "Suggested task priority"
                        ),
                        "category" to Schema.string(description = "Task category such as Work, Finance, Personal, Shopping, Health"),
                        "dueDaysFromNow" to Schema.integer(description = "Suggested due date offset in days from today (0 for today, 1 for tomorrow, etc.)")
                    )
                )
            )
        )
    )

    val taskHelperSchema: Schema = Schema.obj(
        properties = mapOf(
            "goalTitle" to Schema.string(description = "Polished main task/goal title"),
            "strategySummary" to Schema.string(description = "Brief 1-2 sentence action plan overview"),
            "priority" to Schema.enumeration(
                values = listOf("HIGH", "MEDIUM", "LOW"),
                description = "Recommended priority level"
            ),
            "category" to Schema.string(description = "Recommended category (Work, Finance, Personal, Study, Health)"),
            "subtasks" to Schema.array(
                items = Schema.string(),
                description = "Ordered step-by-step subtasks to achieve the goal"
            ),
            "scheduleBlocks" to Schema.array(
                description = "Suggested calendar schedule blocks to execute the subtasks",
                items = Schema.obj(
                    properties = mapOf(
                        "title" to Schema.string(description = "Schedule block title"),
                        "dayOffset" to Schema.integer(description = "Days from today (0 = today, 1 = tomorrow, etc.)"),
                        "startHour" to Schema.integer(description = "Start hour in 24h format (e.g. 9 for 9:00 AM, 18 for 6:00 PM)"),
                        "durationMinutes" to Schema.integer(description = "Duration in minutes (e.g. 30, 45, 60, 90)"),
                        "notes" to Schema.string(description = "What to focus on during this block")
                    )
                )
            )
        )
    )

    // ------------------------------------------------------------------------
    // JSON Validation & Parsing Functions
    // ------------------------------------------------------------------------

    fun parseAndValidateSmartEntry(
        jsonString: String,
        availableAccounts: List<String>,
        availableCategories: List<String>,
        nowMillis: Long = System.currentTimeMillis()
    ): SmartEntryResult {
        val root = JSONObject(jsonString.trim())
        val summaryNote = root.optString("summaryNote", "Parsed your text into structured entries.")
        val arr = root.optJSONArray("entries") ?: JSONArray()
        val defaultAcc = availableAccounts.firstOrNull() ?: "Cash"

        val allowedKinds = setOf(
            "EXPENSE", "INCOME", "TRANSFER", "UDHAAR_GOT", "UDHAAR_GAVE",
            "TASK", "SCHEDULE", "HABIT", "NOTE"
        )
        val parsedList = mutableListOf<SmartParsedEntry>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val rawKind = obj.optString("entryKind", "EXPENSE").uppercase(Locale.ROOT)
            val validKind = if (rawKind in allowedKinds) rawKind else "EXPENSE"

            val rawAmt = obj.optDouble("amount", 0.0)
            val validAmt = if (rawAmt.isNaN() || rawAmt.isInfinite() || rawAmt < 0.0) 0.0 else rawAmt
            val needsAmount = validKind in setOf("EXPENSE", "INCOME", "TRANSFER", "UDHAAR_GOT", "UDHAAR_GAVE")

            val rawAcc = obj.optString("account", defaultAcc).trim()
            val matchedAcc = availableAccounts.firstOrNull { it.equals(rawAcc, ignoreCase = true) }
                ?: when {
                    rawAcc.contains("bank", ignoreCase = true) || rawAcc.contains("upi", ignoreCase = true) ->
                        availableAccounts.firstOrNull { it.contains("bank", ignoreCase = true) || it.contains("upi", ignoreCase = true) } ?: "Bank"
                    rawAcc.contains("cash", ignoreCase = true) ->
                        availableAccounts.firstOrNull { it.contains("cash", ignoreCase = true) } ?: "Cash"
                    else -> defaultAcc
                }

            val rawCat = obj.optString("category", "Other").trim().ifBlank { "Other" }
            val matchedCat = availableCategories.firstOrNull { it.equals(rawCat, ignoreCase = true) } ?: rawCat

            val personName = obj.optString("personName", "").trim()
            val toAcc = obj.optString("toAccount", "").trim()
            val note = obj.optString("note", matchedCat).trim().ifBlank { matchedCat }
            val (validDateStr, ts) = AiDataSummaryBuilder.parseDdMmYyyyOrToday(
                obj.optString("date", ""),
                nowMillis
            )

            val warning = when {
                needsAmount && validAmt <= 0.0 -> "Amount is ₹0 — please edit before saving."
                validKind in setOf("UDHAAR_GOT", "UDHAAR_GAVE") && personName.isBlank() ->
                    "Person name missing for Udhaar entry; will save as regular transaction unless edited."
                else -> null
            }

            parsedList.add(
                SmartParsedEntry(
                    entryKind = validKind,
                    amount = validAmt,
                    account = matchedAcc,
                    toAccount = toAcc,
                    category = matchedCat,
                    personName = personName,
                    dateString = validDateStr,
                    timestamp = ts,
                    note = note,
                    selected = !needsAmount || validAmt > 0.0,
                    validationWarning = warning
                )
            )
        }
        return SmartEntryResult(entries = parsedList, summaryNote = summaryNote)
    }

    fun parseAndValidateReceiptScan(
        jsonString: String,
        availableCategories: List<String>,
        nowMillis: Long = System.currentTimeMillis()
    ): ReceiptScanResult {
        val root = JSONObject(jsonString.trim())
        val rawAmt = root.optDouble("amount", 0.0)
        val validAmt = if (rawAmt.isNaN() || rawAmt.isInfinite() || rawAmt < 0.0) 0.0 else rawAmt
        val (validDateStr, ts) = AiDataSummaryBuilder.parseDdMmYyyyOrToday(root.optString("date", ""), nowMillis)
        val shopName = root.optString("shopName", "Receipt Merchant").trim().ifBlank { "Receipt Merchant" }
        val rawCat = root.optString("category", "Shopping").trim().ifBlank { "Shopping" }
        val matchedCat = availableCategories.firstOrNull { it.equals(rawCat, ignoreCase = true) } ?: rawCat
        val paymentMethod = root.optString("paymentMethod", "UPI").trim().ifBlank { "UPI" }
        val itemsSummary = root.optString("itemsSummary", "").trim()
        val confidenceNote = root.optString("confidenceNote", "").trim()
        val warning = if (validAmt <= 0.0) "Could not clearly read total amount — please enter or verify it below." else null

        return ReceiptScanResult(
            amount = validAmt,
            dateString = validDateStr,
            timestamp = ts,
            shopName = shopName,
            category = matchedCat,
            paymentMethod = paymentMethod,
            itemsSummary = itemsSummary,
            confidenceNote = confidenceNote,
            validationWarning = warning
        )
    }

    fun parseAndValidateAskMyData(question: String, jsonString: String): AskMyDataResult {
        val root = JSONObject(jsonString.trim())
        val answer = root.optString("answer", "").trim().ifBlank { "No answer generated." }
        val figures = root.optJSONArray("figuresCited").toStringList()
        val followUps = root.optJSONArray("followUpSuggestions").toStringList()
        return AskMyDataResult(
            question = question,
            answer = answer,
            figuresCited = figures,
            followUpSuggestions = followUps
        )
    }

    fun parseAndValidateMonthlyInsights(jsonString: String): MonthlyInsightsResult {
        val root = JSONObject(jsonString.trim())
        val headline = root.optString("headlineSummary", "Monthly financial overview ready.").trim()
        val score = root.optInt("healthScore", 75).coerceIn(0, 100)
        return MonthlyInsightsResult(
            headlineSummary = headline,
            healthScore = score,
            spendingPatterns = root.optJSONArray("spendingPatterns").toStringList(),
            unusualExpenses = root.optJSONArray("unusualExpenses").toStringList(),
            savingTips = root.optJSONArray("savingTips").toStringList(),
            budgetWarnings = root.optJSONArray("budgetWarnings").toStringList()
        )
    }

    fun parseAndValidateNoteHelper(jsonString: String): NoteHelperResult {
        val root = JSONObject(jsonString.trim())
        val bullets = root.optJSONArray("summaryBullets").toStringList()
        val fixedTitle = root.optString("grammarFixedTitle", "").trim()
        val fixedContent = root.optString("grammarFixedContent", "").trim()
        val tasksArr = root.optJSONArray("extractedTasks") ?: JSONArray()
        val tasks = mutableListOf<AiSuggestedTask>()
        for (i in 0 until tasksArr.length()) {
            val obj = tasksArr.optJSONObject(i) ?: continue
            val tTitle = obj.optString("title", "").trim()
            if (tTitle.isNotBlank()) {
                val prio = obj.optString("priority", "MEDIUM").uppercase(Locale.ROOT)
                    .let { if (it in setOf("HIGH", "MEDIUM", "LOW")) it else "MEDIUM" }
                val cat = obj.optString("category", "Personal").trim().ifBlank { "Personal" }
                val days = obj.optInt("dueDaysFromNow", 1).coerceIn(0, 365)
                tasks.add(AiSuggestedTask(title = tTitle, priority = prio, category = cat, dueDaysFromNow = days))
            }
        }
        return NoteHelperResult(
            summaryBullets = bullets,
            grammarFixedTitle = fixedTitle,
            grammarFixedContent = fixedContent,
            extractedTasks = tasks
        )
    }

    fun parseAndValidateTaskHelper(jsonString: String): TaskHelperResult {
        val root = JSONObject(jsonString.trim())
        val goalTitle = root.optString("goalTitle", "Goal Plan").trim().ifBlank { "Goal Plan" }
        val strategy = root.optString("strategySummary", "").trim()
        val prio = root.optString("priority", "HIGH").uppercase(Locale.ROOT)
            .let { if (it in setOf("HIGH", "MEDIUM", "LOW")) it else "HIGH" }
        val cat = root.optString("category", "Personal").trim().ifBlank { "Personal" }
        val subtasks = root.optJSONArray("subtasks").toStringList().filter { it.isNotBlank() }

        val schedArr = root.optJSONArray("scheduleBlocks") ?: JSONArray()
        val blocks = mutableListOf<AiSuggestedScheduleBlock>()
        for (i in 0 until schedArr.length()) {
            val obj = schedArr.optJSONObject(i) ?: continue
            val bTitle = obj.optString("title", "").trim()
            if (bTitle.isNotBlank()) {
                blocks.add(
                    AiSuggestedScheduleBlock(
                        title = bTitle,
                        dayOffset = obj.optInt("dayOffset", i).coerceIn(0, 90),
                        startHour = obj.optInt("startHour", 10).coerceIn(5, 22),
                        durationMinutes = obj.optInt("durationMinutes", 60).coerceIn(15, 240),
                        notes = obj.optString("notes", "").trim()
                    )
                )
            }
        }
        return TaskHelperResult(
            goalTitle = goalTitle,
            strategySummary = strategy,
            priority = prio,
            category = cat,
            subtasks = subtasks,
            scheduleBlocks = blocks
        )
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until length()) {
            val item = optString(i, "").trim()
            if (item.isNotBlank()) list.add(item)
        }
        return list
    }
}

// ============================================================================
// 4. Gemini AI Service with Lazy Models, 60s Timeouts & Graceful Error Mapping
// ============================================================================

class GeminiAiService(private val context: Context) {

    companion object {
        // Tracks whether cloud AI endpoint succeeded on this device session so if a phone APK
        // lacks App Check debug registration, subsequent requests resolve in <50ms without waiting.
        @Volatile
        private var cloudEndpointResponsive: Boolean = true
    }

    // Lazy model instances per structured output schema (never evaluated at class construction time)
    private val smartEntryModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                temperature = 0.1f
                maxOutputTokens = 512
                responseMimeType = "application/json"
                responseSchema = AiSchemasAndValidators.smartEntrySchema
            }
        )
    }

    private val receiptScanModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                temperature = 0.1f
                maxOutputTokens = 512
                responseMimeType = "application/json"
                responseSchema = AiSchemasAndValidators.receiptScanSchema
            }
        )
    }

    private val askMyDataModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                temperature = 0.2f
                maxOutputTokens = 512
                responseMimeType = "application/json"
                responseSchema = AiSchemasAndValidators.askMyDataSchema
            }
        )
    }

    private val monthlyInsightsModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                temperature = 0.2f
                maxOutputTokens = 600
                responseMimeType = "application/json"
                responseSchema = AiSchemasAndValidators.monthlyInsightsSchema
            }
        )
    }

    private val noteHelperModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                temperature = 0.2f
                maxOutputTokens = 512
                responseMimeType = "application/json"
                responseSchema = AiSchemasAndValidators.noteHelperSchema
            }
        )
    }

    private val taskHelperModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                temperature = 0.2f
                maxOutputTokens = 512
                responseMimeType = "application/json"
                responseSchema = AiSchemasAndValidators.taskHelperSchema
            }
        )
    }

    fun isOnline(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            true
        }
    }

    private fun shouldUseInstantEngine(): Boolean {
        return !isOnline() || !cloudEndpointResponsive
    }

    /**
     * 1. Smart Entry: parses natural-language text like
     * "chai 20 cash, petrol 300 bank, got 5000 from Raj, task pay wifi bill, habit morning walk"
     */
    suspend fun parseSmartEntry(
        userInput: String,
        accounts: List<Account>,
        categories: List<Category>
    ): AiUiState<SmartEntryResult> {
        if (userInput.isBlank()) {
            return AiUiState.Error("Please enter text to add (e.g., \"chai 20 cash, petrol 300 bank, task pay bill\").")
        }

        val minimalContext = AiDataSummaryBuilder.buildSmartEntryMinimalContext(accounts, categories)
        val availAccs = accounts.map { it.name }.ifEmpty { listOf("Cash", "Bank", "UPI") }
        val availCats = (categories.map { it.name } + listOf(
            "Food", "Transport", "Shopping", "Bills", "Entertainment", "Health", "Groceries", "Rent", "Salary", "Other"
        )).distinct()

        if (shouldUseInstantEngine()) {
            val localParsed = LocalAiFallbackEngine.parseSmartEntryLocally(userInput, availAccs, availCats)
            return if (localParsed.entries.isNotEmpty()) {
                AiUiState.Success(localParsed, minimalPayloadPreview = minimalContext)
            } else {
                AiUiState.Error("Could not detect any entries. Try e.g. 'chai 20 cash, task pay bill, habit walk'.")
            }
        }

        val prompt = """
            Parse user input into structured Kutub data entries (INR ₹).
            Context: $minimalContext
            Input: "$userInput"
            Rules:
            - EXPENSE for spending (e.g. "chai 20 cash").
            - INCOME for salary/income (e.g. "salary 50000 bank").
            - TRANSFER for account transfers (e.g. "transfer 2000 cash to bank").
            - UDHAAR_GOT when receiving money from a person ("got 5000 from Raj", personName="Raj").
            - UDHAAR_GAVE when giving/lending money to a person ("gave 1000 to Amit", personName="Amit").
            - TASK for to-do items ("task pay electricity bill", amount=0).
            - SCHEDULE for calendar events/meetings ("schedule meeting at 4pm", amount=0).
            - HABIT for daily/weekly habits ("habit drink water", amount=0).
            - NOTE for general notes ("note locker number 42", amount=0).
        """.trimIndent()

        return try {
            val response = withTimeout(AI_TIMEOUT_MS) {
                smartEntryModel.generateContent(prompt)
            }
            cloudEndpointResponsive = true
            val jsonText = response.text ?: throw IllegalStateException("Model returned empty response.")
            val parsed = AiSchemasAndValidators.parseAndValidateSmartEntry(jsonText, availAccs, availCats)
            if (parsed.entries.isEmpty()) {
                val localParsed = LocalAiFallbackEngine.parseSmartEntryLocally(userInput, availAccs, availCats)
                if (localParsed.entries.isNotEmpty()) {
                    AiUiState.Success(localParsed, minimalPayloadPreview = minimalContext)
                } else {
                    AiUiState.Error("Could not detect any valid entries. Try e.g. 'chai 20 cash, task pay bill'.")
                }
            } else {
                AiUiState.Success(parsed, minimalPayloadPreview = minimalContext)
            }
        } catch (e: CancellationException) {
            if (e is TimeoutCancellationException) {
                cloudEndpointResponsive = false
                val localParsed = LocalAiFallbackEngine.parseSmartEntryLocally(userInput, availAccs, availCats)
                AiUiState.Success(localParsed, minimalPayloadPreview = minimalContext)
            } else {
                throw e
            }
        } catch (e: Exception) {
            cloudEndpointResponsive = false
            Log.w(TAG, "Using instant smart parser fallback", e)
            val localParsed = LocalAiFallbackEngine.parseSmartEntryLocally(userInput, availAccs, availCats)
            AiUiState.Success(localParsed, minimalPayloadPreview = minimalContext)
        }
    }

    /**
     * 2. Receipt / Bill Scan: multimodal image extraction
     */
    suspend fun scanReceiptPhoto(
        imageUri: Uri,
        categories: List<Category>
    ): AiUiState<ReceiptScanResult> {
        val availCats = (categories.map { it.name } + listOf(
            "Food", "Groceries", "Shopping", "Bills", "Transport", "Health", "Entertainment", "Other"
        )).distinct()
        val todayStr = AiDataSummaryBuilder.todayDateDdMmYyyy()

        val bitmap = loadScaledBitmapUnderLimit(imageUri)
            ?: return AiUiState.Error("Could not decode the selected image. Please choose a clear JPEG or PNG photo.")

        if (shouldUseInstantEngine()) {
            val fallback = LocalAiFallbackEngine.buildReceiptFallback(bitmap, todayStr)
            return AiUiState.Success(
                data = fallback,
                minimalPayloadPreview = "Processed receipt image (${bitmap.width}x${bitmap.height})."
            )
        }

        return try {
            val prompt = """
                Extract bill/receipt details (INR ₹). Today is $todayStr.
                Categories: ${availCats.joinToString(", ")}.
            """.trimIndent()

            val response = withTimeout(4_000L) {
                receiptScanModel.generateContent(
                    content {
                        image(bitmap)
                        text(prompt)
                    }
                )
            }
            cloudEndpointResponsive = true
            val jsonText = response.text ?: throw IllegalStateException("Model returned empty response.")
            val result = AiSchemasAndValidators.parseAndValidateReceiptScan(jsonText, availCats)
            AiUiState.Success(
                data = result,
                minimalPayloadPreview = "Uploaded 1 compressed receipt image + category list (${availCats.size} categories)."
            )
        } catch (e: CancellationException) {
            if (e is TimeoutCancellationException) {
                cloudEndpointResponsive = false
                val fallback = LocalAiFallbackEngine.buildReceiptFallback(bitmap, todayStr)
                AiUiState.Success(fallback, minimalPayloadPreview = "Processed receipt image.")
            } else {
                throw e
            }
        } catch (e: Exception) {
            cloudEndpointResponsive = false
            val fallback = LocalAiFallbackEngine.buildReceiptFallback(bitmap, todayStr)
            AiUiState.Success(
                data = fallback,
                minimalPayloadPreview = "Processed receipt image (${bitmap.width}x${bitmap.height})."
            )
        }
    }

    /**
     * 3. Ask My Data: answers user questions strictly from pre-computed summaries
     */
    suspend fun askMyData(
        question: String,
        computedSummaryJson: String
    ): AiUiState<AskMyDataResult> {
        if (question.isBlank()) {
            return AiUiState.Error("Please type a question about your finances.")
        }
        if (shouldUseInstantEngine()) {
            val localAns = LocalAiFallbackEngine.answerDataQuestionLocally(question, computedSummaryJson)
            return AiUiState.Success(localAns, minimalPayloadPreview = computedSummaryJson)
        }

        val prompt = """
            Financial analyst for Kutub (INR ₹). Answer ONLY using the summary JSON below:
            $computedSummaryJson
            Question: "$question"
        """.trimIndent()

        return try {
            val response = withTimeout(AI_TIMEOUT_MS) {
                askMyDataModel.generateContent(prompt)
            }
            cloudEndpointResponsive = true
            val jsonText = response.text ?: throw IllegalStateException("Model returned empty response.")
            val result = AiSchemasAndValidators.parseAndValidateAskMyData(question, jsonText)
            AiUiState.Success(result, minimalPayloadPreview = computedSummaryJson)
        } catch (e: CancellationException) {
            if (e is TimeoutCancellationException) {
                cloudEndpointResponsive = false
                val localAns = LocalAiFallbackEngine.answerDataQuestionLocally(question, computedSummaryJson)
                AiUiState.Success(localAns, minimalPayloadPreview = computedSummaryJson)
            } else {
                throw e
            }
        } catch (e: Exception) {
            cloudEndpointResponsive = false
            val localAns = LocalAiFallbackEngine.answerDataQuestionLocally(question, computedSummaryJson)
            AiUiState.Success(localAns, minimalPayloadPreview = computedSummaryJson)
        }
    }

    /**
     * 4. Monthly Insights: spending patterns, unusual expenses, saving tips, budget warnings
     */
    suspend fun generateMonthlyInsights(
        computedSummaryJson: String
    ): AiUiState<MonthlyInsightsResult> {
        if (shouldUseInstantEngine()) {
            val localInsights = LocalAiFallbackEngine.generateMonthlyInsightsLocally(computedSummaryJson)
            return AiUiState.Success(localInsights, minimalPayloadPreview = computedSummaryJson)
        }

        val prompt = """
            Analyze monthly financial summary (INR ₹) and return headlineSummary, healthScore (0-100), spendingPatterns, unusualExpenses, savingTips, budgetWarnings:
            $computedSummaryJson
        """.trimIndent()

        return try {
            val response = withTimeout(AI_TIMEOUT_MS) {
                monthlyInsightsModel.generateContent(prompt)
            }
            cloudEndpointResponsive = true
            val jsonText = response.text ?: throw IllegalStateException("Model returned empty response.")
            val result = AiSchemasAndValidators.parseAndValidateMonthlyInsights(jsonText)
            AiUiState.Success(result, minimalPayloadPreview = computedSummaryJson)
        } catch (e: CancellationException) {
            if (e is TimeoutCancellationException) {
                cloudEndpointResponsive = false
                val localInsights = LocalAiFallbackEngine.generateMonthlyInsightsLocally(computedSummaryJson)
                AiUiState.Success(localInsights, minimalPayloadPreview = computedSummaryJson)
            } else {
                throw e
            }
        } catch (e: Exception) {
            cloudEndpointResponsive = false
            val localInsights = LocalAiFallbackEngine.generateMonthlyInsightsLocally(computedSummaryJson)
            AiUiState.Success(localInsights, minimalPayloadPreview = computedSummaryJson)
        }
    }

    /**
     * 5. Notes Helper: summarize, fix grammar, and turn a note into actionable tasks
     */
    suspend fun analyzeNote(
        noteTitle: String,
        noteContent: String,
        checklistItems: List<String> = emptyList()
    ): AiUiState<NoteHelperResult> {
        if (noteTitle.isBlank() && noteContent.isBlank() && checklistItems.isEmpty()) {
            return AiUiState.Error("Please write some note text or checklist items first.")
        }

        val minimalNotePayload = JSONObject().apply {
            put("title", noteTitle.take(200))
            put("content", noteContent.take(4000))
            put("checklist", JSONArray(checklistItems.take(30)))
        }.toString(2)

        if (shouldUseInstantEngine()) {
            val localNote = LocalAiFallbackEngine.analyzeNoteLocally(noteTitle, noteContent, checklistItems)
            return AiUiState.Success(localNote, minimalPayloadPreview = minimalNotePayload)
        }

        val prompt = """
            Analyze note and return summaryBullets, grammarFixedTitle, grammarFixedContent, extractedTasks:
            $minimalNotePayload
        """.trimIndent()

        return try {
            val response = withTimeout(AI_TIMEOUT_MS) {
                noteHelperModel.generateContent(prompt)
            }
            cloudEndpointResponsive = true
            val jsonText = response.text ?: throw IllegalStateException("Model returned empty response.")
            val result = AiSchemasAndValidators.parseAndValidateNoteHelper(jsonText)
            AiUiState.Success(result, minimalPayloadPreview = minimalNotePayload)
        } catch (e: CancellationException) {
            if (e is TimeoutCancellationException) {
                cloudEndpointResponsive = false
                val localNote = LocalAiFallbackEngine.analyzeNoteLocally(noteTitle, noteContent, checklistItems)
                AiUiState.Success(localNote, minimalPayloadPreview = minimalNotePayload)
            } else {
                throw e
            }
        } catch (e: Exception) {
            cloudEndpointResponsive = false
            val localNote = LocalAiFallbackEngine.analyzeNoteLocally(noteTitle, noteContent, checklistItems)
            AiUiState.Success(localNote, minimalPayloadPreview = minimalNotePayload)
        }
    }

    /**
     * 6. Task Helper: break a goal into subtasks and a recommended schedule
     */
    suspend fun breakGoalIntoTasksAndSchedule(
        goalPrompt: String
    ): AiUiState<TaskHelperResult> {
        if (goalPrompt.isBlank()) {
            return AiUiState.Error("Please enter a goal to break down into subtasks and a schedule.")
        }

        val todayStr = AiDataSummaryBuilder.todayDateDdMmYyyy()
        val minimalPayload = JSONObject().apply {
            put("todayDate", todayStr)
            put("goal", goalPrompt.take(500))
        }.toString(2)

        if (shouldUseInstantEngine()) {
            val localPlan = LocalAiFallbackEngine.breakGoalLocally(goalPrompt)
            return AiUiState.Success(localPlan, minimalPayloadPreview = minimalPayload)
        }

        val prompt = """
            Break goal into subtasks and scheduleBlocks starting today ($todayStr):
            Goal: "$goalPrompt"
        """.trimIndent()

        return try {
            val response = withTimeout(AI_TIMEOUT_MS) {
                taskHelperModel.generateContent(prompt)
            }
            cloudEndpointResponsive = true
            val jsonText = response.text ?: throw IllegalStateException("Model returned empty response.")
            val result = AiSchemasAndValidators.parseAndValidateTaskHelper(jsonText)
            AiUiState.Success(result, minimalPayloadPreview = minimalPayload)
        } catch (e: CancellationException) {
            if (e is TimeoutCancellationException) {
                cloudEndpointResponsive = false
                val localPlan = LocalAiFallbackEngine.breakGoalLocally(goalPrompt)
                AiUiState.Success(localPlan, minimalPayloadPreview = minimalPayload)
            } else {
                throw e
            }
        } catch (e: Exception) {
            cloudEndpointResponsive = false
            val localPlan = LocalAiFallbackEngine.breakGoalLocally(goalPrompt)
            AiUiState.Success(localPlan, minimalPayloadPreview = minimalPayload)
        }
    }

    private fun loadScaledBitmapUnderLimit(uri: Uri): Bitmap? {
        return try {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }
            val maxDim = 1280
            var sampleSize = 1
            while (boundsOptions.outWidth / sampleSize > maxDim || boundsOptions.outHeight / sampleSize > maxDim) {
                sampleSize *= 2
            }
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode receipt bitmap", e)
            null
        }
    }
}

/**
 * Fast, Resilient Smart Parser & Financial Analyzer for instant data entry across
 * Transactions, Transfers, Udhaar, Tasks, Schedule Events, Habits, and Notes.
 */
object LocalAiFallbackEngine {

    fun parseSmartEntryLocally(
        userInput: String,
        availableAccounts: List<String>,
        availableCategories: List<String>,
        nowMillis: Long = System.currentTimeMillis()
    ): SmartEntryResult {
        val todayStr = AiDataSummaryBuilder.todayDateDdMmYyyy(nowMillis)
        val defaultCash = availableAccounts.firstOrNull { it.contains("cash", ignoreCase = true) }
            ?: availableAccounts.firstOrNull() ?: "Cash"
        val defaultBank = availableAccounts.firstOrNull { it.contains("bank", ignoreCase = true) }
            ?: defaultCash
        val defaultUpi = availableAccounts.firstOrNull { it.contains("upi", ignoreCase = true) }
            ?: defaultBank

        val segments = userInput.split(Regex("[,;\\n]+|\\band\\b", RegexOption.IGNORE_CASE))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val entries = mutableListOf<SmartParsedEntry>()
        val numberRegex = Regex("(\\d+(?:\\.\\d+)?)")

        for (seg in segments) {
            val lower = seg.lowercase(Locale.ROOT).trim()

            // 1. Check explicit non-monetary data entry commands first: HABIT, SCHEDULE, NOTE, TASK
            if (lower.startsWith("habit ") || lower.startsWith("daily ") || lower.contains("habit:")) {
                val habitTitle = seg.replace(Regex("^(habit\\s*:?\\s*|daily\\s+)", RegexOption.IGNORE_CASE), "")
                    .trim()
                    .replaceFirstChar { it.uppercase() }
                    .ifBlank { "Daily Habit" }
                entries.add(
                    SmartParsedEntry(
                        entryKind = "HABIT",
                        amount = 0.0,
                        account = defaultCash,
                        category = "Health",
                        dateString = todayStr,
                        timestamp = nowMillis,
                        note = habitTitle,
                        selected = true
                    )
                )
                continue
            }

            if (lower.startsWith("schedule ") || lower.startsWith("meeting ") || lower.startsWith("event ") ||
                lower.startsWith("appointment ") || lower.contains("schedule:")
            ) {
                val schedTitle = seg.replace(
                    Regex("^(schedule\\s*:?\\s*|event\\s*:?\\s*)", RegexOption.IGNORE_CASE),
                    ""
                ).trim().replaceFirstChar { it.uppercase() }.ifBlank { "Scheduled Event" }
                entries.add(
                    SmartParsedEntry(
                        entryKind = "SCHEDULE",
                        amount = 0.0,
                        account = defaultCash,
                        category = "Schedule",
                        dateString = todayStr,
                        timestamp = nowMillis,
                        note = schedTitle,
                        selected = true
                    )
                )
                continue
            }

            if (lower.startsWith("note ") || lower.startsWith("note:") || lower.startsWith("remember ")) {
                val noteText = seg.replace(
                    Regex("^(note\\s*:?\\s*|remember\\s+)", RegexOption.IGNORE_CASE),
                    ""
                ).trim().replaceFirstChar { it.uppercase() }.ifBlank { seg }
                entries.add(
                    SmartParsedEntry(
                        entryKind = "NOTE",
                        amount = 0.0,
                        account = defaultCash,
                        category = "Note",
                        dateString = todayStr,
                        timestamp = nowMillis,
                        note = noteText,
                        selected = true
                    )
                )
                continue
            }

            if (lower.startsWith("task ") || lower.startsWith("todo ") || lower.startsWith("remind ") ||
                lower.startsWith("reminder ") || lower.contains("task:")
            ) {
                val taskTitle = seg.replace(
                    Regex("^(task\\s*:?\\s*|todo\\s*:?\\s*|remind(?:er)?(?:\\s+me)?(?:\\s+to)?\\s+)", RegexOption.IGNORE_CASE),
                    ""
                ).trim().replaceFirstChar { it.uppercase() }.ifBlank { seg }
                val taskCat = if (lower.contains("bill") || lower.contains("pay") || lower.contains("bank") || lower.contains("tax")) {
                    "Finance"
                } else {
                    "Personal"
                }
                entries.add(
                    SmartParsedEntry(
                        entryKind = "TASK",
                        amount = 0.0,
                        account = defaultCash,
                        category = taskCat,
                        dateString = todayStr,
                        timestamp = nowMillis,
                        note = taskTitle,
                        selected = true
                    )
                )
                continue
            }

            // 2. Check numeric financial entries (Expense, Income, Transfer, Udhaar)
            val match = numberRegex.find(seg)
            val amount = match?.groupValues?.getOrNull(1)?.toDoubleOrNull()

            if (amount == null || amount <= 0.0) {
                // No amount present -> treat as an actionable TASK so user input is never dropped
                val fallbackTitle = seg.replaceFirstChar { it.uppercase() }
                entries.add(
                    SmartParsedEntry(
                        entryKind = "TASK",
                        amount = 0.0,
                        account = defaultCash,
                        category = "Personal",
                        dateString = todayStr,
                        timestamp = nowMillis,
                        note = fallbackTitle,
                        selected = true
                    )
                )
                continue
            }

            val account = when {
                lower.contains("upi") || lower.contains("gpay") || lower.contains("phonepe") || lower.contains("paytm") -> defaultUpi
                lower.contains("bank") || lower.contains("card") || lower.contains("neft") || lower.contains("online") -> defaultBank
                lower.contains("cash") -> defaultCash
                else -> defaultCash
            }

            // Detect Transfer
            if (lower.contains("transfer")) {
                val fromAcc = if (lower.indexOf("bank") in 0 until (lower.indexOf("to").takeIf { it > 0 } ?: lower.length)) {
                    defaultBank
                } else {
                    defaultCash
                }
                val toAcc = if (fromAcc.equals(defaultCash, ignoreCase = true)) defaultBank else defaultCash
                entries.add(
                    SmartParsedEntry(
                        entryKind = "TRANSFER",
                        amount = amount,
                        account = fromAcc,
                        toAccount = toAcc,
                        category = "Transfer",
                        dateString = todayStr,
                        timestamp = nowMillis,
                        note = "Transfer $fromAcc to $toAcc",
                        selected = true
                    )
                )
                continue
            }

            // Detect Udhaar / Income / Expense
            val fromMatch = Regex("(?:got|received|took)\\s+\\d+(?:\\.\\d+)?\\s+from\\s+([A-Za-z]+)", RegexOption.IGNORE_CASE).find(seg)
                ?: Regex("from\\s+([A-Za-z]+)", RegexOption.IGNORE_CASE).find(seg)
            val toMatch = Regex("(?:gave|lent|paid)\\s+\\d+(?:\\.\\d+)?\\s+to\\s+([A-Za-z]+)", RegexOption.IGNORE_CASE).find(seg)
                ?: Regex("to\\s+([A-Za-z]+)", RegexOption.IGNORE_CASE).find(seg)

            val (entryKind, personName, category) = when {
                (lower.contains("got") || lower.contains("received") || lower.contains("took")) && fromMatch != null -> {
                    Triple("UDHAAR_GOT", fromMatch.groupValues[1].replaceFirstChar { it.uppercase() }, "Udhaar")
                }
                (lower.contains("gave") || lower.contains("lent")) && toMatch != null -> {
                    Triple("UDHAAR_GAVE", toMatch.groupValues[1].replaceFirstChar { it.uppercase() }, "Udhaar")
                }
                lower.contains("salary") || lower.contains("income") || lower.contains("bonus") || lower.contains("freelance") || lower.contains("earned") -> {
                    Triple("INCOME", "", "Salary")
                }
                lower.contains("chai") || lower.contains("tea") || lower.contains("coffee") || lower.contains("food") ||
                    lower.contains("lunch") || lower.contains("dinner") || lower.contains("breakfast") || lower.contains("snack") ||
                    lower.contains("zomato") || lower.contains("swiggy") || lower.contains("restaurant") -> {
                    Triple("EXPENSE", "", "Food")
                }
                lower.contains("petrol") || lower.contains("diesel") || lower.contains("fuel") || lower.contains("auto") ||
                    lower.contains("cab") || lower.contains("uber") || lower.contains("ola") || lower.contains("bus") ||
                    lower.contains("train") || lower.contains("metro") -> {
                    Triple("EXPENSE", "", "Transport")
                }
                lower.contains("grocery") || lower.contains("groceries") || lower.contains("milk") || lower.contains("veg") ||
                    lower.contains("fruits") || lower.contains("kirana") -> {
                    Triple("EXPENSE", "", "Groceries")
                }
                lower.contains("bill") || lower.contains("recharge") || lower.contains("wifi") || lower.contains("electricity") ||
                    lower.contains("rent") || lower.contains("water") || lower.contains("gas") -> {
                    Triple("EXPENSE", "", "Bills")
                }
                lower.contains("medicine") || lower.contains("doctor") || lower.contains("hospital") || lower.contains("medical") -> {
                    Triple("EXPENSE", "", "Health")
                }
                lower.contains("shopping") || lower.contains("amazon") || lower.contains("flipkart") || lower.contains("clothes") -> {
                    Triple("EXPENSE", "", "Shopping")
                }
                else -> {
                    val matchedCat = availableCategories.firstOrNull { lower.contains(it.lowercase(Locale.ROOT)) } ?: "Other"
                    Triple("EXPENSE", "", matchedCat)
                }
            }

            val cleanNote = seg.replace(numberRegex, "")
                .replace(Regex("[₹/-]"), " ")
                .replace(Regex("\\b(cash|bank|upi|rs|inr|spent|on|paid)\\b", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\s+"), " ")
                .trim()
                .replaceFirstChar { it.uppercase() }
                .ifBlank { category }

            entries.add(
                SmartParsedEntry(
                    entryKind = entryKind,
                    amount = amount,
                    account = account,
                    category = category,
                    personName = personName,
                    dateString = todayStr,
                    timestamp = nowMillis,
                    note = cleanNote,
                    selected = true
                )
            )
        }

        return SmartEntryResult(
            entries = entries,
            summaryNote = "Parsed ${entries.size} item(s) ready to save."
        )
    }

    fun buildReceiptFallback(bitmap: Bitmap, todayStr: String): ReceiptScanResult {
        return ReceiptScanResult(
            amount = 0.0,
            dateString = todayStr,
            timestamp = System.currentTimeMillis(),
            shopName = "Scanned Bill / Receipt",
            category = "Shopping",
            paymentMethod = "UPI",
            itemsSummary = "Receipt image (${bitmap.width}x${bitmap.height} px) attached",
            confidenceNote = "Receipt photo loaded. Please verify or enter the total bill amount below before saving.",
            validationWarning = "Please enter the total bill amount from the receipt to save."
        )
    }

    fun answerDataQuestionLocally(question: String, computedSummaryJson: String): AskMyDataResult {
        val root = try { JSONObject(computedSummaryJson) } catch (_: Exception) { JSONObject() }
        val balances = root.optJSONObject("balances") ?: JSONObject()
        val todaySum = root.optJSONObject("todaySummary") ?: JSONObject()
        val thisMonth = root.optJSONObject("thisMonthSummary") ?: JSONObject()
        val lastMonth = root.optJSONObject("lastMonthSummary") ?: JSONObject()
        val udhaar = root.optJSONObject("udhaarSummary") ?: JSONObject()
        val bills = root.optJSONObject("upcomingBillsSummary") ?: JSONObject()

        val totalBal = balances.optLong("totalBalance", 0L)
        val cashBal = balances.optLong("cashBalance", 0L)
        val bankBal = balances.optLong("bankAndUpiBalance", 0L)
        val thisMonthExp = thisMonth.optLong("totalExpense", 0L)
        val thisMonthInc = thisMonth.optLong("totalIncome", 0L)
        val thisMonthNet = thisMonth.optLong("netSavings", 0L)
        val lastMonthExp = lastMonth.optLong("totalExpense", 0L)
        val todayExp = todaySum.optLong("todayExpense", 0L)
        val toReceive = udhaar.optLong("toReceiveFromOthers", 0L)
        val toPay = udhaar.optLong("toPayToOthers", 0L)

        val thisCats = thisMonth.optJSONObject("categoryExpenses") ?: JSONObject()
        val lastCats = lastMonth.optJSONObject("categoryExpenses") ?: JSONObject()

        val qLower = question.lowercase(Locale.ROOT)
        val figures = mutableListOf<String>()

        // Check if asking about a specific category
        val allCatKeys = (thisCats.keys().asSequence().toList() + lastCats.keys().asSequence().toList()).distinct()
        val matchedCat = allCatKeys.firstOrNull { qLower.contains(it.lowercase(Locale.ROOT)) }

        val answer = when {
            matchedCat != null && qLower.contains("last month") -> {
                val amt = lastCats.optLong(matchedCat, 0L)
                val thisAmt = thisCats.optLong(matchedCat, 0L)
                figures.add("Last month $matchedCat: ₹$amt")
                figures.add("This month $matchedCat: ₹$thisAmt")
                "Last month you spent ₹$amt on $matchedCat (compared to ₹$thisAmt so far this month)."
            }
            matchedCat != null -> {
                val thisAmt = thisCats.optLong(matchedCat, 0L)
                val lastAmt = lastCats.optLong(matchedCat, 0L)
                figures.add("This month $matchedCat: ₹$thisAmt")
                figures.add("Last month $matchedCat: ₹$lastAmt")
                "This month you have spent ₹$thisAmt on $matchedCat (last month was ₹$lastAmt)."
            }
            qLower.contains("last month") -> {
                figures.add("Last Month Expense: ₹$lastMonthExp")
                figures.add("This Month Expense: ₹$thisMonthExp")
                "Last month your total spending was ₹$lastMonthExp. This month your spending so far is ₹$thisMonthExp."
            }
            qLower.contains("balance") || qLower.contains("cash") || qLower.contains("bank") -> {
                figures.add("Total Balance: ₹$totalBal")
                figures.add("Cash: ₹$cashBal | Bank & UPI: ₹$bankBal")
                "Your total reconciled balance is ₹$totalBal (Cash in Hand: ₹$cashBal, Bank & UPI: ₹$bankBal)."
            }
            qLower.contains("udhaar") || qLower.contains("owe") || qLower.contains("receive") -> {
                figures.add("To Receive: ₹$toReceive")
                figures.add("To Pay: ₹$toPay")
                "In Udhaar (Khata), others owe you ₹$toReceive and you have ₹$toPay to pay."
            }
            qLower.contains("today") -> {
                figures.add("Today's Expense: ₹$todayExp")
                figures.add("This Month Expense: ₹$thisMonthExp")
                "Today you have spent ₹$todayExp. Your total spending this month is ₹$thisMonthExp."
            }
            else -> {
                val topCat = allCatKeys.maxByOrNull { thisCats.optLong(it, 0L) }
                val topCatStr = if (topCat != null) " Top expense category this month is $topCat (₹${thisCats.optLong(topCat, 0L)})." else ""
                figures.add("Total Balance: ₹$totalBal")
                figures.add("This Month Income: ₹$thisMonthInc | Expense: ₹$thisMonthExp")
                figures.add("Net Savings: ₹$thisMonthNet")
                if (bills.optInt("unpaidCount", 0) > 0) {
                    figures.add("Unpaid Bills: ${bills.optInt("unpaidCount", 0)} (₹${bills.optLong("unpaidTotalAmount", 0L)})")
                }
                "This month your income is ₹$thisMonthInc, expenses are ₹$thisMonthExp, and net savings are ₹$thisMonthNet.$topCatStr Your total balance across accounts is ₹$totalBal."
            }
        }

        return AskMyDataResult(
            question = question,
            answer = answer,
            figuresCited = figures,
            followUpSuggestions = listOf(
                "How much did I spend on Food this month?",
                "What is my total Cash vs Bank balance?",
                "Compare this month's spending with last month"
            )
        )
    }

    fun generateMonthlyInsightsLocally(computedSummaryJson: String): MonthlyInsightsResult {
        val root = try { JSONObject(computedSummaryJson) } catch (_: Exception) { JSONObject() }
        val thisMonth = root.optJSONObject("thisMonthSummary") ?: JSONObject()
        val lastMonth = root.optJSONObject("lastMonthSummary") ?: JSONObject()
        val budgets = root.optJSONArray("budgetsStatus") ?: JSONArray()
        val bills = root.optJSONObject("upcomingBillsSummary") ?: JSONObject()

        val inc = thisMonth.optLong("totalIncome", 0L)
        val exp = thisMonth.optLong("totalExpense", 0L)
        val net = thisMonth.optLong("netSavings", 0L)
        val avgDaily = thisMonth.optLong("averageDailySpend", 0L)
        val highestDay = thisMonth.optString("highestDaySpend", "None")
        val largestSingle = thisMonth.optString("largestSingleExpense", "None")
        val momChange = lastMonth.optString("monthOverMonthExpenseChangePercent", "0.0%")

        val savingsRate = if (inc > 0L) ((net.toDouble() / inc.toDouble()) * 100.0).toInt().coerceIn(0, 100) else if (exp == 0L) 80 else 50
        val healthScore = (50 + (savingsRate / 2)).coerceIn(35, 98)

        val catsObj = thisMonth.optJSONObject("categoryExpenses") ?: JSONObject()
        val sortedCats = catsObj.keys().asSequence().map { it to catsObj.optLong(it, 0L) }.sortedByDescending { it.second }.toList()

        val patterns = mutableListOf(
            "This month's total spending is ₹$exp with an average daily spend of ₹$avgDaily (MoM change: $momChange).",
            "Net cash flow this month is ₹$net (Income: ₹$inc vs Expense: ₹$exp)."
        )
        if (sortedCats.isNotEmpty()) {
            val top = sortedCats.take(3).joinToString(", ") { "${it.first} (₹${it.second})" }
            patterns.add("Top spending categories this month: $top.")
        }

        val unusual = mutableListOf<String>()
        if (highestDay != "None") unusual.add("Highest single-day spending: $highestDay.")
        if (largestSingle != "None") unusual.add("Largest individual transaction: $largestSingle.")
        if (unusual.isEmpty()) unusual.add("No unusual spending spikes detected this month.")

        val tips = mutableListOf(
            "Track daily small cash & UPI expenses every evening to maintain your task & habit streak.",
            "Aim to transfer at least 20% of monthly income into your Savings Goals at the start of the month."
        )
        sortedCats.firstOrNull()?.let { (topCat, topAmt) ->
            tips.add("Reducing $topCat (₹$topAmt) by 10% next month can save you ₹${(topAmt * 0.1).toLong()}.")
        }

        val warnings = mutableListOf<String>()
        for (i in 0 until budgets.length()) {
            val bStr = budgets.optString(i, "")
            if (bStr.isNotBlank()) warnings.add("Budget status: $bStr")
        }
        val unpaidCount = bills.optInt("unpaidCount", 0)
        if (unpaidCount > 0) {
            warnings.add("You have $unpaidCount unpaid bill(s) totaling ₹${bills.optLong("unpaidTotalAmount", 0L)}.")
        }
        if (warnings.isEmpty()) {
            warnings.add("All budgets and bills are currently within healthy limits.")
        }

        return MonthlyInsightsResult(
            headlineSummary = "This month you have spent ₹$exp against ₹$inc income (Net: ₹$net, Average: ₹$avgDaily/day).",
            healthScore = healthScore,
            spendingPatterns = patterns,
            unusualExpenses = unusual,
            savingTips = tips,
            budgetWarnings = warnings
        )
    }

    fun analyzeNoteLocally(
        noteTitle: String,
        noteContent: String,
        checklistItems: List<String>
    ): NoteHelperResult {
        val cleanTitle = noteTitle.trim().replaceFirstChar { it.uppercase() }.ifBlank { "Smart Note Summary" }
        val lines = (noteContent.lines() + checklistItems)
            .map { it.trim().removePrefix("-").removePrefix("•").removePrefix("*").trim() }
            .filter { it.isNotBlank() }

        val bullets = if (lines.isNotEmpty()) {
            lines.take(5)
        } else {
            listOf(cleanTitle)
        }

        val tasks = lines.take(4).mapIndexed { idx, line ->
            val lower = line.lowercase(Locale.ROOT)
            val cat = when {
                lower.contains("pay") || lower.contains("bill") || lower.contains("bank") || lower.contains("₹") -> "Finance"
                lower.contains("buy") || lower.contains("shop") -> "Shopping"
                else -> "Personal"
            }
            AiSuggestedTask(
                title = line.take(70).replaceFirstChar { it.uppercase() },
                priority = if (idx == 0) "HIGH" else "MEDIUM",
                category = cat,
                dueDaysFromNow = idx
            )
        }

        val formattedContent = if (lines.isNotEmpty()) {
            lines.joinToString("\n") { "• ${it.replaceFirstChar { c -> c.uppercase() }}" }
        } else {
            noteContent.trim()
        }

        return NoteHelperResult(
            summaryBullets = bullets,
            grammarFixedTitle = cleanTitle,
            grammarFixedContent = formattedContent,
            extractedTasks = tasks
        )
    }

    fun breakGoalLocally(goalPrompt: String): TaskHelperResult {
        val cleanGoal = goalPrompt.trim().replaceFirstChar { it.uppercase() }
        val lower = cleanGoal.lowercase(Locale.ROOT)
        val category = when {
            lower.contains("save") || lower.contains("budget") || lower.contains("debt") || lower.contains("invest") || lower.contains("₹") -> "Finance"
            lower.contains("study") || lower.contains("exam") || lower.contains("learn") -> "Study"
            lower.contains("health") || lower.contains("gym") || lower.contains("workout") -> "Health"
            else -> "Personal"
        }

        val subtasks = listOf(
            "Define clear target and deadline for: $cleanGoal",
            "Break down weekly milestones and required budget/resources",
            "Complete first action step today and log progress in Kutub",
            "Review mid-week progress and adjust daily schedule",
            "Complete final review and mark goal milestone done"
        )

        val blocks = listOf(
            AiSuggestedScheduleBlock(
                title = "Kickoff: $cleanGoal",
                dayOffset = 0,
                startHour = 10,
                durationMinutes = 45,
                notes = "Set up action plan and complete step 1"
            ),
            AiSuggestedScheduleBlock(
                title = "Deep Focus Session: $cleanGoal",
                dayOffset = 1,
                startHour = 18,
                durationMinutes = 60,
                notes = "Work on core subtasks and track progress"
            ),
            AiSuggestedScheduleBlock(
                title = "Weekly Review: $cleanGoal",
                dayOffset = 3,
                startHour = 19,
                durationMinutes = 30,
                notes = "Review milestones and schedule next week"
            )
        )

        return TaskHelperResult(
            goalTitle = cleanGoal.take(60),
            strategySummary = "Structured 5-step execution plan with scheduled focus blocks to achieve '$cleanGoal' consistently.",
            priority = "HIGH",
            category = category,
            subtasks = subtasks,
            scheduleBlocks = blocks
        )
    }
}


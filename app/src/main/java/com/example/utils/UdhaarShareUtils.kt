package com.example.utils

import android.content.Context
import android.content.Intent
import com.example.data.Person
import com.example.data.UdhaarEntry
import com.example.data.UdhaarRepayment

object UdhaarShareUtils {

    fun buildLedgerSummaryText(
        person: Person,
        entries: List<UdhaarEntry>,
        repayments: List<UdhaarRepayment>,
        currentDateStr: String = FormatUtils.formatDate(System.currentTimeMillis())
    ): String {
        val totalGave = entries.filter { it.type == "GAVE" }.sumOf { it.amount }
        val totalTook = entries.filter { it.type == "TOOK" }.sumOf { it.amount }
        val remainingToReceive = entries.filter { it.type == "GAVE" && !it.isSettled }.sumOf { it.remainingAmount }
        val remainingToPay = entries.filter { it.type == "TOOK" && !it.isSettled }.sumOf { it.remainingAmount }
        val netBalance = remainingToReceive - remainingToPay

        val netStatus = when {
            netBalance > 0 -> "🟢 You will receive: ${FormatUtils.formatInr(netBalance)}"
            netBalance < 0 -> "🔴 You need to pay: ${FormatUtils.formatInr(kotlin.math.abs(netBalance))}"
            else -> "✅ All Settled (₹0 pending)"
        }

        val sb = StringBuilder()
        sb.appendLine("🤝 Khata / Udhaar Summary")
        sb.appendLine("👤 Person: ${person.name}")
        if (person.phone.isNotBlank()) {
            sb.appendLine("📱 Phone: ${person.phone}")
        }
        sb.appendLine("📅 As of: $currentDateStr")
        sb.appendLine("----------------------------------")
        sb.appendLine("Total Given (Lent): ${FormatUtils.formatInr(totalGave)}")
        sb.appendLine("Total Taken (Borrowed): ${FormatUtils.formatInr(totalTook)}")
        sb.appendLine("To Receive: ${FormatUtils.formatInr(remainingToReceive)}")
        sb.appendLine("To Pay: ${FormatUtils.formatInr(remainingToPay)}")
        sb.appendLine("----------------------------------")
        sb.appendLine("⭐ Net Balance: $netStatus")
        sb.appendLine("----------------------------------")

        if (entries.isNotEmpty()) {
            sb.appendLine("\n📋 Detailed Entries:")
            entries.sortedByDescending { it.date }.forEach { entry ->
                val typeLabel = if (entry.type == "GAVE") "I Gave (Lent)" else "I Took (Borrowed)"
                val dateStr = FormatUtils.formatDate(entry.date)
                val statusStr = if (entry.isSettled) "[SETTLED]" else "[PENDING ${FormatUtils.formatInr(entry.remainingAmount)}]"
                sb.appendLine("• $dateStr: $typeLabel ${FormatUtils.formatInr(entry.amount)} via ${entry.account} $statusStr")
                if (entry.note.isNotBlank()) {
                    sb.appendLine("  Note: ${entry.note}")
                }
                if (entry.dueDate != null && !entry.isSettled) {
                    val dueStatus = FormatUtils.calculateDueStatus(entry.dueDate)
                    val dueText = when (dueStatus) {
                        is com.example.data.DueStatus.Overdue -> "⚠️ OVERDUE by ${dueStatus.days} days"
                        com.example.data.DueStatus.DueToday -> "⚠️ DUE TODAY"
                        is com.example.data.DueStatus.DueSoon -> "Due in ${dueStatus.days} days"
                        is com.example.data.DueStatus.DueLater -> "Due: ${dueStatus.dateString}"
                        com.example.data.DueStatus.None -> ""
                    }
                    if (dueText.isNotBlank()) {
                        sb.appendLine("  Due Date: $dueText")
                    }
                }

                // Linked repayments
                val entryRepayments = repayments.filter { it.entryId == entry.id }.sortedBy { it.date }
                if (entryRepayments.isNotEmpty()) {
                    entryRepayments.forEach { rep ->
                        val repDate = FormatUtils.formatDate(rep.date)
                        sb.appendLine("  ↳ Repayment: ${FormatUtils.formatInr(rep.amount)} on $repDate via ${rep.account}" + if (rep.note.isNotBlank()) " (${rep.note})" else "")
                    }
                }
            }
        }

        sb.appendLine("\nGenerated via MoneyMate")
        return sb.toString().trim()
    }

    fun shareSummary(context: Context, person: Person, entries: List<UdhaarEntry>, repayments: List<UdhaarRepayment>) {
        val summaryText = buildLedgerSummaryText(person, entries, repayments)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Udhaar Summary - ${person.name}")
            putExtra(Intent.EXTRA_TEXT, summaryText)
        }
        val chooser = Intent.createChooser(sendIntent, "Share Ledger with ${person.name}")
        context.startActivity(chooser)
    }
}

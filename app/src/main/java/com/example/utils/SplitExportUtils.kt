package com.example.utils

import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.data.SplitExpense
import com.example.data.SplitGroupSummary
import com.example.data.SplitSettlement
import java.io.File
import java.io.FileOutputStream

object SplitExportUtils {

    fun shareGroupWhatsAppText(
        context: Context,
        summary: SplitGroupSummary,
        expenses: List<SplitExpense>,
        settlements: List<SplitSettlement>
    ) {
        val text = SplitUtils.buildWhatsAppGroupSummary(summary, expenses, settlements)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Split Bills Summary - ${summary.group.name}")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(sendIntent, "Share Group Summary (${summary.group.name})")
        context.startActivity(chooser)
    }

    fun exportSplitGroupCsv(
        context: Context,
        summary: SplitGroupSummary,
        expenses: List<SplitExpense>,
        settlements: List<SplitSettlement>
    ): File {
        val safeName = summary.group.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val fileName = "MoneyMate_Split_${safeName}_${System.currentTimeMillis()}.csv"
        val file = File(context.cacheDir, fileName)

        file.bufferedWriter().use { writer ->
            writer.write("\uFEFF")
            writer.write("MoneyMate - Split Bills Group Report\n")
            writer.write("Group Name,${escapeCsv(summary.group.name)}\n")
            writer.write("Group Type,${escapeCsv(summary.group.type)}\n")
            writer.write("Generated On,${FormatUtils.formatDateTime(System.currentTimeMillis())}\n")
            writer.write("Total Group Spend (INR),${summary.totalGroupSpend}\n")
            writer.write("My Total Paid (INR),${summary.myTotalPaid}\n")
            writer.write("My Total Share (INR),${summary.myTotalShare}\n\n")

            writer.write("--- MEMBER BALANCES ---\n")
            writer.write("Member,Total Paid (INR),Total Share (INR),Net Balance (INR),Status\n")
            summary.memberBalances.forEach { mb ->
                val status = when {
                    mb.netBalance > 0.01 -> "Gets Back"
                    mb.netBalance < -0.01 -> "Owes"
                    else -> "Settled Up"
                }
                writer.write("${escapeCsv(mb.memberName)},${mb.totalPaid},${mb.totalShare},${mb.netBalance},$status\n")
            }

            writer.write("\n--- SIMPLIFIED DEBTS (WHO PAYS WHOM) ---\n")
            writer.write("From Member,To Member,Amount (INR)\n")
            if (summary.simplifiedDebts.isEmpty()) {
                writer.write("All Settled,None,0\n")
            } else {
                summary.simplifiedDebts.forEach { debt ->
                    writer.write("${escapeCsv(debt.fromMember)},${escapeCsv(debt.toMember)},${debt.amount}\n")
                }
            }

            writer.write("\n--- SHARED EXPENSES ---\n")
            writer.write("Date,Title,Category,Total Amount (INR),Paid By,Split Mode,My Share (INR),Split Breakdown\n")
            expenses.sortedByDescending { it.timestamp }.forEach { exp ->
                val dateStr = FormatUtils.formatDate(exp.timestamp)
                val breakdown = SplitUtils.parseSplits(exp.splitsJson)
                    .joinToString("; ") { "${it.memberName}: ₹${it.amount}" }
                writer.write(
                    "$dateStr,${escapeCsv(exp.title)},${escapeCsv(exp.category)},${exp.totalAmount}," +
                            "${escapeCsv(exp.paidBy)},${exp.splitType},${exp.myShareAmount},${escapeCsv(breakdown)}\n"
                )
            }

            if (settlements.isNotEmpty()) {
                writer.write("\n--- SETTLEMENT PAYMENTS ---\n")
                writer.write("Date,From Member,To Member,Amount (INR),Account,Note\n")
                settlements.sortedByDescending { it.timestamp }.forEach { st ->
                    val dateStr = FormatUtils.formatDate(st.timestamp)
                    writer.write(
                        "$dateStr,${escapeCsv(st.fromMember)},${escapeCsv(st.toMember)},${st.amount}," +
                                "${escapeCsv(st.account)},${escapeCsv(st.note)}\n"
                    )
                }
            }
        }
        return file
    }

    fun exportSplitGroupPdf(
        context: Context,
        summary: SplitGroupSummary,
        expenses: List<SplitExpense>,
        settlements: List<SplitSettlement>
    ): File {
        val safeName = summary.group.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val fileName = "MoneyMate_Split_${safeName}_${System.currentTimeMillis()}.pdf"
        val file = File(context.cacheDir, fileName)

        val doc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        var pageNum = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
        var page = doc.startPage(pageInfo)
        var canvas = page.canvas

        val titlePaint = Paint().apply {
            color = AndroidColor.parseColor("#1565C0")
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val sectionPaint = Paint().apply {
            color = AndroidColor.parseColor("#1E293B")
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val textPaint = Paint().apply {
            color = AndroidColor.parseColor("#334155")
            textSize = 10f
            isAntiAlias = true
        }
        val boldPaint = Paint().apply {
            color = AndroidColor.parseColor("#0F172A")
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val greenPaint = Paint().apply {
            color = AndroidColor.parseColor("#2E7D32")
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val redPaint = Paint().apply {
            color = AndroidColor.parseColor("#C62828")
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val linePaint = Paint().apply {
            color = AndroidColor.parseColor("#CBD5E1")
            strokeWidth = 1f
        }

        var y = 45f
        canvas.drawText("MoneyMate - Split Bills Group Report", 40f, y, titlePaint)
        y += 20f
        canvas.drawText(
            "Group: ${summary.group.name} (${summary.group.type})  •  Date: ${FormatUtils.formatDate(System.currentTimeMillis())}",
            40f,
            y,
            textPaint
        )
        y += 16f
        canvas.drawText(
            "Total Group Spend: ${FormatUtils.formatInr(summary.totalGroupSpend)}   |   My Paid: ${FormatUtils.formatInr(summary.myTotalPaid)}   |   My Share: ${FormatUtils.formatInr(summary.myTotalShare)}",
            40f,
            y,
            boldPaint
        )
        y += 14f
        canvas.drawLine(40f, y, (pageWidth - 40).toFloat(), y, linePaint)
        y += 22f

        // Member Balances
        canvas.drawText("Per-Person Balances", 40f, y, sectionPaint)
        y += 18f
        canvas.drawText("Member", 45f, y, boldPaint)
        canvas.drawText("Total Paid", 190f, y, boldPaint)
        canvas.drawText("Total Share", 300f, y, boldPaint)
        canvas.drawText("Net Balance", 415f, y, boldPaint)
        y += 8f
        canvas.drawLine(40f, y, (pageWidth - 40).toFloat(), y, linePaint)
        y += 14f

        summary.memberBalances.forEach { mb ->
            canvas.drawText(mb.memberName, 45f, y, boldPaint)
            canvas.drawText(FormatUtils.formatInr(mb.totalPaid), 190f, y, textPaint)
            canvas.drawText(FormatUtils.formatInr(mb.totalShare), 300f, y, textPaint)
            val netLabel = when {
                mb.netBalance > 0.01 -> "+${FormatUtils.formatInr(mb.netBalance)} (Gets back)"
                mb.netBalance < -0.01 -> "-${FormatUtils.formatInr(-mb.netBalance)} (Owes)"
                else -> "Settled up"
            }
            val netPaint = when {
                mb.netBalance > 0.01 -> greenPaint
                mb.netBalance < -0.01 -> redPaint
                else -> textPaint
            }
            canvas.drawText(netLabel, 415f, y, netPaint)
            y += 16f
        }

        y += 10f
        canvas.drawText("Simplified Debts (Fewest Payments)", 40f, y, sectionPaint)
        y += 16f
        if (summary.simplifiedDebts.isEmpty()) {
            canvas.drawText("All members are settled up!", 45f, y, greenPaint)
            y += 16f
        } else {
            summary.simplifiedDebts.forEach { debt ->
                canvas.drawText(
                    "• ${debt.fromMember}  -->  pays ${debt.toMember} :  ${FormatUtils.formatInr(debt.amount)}",
                    45f,
                    y,
                    boldPaint
                )
                y += 16f
            }
        }

        y += 10f
        canvas.drawLine(40f, y, (pageWidth - 40).toFloat(), y, linePaint)
        y += 20f
        canvas.drawText("Shared Expenses (${expenses.size})", 40f, y, sectionPaint)
        y += 18f
        canvas.drawText("Date", 45f, y, boldPaint)
        canvas.drawText("Title", 115f, y, boldPaint)
        canvas.drawText("Paid By", 275f, y, boldPaint)
        canvas.drawText("Mode", 355f, y, boldPaint)
        canvas.drawText("Total", 425f, y, boldPaint)
        canvas.drawText("My Share", 495f, y, boldPaint)
        y += 8f
        canvas.drawLine(40f, y, (pageWidth - 40).toFloat(), y, linePaint)
        y += 14f

        expenses.sortedByDescending { it.timestamp }.forEach { exp ->
            if (y > pageHeight - 60) {
                doc.finishPage(page)
                pageNum++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
                page = doc.startPage(pageInfo)
                canvas = page.canvas
                y = 45f
            }
            canvas.drawText(FormatUtils.formatDate(exp.timestamp), 45f, y, textPaint)
            val titleTrunc = if (exp.title.length > 24) exp.title.take(22) + ".." else exp.title
            canvas.drawText(titleTrunc, 115f, y, boldPaint)
            canvas.drawText(exp.paidBy, 275f, y, textPaint)
            canvas.drawText(exp.splitType, 355f, y, textPaint)
            canvas.drawText(FormatUtils.formatInr(exp.totalAmount), 425f, y, boldPaint)
            canvas.drawText(FormatUtils.formatInr(exp.myShareAmount), 495f, y, textPaint)
            y += 16f
        }

        doc.finishPage(page)
        FileOutputStream(file).use { out ->
            doc.writeTo(out)
        }
        doc.close()
        return file
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else value
    }
}

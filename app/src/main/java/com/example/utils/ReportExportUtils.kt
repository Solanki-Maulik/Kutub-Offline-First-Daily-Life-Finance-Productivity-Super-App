package com.example.utils

import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import com.example.data.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object ReportExportUtils {

    // --- CSV EXPORT ---

    fun exportTransactionsCsv(
        context: Context,
        transactions: List<Transaction>,
        dateRangeLabel: String
    ): File {
        val fileName = "MoneyMate_Transactions_${System.currentTimeMillis()}.csv"
        val file = File(context.cacheDir, fileName)

        file.bufferedWriter().use { writer ->
            // UTF-8 BOM so Excel opens with correct encoding and Rupee symbol
            writer.write("\uFEFF")
            writer.write("MoneyMate Financial Report - $dateRangeLabel\n")
            writer.write("Generated on: ${FormatUtils.formatDateTime(System.currentTimeMillis())}\n\n")

            // Header row
            writer.write("Date,Time,Type,Category,Subcategory,Account,Payment Method,Amount (INR),Note\n")

            transactions.forEach { tx ->
                val type = if (tx.category == "Transfer") "Transfer" else if (tx.isIncome) "Income" else "Expense"
                val date = FormatUtils.formatDate(tx.timestamp)
                val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(tx.timestamp))
                val cat = escapeCsv(tx.category)
                val subcat = escapeCsv(tx.subcategory)
                val acc = escapeCsv(tx.account)
                val payMethod = escapeCsv(tx.paymentMethod)
                val note = escapeCsv(tx.note)
                writer.write("$date,$time,$type,$cat,$subcat,$acc,$payMethod,${tx.amount},$note\n")
            }
        }
        return file
    }

    fun exportUdhaarCsv(
        context: Context,
        people: List<Person>,
        entries: List<UdhaarEntry>,
        repayments: List<UdhaarRepayment>
    ): File {
        val fileName = "MoneyMate_Udhaar_Ledger_${System.currentTimeMillis()}.csv"
        val file = File(context.cacheDir, fileName)

        file.bufferedWriter().use { writer ->
            writer.write("\uFEFF")
            writer.write("MoneyMate People & Udhaar (Khata) Ledger Report\n")
            writer.write("Generated on: ${FormatUtils.formatDateTime(System.currentTimeMillis())}\n\n")

            writer.write("Person Name,Phone,Entry Type,Original Amount,Remaining Amount,Account,Date,Due Date,Status,Note,Repayments Details\n")

            val personMap = people.associateBy { it.id }
            entries.sortedByDescending { it.date }.forEach { entry ->
                val person = personMap[entry.personId]
                val pName = escapeCsv(person?.name ?: "Unknown")
                val phone = escapeCsv(person?.phone ?: "")
                val type = if (entry.type == "GAVE") "I Gave (Lent)" else "I Took (Borrowed)"
                val date = FormatUtils.formatDate(entry.date)
                val dueDate = if (entry.dueDate != null) FormatUtils.formatDate(entry.dueDate) else "None"
                val status = if (entry.isSettled) "Settled" else "Pending"
                val note = escapeCsv(entry.note)

                val entryRepayments = repayments.filter { it.entryId == entry.id }
                val repaymentsSummary = entryRepayments.joinToString("; ") {
                    "₹${it.amount} on ${FormatUtils.formatDate(it.date)} (${it.account})"
                }

                writer.write("$pName,$phone,$type,${entry.amount},${entry.remainingAmount},${escapeCsv(entry.account)},$date,$dueDate,$status,$note,${escapeCsv(repaymentsSummary)}\n")
            }
        }
        return file
    }

    // --- EXCEL (.XLS / SPREADSHEET XML) EXPORT ---

    fun exportTransactionsExcel(
        context: Context,
        transactions: List<Transaction>,
        summary: ReportSummary,
        dateRangeLabel: String
    ): File {
        val fileName = "MoneyMate_Report_${System.currentTimeMillis()}.xls"
        val file = File(context.cacheDir, fileName)

        file.bufferedWriter().use { writer ->
            writer.write("""<?xml version="1.0"?>
<?mso-application progid="Excel.Sheet"?>
<Workbook xmlns="urn:schemas-microsoft-com:office:spreadsheet"
 xmlns:o="urn:schemas-microsoft-com:office:office"
 xmlns:x="urn:schemas-microsoft-com:office:excel"
 xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet">
 <Styles>
  <Style ss:ID="Header">
   <Font ss:Bold="1" ss:Color="#FFFFFF"/>
   <Interior ss:Color="#1565C0" ss:Pattern="Solid"/>
  </Style>
  <Style ss:ID="Title">
   <Font ss:Bold="1" ss:Size="14" ss:Color="#1565C0"/>
  </Style>
  <Style ss:ID="SubTitle">
   <Font ss:Italic="1" ss:Size="10" ss:Color="#555555"/>
  </Style>
  <Style ss:ID="SummaryLabel">
   <Font ss:Bold="1" ss:Color="#333333"/>
  </Style>
  <Style ss:ID="Positive">
   <Font ss:Bold="1" ss:Color="#2E7D32"/>
  </Style>
  <Style ss:ID="Negative">
   <Font ss:Bold="1" ss:Color="#C62828"/>
  </Style>
 </Styles>
 <Worksheet ss:Name="Financial Report">
  <Table>
   <Row><Cell ss:StyleID="Title"><Data ss:Type="String">MoneyMate - Financial Report ($dateRangeLabel)</Data></Cell></Row>
   <Row><Cell ss:StyleID="SubTitle"><Data ss:Type="String">Generated on: ${FormatUtils.formatDateTime(System.currentTimeMillis())}</Data></Cell></Row>
   <Row></Row>
   <Row>
    <Cell ss:StyleID="SummaryLabel"><Data ss:Type="String">Total Income:</Data></Cell>
    <Cell ss:StyleID="Positive"><Data ss:Type="Number">${summary.totalIncome}</Data></Cell>
    <Cell ss:StyleID="SummaryLabel"><Data ss:Type="String">Total Expense:</Data></Cell>
    <Cell ss:StyleID="Negative"><Data ss:Type="Number">${summary.totalExpense}</Data></Cell>
   </Row>
   <Row>
    <Cell ss:StyleID="SummaryLabel"><Data ss:Type="String">Net Savings:</Data></Cell>
    <Cell ss:StyleID="${if (summary.netSavings >= 0) "Positive" else "Negative"}"><Data ss:Type="Number">${summary.netSavings}</Data></Cell>
    <Cell ss:StyleID="SummaryLabel"><Data ss:Type="String">Savings %:</Data></Cell>
    <Cell><Data ss:Type="String">${summary.savingsPercent}%</Data></Cell>
   </Row>
   <Row>
    <Cell ss:StyleID="SummaryLabel"><Data ss:Type="String">Daily Average Spend:</Data></Cell>
    <Cell><Data ss:Type="Number">${summary.avgDailySpend}</Data></Cell>
    <Cell ss:StyleID="SummaryLabel"><Data ss:Type="String">Highest Expense Day:</Data></Cell>
    <Cell><Data ss:Type="String">${summary.highestExpenseDay} (${summary.highestExpenseDayAmount})</Data></Cell>
   </Row>
   <Row></Row>
   <Row ss:StyleID="Header">
    <Cell><Data ss:Type="String">Date</Data></Cell>
    <Cell><Data ss:Type="String">Type</Data></Cell>
    <Cell><Data ss:Type="String">Category</Data></Cell>
    <Cell><Data ss:Type="String">Subcategory</Data></Cell>
    <Cell><Data ss:Type="String">Account</Data></Cell>
    <Cell><Data ss:Type="String">Payment Method</Data></Cell>
    <Cell><Data ss:Type="String">Amount (INR)</Data></Cell>
    <Cell><Data ss:Type="String">Note</Data></Cell>
   </Row>
""")

            transactions.forEach { tx ->
                val type = if (tx.category == "Transfer") "Transfer" else if (tx.isIncome) "Income" else "Expense"
                val date = FormatUtils.formatDate(tx.timestamp)
                writer.write("""   <Row>
    <Cell><Data ss:Type="String">$date</Data></Cell>
    <Cell><Data ss:Type="String">$type</Data></Cell>
    <Cell><Data ss:Type="String">${escapeXml(tx.category)}</Data></Cell>
    <Cell><Data ss:Type="String">${escapeXml(tx.subcategory)}</Data></Cell>
    <Cell><Data ss:Type="String">${escapeXml(tx.account)}</Data></Cell>
    <Cell><Data ss:Type="String">${escapeXml(tx.paymentMethod)}</Data></Cell>
    <Cell><Data ss:Type="Number">${tx.amount}</Data></Cell>
    <Cell><Data ss:Type="String">${escapeXml(tx.note)}</Data></Cell>
   </Row>
""")
            }

            writer.write("""  </Table>
 </Worksheet>
</Workbook>""")
        }
        return file
    }

    // --- PDF REPORT (NATIVE ANDROID PdfDocument) ---

    fun exportTransactionsPdf(
        context: Context,
        transactions: List<Transaction>,
        summary: ReportSummary,
        topCategories: List<ReportUtils.TopCategoryStat>,
        dateRangeLabel: String
    ): File {
        val fileName = "MoneyMate_Report_${System.currentTimeMillis()}.pdf"
        val file = File(context.cacheDir, fileName)

        val doc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842

        val titlePaint = Paint().apply {
            color = AndroidColor.rgb(21, 101, 192) // Royal Blue
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val subtitlePaint = Paint().apply {
            color = AndroidColor.rgb(100, 116, 139)
            textSize = 9f
            isAntiAlias = true
        }

        val sectionPaint = Paint().apply {
            color = AndroidColor.rgb(30, 41, 59)
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val headerPaint = Paint().apply {
            color = AndroidColor.rgb(241, 245, 249)
            style = Paint.Style.FILL
        }

        val textPaint = Paint().apply {
            color = AndroidColor.rgb(30, 41, 59)
            textSize = 8.5f
            isAntiAlias = true
        }

        val boldTextPaint = Paint().apply {
            color = AndroidColor.rgb(15, 23, 42)
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val greenPaint = Paint().apply {
            color = AndroidColor.rgb(46, 125, 50)
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val redPaint = Paint().apply {
            color = AndroidColor.rgb(198, 40, 40)
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val borderPaint = Paint().apply {
            color = AndroidColor.rgb(226, 232, 240)
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }

        var pageNum = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
        var page = doc.startPage(pageInfo)
        var canvas = page.canvas

        // Header
        canvas.drawText("MoneyMate", 40f, 45f, titlePaint)
        canvas.drawText("Comprehensive Financial Summary & Expense Report", 40f, 60f, sectionPaint)
        canvas.drawText("Period: $dateRangeLabel  •  Generated on: ${FormatUtils.formatDateTime(System.currentTimeMillis())}", 40f, 74f, subtitlePaint)
        canvas.drawLine(40f, 85f, (pageWidth - 40).toFloat(), 85f, borderPaint)

        // Summary Boxes
        var y = 105f
        val boxWidth = 120f
        val boxHeight = 44f

        fun drawSummaryBox(x: Float, label: String, value: String, isGreen: Boolean? = null) {
            val bgPaint = Paint().apply {
                color = when (isGreen) {
                    true -> AndroidColor.rgb(232, 245, 233)
                    false -> AndroidColor.rgb(255, 235, 238)
                    else -> AndroidColor.rgb(241, 245, 249)
                }
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(x, y, x + boxWidth, y + boxHeight, 6f, 6f, bgPaint)
            canvas.drawRoundRect(x, y, x + boxWidth, y + boxHeight, 6f, 6f, borderPaint)
            canvas.drawText(label, x + 8f, y + 16f, subtitlePaint)
            val vPaint = when (isGreen) {
                true -> greenPaint
                false -> redPaint
                else -> sectionPaint
            }
            canvas.drawText(value, x + 8f, y + 34f, vPaint)
        }

        drawSummaryBox(40f, "Total Income", FormatUtils.formatInr(summary.totalIncome), true)
        drawSummaryBox(170f, "Total Expense", FormatUtils.formatInr(summary.totalExpense), false)
        drawSummaryBox(300f, "Net Savings (${summary.savingsPercent}%)", FormatUtils.formatInr(summary.netSavings), summary.netSavings >= 0)
        drawSummaryBox(430f, "Daily Avg Spend", FormatUtils.formatInr(summary.avgDailySpend), null)

        y += 65f

        // Top Categories Section
        if (topCategories.isNotEmpty()) {
            canvas.drawText("Top Expense Categories", 40f, y, sectionPaint)
            y += 12f
            canvas.drawRect(40f, y, (pageWidth - 40).toFloat(), y + 18f, headerPaint)
            canvas.drawText("Category", 48f, y + 12f, boldTextPaint)
            canvas.drawText("Amount", 240f, y + 12f, boldTextPaint)
            canvas.drawText("Transactions", 360f, y + 12f, boldTextPaint)
            canvas.drawText("Share (%)", 460f, y + 12f, boldTextPaint)
            y += 18f

            topCategories.forEach { cat ->
                canvas.drawLine(40f, y, (pageWidth - 40).toFloat(), y, borderPaint)
                canvas.drawText(cat.category, 48f, y + 12f, textPaint)
                canvas.drawText(FormatUtils.formatInr(cat.totalAmount), 240f, y + 12f, textPaint)
                canvas.drawText("${cat.transactionCount}", 360f, y + 12f, textPaint)
                canvas.drawText(String.format(Locale.US, "%.1f%%", cat.percentageOfTotal), 460f, y + 12f, textPaint)
                y += 16f
            }
            y += 15f
        }

        // Transactions Table
        canvas.drawText("Detailed Transactions (${transactions.size} records)", 40f, y, sectionPaint)
        y += 12f

        fun drawTableHeader() {
            canvas.drawRect(40f, y, (pageWidth - 40).toFloat(), y + 18f, headerPaint)
            canvas.drawText("Date", 48f, y + 12f, boldTextPaint)
            canvas.drawText("Type", 115f, y + 12f, boldTextPaint)
            canvas.drawText("Category", 170f, y + 12f, boldTextPaint)
            canvas.drawText("Account", 270f, y + 12f, boldTextPaint)
            canvas.drawText("Amount", 370f, y + 12f, boldTextPaint)
            canvas.drawText("Note / Subcategory", 450f, y + 12f, boldTextPaint)
            y += 18f
        }

        drawTableHeader()

        transactions.forEach { tx ->
            if (y > pageHeight - 50) {
                // Footer
                canvas.drawText("Page $pageNum  •  MoneyMate", (pageWidth / 2 - 30).toFloat(), pageHeight - 20f, subtitlePaint)
                doc.finishPage(page)

                pageNum++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
                page = doc.startPage(pageInfo)
                canvas = page.canvas

                y = 40f
                canvas.drawText("MoneyMate - Transactions (Continued)", 40f, y, subtitlePaint)
                y += 15f
                drawTableHeader()
            }

            canvas.drawLine(40f, y, (pageWidth - 40).toFloat(), y, borderPaint)
            val dateStr = FormatUtils.formatDate(tx.timestamp)
            val typeStr = if (tx.category == "Transfer") "Transfer" else if (tx.isIncome) "Income" else "Expense"
            val amtColor = if (tx.isIncome) greenPaint else if (tx.category == "Transfer") textPaint else redPaint
            val sign = if (tx.category == "Transfer") "" else if (tx.isIncome) "+ " else "- "

            canvas.drawText(dateStr, 48f, y + 12f, textPaint)
            canvas.drawText(typeStr, 115f, y + 12f, textPaint)
            canvas.drawText(truncate(tx.category, 16), 170f, y + 12f, textPaint)
            canvas.drawText(truncate(tx.account, 16), 270f, y + 12f, textPaint)
            canvas.drawText("$sign${FormatUtils.formatInr(tx.amount)}", 370f, y + 12f, amtColor)
            val noteCombined = if (tx.subcategory.isNotBlank()) "${tx.subcategory} • ${tx.note}" else tx.note
            canvas.drawText(truncate(noteCombined, 24), 450f, y + 12f, textPaint)

            y += 16f
        }

        // Final page footer
        canvas.drawText("Page $pageNum  •  MoneyMate", (pageWidth / 2 - 30).toFloat(), pageHeight - 20f, subtitlePaint)
        doc.finishPage(page)

        FileOutputStream(file).use { out ->
            doc.writeTo(out)
        }
        doc.close()
        return file
    }

    // --- UDHAAR PDF EXPORT ---

    fun exportUdhaarPdf(
        context: Context,
        people: List<Person>,
        entries: List<UdhaarEntry>,
        repayments: List<UdhaarRepayment>
    ): File {
        val fileName = "MoneyMate_Udhaar_Report_${System.currentTimeMillis()}.pdf"
        val file = File(context.cacheDir, fileName)

        val doc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842

        val titlePaint = Paint().apply {
            color = AndroidColor.rgb(46, 125, 50)
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val sectionPaint = Paint().apply {
            color = AndroidColor.rgb(30, 41, 59)
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val subtitlePaint = Paint().apply {
            color = AndroidColor.rgb(100, 116, 139)
            textSize = 9f
            isAntiAlias = true
        }

        val headerPaint = Paint().apply {
            color = AndroidColor.rgb(241, 245, 249)
            style = Paint.Style.FILL
        }

        val textPaint = Paint().apply {
            color = AndroidColor.rgb(30, 41, 59)
            textSize = 8.5f
            isAntiAlias = true
        }

        val boldTextPaint = Paint().apply {
            color = AndroidColor.rgb(15, 23, 42)
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val greenPaint = Paint().apply {
            color = AndroidColor.rgb(46, 125, 50)
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val redPaint = Paint().apply {
            color = AndroidColor.rgb(198, 40, 40)
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val borderPaint = Paint().apply {
            color = AndroidColor.rgb(226, 232, 240)
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }

        var pageNum = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
        var page = doc.startPage(pageInfo)
        var canvas = page.canvas

        canvas.drawText("MoneyMate", 40f, 45f, titlePaint)
        canvas.drawText("People & Udhaar (Khata) Ledger Report", 40f, 60f, sectionPaint)
        canvas.drawText("Generated on: ${FormatUtils.formatDateTime(System.currentTimeMillis())}", 40f, 74f, subtitlePaint)
        canvas.drawLine(40f, 85f, (pageWidth - 40).toFloat(), 85f, borderPaint)

        val totalToReceive = entries.filter { it.type == "GAVE" && !it.isSettled }.sumOf { it.remainingAmount }
        val totalToPay = entries.filter { it.type == "TOOK" && !it.isSettled }.sumOf { it.remainingAmount }
        val net = totalToReceive - totalToPay

        var y = 105f
        val boxWidth = 160f
        val boxHeight = 44f

        fun drawSummaryBox(x: Float, label: String, value: String, isGreen: Boolean) {
            val bgPaint = Paint().apply {
                color = if (isGreen) AndroidColor.rgb(232, 245, 233) else AndroidColor.rgb(255, 235, 238)
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(x, y, x + boxWidth, y + boxHeight, 6f, 6f, bgPaint)
            canvas.drawRoundRect(x, y, x + boxWidth, y + boxHeight, 6f, 6f, borderPaint)
            canvas.drawText(label, x + 10f, y + 16f, subtitlePaint)
            val vPaint = if (isGreen) greenPaint else redPaint
            canvas.drawText(value, x + 10f, y + 34f, vPaint)
        }

        drawSummaryBox(40f, "To Receive (They Owe You)", FormatUtils.formatInr(totalToReceive), true)
        drawSummaryBox(215f, "To Pay (You Owe Them)", FormatUtils.formatInr(totalToPay), false)
        drawSummaryBox(390f, "Net Position", "${if (net >= 0) "+" else ""}${FormatUtils.formatInr(net)}", net >= 0)

        y += 65f

        // Table Header
        canvas.drawText("Ledger Entries (${entries.size})", 40f, y, sectionPaint)
        y += 12f

        fun drawUdhaarHeader() {
            canvas.drawRect(40f, y, (pageWidth - 40).toFloat(), y + 18f, headerPaint)
            canvas.drawText("Person", 48f, y + 12f, boldTextPaint)
            canvas.drawText("Type", 140f, y + 12f, boldTextPaint)
            canvas.drawText("Original", 210f, y + 12f, boldTextPaint)
            canvas.drawText("Pending", 280f, y + 12f, boldTextPaint)
            canvas.drawText("Date", 350f, y + 12f, boldTextPaint)
            canvas.drawText("Due Date", 420f, y + 12f, boldTextPaint)
            canvas.drawText("Status", 490f, y + 12f, boldTextPaint)
            y += 18f
        }

        drawUdhaarHeader()

        val personMap = people.associateBy { it.id }
        entries.sortedByDescending { it.date }.forEach { entry ->
            if (y > pageHeight - 50) {
                canvas.drawText("Page $pageNum  •  MoneyMate", (pageWidth / 2 - 30).toFloat(), pageHeight - 20f, subtitlePaint)
                doc.finishPage(page)

                pageNum++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
                page = doc.startPage(pageInfo)
                canvas = page.canvas

                y = 40f
                canvas.drawText("MoneyMate - Udhaar Ledger (Continued)", 40f, y, subtitlePaint)
                y += 15f
                drawUdhaarHeader()
            }

            canvas.drawLine(40f, y, (pageWidth - 40).toFloat(), y, borderPaint)
            val pName = personMap[entry.personId]?.name ?: "Unknown"
            val typeStr = if (entry.type == "GAVE") "I Gave" else "I Took"
            val typePaint = if (entry.type == "GAVE") greenPaint else redPaint
            val dateStr = FormatUtils.formatDate(entry.date)
            val dueDateStr = if (entry.dueDate != null) FormatUtils.formatDate(entry.dueDate) else "-"
            val statusStr = if (entry.isSettled) "Settled" else "Active"

            canvas.drawText(truncate(pName, 18), 48f, y + 12f, textPaint)
            canvas.drawText(typeStr, 140f, y + 12f, typePaint)
            canvas.drawText(FormatUtils.formatInr(entry.amount), 210f, y + 12f, textPaint)
            canvas.drawText(FormatUtils.formatInr(entry.remainingAmount), 280f, y + 12f, if (entry.isSettled) textPaint else boldTextPaint)
            canvas.drawText(dateStr, 350f, y + 12f, textPaint)
            canvas.drawText(dueDateStr, 420f, y + 12f, textPaint)
            canvas.drawText(statusStr, 490f, y + 12f, if (entry.isSettled) greenPaint else redPaint)

            y += 16f
        }

        canvas.drawText("Page $pageNum  •  MoneyMate", (pageWidth / 2 - 30).toFloat(), pageHeight - 20f, subtitlePaint)
        doc.finishPage(page)

        FileOutputStream(file).use { out ->
            doc.writeTo(out)
        }
        doc.close()
        return file
    }

    // --- SHARE & PRINT HELPERS ---

    fun shareFile(context: Context, file: File, mimeType: String, title: String) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }

    fun printPdf(context: Context, file: File, docName: String = "MoneyMate_Report") {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val printAdapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback?,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder(docName)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .build()
                callback?.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor?,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback?
            ) {
                try {
                    val input = FileInputStream(file)
                    val output = FileOutputStream(destination?.fileDescriptor)
                    input.copyTo(output)
                    callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback?.onWriteFailed(e.message)
                }
            }
        }

        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
            .build()

        printManager.print(docName, printAdapter, attributes)
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else value
    }

    private fun escapeXml(value: String): String {
        return value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun truncate(value: String, maxLen: Int): String {
        return if (value.length > maxLen) "${value.take(maxLen - 2)}.." else value
    }
}

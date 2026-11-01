package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.utils.ReportExportUtils
import com.example.utils.ReportUtils
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
class ReportSystemTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun testReportFilteringByDateAccountAndCategory() {
        val t1 = Transaction(id = 1, amount = 1000.0, isIncome = true, category = "Salary", account = "Bank Account", timestamp = 5000L)
        val t2 = Transaction(id = 2, amount = 500.0, isIncome = false, category = "Food", account = "Cash in Hand", timestamp = 6000L)
        val t3 = Transaction(id = 3, amount = 200.0, isIncome = false, category = "Food", account = "Bank Account", timestamp = 7000L)
        val t4 = Transaction(id = 4, amount = 1500.0, isIncome = false, category = "Shopping", account = "Bank Account", timestamp = 15000L)

        val txs = listOf(t1, t2, t3, t4)

        // Filter by time: 4000L to 8000L
        val timeFiltered = ReportUtils.filterTransactions(txs, 4000L, 8000L)
        assertEquals(3, timeFiltered.size)

        // Filter by Account: "Cash in Hand"
        val cashFiltered = ReportUtils.filterTransactions(txs, 0L, 20000L, accountFilter = "Cash in Hand")
        assertEquals(1, cashFiltered.size)
        assertEquals("Cash in Hand", cashFiltered.first().account)

        // Filter by Category: "Food"
        val foodFiltered = ReportUtils.filterTransactions(txs, 0L, 20000L, categoryFilter = "Food")
        assertEquals(2, foodFiltered.size)
    }

    @Test
    fun testSummaryCalculationAndHighestExpenseDay() {
        val t1 = Transaction(id = 1, amount = 50000.0, isIncome = true, category = "Salary", account = "Bank", timestamp = 1000L)
        val t2 = Transaction(id = 2, amount = 3000.0, isIncome = false, category = "Rent", account = "Bank", timestamp = 2000L)
        val t3 = Transaction(id = 3, amount = 1000.0, isIncome = false, category = "Groceries", account = "Bank", timestamp = 2000L)
        val t4 = Transaction(id = 4, amount = 500.0, isIncome = false, category = "Chai", account = "Cash", timestamp = 100000000L)
        val t5 = Transaction(id = 5, amount = 10000.0, isIncome = false, category = "Transfer", account = "Bank", timestamp = 2000L)
        val t6 = Transaction(id = 6, amount = 5000.0, isIncome = false, category = "Udhaar", account = "Bank", timestamp = 2000L)

        val list = listOf(t1, t2, t3, t4, t5, t6)
        val summary = ReportUtils.calculateSummary(list, 0L, 200000000L)

        // Transfer and Udhaar must be excluded from expense & income!
        assertEquals(50000.0, summary.totalIncome, 0.001)
        // Expense = 3000 + 1000 + 500 = 4500
        assertEquals(4500.0, summary.totalExpense, 0.001)
        // Net Savings = 50000 - 4500 = 45500
        assertEquals(45500.0, summary.netSavings, 0.001)
        assertEquals(91, summary.savingsPercent) // (45500 / 50000) * 100 = 91%
        assertTrue(summary.highestExpenseDayAmount >= 4000.0) // On day of t2+t3
    }

    @Test
    fun testMonthComparisonPercentageCalculations() {
        val now = Calendar.getInstance()
        val curTimestamp = now.timeInMillis
        val prevTimestamp = Calendar.getInstance().apply {
            add(Calendar.MONTH, -1)
            set(Calendar.DAY_OF_MONTH, 15)
        }.timeInMillis

        val txs = listOf(
            // Previous month: Income 20000, Expense 10000 -> Savings 10000
            Transaction(id = 1, amount = 20000.0, isIncome = true, category = "Salary", timestamp = prevTimestamp),
            Transaction(id = 2, amount = 10000.0, isIncome = false, category = "Rent", timestamp = prevTimestamp),
            // Current month: Income 25000 (+25%), Expense 8000 (-20%) -> Savings 17000 (+70%)
            Transaction(id = 3, amount = 25000.0, isIncome = true, category = "Salary", timestamp = curTimestamp),
            Transaction(id = 4, amount = 8000.0, isIncome = false, category = "Rent", timestamp = curTimestamp)
        )

        val comp = ReportUtils.calculateMonthComparison(txs)
        assertEquals(25000.0, comp.currentMonthIncome, 0.001)
        assertEquals(20000.0, comp.previousMonthIncome, 0.001)
        assertEquals(25.0, comp.incomeChangePercent, 0.1)

        assertEquals(8000.0, comp.currentMonthExpense, 0.001)
        assertEquals(10000.0, comp.previousMonthExpense, 0.001)
        assertEquals(-20.0, comp.expenseChangePercent, 0.1)

        assertEquals(17000.0, comp.currentMonthSavings, 0.001)
        assertEquals(10000.0, comp.previousMonthSavings, 0.001)
        assertEquals(70.0, comp.savingsChangePercent, 0.1)
    }

    @Test
    fun testCashVsBankFlowSegregation() {
        val txs = listOf(
            Transaction(id = 1, amount = 5000.0, isIncome = true, category = "ATM", account = "Cash in Hand", timestamp = 1000L),
            Transaction(id = 2, amount = 1200.0, isIncome = false, category = "Food", account = "Cash in Hand", timestamp = 2000L),
            Transaction(id = 3, amount = 40000.0, isIncome = true, category = "Salary", account = "Bank Account", timestamp = 3000L),
            Transaction(id = 4, amount = 15000.0, isIncome = false, category = "Shopping", account = "Bank Account", timestamp = 4000L)
        )

        val flow = ReportUtils.calculateCashBankFlow(txs, 0L, 5000L)
        assertEquals(5000.0, flow.cashInflow, 0.001)
        assertEquals(1200.0, flow.cashOutflow, 0.001)
        assertEquals(40000.0, flow.bankInflow, 0.001)
        assertEquals(15000.0, flow.bankOutflow, 0.001)
    }

    @Test
    fun testTopCategoriesRanking() {
        val txs = listOf(
            Transaction(id = 1, amount = 3000.0, isIncome = false, category = "Food", timestamp = 1000L),
            Transaction(id = 2, amount = 2000.0, isIncome = false, category = "Food", timestamp = 2000L),
            Transaction(id = 3, amount = 4000.0, isIncome = false, category = "Travel", timestamp = 3000L),
            Transaction(id = 4, amount = 1000.0, isIncome = false, category = "Bills", timestamp = 4000L)
        )

        val top = ReportUtils.calculateTopCategories(txs, 0L, 5000L)
        assertEquals(3, top.size)
        // Total = 10000. Food = 5000 (50%), Travel = 4000 (40%), Bills = 1000 (10%)
        assertEquals("Food", top[0].category)
        assertEquals(5000.0, top[0].totalAmount, 0.001)
        assertEquals(50.0, top[0].percentageOfTotal, 0.1)

        assertEquals("Travel", top[1].category)
        assertEquals(4000.0, top[1].totalAmount, 0.001)
        assertEquals(40.0, top[1].percentageOfTotal, 0.1)
    }

    @Test
    fun testCsvAndExcelExportGeneration() {
        val txs = listOf(
            Transaction(id = 1, amount = 25000.0, isIncome = true, category = "Salary", account = "Bank Account", timestamp = 1000L),
            Transaction(id = 2, amount = 450.0, isIncome = false, category = "Chai/Snacks", account = "Cash in Hand", timestamp = 2000L)
        )
        val summary = ReportSummary(25000.0, 450.0, 24550.0, 98, 450.0, "Today", 450.0, 1)

        val csvFile = ReportExportUtils.exportTransactionsCsv(context, txs, "This Month")
        assertTrue(csvFile.exists())
        val csvContent = csvFile.readText()
        assertTrue(csvContent.contains("MoneyMate Financial Report"))
        assertTrue(csvContent.contains("Chai/Snacks"))
        assertTrue(csvContent.contains("Salary"))

        val excelFile = ReportExportUtils.exportTransactionsExcel(context, txs, summary, "This Month")
        assertTrue(excelFile.exists())
        val excelContent = excelFile.readText()
        assertTrue(excelContent.contains("MoneyMate - Financial Report"))
        assertTrue(excelContent.contains("Salary"))
        assertTrue(excelContent.contains("Total Income"))
    }

    @Test
    fun testUdhaarCsvExport() {
        val person = Person(id = 1, name = "Ramesh Kumar", phone = "9876543210")
        val entry = UdhaarEntry(id = 1, personId = 1, type = "GAVE", amount = 5000.0, remainingAmount = 2000.0, account = "Bank", date = 1000L)
        val repayment = UdhaarRepayment(id = 1, entryId = 1, personId = 1, amount = 3000.0, account = "Cash", date = 2000L)

        val file = ReportExportUtils.exportUdhaarCsv(context, listOf(person), listOf(entry), listOf(repayment))
        assertTrue(file.exists())
        val content = file.readText()
        assertTrue(content.contains("Ramesh Kumar"))
        assertTrue(content.contains("I Gave (Lent)"))
        assertTrue(content.contains("2000.0"))
        assertTrue(content.contains("3000.0"))
    }
}

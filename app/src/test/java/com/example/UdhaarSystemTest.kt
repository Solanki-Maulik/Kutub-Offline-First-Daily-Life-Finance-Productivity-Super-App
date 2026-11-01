package com.example

import com.example.data.*
import com.example.utils.AccountUtils
import com.example.utils.FormatUtils
import com.example.utils.UdhaarShareUtils
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class UdhaarSystemTest {

    @Test
    fun testGaveMoneyDeductsFromAccountBalance() {
        val bankAccount = Account(name = "Bank Account", balance = 20000.0, type = "BANK")
        // User gave ₹5,000 to John from Bank Account
        val udhaarTransaction = Transaction(
            id = 1,
            amount = 5000.0,
            isIncome = false,
            category = "Udhaar",
            account = "Bank Account",
            note = "Udhaar: Lent to John",
            timestamp = 1000L
        )

        val balance = AccountUtils.calculateAccountBalance(bankAccount, listOf(udhaarTransaction))
        // 20000 - 5000 = 15000
        assertEquals(15000.0, balance, 0.001)
    }

    @Test
    fun testTookMoneyIncreasesAccountBalance() {
        val cashAccount = Account(name = "Cash in Hand", balance = 2000.0, type = "CASH")
        // User borrowed ₹3,000 from Sarah into Cash
        val udhaarTransaction = Transaction(
            id = 1,
            amount = 3000.0,
            isIncome = true,
            category = "Udhaar",
            account = "Cash in Hand",
            note = "Udhaar: Borrowed from Sarah",
            timestamp = 1000L
        )

        val balance = AccountUtils.calculateAccountBalance(cashAccount, listOf(udhaarTransaction))
        // 2000 + 3000 = 5000
        assertEquals(5000.0, balance, 0.001)
    }

    @Test
    fun testPartialRepaymentUpdatesRemainingAndAccount() {
        val upiAccount = Account(name = "UPI Wallet", balance = 1000.0, type = "UPI")
        val originalEntry = UdhaarEntry(
            id = 1,
            personId = 10,
            type = "GAVE",
            amount = 5000.0,
            remainingAmount = 5000.0,
            account = "Bank Account",
            date = 1000L
        )

        // John repays ₹2,000 partially into UPI Wallet
        val repaymentAmount = 2000.0
        val repaymentTx = Transaction(
            id = 2,
            amount = repaymentAmount,
            isIncome = true,
            category = "Udhaar",
            account = "UPI Wallet",
            note = "Udhaar: Repayment from John",
            timestamp = 2000L
        )

        val updatedRemaining = maxOf(0.0, originalEntry.remainingAmount - repaymentAmount)
        val isSettled = updatedRemaining <= 0.001

        assertEquals(3000.0, updatedRemaining, 0.001)
        assertFalse(isSettled)

        val upiBalance = AccountUtils.calculateAccountBalance(upiAccount, listOf(repaymentTx))
        // 1000 + 2000 = 3000
        assertEquals(3000.0, upiBalance, 0.001)
    }

    @Test
    fun testFullRepaymentMarksEntrySettled() {
        val entry = UdhaarEntry(
            id = 1,
            personId = 10,
            type = "GAVE",
            amount = 1500.0,
            remainingAmount = 1500.0,
            account = "Cash in Hand",
            date = 1000L
        )

        val repaymentAmount = 1500.0
        val remaining = maxOf(0.0, entry.remainingAmount - repaymentAmount)
        val isSettled = remaining <= 0.001

        assertEquals(0.0, remaining, 0.001)
        assertTrue(isSettled)
    }

    @Test
    fun testUdhaarEntriesDoNotSkewerNormalExpenseOrIncome() {
        val transactions = listOf(
            Transaction(id = 1, amount = 10000.0, isIncome = true, category = "Salary", account = "Bank", timestamp = 100L),
            Transaction(id = 2, amount = 2500.0, isIncome = false, category = "Groceries", account = "Bank", timestamp = 200L),
            // Udhaar money lent
            Transaction(id = 3, amount = 5000.0, isIncome = false, category = "Udhaar", account = "Bank", timestamp = 300L),
            // Udhaar money borrowed
            Transaction(id = 4, amount = 3000.0, isIncome = true, category = "Udhaar", account = "Bank", timestamp = 400L)
        )

        // Standard filter used in Analytics and Reports:
        val expenses = transactions.filter { !it.isIncome && it.category != "Transfer" && it.category != "Adjustment" && it.category != "Udhaar" }
        val income = transactions.filter { it.isIncome && it.category != "Transfer" && it.category != "Adjustment" && it.category != "Udhaar" }

        val totalExpense = expenses.sumOf { it.amount }
        val totalIncome = income.sumOf { it.amount }

        assertEquals(2500.0, totalExpense, 0.001) // Only Groceries
        assertEquals(10000.0, totalIncome, 0.001) // Only Salary
    }

    @Test
    fun testPersonNetBalanceCalculation() {
        val person = Person(id = 1, name = "Amit", phone = "9876543210")
        val entries = listOf(
            // I gave ₹2000, remaining ₹500
            UdhaarEntry(id = 1, personId = 1, type = "GAVE", amount = 2000.0, remainingAmount = 500.0, account = "Bank", date = 100L),
            // I took ₹1000, remaining ₹200
            UdhaarEntry(id = 2, personId = 1, type = "TOOK", amount = 1000.0, remainingAmount = 200.0, account = "Cash", date = 200L)
        )

        val toReceive = entries.filter { it.type == "GAVE" && !it.isSettled }.sumOf { it.remainingAmount }
        val toPay = entries.filter { it.type == "TOOK" && !it.isSettled }.sumOf { it.remainingAmount }
        val netBalance = toReceive - toPay

        assertEquals(500.0, toReceive, 0.001)
        assertEquals(200.0, toPay, 0.001)
        assertEquals(300.0, netBalance, 0.001) // Amit owes me ₹300
    }

    @Test
    fun testDueStatusCalculations() {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply { timeInMillis = now }

        // Past date (overdue)
        cal.add(Calendar.DAY_OF_YEAR, -5)
        val overdueDate = cal.timeInMillis
        val overdueStatus = FormatUtils.calculateDueStatus(overdueDate, now)
        assertTrue(overdueStatus is DueStatus.Overdue)

        // Future date in 3 days (due soon)
        cal.timeInMillis = now
        cal.add(Calendar.DAY_OF_YEAR, 3)
        val soonDate = cal.timeInMillis
        val soonStatus = FormatUtils.calculateDueStatus(soonDate, now)
        assertTrue(soonStatus is DueStatus.DueSoon)
    }

    @Test
    fun testUdhaarShareLedgerTextGeneration() {
        val person = Person(id = 1, name = "Vikas Sharma", phone = "9812345678")
        val entries = listOf(
            UdhaarEntry(id = 1, personId = 1, type = "GAVE", amount = 4000.0, remainingAmount = 1500.0, account = "Bank Account", date = 1000L)
        )
        val repayments = listOf(
            UdhaarRepayment(id = 1, entryId = 1, personId = 1, amount = 2500.0, account = "UPI Wallet", date = 2000L, note = "GPay")
        )

        val shareText = UdhaarShareUtils.buildLedgerSummaryText(person, entries, repayments, "01/11/2026")
        assertTrue(shareText.contains("Vikas Sharma"))
        assertTrue(shareText.contains("9812345678"))
        assertTrue(shareText.contains("₹1,500"))
        assertTrue(shareText.contains("Total Given"))
        assertTrue(shareText.contains("GPay"))
    }
}

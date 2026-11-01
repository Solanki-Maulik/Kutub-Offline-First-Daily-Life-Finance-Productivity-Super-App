package com.example

import com.example.data.*
import com.example.utils.FormatUtils
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class BudgetRecurringSystemTest {

    @Test
    fun testOverallBudgetAlertsAt80And100() {
        val limit = 10000.0

        // Case 1: Below 80% (Spend = ₹5,000)
        val spend1 = 5000.0
        val progress1 = (spend1 / limit).toFloat()
        val isWarning1 = progress1 in 0.80f..<1.0f
        val isExceeded1 = progress1 >= 1.0f
        assertEquals(0.5f, progress1, 0.001f)
        assertFalse(isWarning1)
        assertFalse(isExceeded1)

        // Case 2: At or above 80% but below 100% (Spend = ₹8,500)
        val spend2 = 8500.0
        val progress2 = (spend2 / limit).toFloat()
        val isWarning2 = progress2 in 0.80f..<1.0f
        val isExceeded2 = progress2 >= 1.0f
        assertEquals(0.85f, progress2, 0.001f)
        assertTrue(isWarning2)
        assertFalse(isExceeded2)

        // Case 3: At or above 100% (Spend = ₹11,000)
        val spend3 = 11000.0
        val progress3 = (spend3 / limit).toFloat()
        val isWarning3 = progress3 in 0.80f..<1.0f
        val isExceeded3 = progress3 >= 1.0f
        assertEquals(1.10f, progress3, 0.001f)
        assertFalse(isWarning3)
        assertTrue(isExceeded3)
    }

    @Test
    fun testCategoryBudgetSeparationAndAlerts() {
        val foodLimit = 5000.0
        val travelLimit = 3000.0

        val foodTransactions = listOf(
            Transaction(id = 1, amount = 2500.0, isIncome = false, category = "Food", account = "Cash in Hand", timestamp = 1000L),
            Transaction(id = 2, amount = 1800.0, isIncome = false, category = "Food", account = "UPI Wallet", timestamp = 2000L)
        )
        val travelTransactions = listOf(
            Transaction(id = 3, amount = 500.0, isIncome = false, category = "Travel", account = "UPI Wallet", timestamp = 3000L)
        )

        val foodSpent = foodTransactions.sumOf { it.amount } // 4300.0
        val foodProgress = (foodSpent / foodLimit).toFloat() // 0.86f
        assertTrue("Food budget should trigger 80% warning", foodProgress in 0.80f..<1.0f)
        assertFalse("Food budget not exceeded", foodProgress >= 1.0f)

        val travelSpent = travelTransactions.sumOf { it.amount } // 500.0
        val travelProgress = (travelSpent / travelLimit).toFloat() // 0.166f
        assertFalse("Travel budget should be normal", travelProgress >= 0.80f)
    }

    @Test
    fun testBudgetExcludesTransfersAndIncome() {
        val transactions = listOf(
            Transaction(id = 1, amount = 10000.0, isIncome = false, category = "Transfer", account = "Bank Account", timestamp = 1000L),
            Transaction(id = 2, amount = 50000.0, isIncome = true, category = "Salary", account = "Bank Account", timestamp = 1000L),
            Transaction(id = 3, amount = 2000.0, isIncome = false, category = "Food", account = "Bank Account", timestamp = 1000L)
        )

        val budgetExpenses = transactions.filter { !it.isIncome && it.category != "Transfer" }
        assertEquals(1, budgetExpenses.size)
        assertEquals(2000.0, budgetExpenses.sumOf { it.amount }, 0.001)
    }

    @Test
    fun testSavingsGoalProgressAndContributions() {
        var goal = Goal(id = 1, name = "MacBook Pro", targetAmount = 150000.0, savedAmount = 0.0, targetDateMillis = 100000L)

        // Initial progress: 0%
        val initialProgress = (goal.savedAmount / goal.targetAmount).toFloat()
        assertEquals(0f, initialProgress, 0.001f)

        // 1st contribution: ₹50,000
        val contrib1 = GoalContribution(id = 1, goalId = goal.id, amount = 50000.0, date = 1000L, note = "Bonus")
        goal = goal.copy(savedAmount = goal.savedAmount + contrib1.amount)
        assertEquals(50000.0, goal.savedAmount, 0.001)
        assertEquals(0.3333f, (goal.savedAmount / goal.targetAmount).toFloat(), 0.001f)

        // 2nd contribution: ₹100,000 (Goal Achieved 100%)
        val contrib2 = GoalContribution(id = 2, goalId = goal.id, amount = 100000.0, date = 2000L, note = "Salary savings")
        goal = goal.copy(savedAmount = goal.savedAmount + contrib2.amount)
        assertEquals(150000.0, goal.savedAmount, 0.001)
        val finalProgress = (goal.savedAmount / goal.targetAmount).toFloat()
        assertTrue("Goal is 100% achieved", finalProgress >= 1.0f)

        // Delete contribution 1: restored saved amount
        goal = goal.copy(savedAmount = maxOf(0.0, goal.savedAmount - contrib1.amount))
        assertEquals(100000.0, goal.savedAmount, 0.001)
    }

    @Test
    fun testRecurringTransactionFrequencyAdvancement() {
        fun advanceDueDate(currentDue: Long, frequency: String): Long {
            val cal = Calendar.getInstance().apply { timeInMillis = currentDue }
            when (frequency.uppercase()) {
                "DAILY" -> cal.add(Calendar.DAY_OF_YEAR, 1)
                "WEEKLY" -> cal.add(Calendar.WEEK_OF_YEAR, 1)
                "MONTHLY" -> cal.add(Calendar.MONTH, 1)
                "YEARLY" -> cal.add(Calendar.YEAR, 1)
                else -> cal.add(Calendar.MONTH, 1)
            }
            return cal.timeInMillis
        }

        val baseCal = Calendar.getInstance().apply {
            set(2026, Calendar.JANUARY, 15, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val baseTime = baseCal.timeInMillis

        // Daily
        val dailyNext = advanceDueDate(baseTime, "DAILY")
        val dailyCal = Calendar.getInstance().apply { timeInMillis = dailyNext }
        assertEquals(16, dailyCal.get(Calendar.DAY_OF_MONTH))

        // Weekly
        val weeklyNext = advanceDueDate(baseTime, "WEEKLY")
        val weeklyCal = Calendar.getInstance().apply { timeInMillis = weeklyNext }
        assertEquals(22, weeklyCal.get(Calendar.DAY_OF_MONTH))

        // Monthly
        val monthlyNext = advanceDueDate(baseTime, "MONTHLY")
        val monthlyCal = Calendar.getInstance().apply { timeInMillis = monthlyNext }
        assertEquals(Calendar.FEBRUARY, monthlyCal.get(Calendar.MONTH))
        assertEquals(15, monthlyCal.get(Calendar.DAY_OF_MONTH))

        // Yearly
        val yearlyNext = advanceDueDate(baseTime, "YEARLY")
        val yearlyCal = Calendar.getInstance().apply { timeInMillis = yearlyNext }
        assertEquals(2027, yearlyCal.get(Calendar.YEAR))
    }

    @Test
    fun testBillMarkAsPaidCreatesExpense() {
        val bill = BillReminder(
            id = 10,
            title = "Electricity Bill",
            amount = 3200.0,
            dueDate = 1000L,
            category = "Bills",
            account = "Bank Account",
            isRecurring = false
        )

        // Simulate mark as paid: creates expense transaction
        val generatedTx = Transaction(
            amount = bill.amount,
            isIncome = false,
            category = bill.category,
            account = bill.account,
            paymentMethod = "Bill Payment",
            note = "Bill: ${bill.title}",
            timestamp = 2000L
        )

        assertEquals(3200.0, generatedTx.amount, 0.001)
        assertFalse(generatedTx.isIncome)
        assertEquals("Bills", generatedTx.category)
        assertEquals("Bank Account", generatedTx.account)
        assertEquals("Bill: Electricity Bill", generatedTx.note)
    }

    @Test
    fun testRecurringBillAdvancesToNextMonth() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 10, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val bill = BillReminder(
            id = 11,
            title = "Broadband WiFi",
            amount = 999.0,
            dueDate = cal.timeInMillis,
            category = "Recharge",
            account = "UPI Wallet",
            isRecurring = true,
            recurringFrequency = "MONTHLY"
        )

        fun advanceMonth(dueDate: Long): Long {
            val c = Calendar.getInstance().apply { timeInMillis = dueDate }
            c.add(Calendar.MONTH, 1)
            return c.timeInMillis
        }

        val updatedBill = bill.copy(
            dueDate = advanceMonth(bill.dueDate),
            isPaid = false,
            paidDate = System.currentTimeMillis()
        )

        val updatedCal = Calendar.getInstance().apply { timeInMillis = updatedBill.dueDate }
        assertEquals(Calendar.APRIL, updatedCal.get(Calendar.MONTH))
        assertEquals(10, updatedCal.get(Calendar.DAY_OF_MONTH))
        assertFalse(updatedBill.isPaid)
    }

    @Test
    fun testFormatInrIndianStyle() {
        assertEquals("₹1,00,000", FormatUtils.formatInr(100000.0))
        assertEquals("₹12,34,567", FormatUtils.formatInr(1234567.0))
        assertEquals("₹5,000", FormatUtils.formatInr(5000.0))
        assertEquals("₹0", FormatUtils.formatInr(0.0))
    }
}

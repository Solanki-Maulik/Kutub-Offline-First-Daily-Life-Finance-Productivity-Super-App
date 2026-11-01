package com.example

import com.example.data.*
import com.example.utils.TaskUtils
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class TasksScheduleSystemTest {

    @Test
    fun testSubtaskSerializationAndProgress() {
        val subtasks = listOf(
            Subtask(id = "1", title = "Download electricity bill PDF", isCompleted = true),
            Subtask(id = "2", title = "Check meter reading", isCompleted = true),
            Subtask(id = "3", title = "Pay via UPI", isCompleted = false)
        )

        val json = TaskUtils.serializeSubtasks(subtasks)
        assertNotNull(json)
        assertTrue(json.contains("Download electricity bill PDF"))

        val parsed = TaskUtils.parseSubtasks(json)
        assertEquals(3, parsed.size)
        assertEquals("Download electricity bill PDF", parsed[0].title)
        assertTrue(parsed[0].isCompleted)
        assertTrue(parsed[1].isCompleted)
        assertFalse(parsed[2].isCompleted)

        val completedCount = parsed.count { it.isCompleted }
        assertEquals(2, completedCount)
        val progress = completedCount.toFloat() / parsed.size
        assertEquals(0.666f, progress, 0.01f)
    }

    @Test
    fun testTaskLinkedToMoneyExpenseCreation() {
        // Simulates single source of truth money linking
        val accounts = listOf(
            Account(name = "Bank Account", balance = 15000.0, type = "BANK")
        )
        val initialTransactions = mutableListOf<Transaction>()

        // Initial balance: opening balance + sum of transactions
        var balance = accounts[0].balance + initialTransactions.filter { it.account == "Bank Account" }
            .sumOf { if (it.isIncome) it.amount else -it.amount }
        assertEquals(15000.0, balance, 0.001)

        val task = TaskItem(
            id = 101,
            title = "Pay electricity bill",
            priority = "URGENT",
            category = "Bills",
            status = "TODO",
            isLinkedToMoney = true,
            linkedAmount = 1450.0,
            linkedCategory = "Bills",
            linkedAccount = "Bank Account"
        )

        // When task completed: expense transaction created
        val createdTransaction = Transaction(
            id = 501,
            amount = task.linkedAmount ?: 0.0,
            isIncome = false,
            category = task.linkedCategory ?: "Bills",
            account = task.linkedAccount ?: "Bank Account",
            note = "Completed Task: ${task.title}",
            timestamp = System.currentTimeMillis()
        )
        initialTransactions.add(createdTransaction)

        // New balance is deducted from account
        balance = accounts[0].balance + initialTransactions.filter { it.account == "Bank Account" }
            .sumOf { if (it.isIncome) it.amount else -it.amount }
        assertEquals(13550.0, balance, 0.001)

        // If un-done (toggled back to TODO), transaction is removed and balance restored
        initialTransactions.removeIf { it.id == createdTransaction.id }
        balance = accounts[0].balance + initialTransactions.filter { it.account == "Bank Account" }
            .sumOf { if (it.isIncome) it.amount else -it.amount }
        assertEquals(15000.0, balance, 0.001)
    }

    @Test
    fun testDailyStreakConsecutiveDays() {
        val todayStr = TaskUtils.getTodayIsoDate()
        val yesterdayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val yesterdayStr = TaskUtils.getIsoDate(yesterdayCal.timeInMillis)
        val twoDaysAgoCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -2) }
        val twoDaysAgoStr = TaskUtils.getIsoDate(twoDaysAgoCal.timeInMillis)

        // Case 1: Yesterday was active -> consecutive day increments currentStreak
        val (streak1, max1) = TaskUtils.calculateNewStreak(
            lastDate = yesterdayStr,
            currentStreak = 3,
            maxStreak = 5
        )
        assertEquals(4, streak1)
        assertEquals(5, max1)

        // Case 2: Today already active -> multiple tasks completed on same day keeps streak
        val (streak2, max2) = TaskUtils.calculateNewStreak(
            lastDate = todayStr,
            currentStreak = 4,
            maxStreak = 5
        )
        assertEquals(4, streak2)
        assertEquals(5, max2)

        // Case 3: Break in streak (last active 2 days ago) -> resets to 1
        val (streak3, max3) = TaskUtils.calculateNewStreak(
            lastDate = twoDaysAgoStr,
            currentStreak = 10,
            maxStreak = 10
        )
        assertEquals(1, streak3)
        assertEquals(10, max3) // Max preserved!
    }

    @Test
    fun testRepeatTaskNextDueDateCalculation() {
        val baseCal = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 15, 10, 0, 0)
        }
        val baseTime = baseCal.timeInMillis

        // DAILY repeat
        val nextDaily = TaskUtils.calculateNextDueDate(baseTime, "DAILY")
        val dailyCal = Calendar.getInstance().apply { timeInMillis = nextDaily }
        assertEquals(16, dailyCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.MARCH, dailyCal.get(Calendar.MONTH))

        // WEEKLY repeat
        val nextWeekly = TaskUtils.calculateNextDueDate(baseTime, "WEEKLY")
        val weeklyCal = Calendar.getInstance().apply { timeInMillis = nextWeekly }
        assertEquals(22, weeklyCal.get(Calendar.DAY_OF_MONTH))

        // MONTHLY repeat
        val nextMonthly = TaskUtils.calculateNextDueDate(baseTime, "MONTHLY")
        val monthlyCal = Calendar.getInstance().apply { timeInMillis = nextMonthly }
        assertEquals(15, monthlyCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.APRIL, monthlyCal.get(Calendar.MONTH))
    }

    @Test
    fun testTaskFilteringTodayUpcomingCompleted() {
        val now = System.currentTimeMillis()
        val tomorrow = now + (24 * 60 * 60 * 1000)

        val task1 = TaskItem(id = 1, title = "Today Todo", status = "TODO", dueDateTime = now)
        val task2 = TaskItem(id = 2, title = "Today Doing", status = "DOING", dueDateTime = now)
        val task3 = TaskItem(id = 3, title = "Tomorrow Task", status = "TODO", dueDateTime = tomorrow)
        val task4 = TaskItem(id = 4, title = "Finished Task", status = "DONE", dueDateTime = now)

        val allTasks = listOf(task1, task2, task3, task4)

        // Today active tasks
        val todayActive = allTasks.filter { task ->
            task.status != "DONE" && (task.dueDateTime == null || TaskUtils.isSameDay(task.dueDateTime, now))
        }
        assertEquals(2, todayActive.size)
        assertTrue(todayActive.contains(task1))
        assertTrue(todayActive.contains(task2))

        // Upcoming active tasks
        val upcoming = allTasks.filter { task ->
            task.status != "DONE" && task.dueDateTime != null && task.dueDateTime > now && !TaskUtils.isSameDay(task.dueDateTime, now)
        }
        assertEquals(1, upcoming.size)
        assertEquals("Tomorrow Task", upcoming[0].title)

        // Completed tasks
        val completed = allTasks.filter { it.status == "DONE" }
        assertEquals(1, completed.size)
        assertEquals("Finished Task", completed[0].title)
    }

    @Test
    fun testScheduleEventTimeValidation() {
        val cal = Calendar.getInstance()
        cal.set(2026, Calendar.NOVEMBER, 1, 14, 0, 0)
        val startTime = cal.timeInMillis
        cal.set(2026, Calendar.NOVEMBER, 1, 15, 30, 0)
        val endTime = cal.timeInMillis

        val event = ScheduleEvent(
            id = 1,
            title = "Chartered Accountant Tax Review",
            startTime = startTime,
            endTime = endTime,
            location = "Connaught Place, New Delhi",
            reminderOffsetMinutes = 30
        )

        assertTrue(event.endTime > event.startTime)
        val durationMinutes = (event.endTime - event.startTime) / (60 * 1000)
        assertEquals(90L, durationMinutes)
        assertEquals("Chartered Accountant Tax Review", event.title)
        assertEquals("Connaught Place, New Delhi", event.location)
    }
}

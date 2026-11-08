package com.example

import com.example.data.Habit
import com.example.data.HabitLog
import com.example.data.TaskItem
import com.example.utils.FormatUtils
import com.example.utils.HabitUtils
import com.example.utils.LocalAiFallbackEngine
import com.example.utils.TaskUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleRobolectricTest {

    @Test
    fun `verify greeting, habit streak, task streak, and multi-module AI data entry`() {

        // Verify Morning, Afternoon, Evening, and Night greetings
        val morning = FormatUtils.computeGreetingTimeSnapshot(overridePeriod = "MORNING")
        assertEquals("Good morning", morning.greetingEnglish)
        assertEquals("સુપ્રભાત", morning.greetingGujarati)

        val afternoon = FormatUtils.computeGreetingTimeSnapshot(overridePeriod = "AFTERNOON")
        assertEquals("Good afternoon", afternoon.greetingEnglish)
        assertEquals("શુભ બપોર", afternoon.greetingGujarati)

        val evening = FormatUtils.computeGreetingTimeSnapshot(overridePeriod = "EVENING")
        assertEquals("Good evening", evening.greetingEnglish)
        assertEquals("શુભ સાંજ", evening.greetingGujarati)

        val night = FormatUtils.computeGreetingTimeSnapshot(overridePeriod = "NIGHT")
        assertEquals("Good night", night.greetingEnglish)
        assertEquals("શુભ રાત્રિ", night.greetingGujarati)
        assertTrue(night.formattedTime.isNotBlank())

        // Verify newly created habit does not jump to 33 streak before any prior days have elapsed
        val now = System.currentTimeMillis()
        val todayIso = HabitUtils.getTodayIso()
        val newNoSpendHabit = Habit(
            id = 1L,
            name = "No Junk Food",
            moneyRuleType = "NO_SPEND_DAY",
            createdAt = now
        )
        val statsDay1 = HabitUtils.computeHabitStats(
            habit = newNoSpendHabit,
            selectedDateIso = todayIso,
            logsForHabit = listOf(HabitLog(habitId = 1L, dateIso = todayIso, status = "COMPLETED")),
            transactions = emptyList(),
            goalContributions = emptyList()
        )
        assertEquals(1, statsDay1.currentStreak)
        assertEquals(1, statsDay1.longestStreak)

        // Verify Task Day Streak calculation (completing 1 task sets streak = 1, unchecking resets to 0)
        val completedTask = TaskItem(
            id = 1L,
            title = "Log daily expenses",
            status = "DONE",
            completedAt = now
        )
        val (streakAfterDone, bestAfterDone, _) = TaskUtils.computeStreakFromTasks(listOf(completedTask), previousMaxStreak = 0)
        assertEquals(1, streakAfterDone)
        assertEquals(1, bestAfterDone)

        val uncompletedTask = completedTask.copy(status = "TODO", completedAt = null)
        val (streakAfterUncheck, _, _) = TaskUtils.computeStreakFromTasks(listOf(uncompletedTask), previousMaxStreak = bestAfterDone)
        assertEquals(0, streakAfterUncheck)

        // Verify on-device AI fallback parser across financial + productivity modules
        val parsed = LocalAiFallbackEngine.parseSmartEntryLocally(
            "chai 20 cash, petrol 300 bank, got 5000 from Raj, task pay electricity bill, habit morning walk, schedule client meeting 4pm, note tax receipt saved",
            listOf("Cash", "Bank", "UPI"),
            listOf("Food", "Transport", "Udhaar")
        )
        assertEquals(7, parsed.entries.size)
        assertEquals(20.0, parsed.entries[0].amount, 0.01)
        assertEquals(300.0, parsed.entries[1].amount, 0.01)
        assertEquals("UDHAAR_GOT", parsed.entries[2].entryKind)
        assertEquals("TASK", parsed.entries[3].entryKind)
        assertTrue(parsed.entries[3].isReadyToSave)
        assertEquals("HABIT", parsed.entries[4].entryKind)
        assertTrue(parsed.entries[4].isReadyToSave)
        assertEquals("SCHEDULE", parsed.entries[5].entryKind)
        assertTrue(parsed.entries[5].isReadyToSave)
        assertEquals("NOTE", parsed.entries[6].entryKind)
        assertTrue(parsed.entries[6].isReadyToSave)

        // Verify Vehicle Fuel Mileage & Service Tracker calculations
        val fuelLogs = listOf(
            com.example.utils.FuelLogEntry(
                id = 1L,
                vehicleName = "Honda Activa",
                odometerKm = 12400.0,
                litres = 4.0,
                totalCostInr = 384.0,
                pricePerLitre = 96.0,
                nextServiceKm = 15000.0
            ),
            com.example.utils.FuelLogEntry(
                id = 2L,
                vehicleName = "Honda Activa",
                odometerKm = 12610.0,
                litres = 4.0,
                totalCostInr = 384.0,
                pricePerLitre = 96.0,
                nextServiceKm = 15000.0
            )
        )
        val mileageStats = com.example.utils.DailyToolkitUtils.computeVehicleMileageStats(fuelLogs)
        assertEquals(210.0, mileageStats.totalDistanceKm, 0.01)
        assertEquals(52.5, mileageStats.averageKmPerLitre, 0.01)
        assertEquals(384.0 / 210.0, mileageStats.costPerKmInr, 0.01)
        assertEquals(2390.0, mileageStats.kmUntilNextService ?: 0.0, 0.01)

        // Verify Live Meditation Countdown & 4-4-6 Breathing Pacer
        assertEquals("10:00", com.example.utils.DailyToolkitUtils.formatTimerMmSs(600))
        assertEquals("Breathe In", com.example.utils.DailyToolkitUtils.computeBreathingPhase(1).label)
        assertEquals("Hold Gently", com.example.utils.DailyToolkitUtils.computeBreathingPhase(5).label)
        assertEquals("Breathe Out", com.example.utils.DailyToolkitUtils.computeBreathingPhase(10).label)

        // Verify Safe-to-Spend & Subscription Burn
        val subs = listOf(
            com.example.utils.SubscriptionItem(
                name = "WiFi",
                amountInr = 500.0,
                billingCycle = "MONTHLY",
                nextRenewalMillis = now + 86_400_000L
            )
        )
        val safeSpend = com.example.utils.DailyToolkitUtils.computeSafeToSpend(
            totalBalanceInr = 15500.0,
            unpaidBillsInr = 3000.0,
            subscriptions = subs
        )
        assertEquals(500.0, safeSpend.monthlySubscriptionBurnInr, 0.01)
        assertEquals(12000.0, safeSpend.netAvailableForMonthInr, 0.01)
        assertTrue(safeSpend.safeDailySpendInr > 0.0)

        // Verify EMI Prepayment Saver & WhatsApp Reminder
        val emiRes = com.example.utils.DailyToolkitUtils.calculateEmiAndPrepayment(
            principal = 500000.0,
            annualRatePercent = 9.5,
            tenureMonths = 60,
            extraMonthlyPayment = 2000.0
        )
        assertTrue(emiRes.monthlyEmi > 10000.0)
        assertTrue(emiRes.interestSaved > 15000.0)
        assertTrue(emiRes.monthsSaved >= 8)

        val gujReminder = com.example.utils.DailyToolkitUtils.buildWhatsAppReminderText(
            personName = "Raj",
            amountInr = 1500.0,
            upiId = "kutub@upi",
            language = "GUJARATI"
        )
        assertTrue(gujReminder.contains("Raj"))
        assertTrue(gujReminder.contains("kutub@upi"))

        val wrapScore = com.example.utils.DailyToolkitUtils.computeDailyWrapUpScore(
            loggedExpenseToday = true,
            completedTasksToday = 2,
            completedHabitsToday = 2,
            meditationMinutesToday = 10
        )
        assertEquals(100, wrapScore.scorePercent)
    }
}

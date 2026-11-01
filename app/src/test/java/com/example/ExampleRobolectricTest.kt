package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context and verify English launcher name, Gujarati greeting and streak logic`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Kutub", appName)

        // Verify automatic migration of old default "Maulik" to "કુતુબ"
        val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("user_name", "Maulik").commit()
        assertEquals("કુતુબ", FormatUtils.resolveUserDisplayName(prefs))

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

        // Verify on-device AI fallback parser
        val parsed = LocalAiFallbackEngine.parseSmartEntryLocally(
            "chai 20 cash, petrol 300 bank, got 5000 from Raj",
            listOf("Cash", "Bank", "UPI"),
            listOf("Food", "Transport", "Udhaar")
        )
        assertEquals(3, parsed.entries.size)
        assertEquals(20.0, parsed.entries[0].amount, 0.01)
        assertEquals(300.0, parsed.entries[1].amount, 0.01)
        assertEquals("UDHAAR_GOT", parsed.entries[2].entryKind)
    }
}

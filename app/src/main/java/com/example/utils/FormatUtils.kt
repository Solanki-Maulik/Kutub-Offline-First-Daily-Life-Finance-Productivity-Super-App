package com.example.utils

import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.round

data class CurrencyConfig(
    val code: String,
    val symbol: String,
    val label: String,
    val useIndianGrouping: Boolean
)

data class GreetingTimeSnapshot(
    val greetingEnglish: String,
    val greetingGujarati: String,
    val periodLabel: String,
    val formattedTime: String,
    val formattedDate: String,
    val timeZoneLabel: String,
    val hourOfDay: Int
)

/**
 * Centralized currency, number, and date formatting utilities.
 * Handles edge cases such as NaN/Infinite values, negative zero, huge amounts,
 * midnight boundaries (00:00:00.000 / 23:59:59.999), and month-end date clamping.
 */
object FormatUtils {
    const val DEFAULT_USER_NAME_GUJARATI = "કુતુબ"

    /**
     * Resolves the user's display name from SharedPreferences, migrating the old
     * template placeholder ("Maulik") to "કુતુબ" (Kutub in Gujarati).
     */
    fun resolveUserDisplayName(prefs: SharedPreferences): String {
        val stored = prefs.getString("user_name", null)?.trim()
        return if (stored.isNullOrEmpty() || stored.equals("Maulik", ignoreCase = true)) {
            prefs.edit().putString("user_name", DEFAULT_USER_NAME_GUJARATI).apply()
            DEFAULT_USER_NAME_GUJARATI
        } else {
            stored
        }
    }

    /**
     * Resolves the active TimeZone for top-left greeting and clock.
     * Defaults to India Standard Time (Asia/Kolkata, UTC+5:30) so cloud emulators
     * (which run in America/Los_Angeles or UTC) show accurate Indian Morning/Afternoon/Evening/Night.
     */
    fun resolveGreetingTimeZone(timeZoneMode: String = "IST"): TimeZone {
        return when (timeZoneMode.uppercase(Locale.US)) {
            "IST", "ASIA/KOLKATA" -> TimeZone.getTimeZone("Asia/Kolkata")
            "SYSTEM", "DEVICE" -> TimeZone.getDefault()
            else -> {
                val sysTz = TimeZone.getDefault()
                val sysId = sysTz.id
                if (currentCurrencyCode == "INR" && (
                        sysId == "America/Los_Angeles" ||
                        sysId == "America/New_York" ||
                        sysId == "UTC" ||
                        sysId == "GMT" ||
                        sysId == "Etc/UTC"
                    )
                ) {
                    TimeZone.getTimeZone("Asia/Kolkata")
                } else {
                    sysTz
                }
            }
        }
    }

    /**
     * Computes the time-of-day greeting (Morning, Afternoon, Evening, Night) and formatted live time/date.
     * - Morning:   05:00 AM – 11:59 AM (hour 5..11)  -> "Good morning" (સુપ્રભાત)
     * - Afternoon: 12:00 PM – 04:59 PM (hour 12..16) -> "Good afternoon" (શુભ બપોર)
     * - Evening:   05:00 PM – 08:59 PM (hour 17..20) -> "Good evening" (શુભ સાંજ)
     * - Night:     09:00 PM – 04:59 AM (hour 21..23, 0..4) -> "Good night" (શુભ રાત્રિ)
     */
    fun computeGreetingTimeSnapshot(
        nowMillis: Long = System.currentTimeMillis(),
        timeZoneMode: String = "IST",
        overridePeriod: String = "AUTO"
    ): GreetingTimeSnapshot {
        val tz = resolveGreetingTimeZone(timeZoneMode)
        val cal = Calendar.getInstance(tz).apply {
            timeInMillis = nowMillis
        }
        val actualHour = cal.get(Calendar.HOUR_OF_DAY)
        val effectiveHour = when (overridePeriod.uppercase(Locale.US)) {
            "MORNING" -> 8
            "AFTERNOON" -> 14
            "EVENING" -> 18
            "NIGHT" -> 22
            else -> actualHour
        }

        val (greetingEn, greetingGu, period) = when (effectiveHour) {
            in 5..11 -> Triple("Good morning", "સુપ્રભાત", "Morning")
            in 12..16 -> Triple("Good afternoon", "શુભ બપોર", "Afternoon")
            in 17..20 -> Triple("Good evening", "શુભ સાંજ", "Evening")
            else -> Triple("Good night", "શુભ રાત્રિ", "Night")
        }

        val timeFmt = SimpleDateFormat("hh:mm a", Locale.US).apply {
            timeZone = tz
        }
        val dateFmt = SimpleDateFormat("EEEE, dd MMM yyyy", Locale.US).apply {
            timeZone = tz
        }
        val tzShort = if (tz.id == "Asia/Kolkata" || tz.id == "Asia/Calcutta") {
            "IST"
        } else {
            tz.getDisplayName(false, TimeZone.SHORT, Locale.US)
        }

        return GreetingTimeSnapshot(
            greetingEnglish = greetingEn,
            greetingGujarati = greetingGu,
            periodLabel = period,
            formattedTime = timeFmt.format(Date(nowMillis)),
            formattedDate = dateFmt.format(Date(nowMillis)),
            timeZoneLabel = tzShort,
            hourOfDay = effectiveHour
        )
    }

    val SUPPORTED_CURRENCIES = listOf(
        CurrencyConfig("INR", "₹", "Indian Rupee (₹ INR)", true),
        CurrencyConfig("USD", "$", "US Dollar ($ USD)", false),
        CurrencyConfig("EUR", "€", "Euro (€ EUR)", false),
        CurrencyConfig("GBP", "£", "British Pound (£ GBP)", false),
        CurrencyConfig("AED", "AED ", "UAE Dirham (AED)", false),
        CurrencyConfig("JPY", "¥", "Japanese Yen (¥ JPY)", false),
        CurrencyConfig("CAD", "CA$", "Canadian Dollar (CA$ CAD)", false),
        CurrencyConfig("AUD", "A$", "Australian Dollar (A$ AUD)", false),
        CurrencyConfig("SGD", "S$", "Singapore Dollar (S$ SGD)", false)
    )

    @Volatile
    var currentCurrencyCode: String = "INR"
        private set

    @Volatile
    var currentCurrencySymbol: String = "₹"
        private set

    @Volatile
    var useIndianGrouping: Boolean = true
        private set

    @Volatile
    var hideBalances: Boolean = false

    fun setCurrency(code: String) {
        val found = SUPPORTED_CURRENCIES.find { it.code.equals(code, ignoreCase = true) }
            ?: SUPPORTED_CURRENCIES.first()
        currentCurrencyCode = found.code
        currentCurrencySymbol = found.symbol
        useIndianGrouping = found.useIndianGrouping
    }

    /**
     * Rounds a Double to 2 decimal places without Int overflow on huge amounts (> 2.14 Crore).
     */
    fun round2(value: Double): Double {
        if (!value.isFinite()) return 0.0
        val rounded = round(value * 100.0) / 100.0
        return if (abs(rounded) < 0.0001) 0.0 else rounded
    }

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val dateTimeFormat = SimpleDateFormat("dd/MM/yyyy, hh:mm a", Locale.getDefault())

    /**
     * Formats a numeric amount using the active currency symbol and grouping format.
     * Defaults to Indian number format with the rupee symbol (₹):
     * e.g., 100000 -> ₹1,00,000 or 1234567 -> ₹12,34,567.
     * Respects global [hideBalances] toggle unless [ignorePrivacy] is true.
     */
    fun formatInr(amount: Double, showDecimals: Boolean = false, ignorePrivacy: Boolean = false): String {
        val sym = currentCurrencySymbol
        if (hideBalances && !ignorePrivacy) {
            return "${sym}••••"
        }
        if (!amount.isFinite()) {
            return "${sym}0"
        }

        val normalized = if (abs(amount) < 0.005) 0.0 else amount
        val isNegative = normalized < 0.0
        val absAmount = abs(normalized)
        val wholePart = absAmount.toLong()
        val decimalPart = if (showDecimals && abs(absAmount - wholePart) >= 0.005) {
            String.format(Locale.US, "%.2f", absAmount - wholePart).removePrefix("0")
        } else ""

        val wholeString = wholePart.toString()
        val formattedWhole = if (wholeString.length <= 3) {
            wholeString
        } else if (useIndianGrouping) {
            val lastThree = wholeString.takeLast(3)
            val remaining = wholeString.dropLast(3)
            val grouped = remaining.reversed().chunked(2).joinToString(",").reversed()
            "$grouped,$lastThree"
        } else {
            wholeString.reversed().chunked(3).joinToString(",").reversed()
        }

        val prefix = if (isNegative) "-$sym" else sym
        return "$prefix$formattedWhole$decimalPart"
    }

    /**
     * Formats a timestamp into strict DD/MM/YYYY format.
     */
    fun formatDate(timestamp: Long): String {
        return synchronized(dateFormat) {
            dateFormat.format(Date(timestamp))
        }
    }

    /**
     * Formats a timestamp into DD/MM/YYYY, hh:mm a format.
     */
    fun formatDateTime(timestamp: Long): String {
        return synchronized(dateTimeFormat) {
            dateTimeFormat.format(Date(timestamp))
        }
    }

    /**
     * Parses a DD/MM/YYYY string into epoch milliseconds.
     */
    fun parseDate(dateStr: String): Long? {
        return try {
            val parsedDate = synchronized(dateFormat) {
                dateFormat.parse(dateStr.trim())
            } ?: return null
            val cal = Calendar.getInstance().apply { time = parsedDate }
            val now = Calendar.getInstance()
            if (cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
            ) {
                now.timeInMillis
            } else {
                cal.set(Calendar.HOUR_OF_DAY, 12)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Normalizes a timestamp to start of day (00:00:00.000).
     */
    fun startOfDayMillis(timestamp: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    /**
     * Normalizes a timestamp to end of day (23:59:59.999).
     */
    fun endOfDayMillis(timestamp: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
    }

    /**
     * Advances a recurring due date by [frequency] ("DAILY", "WEEKLY", "MONTHLY", "YEARLY").
     * Safely clamps month-end dates (e.g., Jan 31 -> Feb 28/29 -> Mar 31 when [anchorDayOfMonth] is 31).
     */
    fun advanceRecurringDate(
        currentDue: Long,
        frequency: String,
        anchorDayOfMonth: Int? = null
    ): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = currentDue }
        val originalDay = anchorDayOfMonth ?: cal.get(Calendar.DAY_OF_MONTH)
        when (frequency.uppercase(Locale.US)) {
            "DAILY" -> cal.add(Calendar.DAY_OF_YEAR, 1)
            "WEEKLY" -> cal.add(Calendar.DAY_OF_YEAR, 7)
            "MONTHLY" -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.add(Calendar.MONTH, 1)
                val maxDayInTargetMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                cal.set(Calendar.DAY_OF_MONTH, minOf(originalDay, maxDayInTargetMonth))
            }
            "YEARLY" -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.add(Calendar.YEAR, 1)
                val maxDayInTargetMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                cal.set(Calendar.DAY_OF_MONTH, minOf(originalDay, maxDayInTargetMonth))
            }
            else -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.add(Calendar.MONTH, 1)
                val maxDayInTargetMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                cal.set(Calendar.DAY_OF_MONTH, minOf(originalDay, maxDayInTargetMonth))
            }
        }
        return cal.timeInMillis
    }

    /**
     * Determines due date status (Overdue, Due Today, Due Soon, Due Later, None).
     * Uses calendar-day boundaries so midnight (00:00:00.000) and end-of-day (23:59:59.999)
     * timestamps are classified deterministically.
     */
    fun calculateDueStatus(dueDate: Long?, nowMillis: Long = System.currentTimeMillis()): com.example.data.DueStatus {
        if (dueDate == null || dueDate <= 0) return com.example.data.DueStatus.None
        val dueStart = startOfDayMillis(dueDate)
        val nowStart = startOfDayMillis(nowMillis)
        val diffDays = round((dueStart - nowStart).toDouble() / (24.0 * 60.0 * 60.0 * 1000.0)).toLong()

        return when {
            diffDays < 0L -> com.example.data.DueStatus.Overdue(maxOf(1L, abs(diffDays)))
            diffDays == 0L -> com.example.data.DueStatus.DueToday
            diffDays in 1L..7L -> com.example.data.DueStatus.DueSoon(diffDays)
            else -> com.example.data.DueStatus.DueLater(formatDate(dueDate))
        }
    }
}

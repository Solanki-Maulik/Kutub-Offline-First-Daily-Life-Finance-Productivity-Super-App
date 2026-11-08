package com.example.utils

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

data class FuelLogEntry(
    val id: Long = System.currentTimeMillis(),
    val vehicleName: String = "My Bike / Car",
    val odometerKm: Double,
    val endOdometerKm: Double = 0.0,
    val litres: Double,
    val totalCostInr: Double,
    val pricePerLitre: Double = if (litres > 0.0) totalCostInr / litres else 100.0,
    val account: String = "Cash",
    val dateMillis: Long = System.currentTimeMillis(),
    val nextServiceKm: Double = 0.0,
    val isDraft: Boolean = false,
    val note: String = ""
)

data class FuelLogWithMileage(
    val entry: FuelLogEntry,
    val distanceSincePreviousKm: Double?,
    val calculatedKmPerLitre: Double?,
    val runningCostPerKmInr: Double?
)

data class VehicleMileageSummary(
    val averageKmPerLitre: Double,
    val costPerKmInr: Double,
    val totalFuelSpendInr: Double,
    val totalDistanceKm: Double,
    val totalLitres: Double,
    val latestOdometerKm: Double,
    val nextServiceDueKm: Double,
    val kmUntilNextService: Double?,
    val logsWithMileage: List<FuelLogWithMileage>
)

data class MeditationSessionLog(
    val id: Long = System.currentTimeMillis(),
    val modeTitle: String, // "Meditation", "Deep Focus", "Breathing Calm"
    val durationMinutes: Int,
    val completedAtMillis: Long = System.currentTimeMillis(),
    val note: String = ""
)

data class BreathingPhase(
    val label: String,
    val subtitle: String,
    val secondsRemainingInPhase: Int,
    val phaseProgress: Float // 0f..1f
)

data class SubscriptionItem(
    val id: Long = System.currentTimeMillis(),
    val name: String,
    val amountInr: Double,
    val billingCycle: String = "MONTHLY", // "MONTHLY" or "YEARLY"
    val nextRenewalMillis: Long,
    val category: String = "Bills",
    val isActive: Boolean = true
) {
    val monthlyEquivalentInr: Double
        get() = if (billingCycle.equals("YEARLY", ignoreCase = true)) amountInr / 12.0 else amountInr
}

data class SafeToSpendSummary(
    val totalBalanceInr: Double,
    val unpaidBillsInr: Double,
    val monthlySubscriptionBurnInr: Double,
    val reservedForGoalsInr: Double,
    val netAvailableForMonthInr: Double,
    val daysRemainingInMonth: Int,
    val safeDailySpendInr: Double
)

data class GroceryMemoryItem(
    val id: Long = System.currentTimeMillis(),
    val name: String,
    val lastPriceInr: Double,
    val quantityLabel: String = "1 unit",
    val isChecked: Boolean = false
)

data class EmiPrepaymentResult(
    val monthlyEmi: Double,
    val regularTotalInterest: Double,
    val prepaidTotalInterest: Double,
    val interestSaved: Double,
    val originalMonths: Int,
    val newMonths: Int,
    val monthsSaved: Int
)

data class DailyWrapUpScore(
    val scorePercent: Int,
    val loggedExpenseToday: Boolean,
    val completedTasksToday: Int,
    val completedHabitsToday: Int,
    val meditationMinutesToday: Int,
    val statusHeadline: String
)

object DailyToolkitUtils {

    private const val PREFS_NAME = "kutub_daily_toolkit_prefs"
    private const val KEY_FUEL_LOGS = "fuel_logs_json"
    private const val KEY_MEDITATION_LOGS = "meditation_logs_json"
    private const val KEY_SUBSCRIPTIONS = "subscriptions_json"
    private const val KEY_GROCERY_ITEMS = "grocery_items_json"

    // ========================================================================
    // 1. Vehicle Fuel & Mileage Calculations
    // ========================================================================

    fun computeVehicleMileageStats(
        allLogs: List<FuelLogEntry>,
        vehicleFilter: String? = null
    ): VehicleMileageSummary {
        val filtered = allLogs
            .filter { vehicleFilter.isNullOrBlank() || it.vehicleName.equals(vehicleFilter, ignoreCase = true) }
            .sortedBy { it.odometerKm }

        if (filtered.isEmpty()) {
            return VehicleMileageSummary(
                averageKmPerLitre = 0.0,
                costPerKmInr = 0.0,
                totalFuelSpendInr = 0.0,
                totalDistanceKm = 0.0,
                totalLitres = 0.0,
                latestOdometerKm = 0.0,
                nextServiceDueKm = 0.0,
                kmUntilNextService = null,
                logsWithMileage = emptyList()
            )
        }

        // Group by vehicle so each vehicle's consecutive odometer readings compute accurate mileage
        val byVehicle = filtered.groupBy { it.vehicleName.trim().lowercase(Locale.ROOT) }
        val enrichedMap = mutableMapOf<Long, FuelLogWithMileage>()
        var totalMeasuredDistance = 0.0
        var totalMeasuredLitres = 0.0
        var totalMeasuredCost = 0.0

        byVehicle.values.forEach { vehicleLogs ->
            val sorted = vehicleLogs.sortedWith(compareBy({ if (it.odometerKm > 0.0) it.odometerKm else Double.MAX_VALUE }, { it.dateMillis }))
            for (i in sorted.indices) {
                val current = sorted[i]
                val prev = if (i > 0) sorted[i - 1] else null

                // Support both direct Start->End Odometer on a single entry (edited after a few days/weeks)
                // AND consecutive fill-up odometer readings
                val dist = when {
                    current.endOdometerKm > current.odometerKm && current.odometerKm > 0.0 -> {
                        current.endOdometerKm - current.odometerKm
                    }
                    prev != null && prev.odometerKm > 0.0 && current.odometerKm > prev.odometerKm -> {
                        current.odometerKm - prev.odometerKm
                    }
                    else -> null
                }

                val kmPerL = if (dist != null && dist > 0.0 && current.litres > 0.0) {
                    dist / current.litres
                } else null

                val costPerKm = if (dist != null && dist > 0.0 && current.totalCostInr > 0.0) {
                    current.totalCostInr / dist
                } else null

                if (dist != null && dist > 0.0 && current.litres > 0.0) {
                    totalMeasuredDistance += dist
                    totalMeasuredLitres += current.litres
                    totalMeasuredCost += current.totalCostInr
                }

                enrichedMap[current.id] = FuelLogWithMileage(
                    entry = current,
                    distanceSincePreviousKm = dist,
                    calculatedKmPerLitre = kmPerL,
                    runningCostPerKmInr = costPerKm
                )
            }
        }

        val avgKmPerLitre = if (totalMeasuredLitres > 0.0) {
            totalMeasuredDistance / totalMeasuredLitres
        } else 0.0

        val avgCostPerKm = if (totalMeasuredDistance > 0.0) {
            totalMeasuredCost / totalMeasuredDistance
        } else 0.0

        val latestOdo = filtered.maxOfOrNull { max(it.odometerKm, it.endOdometerKm) } ?: 0.0
        val nextServiceTarget = filtered.mapNotNull { it.nextServiceKm.takeIf { km -> km > 0.0 } }.maxOrNull() ?: 0.0
        val kmUntilService = if (nextServiceTarget > 0.0 && latestOdo > 0.0) {
            nextServiceTarget - latestOdo
        } else null

        val logsDescending = filtered
            .sortedByDescending { it.dateMillis }
            .mapNotNull { enrichedMap[it.id] }

        return VehicleMileageSummary(
            averageKmPerLitre = avgKmPerLitre,
            costPerKmInr = avgCostPerKm,
            totalFuelSpendInr = filtered.sumOf { it.totalCostInr },
            totalDistanceKm = totalMeasuredDistance,
            totalLitres = filtered.sumOf { it.litres },
            latestOdometerKm = latestOdo,
            nextServiceDueKm = nextServiceTarget,
            kmUntilNextService = kmUntilService,
            logsWithMileage = logsDescending
        )
    }

    // ========================================================================
    // 2. Live Meditation & Breathing Countdown Rhythm (4-4-6 Calming Cycle)
    // ========================================================================

    /**
     * 14-second calming breathing cycle:
     * - 0..3s (4s): Breathe In Slowly (Inhale)
     * - 4..7s (4s): Hold Gently (Hold)
     * - 8..13s (6s): Breathe Out Completely (Exhale)
     */
    fun computeBreathingPhase(elapsedSeconds: Int): BreathingPhase {
        val safeSec = max(0, elapsedSeconds)
        val cyclePos = safeSec % 14
        return when {
            cyclePos < 4 -> {
                val rem = 4 - cyclePos
                val prog = (cyclePos + 1) / 4f
                BreathingPhase(
                    label = "Breathe In",
                    subtitle = "Inhale slowly through your nose (4s)",
                    secondsRemainingInPhase = rem,
                    phaseProgress = prog
                )
            }
            cyclePos < 8 -> {
                val inHold = cyclePos - 4
                val rem = 4 - inHold
                BreathingPhase(
                    label = "Hold Gently",
                    subtitle = "Keep your shoulders relaxed (4s)",
                    secondsRemainingInPhase = rem,
                    phaseProgress = 1f
                )
            }
            else -> {
                val inExhale = cyclePos - 8
                val rem = 6 - inExhale
                val prog = 1f - ((inExhale + 1) / 6f).coerceIn(0f, 1f)
                BreathingPhase(
                    label = "Breathe Out",
                    subtitle = "Exhale slowly and release tension (6s)",
                    secondsRemainingInPhase = rem,
                    phaseProgress = prog
                )
            }
        }
    }

    fun formatTimerMmSs(totalSeconds: Int): String {
        val clamped = max(0, totalSeconds)
        val mins = clamped / 60
        val secs = clamped % 60
        return String.format(Locale.US, "%02d:%02d", mins, secs)
    }

    // ========================================================================
    // 3. Safe-to-Spend Daily Allowance & Subscription Burn
    // ========================================================================

    fun computeSafeToSpend(
        totalBalanceInr: Double,
        unpaidBillsInr: Double,
        subscriptions: List<SubscriptionItem>,
        reservedForGoalsInr: Double = 0.0,
        nowMillis: Long = System.currentTimeMillis()
    ): SafeToSpendSummary {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val todayDay = cal.get(Calendar.DAY_OF_MONTH)
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val daysRemaining = (maxDay - todayDay + 1).coerceAtLeast(1)

        val activeSubsBurn = subscriptions
            .filter { it.isActive }
            .sumOf { it.monthlyEquivalentInr }

        val netAvailable = (totalBalanceInr - unpaidBillsInr - activeSubsBurn - reservedForGoalsInr)
            .coerceAtLeast(0.0)
        val safePerDay = netAvailable / daysRemaining.toDouble()

        return SafeToSpendSummary(
            totalBalanceInr = totalBalanceInr,
            unpaidBillsInr = unpaidBillsInr,
            monthlySubscriptionBurnInr = activeSubsBurn,
            reservedForGoalsInr = reservedForGoalsInr,
            netAvailableForMonthInr = netAvailable,
            daysRemainingInMonth = daysRemaining,
            safeDailySpendInr = safePerDay
        )
    }

    fun daysUntilRenewal(nextRenewalMillis: Long, nowMillis: Long = System.currentTimeMillis()): Int {
        val diff = nextRenewalMillis - nowMillis
        return ceil(diff / (24.0 * 60.0 * 60.0 * 1000.0)).toInt()
    }

    // ========================================================================
    // 4. EMI & Loan Prepayment Saver Calculator
    // ========================================================================

    fun calculateEmiAndPrepayment(
        principal: Double,
        annualRatePercent: Double,
        tenureMonths: Int,
        extraMonthlyPayment: Double
    ): EmiPrepaymentResult {
        if (principal <= 0.0 || tenureMonths <= 0) {
            return EmiPrepaymentResult(0.0, 0.0, 0.0, 0.0, 0, 0, 0)
        }
        val monthlyRate = (annualRatePercent.coerceAtLeast(0.0) / 12.0) / 100.0
        val emi = if (monthlyRate == 0.0) {
            principal / tenureMonths
        } else {
            val factor = (1.0 + monthlyRate).pow(tenureMonths.toDouble())
            (principal * monthlyRate * factor) / (factor - 1.0)
        }

        val regularTotalPayment = emi * tenureMonths
        val regularTotalInterest = (regularTotalPayment - principal).coerceAtLeast(0.0)

        if (extraMonthlyPayment <= 0.0) {
            return EmiPrepaymentResult(
                monthlyEmi = emi,
                regularTotalInterest = regularTotalInterest,
                prepaidTotalInterest = regularTotalInterest,
                interestSaved = 0.0,
                originalMonths = tenureMonths,
                newMonths = tenureMonths,
                monthsSaved = 0
            )
        }

        var balance = principal
        var months = 0
        var prepaidInterest = 0.0
        val actualMonthlyPay = emi + extraMonthlyPayment

        while (balance > 0.01 && months < tenureMonths * 2) {
            val interestForMonth = balance * monthlyRate
            prepaidInterest += interestForMonth
            val principalPaid = (actualMonthlyPay - interestForMonth).coerceAtLeast(1.0)
            balance -= principalPaid
            months++
        }

        val interestSaved = (regularTotalInterest - prepaidInterest).coerceAtLeast(0.0)
        val monthsSaved = (tenureMonths - months).coerceAtLeast(0)

        return EmiPrepaymentResult(
            monthlyEmi = emi,
            regularTotalInterest = regularTotalInterest,
            prepaidTotalInterest = prepaidInterest,
            interestSaved = interestSaved,
            originalMonths = tenureMonths,
            newMonths = months,
            monthsSaved = monthsSaved
        )
    }

    // ========================================================================
    // 5. WhatsApp Polite Reminder Generator (English, Gujarati, Hindi)
    // ========================================================================

    fun buildWhatsAppReminderText(
        personName: String,
        amountInr: Double,
        upiId: String = "",
        language: String = "ENGLISH",
        note: String = ""
    ): String {
        val cleanName = personName.trim().ifBlank { "Friend" }
        val amtFormatted = FormatUtils.formatInr(amountInr)
        val notePart = if (note.isNotBlank()) " ($note)" else ""
        val upiPart = if (upiId.isNotBlank()) "\nUPI ID: $upiId" else ""

        return when (language.uppercase(Locale.ROOT)) {
            "GUJARATI" -> {
                "નમસ્તે $cleanName,\n" +
                    "કુતુબ (Kutub) ખાતા મુજબ બાકી રકમ $amtFormatted$notePart ની નમ્ર યાદ અપાવું છું.$upiPart\n" +
                    "અનુકૂળતાએ ચુકવણી કરવા વિનંતી. આભાર! 🙏"
            }
            "HINDI" -> {
                "नमस्ते $cleanName,\n" +
                    "कुतुब (Kutub) खाते के अनुसार आपकी बकाया राशि $amtFormatted$notePart का विनम्र स्मरण है।$upiPart\n" +
                    "कृपया सुविधा अनुसार भुगतान करें। धन्यवाद! 🙏"
            }
            else -> {
                "Hi $cleanName,\n" +
                    "Friendly reminder from my Kutub ledger for the pending amount of $amtFormatted$notePart.$upiPart\n" +
                    "Please let me know once settled. Thank you! 🙏"
            }
        }
    }

    // ========================================================================
    // 6. Daily Wrap-Up Score Computation
    // ========================================================================

    fun computeDailyWrapUpScore(
        loggedExpenseToday: Boolean,
        completedTasksToday: Int,
        completedHabitsToday: Int,
        meditationMinutesToday: Int
    ): DailyWrapUpScore {
        var score = 0
        if (loggedExpenseToday) score += 30
        if (completedTasksToday > 0) score += (completedTasksToday * 15).coerceAtMost(30)
        if (completedHabitsToday > 0) score += (completedHabitsToday * 15).coerceAtMost(20)
        if (meditationMinutesToday > 0) score += 20

        val clamped = score.coerceIn(0, 100)
        val headline = when {
            clamped >= 85 -> "Outstanding Day! Your money, habits & mindfulness are on track."
            clamped >= 55 -> "Good Progress! Complete 1 more habit or meditation session to hit 85%+."
            clamped > 0 -> "Day Started! Log today's expenses and take a 5-min meditation break."
            else -> "Ready for Your Daily Check-In! Log expenses, tasks & meditation below."
        }

        return DailyWrapUpScore(
            scorePercent = clamped,
            loggedExpenseToday = loggedExpenseToday,
            completedTasksToday = completedTasksToday,
            completedHabitsToday = completedHabitsToday,
            meditationMinutesToday = meditationMinutesToday,
            statusHeadline = headline
        )
    }

    // ========================================================================
    // 7. Local Persistence in SharedPreferences (JSON)
    // ========================================================================

    fun loadFuelLogs(context: Context): List<FuelLogEntry> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_FUEL_LOGS, null)
        if (raw.isNullOrBlank()) {
            val now = System.currentTimeMillis()
            val dayMs = 24L * 60L * 60L * 1000L
            val defaults = listOf(
                FuelLogEntry(
                    id = now - 10 * dayMs,
                    vehicleName = "Honda Activa",
                    odometerKm = 12400.0,
                    litres = 4.0,
                    totalCostInr = 384.0,
                    pricePerLitre = 96.0,
                    account = "UPI",
                    dateMillis = now - 10 * dayMs,
                    nextServiceKm = 15000.0,
                    note = "Full tank"
                ),
                FuelLogEntry(
                    id = now - 2 * dayMs,
                    vehicleName = "Honda Activa",
                    odometerKm = 12610.0,
                    litres = 4.0,
                    totalCostInr = 384.0,
                    pricePerLitre = 96.0,
                    account = "Cash",
                    dateMillis = now - 2 * dayMs,
                    nextServiceKm = 15000.0,
                    note = "City commute fill-up"
                )
            )
            saveFuelLogs(context, defaults)
            return defaults
        }
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(
                        FuelLogEntry(
                            id = o.optLong("id", System.currentTimeMillis() + i),
                            vehicleName = o.optString("vehicleName", "My Vehicle"),
                            odometerKm = o.optDouble("odometerKm", 0.0),
                            endOdometerKm = o.optDouble("endOdometerKm", 0.0),
                            litres = o.optDouble("litres", 0.0),
                            totalCostInr = o.optDouble("totalCostInr", 0.0),
                            pricePerLitre = o.optDouble("pricePerLitre", 96.0),
                            account = o.optString("account", "Cash"),
                            dateMillis = o.optLong("dateMillis", System.currentTimeMillis()),
                            nextServiceKm = o.optDouble("nextServiceKm", 0.0),
                            isDraft = o.optBoolean("isDraft", false),
                            note = o.optString("note", "")
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveFuelLogs(context: Context, logs: List<FuelLogEntry>) {
        val arr = JSONArray()
        logs.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("vehicleName", item.vehicleName)
                    put("odometerKm", item.odometerKm)
                    put("endOdometerKm", item.endOdometerKm)
                    put("litres", item.litres)
                    put("totalCostInr", item.totalCostInr)
                    put("pricePerLitre", item.pricePerLitre)
                    put("account", item.account)
                    put("dateMillis", item.dateMillis)
                    put("nextServiceKm", item.nextServiceKm)
                    put("isDraft", item.isDraft)
                    put("note", item.note)
                }
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FUEL_LOGS, arr.toString())
            .apply()
    }

    fun loadMeditationLogs(context: Context): List<MeditationSessionLog> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_MEDITATION_LOGS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(
                        MeditationSessionLog(
                            id = o.optLong("id", System.currentTimeMillis() + i),
                            modeTitle = o.optString("modeTitle", "Meditation"),
                            durationMinutes = o.optInt("durationMinutes", 10),
                            completedAtMillis = o.optLong("completedAtMillis", System.currentTimeMillis()),
                            note = o.optString("note", "")
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveMeditationLogs(context: Context, logs: List<MeditationSessionLog>) {
        val arr = JSONArray()
        logs.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("modeTitle", item.modeTitle)
                    put("durationMinutes", item.durationMinutes)
                    put("completedAtMillis", item.completedAtMillis)
                    put("note", item.note)
                }
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MEDITATION_LOGS, arr.toString())
            .apply()
    }

    fun loadSubscriptions(context: Context): List<SubscriptionItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_SUBSCRIPTIONS, null)
        if (raw.isNullOrBlank()) {
            val now = System.currentTimeMillis()
            val dayMs = 24L * 60L * 60L * 1000L
            val defaults = listOf(
                SubscriptionItem(
                    id = now - 1000,
                    name = "Mobile / WiFi Recharge",
                    amountInr = 299.0,
                    billingCycle = "MONTHLY",
                    nextRenewalMillis = now + 3 * dayMs,
                    category = "Bills",
                    isActive = true
                ),
                SubscriptionItem(
                    id = now - 2000,
                    name = "OTT / Music Membership",
                    amountInr = 199.0,
                    billingCycle = "MONTHLY",
                    nextRenewalMillis = now + 9 * dayMs,
                    category = "Entertainment",
                    isActive = true
                )
            )
            saveSubscriptions(context, defaults)
            return defaults
        }
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(
                        SubscriptionItem(
                            id = o.optLong("id", System.currentTimeMillis() + i),
                            name = o.optString("name", "Subscription"),
                            amountInr = o.optDouble("amountInr", 0.0),
                            billingCycle = o.optString("billingCycle", "MONTHLY"),
                            nextRenewalMillis = o.optLong("nextRenewalMillis", System.currentTimeMillis()),
                            category = o.optString("category", "Bills"),
                            isActive = o.optBoolean("isActive", true)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveSubscriptions(context: Context, list: List<SubscriptionItem>) {
        val arr = JSONArray()
        list.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("name", item.name)
                    put("amountInr", item.amountInr)
                    put("billingCycle", item.billingCycle)
                    put("nextRenewalMillis", item.nextRenewalMillis)
                    put("category", item.category)
                    put("isActive", item.isActive)
                }
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SUBSCRIPTIONS, arr.toString())
            .apply()
    }

    fun loadGroceryItems(context: Context): List<GroceryMemoryItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_GROCERY_ITEMS, null)
        if (raw.isNullOrBlank()) {
            val now = System.currentTimeMillis()
            val defaults = listOf(
                GroceryMemoryItem(id = now + 1, name = "Milk (1 Litre)", lastPriceInr = 66.0, quantityLabel = "1 L", isChecked = true),
                GroceryMemoryItem(id = now + 2, name = "Vegetables & Fruits", lastPriceInr = 150.0, quantityLabel = "1 bag", isChecked = true),
                GroceryMemoryItem(id = now + 3, name = "Cooking Oil (1 L)", lastPriceInr = 165.0, quantityLabel = "1 pouch", isChecked = false),
                GroceryMemoryItem(id = now + 4, name = "Atta / Wheat Flour (5 kg)", lastPriceInr = 240.0, quantityLabel = "5 kg", isChecked = false)
            )
            saveGroceryItems(context, defaults)
            return defaults
        }
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(
                        GroceryMemoryItem(
                            id = o.optLong("id", System.currentTimeMillis() + i),
                            name = o.optString("name", "Item"),
                            lastPriceInr = o.optDouble("lastPriceInr", 0.0),
                            quantityLabel = o.optString("quantityLabel", "1 unit"),
                            isChecked = o.optBoolean("isChecked", false)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveGroceryItems(context: Context, list: List<GroceryMemoryItem>) {
        val arr = JSONArray()
        list.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("name", item.name)
                    put("lastPriceInr", item.lastPriceInr)
                    put("quantityLabel", item.quantityLabel)
                    put("isChecked", item.isChecked)
                }
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_GROCERY_ITEMS, arr.toString())
            .apply()
    }
}

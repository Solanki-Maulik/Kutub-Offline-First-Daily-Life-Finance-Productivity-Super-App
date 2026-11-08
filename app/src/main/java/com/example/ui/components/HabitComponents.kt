package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.*
import com.example.utils.FormatUtils
import com.example.utils.HabitUtils

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateHabitDialog(
    initialHabit: Habit? = null,
    onDismiss: () -> Unit,
    onSave: (Habit) -> Unit
) {
    var name by remember { mutableStateOf(initialHabit?.name ?: "") }
    var iconName by remember { mutableStateOf(initialHabit?.iconName ?: "SelfImprovement") }
    var colorHex by remember { mutableStateOf(initialHabit?.colorHex ?: "#2E7D32") }
    var frequencyType by remember { mutableStateOf(initialHabit?.frequencyType ?: "DAILY") }
    var selectedDays by remember {
        val parsed = (initialHabit?.scheduledDaysCsv ?: "1,2,3,4,5,6,7")
            .split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .toSet()
        mutableStateOf(if (parsed.isEmpty()) setOf(1, 2, 3, 4, 5, 6, 7) else parsed)
    }
    var timesPerWeek by remember { mutableIntStateOf(initialHabit?.timesPerWeek ?: 5) }
    var targetCountInput by remember { mutableStateOf((initialHabit?.targetCount ?: 1).toString()) }
    var unitLabel by remember { mutableStateOf(initialHabit?.unitLabel ?: "") }
    var moneyRuleType by remember { mutableStateOf(initialHabit?.moneyRuleType ?: "NONE") }
    var moneyThresholdInput by remember {
        mutableStateOf(if ((initialHabit?.moneyThreshold ?: 0.0) > 0) initialHabit!!.moneyThreshold.toString() else "100")
    }
    var reminderEnabled by remember { mutableStateOf(initialHabit?.reminderEnabled ?: false) }
    var reminderHour by remember { mutableIntStateOf(initialHabit?.reminderHour ?: 9) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val dayLabels = listOf(
        1 to "M",
        2 to "T",
        3 to "W",
        4 to "T",
        5 to "F",
        6 to "S",
        7 to "S"
    )

    fun submitHabit() {
        if (name.isBlank()) {
            errorMsg = "Please enter a habit name."
        } else {
            val target = (targetCountInput.toIntOrNull() ?: 1).coerceAtLeast(1)
            val threshold = moneyThresholdInput.toDoubleOrNull() ?: 0.0
            onSave(
                Habit(
                    id = initialHabit?.id ?: 0L,
                    name = name.trim(),
                    iconName = iconName,
                    colorHex = colorHex,
                    frequencyType = frequencyType,
                    scheduledDaysCsv = selectedDays.sorted().joinToString(","),
                    timesPerWeek = timesPerWeek,
                    targetCount = target,
                    unitLabel = unitLabel.trim(),
                    reminderEnabled = reminderEnabled,
                    reminderHour = reminderHour,
                    reminderMinute = 0,
                    moneyRuleType = moneyRuleType,
                    moneyThreshold = threshold,
                    createdAt = initialHabit?.createdAt ?: System.currentTimeMillis()
                )
            )
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(8.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("habit_dialog_back_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        AutoSizeSingleLineText(
                            text = if (initialHabit == null) "New Habit" else "Edit Habit",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { submitHabit() },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .testTag("habit_dialog_top_save_btn")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Save Habit", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save", fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }

                HorizontalDivider()

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item { Spacer(Modifier.height(4.dp)) }

                    // Quick Presets (including Money-Linked Habits!)
                    if (initialHabit == null) {
                        item {
                            Text(
                                "Quick Presets (1-Tap Fill):",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AssistChip(
                                    onClick = {
                                        name = "No-Spend Day"
                                        iconName = "MoneyOff"
                                        colorHex = "#2E7D32"
                                        targetCountInput = "1"
                                        unitLabel = ""
                                        moneyRuleType = "NO_SPEND_DAY"
                                    },
                                    label = { Text("💸 No-Spend Day (Auto)") },
                                    modifier = Modifier.testTag("habit_preset_no_spend")
                                )
                                AssistChip(
                                    onClick = {
                                        name = "Save ₹100 Today"
                                        iconName = "Savings"
                                        colorHex = "#1565C0"
                                        targetCountInput = "1"
                                        unitLabel = ""
                                        moneyRuleType = "SAVED_TODAY"
                                        moneyThresholdInput = "100"
                                    },
                                    label = { Text("🏦 Save ₹100 Today (Auto)") },
                                    modifier = Modifier.testTag("habit_preset_save_100")
                                )
                                AssistChip(
                                    onClick = {
                                        name = "Drink 8 Glasses Water"
                                        iconName = "WaterDrop"
                                        colorHex = "#00897B"
                                        targetCountInput = "8"
                                        unitLabel = "glasses"
                                        moneyRuleType = "NONE"
                                    },
                                    label = { Text("💧 Drink 8 Glasses") },
                                    modifier = Modifier.testTag("habit_preset_water")
                                )
                                AssistChip(
                                    onClick = {
                                        name = "Read 20 Pages"
                                        iconName = "MenuBook"
                                        colorHex = "#7B1FA2"
                                        targetCountInput = "20"
                                        unitLabel = "pages"
                                        moneyRuleType = "NONE"
                                    },
                                    label = { Text("📚 Read 20 Pages") }
                                )
                            }
                        }
                    }

                    // Habit Name
                    item {
                        OutlinedTextField(
                            value = name,
                            onValueChange = {
                                name = it
                                errorMsg = null
                            },
                            label = { Text("Habit Name (e.g. No-Spend Day, Drink Water)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("habit_name_input")
                        )
                    }

                    // Icon & Color Pickers
                    item {
                        Text("Icon & Color", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HabitUtils.HABIT_ICONS.forEach { (key, vec) ->
                                val selected = iconName == key
                                Surface(
                                    shape = CircleShape,
                                    color = if (selected) HabitUtils.parseColor(colorHex) else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clickable { iconName = key }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            vec,
                                            contentDescription = key,
                                            tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            HabitUtils.HABIT_COLORS.forEach { (hex, label) ->
                                val selected = colorHex.equals(hex, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(HabitUtils.parseColor(hex))
                                        .border(
                                            width = if (selected) 3.dp else 1.dp,
                                            color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable { colorHex = hex },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (selected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = label,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Frequency Selector
                    item {
                        Text("Frequency", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "DAILY" to "Every Day",
                                "SPECIFIC_DAYS" to "Specific Weekdays",
                                "TIMES_PER_WEEK" to "X Times / Week"
                            ).forEach { (id, label) ->
                                FilterChip(
                                    selected = frequencyType == id,
                                    onClick = { frequencyType = id },
                                    label = { Text(label) }
                                )
                            }
                        }

                        if (frequencyType == "SPECIFIC_DAYS") {
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                dayLabels.forEach { (dow, shortLabel) ->
                                    val isSel = selectedDays.contains(dow)
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clickable {
                                                if (isSel && selectedDays.size > 1) {
                                                    selectedDays = selectedDays - dow
                                                } else {
                                                    selectedDays = selectedDays + dow
                                                }
                                            }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                shortLabel,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        } else if (frequencyType == "TIMES_PER_WEEK") {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Target days per week:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                (1..7).forEach { n ->
                                    FilterChip(
                                        selected = timesPerWeek == n,
                                        onClick = { timesPerWeek = n },
                                        label = { Text("${n}x / wk") }
                                    )
                                }
                            }
                        }
                    }

                    // Daily Target (1 = one-tap check-off, >1 = count-based with +/- buttons)
                    item {
                        Text(
                            "Daily Target (1 for simple check-off, >1 for count-based +/-)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = targetCountInput,
                                onValueChange = { targetCountInput = it },
                                label = { Text("Target Count") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("habit_target_count_input")
                            )
                            OutlinedTextField(
                                value = unitLabel,
                                onValueChange = { unitLabel = it },
                                label = { Text("Unit (e.g. glasses, mins)") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1.3f)
                                    .testTag("habit_unit_input")
                            )
                        }
                    }

                    // Optional Money-Linked Habit Rule
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        "Optional Money-Linked Rule (Auto-tracks from Transactions)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(
                                        "NONE" to "None (Manual)",
                                        "NO_SPEND_DAY" to "No-Spend Day (₹0 Expense)",
                                        "SAVED_TODAY" to "Saved ≥ ₹X Today",
                                        "SPEND_UNDER_LIMIT" to "Spend ≤ ₹X Today"
                                    ).forEach { (ruleId, label) ->
                                        FilterChip(
                                            selected = moneyRuleType == ruleId,
                                            onClick = { moneyRuleType = ruleId },
                                            label = { Text(label) }
                                        )
                                    }
                                }
                                if (moneyRuleType == "SAVED_TODAY" || moneyRuleType == "SPEND_UNDER_LIMIT") {
                                    OutlinedTextField(
                                        value = moneyThresholdInput,
                                        onValueChange = { moneyThresholdInput = it },
                                        label = {
                                            Text(
                                                if (moneyRuleType == "SAVED_TODAY") "Minimum Daily Savings Target (₹)"
                                                else "Maximum Daily Spend Limit (₹)"
                                            )
                                        },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }

                    // Daily Reminder
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Daily Reminder Notification", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Remind at ${String.format("%02d:00", reminderHour)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = reminderEnabled,
                                onCheckedChange = { reminderEnabled = it }
                            )
                        }
                    }

                    if (errorMsg != null) {
                        item {
                            Text(errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    item { Spacer(Modifier.height(16.dp)) }
                }

                Surface(tonalElevation = 8.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(0.38f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Back", maxLines = 1)
                        }
                        Button(
                            onClick = { submitHabit() },
                            modifier = Modifier
                                .weight(0.62f)
                                .testTag("habit_save_btn"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (initialHabit == null) "Save Habit" else "Save Changes", fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HabitHeatmapGrid(
    stats: HabitComputedStats,
    selectedDateIso: String,
    onSelectDate: (String) -> Unit
) {
    val habitColor = HabitUtils.parseColor(stats.habit.colorHex)
    // Display last 35 days (5 weeks x 7 columns) in a compact interactive calendar heatmap
    val last35Days = remember { HabitUtils.getLastNDaysIso(35).reversed() }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "35-Day Consistency Heatmap (Tap any day to edit)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(habitColor)
                    )
                    Text("Done", fontSize = 10.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFFFFB300))
                    )
                    Text("Rest/Skip", fontSize = 10.sp)
                }
            }
        }

        val rows = last35Days.chunked(7)
        rows.forEach { weekDays ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                weekDays.forEach { dateIso ->
                    val status = stats.heatmapMap[dateIso] ?: "NONE"
                    val isSelected = dateIso == selectedDateIso
                    val cellColor = when (status) {
                        "COMPLETED" -> habitColor
                        "SKIPPED" -> Color(0xFFFFB300)
                        "PARTIAL" -> habitColor.copy(alpha = 0.4f)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    }
                    val dayNum = dateIso.takeLast(2).removePrefix("0")
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(cellColor)
                            .border(
                                width = if (isSelected) 2.dp else 0.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable { onSelectDate(dateIso) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (status == "SKIPPED") "Zz" else dayNum,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected || status == "COMPLETED") FontWeight.Bold else FontWeight.Normal,
                            color = if (status == "COMPLETED" || status == "SKIPPED") Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DailyHabitsDashboardCard(
    habits: List<Habit>,
    habitLogs: List<HabitLog>,
    transactions: List<Transaction>,
    goalContributions: List<GoalContribution>,
    onToggleHabitToday: (Habit) -> Unit,
    onAdjustHabitCountToday: (Habit, Int) -> Unit,
    onOpenHabitsScreen: () -> Unit
) {
    val todayIso = remember { HabitUtils.getTodayIso() }
    val todayStats = remember(habits, habitLogs, transactions, goalContributions, todayIso) {
        habits.map { habit ->
            val logs = habitLogs.filter { it.habitId == habit.id }
            HabitUtils.computeHabitStats(habit, todayIso, logs, transactions, goalContributions)
        }
    }

    val completedToday = todayStats.count { it.selectedDayStatus == "COMPLETED" }
    val consistencyScore = if (todayStats.isEmpty()) 0 else todayStats.map { it.monthCompletionPercent }.average().toInt()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_habits_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.LocalFireDepartment,
                        contentDescription = null,
                        tint = Color(0xFFE65100),
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            "Today's Habits & Streaks",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (todayStats.isNotEmpty()) {
                            Text(
                                "$completedToday/${todayStats.size} done today • $consistencyScore% consistency",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                TextButton(
                    onClick = onOpenHabitsScreen,
                    modifier = Modifier.testTag("home_habits_view_all")
                ) {
                    Text("View All")
                }
            }

            Spacer(Modifier.height(10.dp))

            if (todayStats.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenHabitsScreen() }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Build Financial & Life Habits",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Track 'No-Spend Day', 'Save ₹100', water intake & daily streaks",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Default.AddCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    todayStats.take(4).forEach { st ->
                        val hColor = HabitUtils.parseColor(st.habit.colorHex)
                        val isDone = st.selectedDayStatus == "COMPLETED"
                        val isSkipped = st.selectedDayStatus == "SKIPPED"
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isDone) hColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = hColor.copy(alpha = 0.2f),
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                HabitUtils.getIcon(st.habit.iconName),
                                                contentDescription = null,
                                                tint = hColor,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Column {
                                        Text(
                                            st.habit.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val sub = st.autoMoneySummary
                                            ?: "🔥 ${st.currentStreak}d streak" +
                                            if (st.habit.targetCount > 1) " • ${st.selectedDayCount}/${st.habit.targetCount} ${st.habit.unitLabel}" else ""
                                        Text(
                                            sub,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (st.isAutoMoneySatisfied) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                if (st.habit.targetCount > 1) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        FilledTonalIconButton(
                                            onClick = { onAdjustHabitCountToday(st.habit, -1) },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(Icons.Default.Remove, contentDescription = "Minus", modifier = Modifier.size(14.dp))
                                        }
                                        Text(
                                            "${st.selectedDayCount}/${st.habit.targetCount}",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        FilledTonalIconButton(
                                            onClick = { onAdjustHabitCountToday(st.habit, 1) },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = "Plus", modifier = Modifier.size(14.dp))
                                        }
                                    }
                                } else {
                                    IconButton(
                                        onClick = { onToggleHabitToday(st.habit) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            when {
                                                isDone -> Icons.Default.CheckCircle
                                                isSkipped -> Icons.Default.Hotel
                                                else -> Icons.Default.RadioButtonUnchecked
                                            },
                                            contentDescription = "Check off ${st.habit.name}",
                                            tint = if (isDone) hColor else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

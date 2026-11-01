package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.data.*
import com.example.ui.components.CreateHabitDialog
import com.example.ui.components.HabitHeatmapGrid
import com.example.utils.HabitUtils
import com.example.viewmodel.FinanceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitsScreen(viewModel: FinanceViewModel, navController: NavController) {
    val habits by viewModel.activeHabits.collectAsStateWithLifecycle()
    val habitLogs by viewModel.habitLogs.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val goalContributions by viewModel.allGoalContributions.collectAsStateWithLifecycle()

    var selectedDateIso by remember { mutableStateOf(HabitUtils.getTodayIso()) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingHabit by remember { mutableStateOf<Habit?>(null) }
    var habitPendingDelete by remember { mutableStateOf<Habit?>(null) }

    val last14Days = remember { HabitUtils.getLastNDaysIso(14) }

    val computedHabitStats = remember(habits, habitLogs, transactions, goalContributions, selectedDateIso) {
        habits.map { habit ->
            val logs = habitLogs.filter { it.habitId == habit.id }
            HabitUtils.computeHabitStats(habit, selectedDateIso, logs, transactions, goalContributions)
        }
    }

    val overallConsistency = remember(computedHabitStats) {
        if (computedHabitStats.isEmpty()) 0
        else computedHabitStats.map { it.monthCompletionPercent }.average().toInt()
    }
    val completedSelectedDay = remember(computedHabitStats) {
        computedHabitStats.count { it.selectedDayStatus == "COMPLETED" }
    }
    val bestActiveStreak = remember(computedHabitStats) {
        computedHabitStats.maxOfOrNull { it.currentStreak } ?: 0
    }

    if (showCreateDialog || editingHabit != null) {
        CreateHabitDialog(
            initialHabit = editingHabit,
            onDismiss = {
                showCreateDialog = false
                editingHabit = null
            },
            onSave = { habit ->
                if (editingHabit == null) {
                    viewModel.addHabit(habit)
                } else {
                    viewModel.updateHabit(habit)
                }
                showCreateDialog = false
                editingHabit = null
            }
        )
    }

    if (habitPendingDelete != null) {
        AlertDialog(
            onDismissRequest = { habitPendingDelete = null },
            title = { Text("Delete Habit?") },
            text = {
                Text("Are you sure you want to delete '${habitPendingDelete?.name}' and all its streak history?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        habitPendingDelete?.let { viewModel.deleteHabit(it.id) }
                        habitPendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { habitPendingDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {
                    Column {
                        Text("Habits & Consistency", fontWeight = FontWeight.Bold)
                        Text(
                            HabitUtils.formatIsoToShortLabel(selectedDateIso),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.testTag("habits_back_btn")
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    editingHabit = null
                    showCreateDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New Habit") },
                modifier = Modifier.testTag("habits_fab_add")
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            // Overall Consistency Score Hero Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("habits_consistency_hero_card"),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "Overall Consistency Score",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                )
                                Text(
                                    "$overallConsistency%",
                                    style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.LocalFireDepartment,
                                        contentDescription = null,
                                        tint = Color(0xFFE65100),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        "Best Streak: ${bestActiveStreak}d",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { (overallConsistency / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Completed on ${HabitUtils.formatIsoToIndianDate(selectedDateIso)}: $completedSelectedDay / ${computedHabitStats.size} habits",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Date Selector Strip (Edit Past Days if forgot to log)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Log for Today or Edit Past Days:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (selectedDateIso != HabitUtils.getTodayIso()) {
                            TextButton(onClick = { selectedDateIso = HabitUtils.getTodayIso() }) {
                                Text("Back to Today")
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        last14Days.forEachIndexed { idx, dIso ->
                            val isSelected = dIso == selectedDateIso
                            val label = when (idx) {
                                0 -> "Today"
                                1 -> "Yesterday"
                                else -> HabitUtils.formatIsoToIndianDate(dIso).take(5)
                            }
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedDateIso = dIso },
                                label = { Text(label) },
                                modifier = Modifier.testTag("habit_date_chip_$idx")
                            )
                        }
                    }
                }
            }

            if (computedHabitStats.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Default.SelfImprovement,
                                contentDescription = null,
                                modifier = Modifier.size(54.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "No Habits Configured Yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Track daily habits, count-based goals (8 glasses of water), or money-linked habits like 'No-Spend Day' and 'Save ₹100 Today' that automatically verify from your transactions!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(6.dp))
                            Button(onClick = { showCreateDialog = true }) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Create First Habit")
                            }
                        }
                    }
                }
            } else {
                items(computedHabitStats, key = { it.habit.id }) { stats ->
                    HabitDetailCard(
                        stats = stats,
                        selectedDateIso = selectedDateIso,
                        onSelectDate = { selectedDateIso = it },
                        onToggleCheck = {
                            viewModel.toggleHabitForDate(stats.habit, selectedDateIso)
                        },
                        onAdjustCount = { delta ->
                            viewModel.adjustHabitCountForDate(stats.habit, selectedDateIso, delta)
                        },
                        onSkipRestDay = {
                            viewModel.skipHabitDay(stats.habit, selectedDateIso)
                        },
                        onEditHabit = { editingHabit = stats.habit },
                        onDeleteHabit = { habitPendingDelete = stats.habit }
                    )
                }
            }
        }
    }
}

@Composable
private fun HabitDetailCard(
    stats: HabitComputedStats,
    selectedDateIso: String,
    onSelectDate: (String) -> Unit,
    onToggleCheck: () -> Unit,
    onAdjustCount: (Int) -> Unit,
    onSkipRestDay: () -> Unit,
    onEditHabit: () -> Unit,
    onDeleteHabit: () -> Unit
) {
    val habit = stats.habit
    val habitColor = HabitUtils.parseColor(habit.colorHex)
    val isDone = stats.selectedDayStatus == "COMPLETED"
    val isSkipped = stats.selectedDayStatus == "SKIPPED"
    var showHeatmap by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("habit_card_${habit.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = if (isDone) 1.5.dp else 1.dp,
            color = if (isDone) habitColor else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Row: Icon, Name, Frequency & Check-off / Count +/- Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = habitColor.copy(alpha = 0.16f),
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                HabitUtils.getIcon(habit.iconName),
                                contentDescription = null,
                                tint = habitColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = habit.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        val freqText = when (habit.frequencyType) {
                            "SPECIFIC_DAYS" -> "Specific weekdays"
                            "TIMES_PER_WEEK" -> "${habit.timesPerWeek}x per week"
                            else -> "Daily"
                        }
                        Text(
                            text = "$freqText • 🔥 ${stats.currentStreak}d streak (Best: ${stats.longestStreak}d)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Check-off or Count +/- Controls
                if (habit.targetCount > 1) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilledTonalIconButton(
                            onClick = { onAdjustCount(-1) },
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("habit_minus_${habit.id}")
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(16.dp))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${stats.selectedDayCount}/${habit.targetCount}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isDone) habitColor else MaterialTheme.colorScheme.onSurface
                            )
                            if (habit.unitLabel.isNotBlank()) {
                                Text(
                                    habit.unitLabel,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        FilledTonalIconButton(
                            onClick = { onAdjustCount(1) },
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("habit_plus_${habit.id}")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(16.dp))
                        }
                    }
                } else {
                    FilledIconToggleButton(
                        checked = isDone,
                        onCheckedChange = { onToggleCheck() },
                        modifier = Modifier.testTag("habit_toggle_${habit.id}")
                    ) {
                        Icon(
                            if (isDone) Icons.Default.Check else Icons.Default.RadioButtonUnchecked,
                            contentDescription = "Complete Habit"
                        )
                    }
                }
            }

            // Progress bar for count-based habits
            if (habit.targetCount > 1) {
                LinearProgressIndicator(
                    progress = {
                        (stats.selectedDayCount.toFloat() / habit.targetCount.toFloat()).coerceIn(0f, 1f)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = habitColor
                )
            }

            // Money-Linked Auto-Verification Banner
            if (stats.autoMoneySummary != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (stats.isAutoMoneySatisfied) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            if (stats.isAutoMoneySatisfied) Icons.Default.Verified else Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (stats.isAutoMoneySatisfied) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stats.autoMoneySummary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (stats.isAutoMoneySatisfied) Color(0xFF1B5E20) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Completion Stats Row (Week / Month / Year %)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatPill(label = "Week", value = "${stats.weekCompletionPercent}%", modifier = Modifier.weight(1f))
                StatPill(label = "Month", value = "${stats.monthCompletionPercent}%", modifier = Modifier.weight(1f))
                StatPill(label = "Year", value = "${stats.yearCompletionPercent}%", modifier = Modifier.weight(1f))
            }

            // Calendar Heatmap
            if (showHeatmap) {
                HabitHeatmapGrid(
                    stats = stats,
                    selectedDateIso = selectedDateIso,
                    onSelectDate = onSelectDate
                )
            }

            // Bottom Actions: Skip Day (Rest Day), Toggle Heatmap, Edit, Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = onSkipRestDay,
                    label = {
                        Text(
                            if (isSkipped) "Skipped (Rest Day ✓)" else "Skip Day (Rest Day)",
                            fontSize = 11.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Hotel,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    modifier = Modifier.testTag("habit_skip_${habit.id}")
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { showHeatmap = !showHeatmap }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.CalendarMonth,
                            contentDescription = "Toggle Heatmap",
                            modifier = Modifier.size(18.dp),
                            tint = if (showHeatmap) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onEditHabit, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit Habit",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onDeleteHabit, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete Habit",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatPill(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
    }
}

package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.data.Habit
import com.example.data.Transaction
import com.example.ui.components.AutoSizeSingleLineText
import com.example.utils.*
import com.example.viewmodel.FinanceViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

enum class DailyToolkitTab(val title: String, val icon: ImageVector) {
    MEDITATION_FOCUS("Meditation & Timer", Icons.Default.SelfImprovement),
    VEHICLE_FUEL("Vehicle & Fuel", Icons.Default.LocalGasStation),
    SAFE_SPEND_SUBS("Safe Spend & Subs", Icons.Default.Autorenew),
    GROCERY_MEMORY("Smart Grocery", Icons.Default.ShoppingCart),
    EMI_WHATSAPP("EMI & WhatsApp", Icons.Default.Calculate),
    DAILY_WRAPUP("Daily Wrap-Up", Icons.Default.Verified)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyToolkitScreen(
    viewModel: FinanceViewModel,
    navController: NavController,
    initialTab: Int = 0
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember(initialTab) {
        mutableStateOf(DailyToolkitTab.entries.getOrElse(initialTab) { DailyToolkitTab.MEDITATION_FOCUS })
    }

    // Persistent states loaded from DailyToolkitUtils
    var fuelLogs by remember { mutableStateOf(DailyToolkitUtils.loadFuelLogs(context)) }
    var meditationLogs by remember { mutableStateOf(DailyToolkitUtils.loadMeditationLogs(context)) }
    var subscriptions by remember { mutableStateOf(DailyToolkitUtils.loadSubscriptions(context)) }
    var groceryItems by remember { mutableStateOf(DailyToolkitUtils.loadGroceryItems(context)) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.testTag("daily_toolkit_back_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text(
                            "Daily Life & Pro Toolkit",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "Meditation • Fuel Mileage • Safe Spend • Grocery • EMI",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)
        ) {
            // Feature Selector Strip
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DailyToolkitTab.entries.forEach { tab ->
                        val isSelected = selectedTab == tab
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedTab = tab },
                            label = {
                                Text(
                                    tab.title,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            leadingIcon = {
                                Icon(tab.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            modifier = Modifier.testTag("toolkit_tab_${tab.name.lowercase(Locale.ROOT)}")
                        )
                    }
                }
            }

            item {
                when (selectedTab) {
                    DailyToolkitTab.MEDITATION_FOCUS -> {
                        MeditationCountdownSection(
                            viewModel = viewModel,
                            meditationLogs = meditationLogs,
                            onUpdateLogs = { updated ->
                                meditationLogs = updated
                                DailyToolkitUtils.saveMeditationLogs(context, updated)
                            },
                            onShowMessage = { msg ->
                                coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        )
                    }
                    DailyToolkitTab.VEHICLE_FUEL -> {
                        VehicleFuelTrackerSection(
                            viewModel = viewModel,
                            fuelLogs = fuelLogs,
                            onUpdateLogs = { updated ->
                                fuelLogs = updated
                                DailyToolkitUtils.saveFuelLogs(context, updated)
                            },
                            onShowMessage = { msg ->
                                coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        )
                    }
                    DailyToolkitTab.SAFE_SPEND_SUBS -> {
                        SafeSpendAndSubscriptionsSection(
                            viewModel = viewModel,
                            subscriptions = subscriptions,
                            onUpdateSubscriptions = { updated ->
                                subscriptions = updated
                                DailyToolkitUtils.saveSubscriptions(context, updated)
                            },
                            onShowMessage = { msg ->
                                coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        )
                    }
                    DailyToolkitTab.GROCERY_MEMORY -> {
                        SmartGroceryMemorySection(
                            viewModel = viewModel,
                            groceryItems = groceryItems,
                            onUpdateItems = { updated ->
                                groceryItems = updated
                                DailyToolkitUtils.saveGroceryItems(context, updated)
                            },
                            onShowMessage = { msg ->
                                coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        )
                    }
                    DailyToolkitTab.EMI_WHATSAPP -> {
                        EmiAndWhatsAppSection(
                            viewModel = viewModel,
                            context = context,
                            onShowMessage = { msg ->
                                coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        )
                    }
                    DailyToolkitTab.DAILY_WRAPUP -> {
                        DailyWrapUpAndPassbookSection(
                            viewModel = viewModel,
                            meditationLogs = meditationLogs,
                            context = context,
                            onOpenTab = { selectedTab = it },
                            onShowMessage = { msg ->
                                coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// TAB 1: LIVE MEDITATION & FOCUS COUNTDOWN TIMER (WITH 4-4-6 BREATHING GUIDE)
// ============================================================================

@Composable
private fun MeditationCountdownSection(
    viewModel: FinanceViewModel,
    meditationLogs: List<MeditationSessionLog>,
    onUpdateLogs: (List<MeditationSessionLog>) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val activeHabits by viewModel.activeHabits.collectAsStateWithLifecycle()

    val presets = listOf(
        Triple("Calm Breathing", 5, "4-4-6 guided breath relaxation"),
        Triple("Daily Meditation", 10, "Mindful awareness & stress relief"),
        Triple("Deep Meditation", 15, "Deep stillness & mental clarity"),
        Triple("Pomodoro Focus", 25, "Distraction-free task sprint")
    )

    var selectedModeTitle by remember { mutableStateOf("Daily Meditation") }
    var totalDurationSeconds by remember { mutableIntStateOf(10 * 60) }
    var remainingSeconds by remember { mutableIntStateOf(10 * 60) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var isRunning by remember { mutableStateOf(false) }
    var autoLogHabitOnComplete by remember { mutableStateOf(true) }
    var customMinutesInput by remember { mutableStateOf("") }

    // Live 1-second countdown ticker
    LaunchedEffect(isRunning, remainingSeconds) {
        if (isRunning && remainingSeconds > 0) {
            delay(1000L)
            remainingSeconds -= 1
            elapsedSeconds += 1
            if (remainingSeconds == 0) {
                isRunning = false
                val completedMins = (totalDurationSeconds / 60).coerceAtLeast(1)
                val newLog = MeditationSessionLog(
                    modeTitle = selectedModeTitle,
                    durationMinutes = completedMins,
                    completedAtMillis = System.currentTimeMillis(),
                    note = "Completed full countdown session"
                )
                onUpdateLogs(listOf(newLog) + meditationLogs)

                if (autoLogHabitOnComplete) {
                    val existingMeditationHabit = activeHabits.firstOrNull {
                        it.name.contains("meditat", ignoreCase = true) ||
                            it.name.contains("breath", ignoreCase = true) ||
                            it.name.contains("mindful", ignoreCase = true)
                    }
                    val todayIso = HabitUtils.getTodayIso()
                    if (existingMeditationHabit != null) {
                        viewModel.toggleHabitForDate(existingMeditationHabit, todayIso)
                    } else {
                        viewModel.addHabit(
                            Habit(
                                name = "Daily Meditation",
                                iconName = "SelfImprovement",
                                colorHex = "#00897B",
                                frequencyType = "DAILY",
                                timesPerWeek = 7
                            )
                        )
                    }
                }
                onShowMessage("Namaste! Completed $completedMins min $selectedModeTitle session.")
            }
        }
    }

    val progressFraction = if (totalDurationSeconds > 0) {
        (remainingSeconds.toFloat() / totalDurationSeconds.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction,
        animationSpec = tween(durationMillis = 350),
        label = "timer_ring_progress"
    )

    val breathingPhase = remember(elapsedSeconds) {
        DailyToolkitUtils.computeBreathingPhase(elapsedSeconds)
    }

    val todayStartMs = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val todayMinutes = meditationLogs
        .filter { it.completedAtMillis >= todayStartMs }
        .sumOf { it.durationMinutes }
    val totalMinutesAllTime = meditationLogs.sumOf { it.durationMinutes }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Summary Strip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Today's Mindful Time", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(
                        "$todayMinutes mins",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Total Sessions", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(
                        "${meditationLogs.size} (${totalMinutesAllTime}m)",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        // Main Countdown Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("meditation_timer_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Mode Preset Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { (title, mins, _) ->
                        val isSelected = selectedModeTitle == title && totalDurationSeconds == mins * 60
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                isRunning = false
                                selectedModeTitle = title
                                totalDurationSeconds = mins * 60
                                remainingSeconds = mins * 60
                                elapsedSeconds = 0
                            },
                            label = { Text("$title (${mins}m)") }
                        )
                    }
                }

                // Live Circular Countdown Ring
                val ringPrimary = MaterialTheme.colorScheme.primary
                val ringTrack = MaterialTheme.colorScheme.surfaceVariant
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(210.dp)
                        .padding(8.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 14.dp.toPx()
                        drawArc(
                            color = ringTrack,
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = ringPrimary,
                            startAngle = -90f,
                            sweepAngle = 360f * animatedProgress,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = selectedModeTitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = DailyToolkitUtils.formatTimerMmSs(remainingSeconds),
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("meditation_countdown_display")
                        )
                        Text(
                            text = if (isRunning) "LIVE COUNTDOWN" else "READY",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isRunning) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Guided 4-4-6 Breathing Pacer Box
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Air,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "${breathingPhase.label} • ${breathingPhase.secondsRemainingInPhase}s",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = breathingPhase.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LinearProgressIndicator(
                            progress = { breathingPhase.phaseProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }
                }

                // Primary Timer Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { isRunning = !isRunning },
                        modifier = Modifier
                            .weight(1.3f)
                            .testTag("meditation_start_pause_btn"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (isRunning) "Pause" else if (remainingSeconds < totalDurationSeconds) "Resume" else "Start",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            totalDurationSeconds += 5 * 60
                            remainingSeconds += 5 * 60
                        },
                        modifier = Modifier.weight(0.8f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("+5m", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            isRunning = false
                            remainingSeconds = totalDurationSeconds
                            elapsedSeconds = 0
                        },
                        modifier = Modifier
                            .weight(0.9f)
                            .testTag("meditation_reset_btn"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Reset")
                    }
                }

                // Custom minutes & Complete Now row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = customMinutesInput,
                        onValueChange = { customMinutesInput = it.filter { ch -> ch.isDigit() }.take(3) },
                        label = { Text("Custom Mins") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    FilledTonalButton(
                        onClick = {
                            val mins = customMinutesInput.toIntOrNull()?.coerceIn(1, 180)
                            if (mins != null) {
                                isRunning = false
                                selectedModeTitle = "Custom Meditation"
                                totalDurationSeconds = mins * 60
                                remainingSeconds = mins * 60
                                elapsedSeconds = 0
                                customMinutesInput = ""
                            }
                        },
                        enabled = (customMinutesInput.toIntOrNull() ?: 0) > 0,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Set Timer")
                    }
                    FilledTonalButton(
                        onClick = {
                            isRunning = false
                            val minsDone = ((totalDurationSeconds - remainingSeconds) / 60)
                                .coerceAtLeast((totalDurationSeconds / 60).coerceAtLeast(1))
                            val newLog = MeditationSessionLog(
                                modeTitle = selectedModeTitle,
                                durationMinutes = minsDone,
                                completedAtMillis = System.currentTimeMillis(),
                                note = "Logged mindfulness session"
                            )
                            onUpdateLogs(listOf(newLog) + meditationLogs)
                            remainingSeconds = totalDurationSeconds
                            elapsedSeconds = 0
                            onShowMessage("Logged $minsDone min $selectedModeTitle session!")
                        },
                        modifier = Modifier.testTag("meditation_log_now_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Log Session")
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = autoLogHabitOnComplete,
                        onCheckedChange = { autoLogHabitOnComplete = it }
                    )
                    Text(
                        "Automatically mark Meditation Habit done when countdown finishes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Recent Meditation & Focus Sessions
        if (meditationLogs.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Recent Meditation & Focus History",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    meditationLogs.take(8).forEach { log ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Default.SelfImprovement,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        "${log.modeTitle} • ${log.durationMinutes} min",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        FormatUtils.formatDateTime(log.completedAtMillis),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    onUpdateLogs(meditationLogs.filterNot { it.id == log.id })
                                }
                            ) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = "Delete Session",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// TAB 2: VEHICLE FUEL MILEAGE TRACKER (REAL-LIFE DRAFT & EDIT WORKFLOW)
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VehicleFuelTrackerSection(
    viewModel: FinanceViewModel,
    fuelLogs: List<FuelLogEntry>,
    onUpdateLogs: (List<FuelLogEntry>) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()

    var editingEntryId by remember { mutableStateOf<Long?>(null) }
    var vehicleName by remember { mutableStateOf("Honda Activa") }
    var odometerInput by remember { mutableStateOf("") }
    var endOdometerInput by remember { mutableStateOf("") }
    var totalCostInput by remember { mutableStateOf("") }
    var pricePerLitreInput by remember { mutableStateOf("96") }
    var selectedAccount by remember(accounts) {
        mutableStateOf(accounts.firstOrNull()?.name ?: "Cash")
    }
    var noteInput by remember { mutableStateOf("") }
    var alsoLogToMoneyLedger by remember { mutableStateOf(true) }

    val stats = remember(fuelLogs) {
        DailyToolkitUtils.computeVehicleMileageStats(fuelLogs)
    }

    val computedLitres = remember(totalCostInput, pricePerLitreInput) {
        val cost = totalCostInput.toDoubleOrNull() ?: 0.0
        val ppl = pricePerLitreInput.toDoubleOrNull() ?: 0.0
        if (cost > 0.0 && ppl > 0.0) cost / ppl else 0.0
    }

    val livePreviewKmPerLitre = remember(odometerInput, endOdometerInput, computedLitres) {
        val start = odometerInput.toDoubleOrNull() ?: 0.0
        val end = endOdometerInput.toDoubleOrNull() ?: 0.0
        if (end > start && start > 0.0 && computedLitres > 0.0) {
            (end - start) / computedLitres
        } else null
    }

    fun resetForm() {
        editingEntryId = null
        odometerInput = ""
        endOdometerInput = ""
        totalCostInput = ""
        noteInput = ""
    }

    fun saveFuelRecord(saveAsDraft: Boolean) {
        val startOdo = odometerInput.toDoubleOrNull() ?: 0.0
        val endOdo = endOdometerInput.toDoubleOrNull() ?: 0.0
        val cost = totalCostInput.toDoubleOrNull() ?: 0.0
        val rate = (pricePerLitreInput.toDoubleOrNull() ?: 96.0).coerceAtLeast(1.0)
        val litres = if (cost > 0.0) cost / rate else 0.0

        if (cost <= 0.0) {
            onShowMessage("Please enter Fuel Paid (₹) amount first.")
            return
        }

        val isEditing = editingEntryId != null
        val targetId = editingEntryId ?: System.currentTimeMillis()
        val existingEntry = fuelLogs.find { it.id == targetId }

        // Entry is marked draft if saved via "Save Draft" or if End Odo isn't known yet
        val draftFlag = saveAsDraft || (startOdo <= 0.0) || (endOdo <= 0.0 && !isEditing)

        val updatedEntry = FuelLogEntry(
            id = targetId,
            vehicleName = vehicleName.trim().ifBlank { "My Vehicle" },
            odometerKm = startOdo,
            endOdometerKm = endOdo,
            litres = litres,
            totalCostInr = cost,
            pricePerLitre = rate,
            account = selectedAccount,
            dateMillis = existingEntry?.dateMillis ?: System.currentTimeMillis(),
            nextServiceKm = 0.0,
            isDraft = draftFlag && endOdo <= startOdo,
            note = noteInput.trim().ifBlank {
                if (draftFlag && endOdo <= startOdo) {
                    "Fuel draft (${String.format(Locale.US, "%.2f", litres)}L) — Update Odo on next refill"
                } else {
                    "Fuel fill-up (${String.format(Locale.US, "%.2f", litres)}L)"
                }
            }
        )

        val updatedList = if (isEditing) {
            fuelLogs.map { if (it.id == targetId) updatedEntry else it }
        } else {
            listOf(updatedEntry) + fuelLogs
        }
        onUpdateLogs(updatedList)

        if (!isEditing && alsoLogToMoneyLedger) {
            viewModel.restoreTransaction(
                Transaction(
                    amount = cost,
                    isIncome = false,
                    category = "Transport",
                    account = selectedAccount,
                    paymentMethod = selectedAccount,
                    note = buildString {
                        append("${updatedEntry.vehicleName} Fuel (${String.format(Locale.US, "%.2f", litres)}L")
                        if (startOdo > 0.0) append(", Odo ${startOdo.toInt()} km")
                        append(")")
                    },
                    timestamp = updatedEntry.dateMillis
                )
            )
        }

        val msg = when {
            isEditing && endOdo > startOdo && startOdo > 0.0 -> {
                val kml = (endOdo - startOdo) / litres.coerceAtLeast(0.01)
                "Updated Odometer! Calculated Mileage: ${String.format(Locale.US, "%.1f km/L", kml)}"
            }
            isEditing -> "Fuel entry updated!"
            saveAsDraft -> "Saved as Draft! Tap 'Edit / Update Odo' after a few days when you check your odometer."
            else -> "Saved fuel fill-up for ${updatedEntry.vehicleName}!"
        }
        resetForm()
        onShowMessage(msg)
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Top Mileage & Cost KPI Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Avg Mileage", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    AutoSizeSingleLineText(
                        text = if (stats.averageKmPerLitre > 0.0) String.format(Locale.US, "%.1f km/L", stats.averageKmPerLitre) else "-- km/L",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Distance: ${stats.totalDistanceKm.toInt()} km",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Running Cost", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    AutoSizeSingleLineText(
                        text = if (stats.costPerKmInr > 0.0) String.format(Locale.US, "₹%.2f / km", stats.costPerKmInr) else "-- / km",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        "Fuel Spend: ${FormatUtils.formatInr(stats.totalFuelSpendInr)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        // Real-Life Petrol Pump Workflow Tip
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Default.LocalGasStation,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "At the petrol pump? Just enter ₹ Paid & tap 'Save Draft'. After a few days/weeks when you refill again, tap 'Edit Odo' below to enter your new km reading and get exact km/L!",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Add or Edit Fuel Fill-Up Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("vehicle_fuel_form_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(
                width = if (editingEntryId != null) 2.dp else 1.dp,
                color = if (editingEntryId != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AutoSizeSingleLineText(
                        text = if (editingEntryId != null) "Editing Fuel Entry — Update Odo (km)" else "Log Fuel Fill-Up (Save Draft or Full)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    if (editingEntryId != null) {
                        TextButton(
                            onClick = { resetForm() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Cancel Edit", maxLines = 1)
                        }
                    }
                }

                // Quick Vehicle Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Honda Activa", "Bike", "Car", "Scooter").forEach { v ->
                        FilterChip(
                            selected = vehicleName.equals(v, ignoreCase = true),
                            onClick = { vehicleName = v },
                            label = { Text(v, maxLines = 1) }
                        )
                    }
                }

                // Row 1: Fuel Paid (₹) + Rate (₹/L) -> Auto Litres
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = totalCostInput,
                        onValueChange = { totalCostInput = it },
                        label = { Text("Fuel Paid (₹) *") },
                        placeholder = { Text("e.g. 200") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("fuel_cost_input"),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = pricePerLitreInput,
                        onValueChange = { pricePerLitreInput = it },
                        label = { Text("Rate (₹/Litre)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Row 2: Start Odometer (km) + Next / End Odometer (km — Optional / Fill Later!)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = odometerInput,
                        onValueChange = { odometerInput = it },
                        label = { Text("Start Odo (km)") },
                        placeholder = { Text("At pump (optional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("fuel_odometer_input"),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = endOdometerInput,
                        onValueChange = { endOdometerInput = it },
                        label = { Text("Next / End Odo (km)") },
                        placeholder = { Text("Fill after few days") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("fuel_end_odometer_input"),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Row 3: Vehicle Name + Auto Litres / Live Mileage Preview
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = vehicleName,
                        onValueChange = { vehicleName = it },
                        label = { Text("Vehicle Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                            Text(
                                text = if (livePreviewKmPerLitre != null) "Auto Litres & Mileage" else "Auto Calculated Fuel",
                                style = MaterialTheme.typography.labelSmall
                            )
                            AutoSizeSingleLineText(
                                text = if (livePreviewKmPerLitre != null) {
                                    String.format(Locale.US, "%.2fL • %.1f km/L", computedLitres, livePreviewKmPerLitre)
                                } else {
                                    String.format(Locale.US, "%.2f Litres", computedLitres)
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (editingEntryId == null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = alsoLogToMoneyLedger,
                            onCheckedChange = { alsoLogToMoneyLedger = it }
                        )
                        Text(
                            "Also save ₹${totalCostInput.ifBlank { "0" }} as a Transport Expense in Money Ledger ($selectedAccount)",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // Action Buttons Row (Single-Line Save Draft + Save/Update Entry)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (editingEntryId == null) {
                        OutlinedButton(
                            onClick = { saveFuelRecord(saveAsDraft = true) },
                            enabled = (totalCostInput.toDoubleOrNull() ?: 0.0) > 0.0,
                            modifier = Modifier
                                .weight(0.46f)
                                .height(46.dp)
                                .testTag("fuel_save_draft_btn"),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.BookmarkBorder, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            AutoSizeSingleLineText(
                                text = "Save Draft",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                minFontSizeSp = 9.5f
                            )
                        }
                    }

                    Button(
                        onClick = { saveFuelRecord(saveAsDraft = false) },
                        enabled = (totalCostInput.toDoubleOrNull() ?: 0.0) > 0.0,
                        modifier = Modifier
                            .weight(if (editingEntryId == null) 0.54f else 1f)
                            .height(46.dp)
                            .testTag("fuel_save_btn"),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            if (editingEntryId != null) Icons.Default.Check else Icons.Default.LocalGasStation,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        AutoSizeSingleLineText(
                            text = if (editingEntryId != null) "Update Fuel & Mileage" else "Save Entry",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            minFontSizeSp = 9.5f
                        )
                    }
                }
            }
        }

        // Fill-Up History List with Calculated Mileage & 1-Tap Edit Odometer
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Fuel Fill-Up History & Drafts (${stats.logsWithMileage.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                stats.logsWithMileage.forEach { item ->
                    val isBeingEdited = editingEntryId == item.entry.id
                    Surface(
                        color = if (isBeingEdited) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        },
                        border = if (item.entry.isDraft || item.calculatedKmPerLitre == null) {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                        } else null,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        AutoSizeSingleLineText(
                                            text = "${item.entry.vehicleName} • ${FormatUtils.formatInr(item.entry.totalCostInr)}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (item.calculatedKmPerLitre != null) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer
                                            ) {
                                                Text(
                                                    String.format(Locale.US, "%.1f km/L", item.calculatedKmPerLitre),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1
                                                )
                                            }
                                        } else {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.tertiaryContainer
                                            ) {
                                                Text(
                                                    "Draft • Add Odo Later",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        buildString {
                                            if (item.entry.odometerKm > 0.0) {
                                                append("Start Odo: ${item.entry.odometerKm.toInt()} km")
                                            } else {
                                                append("Start Odo: Pending")
                                            }
                                            if (item.entry.endOdometerKm > 0.0) {
                                                append(" -> End: ${item.entry.endOdometerKm.toInt()} km")
                                            }
                                            item.distanceSincePreviousKm?.let { append(" (+${it.toInt()} km)") }
                                            append(" • ${String.format(Locale.US, "%.2f", item.entry.litres)}L")
                                            item.runningCostPerKmInr?.let { append(" • ₹${String.format(Locale.US, "%.2f", it)}/km") }
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    FilledTonalButton(
                                        onClick = {
                                            editingEntryId = item.entry.id
                                            vehicleName = item.entry.vehicleName
                                            totalCostInput = item.entry.totalCostInr.toInt().toString()
                                            pricePerLitreInput = item.entry.pricePerLitre.toInt().toString()
                                            odometerInput = if (item.entry.odometerKm > 0.0) item.entry.odometerKm.toInt().toString() else ""
                                            endOdometerInput = if (item.entry.endOdometerKm > 0.0) item.entry.endOdometerKm.toInt().toString() else ""
                                            noteInput = item.entry.note
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .height(34.dp)
                                            .testTag("fuel_edit_btn_${item.entry.id}")
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit Fuel Entry", modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            "Edit Odo",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            if (editingEntryId == item.entry.id) resetForm()
                                            onUpdateLogs(fuelLogs.filterNot { it.id == item.entry.id })
                                        },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.DeleteOutline,
                                            contentDescription = "Delete Fuel Entry",
                                            modifier = Modifier.size(18.dp)
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

// ============================================================================
// TAB 3: SAFE-TO-SPEND TODAY & SUBSCRIPTION RENEWAL RADAR
// ============================================================================

@Composable
private fun SafeSpendAndSubscriptionsSection(
    viewModel: FinanceViewModel,
    subscriptions: List<SubscriptionItem>,
    onUpdateSubscriptions: (List<SubscriptionItem>) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val allBills by viewModel.allBills.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val totalBalance = remember(accounts, transactions) {
        AccountUtils.buildAccountBalanceSnapshot(accounts, transactions).totalBalance
    }

    val unpaidBillsTotal = remember(allBills) {
        allBills.filter { !it.isPaid }.sumOf { it.amount }
    }

    val safeSummary = remember(totalBalance, unpaidBillsTotal, subscriptions) {
        DailyToolkitUtils.computeSafeToSpend(
            totalBalanceInr = totalBalance,
            unpaidBillsInr = unpaidBillsTotal,
            subscriptions = subscriptions
        )
    }

    var subName by remember { mutableStateOf("") }
    var subAmount by remember { mutableStateOf("") }
    var subDaysUntilRenewal by remember { mutableStateOf("7") }
    var subCycle by remember { mutableStateOf("MONTHLY") }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Safe-to-Spend Today Hero Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("safe_to_spend_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        "Safe-to-Spend Daily Allowance",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                AutoSizeSingleLineText(
                    text = "${FormatUtils.formatInr(safeSummary.safeDailySpendInr)} / day",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "For the remaining ${safeSummary.daysRemainingInMonth} day(s) of this month after subtracting Unpaid Bills (${FormatUtils.formatInr(safeSummary.unpaidBillsInr)}) & Monthly Subscriptions (${FormatUtils.formatInr(safeSummary.monthlySubscriptionBurnInr)}).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        // Add Subscription Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Subscription & Auto-Pay Radar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(
                            "${FormatUtils.formatInr(safeSummary.monthlySubscriptionBurnInr)}/mo",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = subName,
                        onValueChange = { subName = it },
                        label = { Text("Service (e.g. Netflix, Jio)") },
                        singleLine = true,
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = subAmount,
                        onValueChange = { subAmount = it },
                        label = { Text("Amount (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(0.8f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = subDaysUntilRenewal,
                        onValueChange = { subDaysUntilRenewal = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Renews in (Days)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    FilterChip(
                        selected = subCycle == "MONTHLY",
                        onClick = { subCycle = "MONTHLY" },
                        label = { Text("Monthly") }
                    )
                    FilterChip(
                        selected = subCycle == "YEARLY",
                        onClick = { subCycle = "YEARLY" },
                        label = { Text("Yearly") }
                    )
                }

                Button(
                    onClick = {
                        val amt = subAmount.toDoubleOrNull() ?: 0.0
                        val days = (subDaysUntilRenewal.toIntOrNull() ?: 30).coerceAtLeast(0)
                        if (subName.isNotBlank() && amt > 0.0) {
                            val newSub = SubscriptionItem(
                                id = System.currentTimeMillis(),
                                name = subName.trim(),
                                amountInr = amt,
                                billingCycle = subCycle,
                                nextRenewalMillis = System.currentTimeMillis() + days * 86_400_000L
                            )
                            onUpdateSubscriptions(listOf(newSub) + subscriptions)
                            subName = ""
                            subAmount = ""
                            onShowMessage("Added ${newSub.name} to Subscription Radar!")
                        }
                    },
                    enabled = subName.isNotBlank() && (subAmount.toDoubleOrNull() ?: 0.0) > 0.0,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add Subscription Alert", fontWeight = FontWeight.Bold)
                }

                subscriptions.forEach { sub ->
                    val daysLeft = DailyToolkitUtils.daysUntilRenewal(sub.nextRenewalMillis)
                    val isUrgent = daysLeft <= 5
                    Surface(
                        color = if (isUrgent) Color(0xFFFFF3E0) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(sub.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${FormatUtils.formatInr(sub.amountInr)} (${sub.billingCycle}) • Renews in ${daysLeft}d",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isUrgent) Color(0xFFE65100) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    onClick = {
                                        val accName = accounts.firstOrNull()?.name ?: "UPI"
                                        viewModel.restoreTransaction(
                                            Transaction(
                                                amount = sub.amountInr,
                                                isIncome = false,
                                                category = "Bills",
                                                account = accName,
                                                paymentMethod = accName,
                                                note = "Subscription Renewal: ${sub.name}",
                                                timestamp = System.currentTimeMillis()
                                            )
                                        )
                                        val nextCycleDays = if (sub.billingCycle == "YEARLY") 365L else 30L
                                        val updated = subscriptions.map {
                                            if (it.id == sub.id) it.copy(nextRenewalMillis = System.currentTimeMillis() + nextCycleDays * 86_400_000L)
                                            else it
                                        }
                                        onUpdateSubscriptions(updated)
                                        onShowMessage("Logged ${sub.name} payment & advanced next renewal date!")
                                    }
                                ) {
                                    Text("Pay & Log", fontWeight = FontWeight.Bold)
                                }
                                IconButton(
                                    onClick = {
                                        onUpdateSubscriptions(subscriptions.filterNot { it.id == sub.id })
                                    }
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// TAB 4: SMART GROCERY PRICE MEMORY LIST (1-TAP CHECKOUT TO EXPENSE)
// ============================================================================

@Composable
private fun SmartGroceryMemorySection(
    viewModel: FinanceViewModel,
    groceryItems: List<GroceryMemoryItem>,
    onUpdateItems: (List<GroceryMemoryItem>) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    var newItemName by remember { mutableStateOf("") }
    var newItemPrice by remember { mutableStateOf("") }
    var newItemQty by remember { mutableStateOf("1 unit") }

    val checkedItems = groceryItems.filter { it.isChecked }
    val cartTotal = checkedItems.sumOf { it.lastPriceInr }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("smart_grocery_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Smart Grocery Price Memory", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Remembers last price paid & logs checked items as a Grocery Expense in 1 tap.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                    Text(
                        "Cart: ${FormatUtils.formatInr(cartTotal)}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newItemName,
                    onValueChange = { newItemName = it },
                    label = { Text("Item (e.g. Rice 5kg)") },
                    singleLine = true,
                    modifier = Modifier.weight(1.2f),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = newItemPrice,
                    onValueChange = { newItemPrice = it },
                    label = { Text("Price (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(0.8f),
                    shape = RoundedCornerShape(12.dp)
                )
                Button(
                    onClick = {
                        val price = newItemPrice.toDoubleOrNull() ?: 0.0
                        if (newItemName.isNotBlank() && price > 0.0) {
                            val item = GroceryMemoryItem(
                                id = System.currentTimeMillis(),
                                name = newItemName.trim(),
                                lastPriceInr = price,
                                quantityLabel = newItemQty,
                                isChecked = true
                            )
                            onUpdateItems(listOf(item) + groceryItems)
                            newItemName = ""
                            newItemPrice = ""
                        }
                    },
                    enabled = newItemName.isNotBlank() && (newItemPrice.toDoubleOrNull() ?: 0.0) > 0.0,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.align(Alignment.CenterVertically)
                ) {
                    Text("Add")
                }
            }

            groceryItems.forEach { item ->
                Surface(
                    color = if (item.isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Checkbox(
                                checked = item.isChecked,
                                onCheckedChange = { checked ->
                                    onUpdateItems(
                                        groceryItems.map { if (it.id == item.id) it.copy(isChecked = checked) else it }
                                    )
                                }
                            )
                            Column {
                                Text(
                                    item.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    textDecoration = if (item.isChecked) TextDecoration.None else TextDecoration.None
                                )
                                Text(
                                    "Last remembered price: ${FormatUtils.formatInr(item.lastPriceInr)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                onUpdateItems(groceryItems.filterNot { it.id == item.id })
                            }
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Remove Item", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            Button(
                onClick = {
                    if (cartTotal > 0.0) {
                        val accName = accounts.firstOrNull()?.name ?: "Cash"
                        val summaryNames = checkedItems.joinToString(", ") { it.name }
                        viewModel.restoreTransaction(
                            Transaction(
                                amount = cartTotal,
                                isIncome = false,
                                category = "Groceries",
                                account = accName,
                                paymentMethod = accName,
                                note = "Smart Grocery Checkout: $summaryNames",
                                timestamp = System.currentTimeMillis()
                            )
                        )
                        // Uncheck items after checkout while keeping their price memory for next trip
                        onUpdateItems(groceryItems.map { it.copy(isChecked = false) })
                        onShowMessage("Logged Grocery Expense of ${FormatUtils.formatInr(cartTotal)}!")
                    }
                },
                enabled = cartTotal > 0.0,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("grocery_checkout_save_btn"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.ShoppingCartCheckout, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Checkout & Save ${FormatUtils.formatInr(cartTotal)} to Money Ledger",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ============================================================================
// TAB 5: EMI PREPAYMENT SAVER & WHATSAPP POLITE REMINDER GENERATOR
// ============================================================================

@Composable
private fun EmiAndWhatsAppSection(
    viewModel: FinanceViewModel,
    context: Context,
    onShowMessage: (String) -> Unit
) {
    val people by viewModel.people.collectAsStateWithLifecycle()

    // EMI Calculator state
    var loanPrincipalInput by remember { mutableStateOf("500000") }
    var annualRateInput by remember { mutableStateOf("9.5") }
    var tenureMonthsInput by remember { mutableStateOf("60") }
    var extraPrepayInput by remember { mutableStateOf("2000") }

    val emiResult = remember(loanPrincipalInput, annualRateInput, tenureMonthsInput, extraPrepayInput) {
        DailyToolkitUtils.calculateEmiAndPrepayment(
            principal = loanPrincipalInput.toDoubleOrNull() ?: 0.0,
            annualRatePercent = annualRateInput.toDoubleOrNull() ?: 0.0,
            tenureMonths = tenureMonthsInput.toIntOrNull() ?: 0,
            extraMonthlyPayment = extraPrepayInput.toDoubleOrNull() ?: 0.0
        )
    }

    // WhatsApp Reminder state
    var reminderPerson by remember(people) {
        mutableStateOf(people.firstOrNull()?.name ?: "Raj")
    }
    var reminderAmount by remember { mutableStateOf("1500") }
    var reminderUpiId by remember { mutableStateOf("") }
    var reminderLang by remember { mutableStateOf("GUJARATI") }

    val generatedMessage = remember(reminderPerson, reminderAmount, reminderUpiId, reminderLang) {
        DailyToolkitUtils.buildWhatsAppReminderText(
            personName = reminderPerson,
            amountInr = reminderAmount.toDoubleOrNull() ?: 0.0,
            upiId = reminderUpiId,
            language = reminderLang
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 1. EMI & Loan Prepayment Saver
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("emi_calculator_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("EMI & Loan Prepayment Saver", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "See how much interest and time you save by paying a little extra each month.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = loanPrincipalInput,
                        onValueChange = { loanPrincipalInput = it },
                        label = { Text("Loan Amount (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = annualRateInput,
                        onValueChange = { annualRateInput = it },
                        label = { Text("Interest (% p.a.)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = tenureMonthsInput,
                        onValueChange = { tenureMonthsInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Tenure (Months)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = extraPrepayInput,
                        onValueChange = { extraPrepayInput = it },
                        label = { Text("Extra Prepay / mo (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "Monthly EMI: ${FormatUtils.formatInr(emiResult.monthlyEmi)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Interest Saved with Extra Prepayment: ${FormatUtils.formatInr(emiResult.interestSaved)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32)
                        )
                        Text(
                            "Loan finishes ${emiResult.monthsSaved} months earlier (${emiResult.newMonths} vs ${emiResult.originalMonths} months)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        // 2. WhatsApp Udhaar / Split Bill Reminder Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("whatsapp_reminder_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "1-Tap WhatsApp Udhaar & Bill Reminder",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Generate a polite payment reminder in Gujarati, Hindi, or English.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (people.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        people.forEach { p ->
                            AssistChip(
                                onClick = { reminderPerson = p.name },
                                label = { Text(p.name) }
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = reminderPerson,
                        onValueChange = { reminderPerson = it },
                        label = { Text("Person Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = reminderAmount,
                        onValueChange = { reminderAmount = it },
                        label = { Text("Amount (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                OutlinedTextField(
                    value = reminderUpiId,
                    onValueChange = { reminderUpiId = it },
                    label = { Text("Your UPI ID (Optional, e.g. name@upi)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("GUJARATI" to "ગુજરાતી", "HINDI" to "हिन्दी", "ENGLISH" to "English").forEach { (code, label) ->
                        FilterChip(
                            selected = reminderLang == code,
                            onClick = { reminderLang = code },
                            label = { Text(label, fontWeight = FontWeight.Bold) }
                        )
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = generatedMessage,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cm?.setPrimaryClip(ClipData.newPlainText("Kutub Reminder", generatedMessage))
                            onShowMessage("Copied reminder text to clipboard!")
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Copy Text")
                    }

                    Button(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, generatedMessage)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Payment Reminder"))
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Share WhatsApp", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ============================================================================
// TAB 6: 60-SECOND DAILY WRAP-UP & PASSBOOK STATEMENT SHARE
// ============================================================================

@Composable
private fun DailyWrapUpAndPassbookSection(
    viewModel: FinanceViewModel,
    meditationLogs: List<MeditationSessionLog>,
    context: Context,
    onOpenTab: (DailyToolkitTab) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val allTasks by viewModel.allTasks.collectAsStateWithLifecycle()
    val habitLogs by viewModel.habitLogs.collectAsStateWithLifecycle()
    val totalBalance = remember(accounts, transactions) {
        AccountUtils.buildAccountBalanceSnapshot(accounts, transactions).totalBalance
    }

    val todayStartMs = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val todayIso = remember { HabitUtils.getTodayIso() }

    val loggedExpenseToday = remember(transactions, todayStartMs) {
        transactions.any { it.timestamp >= todayStartMs }
    }
    val completedTasksToday = remember(allTasks, todayStartMs) {
        allTasks.count { it.status == "DONE" && (it.completedAt ?: 0L) >= todayStartMs }
    }
    val completedHabitsToday = remember(habitLogs, todayIso) {
        habitLogs.count { it.dateIso == todayIso && it.status == "COMPLETED" }
    }
    val meditationMinsToday = remember(meditationLogs, todayStartMs) {
        meditationLogs.filter { it.completedAtMillis >= todayStartMs }.sumOf { it.durationMinutes }
    }

    val wrapUp = remember(loggedExpenseToday, completedTasksToday, completedHabitsToday, meditationMinsToday) {
        DailyToolkitUtils.computeDailyWrapUpScore(
            loggedExpenseToday = loggedExpenseToday,
            completedTasksToday = completedTasksToday,
            completedHabitsToday = completedHabitsToday,
            meditationMinutesToday = meditationMinsToday
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("daily_wrapup_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "60-Second Daily Wrap-Up",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            wrapUp.statusHeadline,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "${wrapUp.scorePercent}%",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }

                HorizontalDivider()

                WrapUpRow("Logged Today's Expenses", wrapUp.loggedExpenseToday, if (wrapUp.loggedExpenseToday) "Done" else "Pending")
                WrapUpRow("Tasks Completed Today", wrapUp.completedTasksToday > 0, "${wrapUp.completedTasksToday} done")
                WrapUpRow("Habits Checked Off Today", wrapUp.completedHabitsToday > 0, "${wrapUp.completedHabitsToday} done")
                WrapUpRow(
                    "Mindful Meditation Session",
                    wrapUp.meditationMinutesToday > 0,
                    "${wrapUp.meditationMinutesToday} mins",
                    onAction = { onOpenTab(DailyToolkitTab.MEDITATION_FOCUS) }
                )
            }
        }

        // Bank-Passbook Style Statement Share Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("1-Tap Passbook Statement Share", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Generate a clean Bank-Passbook style financial statement for your CA or family.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = {
                        val recent = transactions.take(15)
                        val statement = buildString {
                            appendLine("=== KUTUB PASSBOOK STATEMENT ===")
                            appendLine("Generated: ${FormatUtils.formatDateTime(System.currentTimeMillis())}")
                            appendLine("Total Reconciled Balance: ${FormatUtils.formatInr(totalBalance)}")
                            appendLine("--------------------------------")
                            recent.forEach { tx ->
                                val sign = if (tx.isIncome) "+" else "-"
                                appendLine("${FormatUtils.formatDate(tx.timestamp)} | $sign${FormatUtils.formatInr(tx.amount)} | ${tx.category} (${tx.account}) | ${tx.note}")
                            }
                        }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Kutub Passbook Statement")
                            putExtra(Intent.EXTRA_TEXT, statement)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share Passbook Statement"))
                        onShowMessage("Passbook statement ready to share!")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Description, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Generate & Share Passbook Statement", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun WrapUpRow(
    label: String,
    isDone: Boolean,
    badgeText: String,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onAction != null) Modifier.clickable { onAction() } else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                if (isDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isDone) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
        }
        Text(
            badgeText,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (isDone) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
        )
    }
}

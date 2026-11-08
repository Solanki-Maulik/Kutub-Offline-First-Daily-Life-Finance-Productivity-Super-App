package com.example.ui.screens

import android.content.Context
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
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.data.Account
import com.example.data.Transaction
import com.example.ui.components.*
import com.example.utils.AccountUtils
import com.example.utils.CategoryUtils
import com.example.utils.FormatUtils
import com.example.viewmodel.FinanceViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: FinanceViewModel, navController: NavController) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE) }
    var userName by remember { mutableStateOf(FormatUtils.resolveUserDisplayName(prefs)) }

    DisposableEffect(prefs) {
        userName = FormatUtils.resolveUserDisplayName(prefs)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
            if (key == "user_name") {
                userName = FormatUtils.resolveUserDisplayName(sp)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val debts by viewModel.debts.collectAsStateWithLifecycle()
    val privacyMode by viewModel.privacyMode.collectAsStateWithLifecycle()

    val overallToReceive by viewModel.overallToReceive.collectAsStateWithLifecycle()
    val overallToPay by viewModel.overallToPay.collectAsStateWithLifecycle()
    val overdueEntries by viewModel.overdueEntries.collectAsStateWithLifecycle()
    val allBills by viewModel.allBills.collectAsStateWithLifecycle()
    val pendingApprovals by viewModel.pendingApprovalRecurring.collectAsStateWithLifecycle()
    val allTasks by viewModel.allTasks.collectAsStateWithLifecycle()
    val allScheduleEvents by viewModel.allScheduleEvents.collectAsStateWithLifecycle()
    val taskStats by viewModel.taskStats.collectAsStateWithLifecycle()
    val activeHabits by viewModel.activeHabits.collectAsStateWithLifecycle()
    val habitLogs by viewModel.habitLogs.collectAsStateWithLifecycle()
    val allGoalContributions by viewModel.allGoalContributions.collectAsStateWithLifecycle()
    val enabledModules by viewModel.enabledModules.collectAsStateWithLifecycle()

    val overdueBills = remember(allBills) {
        val now = System.currentTimeMillis()
        allBills.filter { !it.isPaid && it.dueDate < now }
    }

    // Single-pass O(N) balance snapshot (reconciled across all accounts)
    val balanceSnapshot = remember(accounts, transactions) {
        AccountUtils.buildAccountBalanceSnapshot(accounts, transactions)
    }
    val totalBalance = balanceSnapshot.totalBalance
    val cashBalance = balanceSnapshot.cashBalance
    val bankBalance = FormatUtils.round2(
        balanceSnapshot.bankBalance + balanceSnapshot.upiBalance + balanceSnapshot.creditCardBalance
    )

    // Legacy debts compatibility
    val legacyOwedByMe = debts.filter { !it.isOwedToMe && !it.isSettled }.sumOf { it.amount }
    val legacyOwedToMe = debts.filter { it.isOwedToMe && !it.isSettled }.sumOf { it.amount }
    val totalOwedByMe = overallToPay + legacyOwedByMe
    val totalOwedToMe = overallToReceive + legacyOwedToMe

    fun formatMoney(amount: Double): String =
        if (privacyMode) "${FormatUtils.currentCurrencySymbol}••••" else FormatUtils.formatInr(amount)

    // Automatic phone clock & time-of-day greeting
    var currentNowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentNowMillis = System.currentTimeMillis()
            delay(30_000L)
        }
    }
    val greetingSnapshot = remember(currentNowMillis) {
        FormatUtils.computeGreetingTimeSnapshot(nowMillis = currentNowMillis)
    }
    val greeting = greetingSnapshot.greetingEnglish

    // Today's Spend calculation
    val todaySpend = remember(transactions) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = cal.timeInMillis
        transactions
            .filter { !it.isIncome && it.category != "Transfer" && it.category != "Adjustment" && it.category != "Udhaar" && it.category != "Investment" && it.timestamp >= startOfDay }
            .sumOf { it.amount }
    }

    // This Month's Income, Expense, and Savings %
    val (monthIncome, monthExpense, savingsPercent) = remember(transactions) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfMonth = cal.timeInMillis

        val inc = transactions
            .filter { it.isIncome && it.category != "Transfer" && it.category != "Adjustment" && it.category != "Udhaar" && it.category != "Investment" && it.timestamp >= startOfMonth }
            .sumOf { it.amount }

        val exp = transactions
            .filter { !it.isIncome && it.category != "Transfer" && it.category != "Adjustment" && it.category != "Udhaar" && it.category != "Investment" && it.timestamp >= startOfMonth }
            .sumOf { it.amount }

        val savings = inc - exp
        val percent = if (inc > 0) ((savings / inc) * 100).toInt().coerceIn(0, 100) else 0

        Triple(inc, exp, percent)
    }

    // Latest 5 transactions
    val latestTransactions = remember(transactions) {
        transactions.take(5)
    }

    // Dialog & Sheet States
    var showQuickAddExpense by remember { mutableStateOf(false) }
    var showQuickAddIncome by remember { mutableStateOf(false) }
    var showTransferDialog by remember { mutableStateOf(false) }
    var showQuickNoteDialog by remember { mutableStateOf(false) }
    var showQuickTaskDialog by remember { mutableStateOf(false) }
    var showAddEventFromHome by remember { mutableStateOf(false) }
    var taskToEditFromHome by remember { mutableStateOf<com.example.data.TaskItem?>(null) }
    var eventToEditFromHome by remember { mutableStateOf<com.example.data.ScheduleEvent?>(null) }
    var showGlobalSearchDialog by remember { mutableStateOf(false) }
    var showAppQuickGuideDialog by remember { mutableStateOf(false) }
    var showBugReportDialog by remember { mutableStateOf(false) }
    var showHomeGuideBanner by remember {
        mutableStateOf(!prefs.getBoolean("dismissed_home_quick_guide_v1", false))
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    if (showAppQuickGuideDialog) {
        AppQuickGuideDialog(
            onDismiss = { showAppQuickGuideDialog = false },
            onOpenBugReport = {
                showAppQuickGuideDialog = false
                showBugReportDialog = true
            }
        )
    }

    if (showBugReportDialog) {
        BugReportAndFeedbackDialog(
            onDismiss = { showBugReportDialog = false },
            onShowMessage = { msg ->
                coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
            }
        )
    }

    if (showGlobalSearchDialog) {
        GlobalSearchDialog(
            viewModel = viewModel,
            navController = navController,
            onDismiss = { showGlobalSearchDialog = false }
        )
    }

    // Sheets & Dialogs
    if (showQuickAddExpense) {
        QuickAddBottomSheet(
            viewModel = viewModel,
            initialIsIncome = false,
            onDismiss = { showQuickAddExpense = false },
            onSuccess = { msg ->
                coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
            }
        )
    }

    if (showQuickAddIncome) {
        QuickAddBottomSheet(
            viewModel = viewModel,
            initialIsIncome = true,
            onDismiss = { showQuickAddIncome = false },
            onSuccess = { msg ->
                coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
            }
        )
    }

    if (showTransferDialog) {
        TransferDialog(
            accounts = accounts,
            transactions = transactions,
            initialFromAccount = accounts.firstOrNull()?.name ?: "",
            onDismiss = { showTransferDialog = false },
            onTransfer = { amount, from, to ->
                viewModel.addTransfer(amount, from, to, System.currentTimeMillis())
                showTransferDialog = false
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Transferred ${FormatUtils.formatInr(amount)} from $from to $to")
                }
            }
        )
    }

    if (showQuickNoteDialog) {
        QuickNoteDialog(
            onDismiss = { showQuickNoteDialog = false },
            onSave = { title, content ->
                viewModel.addNote(title, content)
                showQuickNoteDialog = false
                coroutineScope.launch { snackbarHostState.showSnackbar("Note \"$title\" saved!") }
            }
        )
    }

    if (showQuickTaskDialog) {
        AddTaskDialog(
            onDismiss = { showQuickTaskDialog = false },
            accounts = accounts,
            categories = categories,
            onSaveTask = { title, desc, prio, cat, due, repeat, subtasks, isMoney, amt, acc, lCat, reminder ->
                viewModel.addTask(
                    title = title,
                    description = desc,
                    priority = prio,
                    category = cat,
                    dueDateTime = due ?: System.currentTimeMillis(),
                    repeatFrequency = repeat,
                    subtasks = subtasks,
                    isLinkedToMoney = isMoney,
                    linkedAmount = amt,
                    linkedAccount = acc,
                    linkedCategory = lCat,
                    reminderEnabled = reminder
                )
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Task \"$title\" added!")
                }
            }
        )
    }

    if (showAddEventFromHome) {
        AddScheduleEventDialog(
            onDismiss = { showAddEventFromHome = false },
            onSaveEvent = { title, desc, start, end, loc, cat, rem, off ->
                viewModel.addScheduleEvent(
                    title = title,
                    description = desc,
                    startTime = start,
                    endTime = end,
                    location = loc,
                    category = cat,
                    reminderEnabled = rem,
                    reminderOffsetMinutes = off
                )
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Schedule \"$title\" saved!")
                }
            }
        )
    }

    if (taskToEditFromHome != null) {
        val editingTask = taskToEditFromHome!!
        AddTaskDialog(
            onDismiss = { taskToEditFromHome = null },
            accounts = accounts,
            categories = categories,
            existingTask = editingTask,
            onSaveTask = { title, desc, prio, cat, due, repeat, subtasks, isMoney, amt, acc, lCat, reminder ->
                viewModel.updateTask(
                    editingTask.copy(
                        title = title,
                        description = desc,
                        priority = prio,
                        category = cat,
                        dueDateTime = due,
                        repeatFrequency = repeat,
                        subtasksJson = com.example.utils.TaskUtils.serializeSubtasks(subtasks),
                        isLinkedToMoney = isMoney,
                        linkedAmount = amt,
                        linkedAccount = acc,
                        linkedCategory = lCat,
                        reminderEnabled = reminder
                    )
                )
                taskToEditFromHome = null
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Task \"$title\" updated!")
                }
            }
        )
    }

    if (eventToEditFromHome != null) {
        val editingEvt = eventToEditFromHome!!
        AddScheduleEventDialog(
            onDismiss = { eventToEditFromHome = null },
            existingEvent = editingEvt,
            onSaveEvent = { title, desc, start, end, loc, cat, rem, off ->
                viewModel.updateScheduleEvent(
                    editingEvt.copy(
                        title = title,
                        description = desc,
                        startTime = start,
                        endTime = end,
                        location = loc,
                        category = cat,
                        reminderEnabled = rem,
                        reminderOffsetMinutes = off
                    )
                )
                eventToEditFromHome = null
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Schedule \"$title\" updated!")
                }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {
                    val periodIcon = when (greetingSnapshot.periodLabel) {
                        "Morning" -> Icons.Default.WbSunny
                        "Afternoon" -> Icons.Default.LightMode
                        "Evening" -> Icons.Default.WbTwilight
                        else -> Icons.Default.NightsStay
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 4.dp)
                            .testTag("home_top_left_greeting"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = periodIcon,
                            contentDescription = greetingSnapshot.periodLabel,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        AutoSizeSingleLineText(
                            text = greeting,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            minFontSizeSp = 11f,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                },
                actions = {
                    // Quick Guide (?) shortcut
                    IconButton(
                        onClick = { showAppQuickGuideDialog = true },
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("home_quick_guide_top_btn")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = "App Quick Start Guide",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Global Search across all modules
                    IconButton(
                        onClick = { showGlobalSearchDialog = true },
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("home_global_search_btn")
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Global Ecosystem Search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Privacy mode eye toggle
                    IconButton(
                        onClick = { viewModel.togglePrivacyMode() },
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("home_privacy_toggle")
                    ) {
                        Icon(
                            if (privacyMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle Privacy Mode",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Avatar button (48dp accessible touch target)
                    IconButton(
                        onClick = { navController.navigate("profile") },
                        modifier = Modifier
                            .padding(end = 2.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("home_profile_avatar_btn")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            val initial = if (userName.isNotBlank()) userName.first().uppercase() else "ક"
                            Text(
                                initial,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 90.dp)
        ) {
            // 0a. Quick Start Guide Banner (dismissible, or reopen anytime via '?' in top bar)
            if (showHomeGuideBanner) {
                item {
                    HomeQuickGuideBanner(
                        onOpenFullGuide = { showAppQuickGuideDialog = true },
                        onOpenBugReport = { showBugReportDialog = true },
                        onDismissBanner = {
                            showHomeGuideBanner = false
                            prefs.edit().putBoolean("dismissed_home_quick_guide_v1", true).apply()
                        }
                    )
                }
            }

            // 0. Unified Today Hub Banner (Tasks + Habits + Bills Due + Expiring Vault Docs)
            item {
                UnifiedTodayDashboardBanner(
                    viewModel = viewModel,
                    onOpenUnifiedToday = { navController.navigate("today_hub") },
                    onOpenGlobalSearch = { showGlobalSearchDialog = true }
                )
            }

            // 0b. AI Assistant Quick Data Entry & Helpers Banner (if enabled)
            if ("ai_assistant" in enabledModules) {
                item {
                    var quickAiInput by remember { mutableStateOf("") }
                    var quickAiFeedback by remember { mutableStateOf<String?>(null) }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("home_ai_assistant_card"),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Column {
                                        Text(
                                            "Gemini AI Fast Data Entry",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            "Add Expense, Income, Udhaar, Task, Habit or Note instantly",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                TextButton(onClick = { navController.navigate("ai_assistant") }) {
                                    Text("AI Hub", fontWeight = FontWeight.Bold, maxLines = 1)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = quickAiInput,
                                    onValueChange = {
                                        quickAiInput = it
                                        quickAiFeedback = null
                                    },
                                    placeholder = {
                                        Text(
                                            "e.g. chai 20 cash, task pay bill, habit walk",
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("home_quick_ai_input"),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                Button(
                                    onClick = {
                                        if (quickAiInput.isNotBlank()) {
                                            val toParse = quickAiInput
                                            viewModel.quickSaveSmartEntryWithAi(toParse) { count, msg ->
                                                quickAiFeedback = msg
                                                if (count > 0) {
                                                    quickAiInput = ""
                                                }
                                            }
                                        }
                                    },
                                    enabled = quickAiInput.isNotBlank(),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                                    modifier = Modifier.testTag("home_quick_ai_add_btn")
                                ) {
                                    Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Add", fontWeight = FontWeight.Bold, maxLines = 1)
                                }
                            }

                            if (quickAiFeedback != null) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = quickAiFeedback ?: "",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AssistChip(
                                    onClick = { navController.navigate("ai_assistant?tab=0") },
                                    leadingIcon = { Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    label = { Text("Smart Entry") },
                                    modifier = Modifier.testTag("home_ai_smart_entry_chip")
                                )
                                AssistChip(
                                    onClick = { navController.navigate("ai_assistant?tab=1") },
                                    leadingIcon = { Icon(Icons.Default.DocumentScanner, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    label = { Text("Scan Receipt") },
                                    modifier = Modifier.testTag("home_ai_receipt_scan_chip")
                                )
                                AssistChip(
                                    onClick = { navController.navigate("ai_assistant?tab=2") },
                                    leadingIcon = { Icon(Icons.Default.QuestionAnswer, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    label = { Text("Ask My Data") },
                                    modifier = Modifier.testTag("home_ai_ask_data_chip")
                                )
                                AssistChip(
                                    onClick = { navController.navigate("ai_assistant?tab=3") },
                                    leadingIcon = { Icon(Icons.Default.Insights, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    label = { Text("Monthly Insights") },
                                    modifier = Modifier.testTag("home_ai_insights_chip")
                                )
                            }
                        }
                    }
                }
            }

            // 0c. Daily Life & Pro Toolkit Quick Hub (Meditation Countdown, Vehicle Fuel Mileage, Safe Spend, Grocery, EMI & WhatsApp)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_daily_toolkit_card"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Default.SelfImprovement,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column {
                                    Text(
                                        "Daily Life & Pro Toolkit",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "Live Meditation Timer • Vehicle Fuel Mileage • Safe Spend • Grocery • EMI",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            TextButton(onClick = { navController.navigate("daily_toolkit") }) {
                                Text("Open All", fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }

                        // Highlighted Favorites Row: Meditation Countdown & Vehicle Fuel Mileage
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { navController.navigate("daily_toolkit?tab=0") },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("home_toolkit_meditation_btn"),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                            ) {
                                Icon(Icons.Default.SelfImprovement, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Meditation Timer", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            FilledTonalButton(
                                onClick = { navController.navigate("daily_toolkit?tab=1") },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("home_toolkit_fuel_btn"),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                            ) {
                                Icon(Icons.Default.LocalGasStation, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Vehicle Fuel", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }

                        // Secondary Pro Tools Strip
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AssistChip(
                                onClick = { navController.navigate("daily_toolkit?tab=2") },
                                leadingIcon = { Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text("Safe-to-Spend & Subs") }
                            )
                            AssistChip(
                                onClick = { navController.navigate("daily_toolkit?tab=3") },
                                leadingIcon = { Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text("Smart Grocery List") }
                            )
                            AssistChip(
                                onClick = { navController.navigate("daily_toolkit?tab=4") },
                                leadingIcon = { Icon(Icons.Default.Calculate, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text("EMI & WhatsApp Reminder") }
                            )
                            AssistChip(
                                onClick = { navController.navigate("daily_toolkit?tab=5") },
                                leadingIcon = { Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text("Daily Wrap-Up & Passbook") }
                            )
                        }
                    }
                }
            }

            // Overdue Udhaar Alert Banner (if any)
            if (overdueEntries.isNotEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFFFEBEE),
                        border = BorderStroke(1.dp, Color(0xFFEF9A9A)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { navController.navigate("udhaar") }
                            .testTag("home_overdue_banner")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFC62828), modifier = Modifier.size(20.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "${overdueEntries.size} Udhaar payment${if (overdueEntries.size > 1) "s" else ""} overdue",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFB71C1C)
                                )
                                Text("Tap to view details and settle", style = MaterialTheme.typography.labelSmall, color = Color(0xFFC62828))
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFFC62828), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // Overdue Bills Alert Banner (if any)
            if (overdueBills.isNotEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFFFEBEE),
                        border = BorderStroke(1.dp, Color(0xFFEF9A9A)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { navController.navigate("bills") }
                            .testTag("home_overdue_bills_banner")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Receipt, contentDescription = null, tint = Color(0xFFC62828), modifier = Modifier.size(20.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "${overdueBills.size} Bill${if (overdueBills.size > 1) "s" else ""} overdue!",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFB71C1C)
                                )
                                Text("Total due: ${FormatUtils.formatInr(overdueBills.sumOf { it.amount })} • Tap to pay", style = MaterialTheme.typography.labelSmall, color = Color(0xFFC62828))
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFFC62828), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // 1. Balance Cards (Total, Cash, Bank)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_total_balance_card"),
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(
                                    Icons.Default.AccountBalanceWallet,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    "Total Balance",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f),
                                modifier = Modifier.clickable { navController.navigate("accounts") }
                            ) {
                                Text(
                                    "${accounts.size} ACCOUNTS",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        AutoSizeSingleLineText(
                            text = formatMoney(totalBalance),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            minFontSizeSp = 18f
                        )

                        Spacer(Modifier.height(16.dp))

                        // Cash in Hand & Bank Balance Split Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Cash in Hand
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { navController.navigate("accounts") }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFFE8F5E9)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Payments, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        AutoSizeSingleLineText(
                                            text = "Cash in Hand",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            minFontSizeSp = 9f
                                        )
                                        AutoSizeSingleLineText(
                                            text = formatMoney(cashBalance),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            minFontSizeSp = 10f
                                        )
                                    }
                                }
                            }

                            // Bank Balance
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { navController.navigate("accounts") }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFFE3F2FD)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.AccountBalance, contentDescription = null, tint = Color(0xFF1565C0), modifier = Modifier.size(16.dp))
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        AutoSizeSingleLineText(
                                            text = "Bank Balance",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            minFontSizeSp = 9f
                                        )
                                        AutoSizeSingleLineText(
                                            text = formatMoney(bankBalance),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            minFontSizeSp = 10f
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. Quick Actions Row (5 distinct buttons: Add expense, Add income, Transfer, Add task, Add note)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Quick Actions",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 1. Add Expense
                        QuickActionButton(
                            label = "Expense",
                            icon = Icons.Default.ArrowOutward,
                            containerColor = Color(0xFFFFEBEE),
                            contentColor = Color(0xFFC62828),
                            testTag = "quick_action_expense",
                            onClick = { showQuickAddExpense = true }
                        )

                        // 2. Add Income
                        QuickActionButton(
                            label = "Income",
                            icon = Icons.Default.ArrowDownward,
                            containerColor = Color(0xFFE8F5E9),
                            contentColor = Color(0xFF2E7D32),
                            testTag = "quick_action_income",
                            onClick = { showQuickAddIncome = true }
                        )

                        // 3. Transfer
                        QuickActionButton(
                            label = "Transfer",
                            icon = Icons.Default.SwapHoriz,
                            containerColor = Color(0xFFE3F2FD),
                            contentColor = Color(0xFF1565C0),
                            testTag = "quick_action_transfer",
                            onClick = { showTransferDialog = true }
                        )

                        // 4. Add Task
                        if ("tasks" in enabledModules) {
                            QuickActionButton(
                                label = "Add Task",
                                icon = Icons.Default.Checklist,
                                containerColor = Color(0xFFF3E5F5),
                                contentColor = Color(0xFF7B1FA2),
                                testTag = "quick_action_task",
                                onClick = { navController.navigate("tasks") }
                            )
                        }

                        // 5. Add Note
                        if ("notes" in enabledModules) {
                            QuickActionButton(
                                label = "Add Note",
                                icon = Icons.Default.EditNote,
                                containerColor = Color(0xFFFFF3E0),
                                contentColor = Color(0xFFE65100),
                                testTag = "quick_action_note",
                                onClick = { showQuickNoteDialog = true }
                            )
                        }

                        // 6. Bills
                        QuickActionButton(
                            label = "Bills",
                            icon = Icons.Default.Receipt,
                            containerColor = Color(0xFFE8EAF6),
                            contentColor = Color(0xFF283593),
                            testTag = "quick_action_bills",
                            onClick = { navController.navigate("bills") }
                        )

                        // 7. Goals
                        QuickActionButton(
                            label = "Goals",
                            icon = Icons.Default.Savings,
                            containerColor = Color(0xFFE0F2F1),
                            contentColor = Color(0xFF00695C),
                            testTag = "quick_action_goals",
                            onClick = { navController.navigate("goals") }
                        )

                        // 8. Split Bills
                        if ("split_bills" in enabledModules) {
                            QuickActionButton(
                                label = "Split Bills",
                                icon = Icons.Default.Groups,
                                containerColor = Color(0xFFE1F5FE),
                                contentColor = Color(0xFF0277BD),
                                testTag = "quick_action_split_bills",
                                onClick = { navController.navigate("split_bills") }
                            )
                        }

                        // 9. Habits
                        if ("habits" in enabledModules) {
                            QuickActionButton(
                                label = "Habits",
                                icon = Icons.Default.LocalFireDepartment,
                                containerColor = Color(0xFFFBE9E7),
                                contentColor = Color(0xFFD84315),
                                testTag = "quick_action_habits",
                                onClick = { navController.navigate("habits") }
                            )
                        }

                        // 10. Investments & Net Worth
                        if ("investments" in enabledModules) {
                            QuickActionButton(
                                label = "Invest",
                                icon = Icons.Default.TrendingUp,
                                containerColor = Color(0xFFE8F5E9),
                                contentColor = Color(0xFF1B5E20),
                                testTag = "quick_action_investments",
                                onClick = { navController.navigate("investments") }
                            )
                        }

                        // 11. Document Vault
                        if ("document_vault" in enabledModules) {
                            QuickActionButton(
                                label = "Doc Vault",
                                icon = Icons.Default.FolderSpecial,
                                containerColor = Color(0xFFEDE7F6),
                                contentColor = Color(0xFF4527A0),
                                testTag = "quick_action_document_vault",
                                onClick = { navController.navigate("document_vault") }
                            )
                        }

                        // 12. All Notes
                        if ("notes" in enabledModules) {
                            QuickActionButton(
                                label = "Notes Hub",
                                icon = Icons.Default.Notes,
                                containerColor = Color(0xFFFFF8E1),
                                contentColor = Color(0xFFF57F17),
                                testTag = "quick_action_notes_hub",
                                onClick = { navController.navigate("notes") }
                            )
                        }
                    }
                }
            }

            // 3. Today's Spend, This Month's Income vs Expense, Savings %
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
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
                            Text(
                                "Monthly & Daily Snapshot",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    "Savings: $savingsPercent%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Today's Spend
                            Column(modifier = Modifier.weight(1f)) {
                                AutoSizeSingleLineText("Today's Spend", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, minFontSizeSp = 9f)
                                Spacer(Modifier.height(4.dp))
                                AutoSizeSingleLineText(formatMoney(todaySpend), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error, minFontSizeSp = 11f)
                            }

                            // This Month Income
                            Column(modifier = Modifier.weight(1f)) {
                                AutoSizeSingleLineText("Month Income", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, minFontSizeSp = 9f)
                                Spacer(Modifier.height(4.dp))
                                AutoSizeSingleLineText(formatMoney(monthIncome), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32), minFontSizeSp = 11f)
                            }

                            // This Month Expense
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                AutoSizeSingleLineText("Month Expense", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, minFontSizeSp = 9f)
                                Spacer(Modifier.height(4.dp))
                                AutoSizeSingleLineText(formatMoney(monthExpense), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error, minFontSizeSp = 11f)
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // Savings Progress Bar
                        LinearProgressIndicator(
                            progress = { (savingsPercent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape),
                            color = Color(0xFF2E7D32),
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }

            // 4. Mini Chart: Last 7 Days Spending
            item {
                SpendingMiniChart(transactions = transactions, privacyMode = privacyMode)
            }

            // 5. Today's Tasks and Upcoming Schedule (Gated by Tasks module toggle)
            if ("tasks" in enabledModules) {
                item {
                    DailyTasksScheduleCard(
                        onAddTaskClick = { showQuickTaskDialog = true },
                        onViewAllClick = { navController.navigate("tasks") },
                        onAddEventClick = { showAddEventFromHome = true },
                        liveTasks = allTasks,
                        liveEvents = allScheduleEvents,
                        currentStreak = taskStats.currentStreak,
                        onToggleLiveTask = { task ->
                            val nextStatus = if (task.status == "DONE") "TODO" else "DONE"
                            viewModel.toggleTaskStatus(task, nextStatus)
                        },
                        onEditLiveTask = { task -> taskToEditFromHome = task },
                        onEditLiveEvent = { evt -> eventToEditFromHome = evt },
                        onQuickAddStarterTask = { title, repeatFreq ->
                            viewModel.addTask(
                                title = title,
                                description = "",
                                priority = "MEDIUM",
                                category = "Finance",
                                dueDateTime = System.currentTimeMillis(),
                                repeatFrequency = repeatFreq
                            )
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Added \"$title\" ($repeatFreq) — tap its circle to complete & build your streak!")
                            }
                        }
                    )
                }
            }

            // 5b. Today's Habits & Consistency Card (Gated by Habits module toggle)
            if ("habits" in enabledModules) {
                item {
                    com.example.ui.components.DailyHabitsDashboardCard(
                        habits = activeHabits,
                        habitLogs = habitLogs,
                        transactions = transactions,
                        goalContributions = allGoalContributions,
                        onToggleHabitToday = { habit ->
                            viewModel.toggleHabitForDate(habit, com.example.utils.HabitUtils.getTodayIso())
                        },
                        onAdjustHabitCountToday = { habit, delta ->
                            viewModel.adjustHabitCountForDate(habit, com.example.utils.HabitUtils.getTodayIso(), delta)
                        },
                        onOpenHabitsScreen = { navController.navigate("habits") }
                    )
                }
            }

            // 6. Pending Udhaar (To Receive / To Pay) (Gated by Udhaar module toggle)
            if ("udhaar" in enabledModules) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { navController.navigate("udhaar") }
                            .testTag("home_udhaar_card"),
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
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                    Text("Pending Udhaar (Khata)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                                Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            }

                            Spacer(Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // To Receive
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color(0xFFE8F5E9),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("To Receive", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32), fontWeight = FontWeight.SemiBold)
                                        Spacer(Modifier.height(4.dp))
                                        Text(formatMoney(totalOwedToMe), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
                                        Text("They owe you", style = MaterialTheme.typography.labelSmall, color = Color(0xFF388E3C))
                                    }
                                }

                                // To Pay
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color(0xFFFFEBEE),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("To Pay", style = MaterialTheme.typography.labelSmall, color = Color(0xFFC62828), fontWeight = FontWeight.SemiBold)
                                        Spacer(Modifier.height(4.dp))
                                        Text(formatMoney(totalOwedByMe), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFFB71C1C))
                                        Text("You owe them", style = MaterialTheme.typography.labelSmall, color = Color(0xFFD32F2F))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 7. Latest 5 Transactions
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Latest Transactions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    TextButton(
                        onClick = { navController.navigate("history") },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Text("View All", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp))
                    }
                }
            }

            if (latestTransactions.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("No transactions logged yet", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Use Quick Actions above to log your first spend.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                items(latestTransactions, key = { it.id }) { transaction ->
                    val isTransfer = transaction.category == "Transfer"
                    val isUdhaar = transaction.category == "Udhaar"
                    val sign = if (isTransfer) "" else if (transaction.isIncome) "+" else "-"
                    val amountColor = when {
                        isTransfer -> MaterialTheme.colorScheme.tertiary
                        isUdhaar -> if (transaction.isIncome) Color(0xFF2E7D32) else Color(0xFFC62828)
                        transaction.isIncome -> Color(0xFF2E7D32)
                        else -> MaterialTheme.colorScheme.error
                    }

                    val categoryObj = categories.firstOrNull { it.name.equals(transaction.category, ignoreCase = true) }
                    val categoryColor = when {
                        isTransfer -> MaterialTheme.colorScheme.tertiary
                        isUdhaar -> Color(0xFF1565C0)
                        categoryObj != null -> CategoryUtils.parseColor(categoryObj.colorHex)
                        else -> MaterialTheme.colorScheme.primary
                    }
                    val categoryIcon = when {
                        isTransfer -> Icons.Default.SwapHoriz
                        isUdhaar -> Icons.Default.People
                        categoryObj != null -> CategoryUtils.getIcon(categoryObj.iconName)
                        else -> Icons.Default.Category
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { navController.navigate("add?id=${transaction.id}") }
                            .testTag("latest_transaction_item_${transaction.id}"),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(categoryColor.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        categoryIcon,
                                        contentDescription = null,
                                        tint = categoryColor,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        if (isTransfer || isUdhaar) transaction.note.ifBlank { transaction.category } else transaction.category,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "${transaction.account} • ${FormatUtils.formatDate(transaction.timestamp)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.End) {
                                    AutoSizeSingleLineText(
                                        text = "$sign${formatMoney(transaction.amount)}",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = amountColor,
                                        minFontSizeSp = 11f
                                    )
                                    if (transaction.paymentMethod.isNotBlank() && transaction.paymentMethod != "Transfer") {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Text(
                                                transaction.paymentMethod,
                                                style = MaterialTheme.typography.labelSmall,
                                                maxLines = 1,
                                                softWrap = false,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                IconButton(
                                    onClick = { navController.navigate("add?id=${transaction.id}") },
                                    modifier = Modifier
                                        .size(34.dp)
                                        .testTag("home_edit_tx_${transaction.id}")
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Edit Transaction",
                                        tint = MaterialTheme.colorScheme.primary,
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

@Composable
private fun QuickActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: Color,
    contentColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag(testTag)
            .padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(containerColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

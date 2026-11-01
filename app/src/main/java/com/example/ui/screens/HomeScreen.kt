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
    var tzMode by remember { mutableStateOf(prefs.getString("greeting_tz_mode", "IST") ?: "IST") }
    var periodOverride by remember { mutableStateOf(prefs.getString("greeting_period_override", "AUTO") ?: "AUTO") }

    DisposableEffect(prefs) {
        userName = FormatUtils.resolveUserDisplayName(prefs)
        tzMode = prefs.getString("greeting_tz_mode", "IST") ?: "IST"
        periodOverride = prefs.getString("greeting_period_override", "AUTO") ?: "AUTO"
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
            when (key) {
                "user_name" -> userName = FormatUtils.resolveUserDisplayName(sp)
                "greeting_tz_mode" -> tzMode = sp.getString("greeting_tz_mode", "IST") ?: "IST"
                "greeting_period_override" -> periodOverride = sp.getString("greeting_period_override", "AUTO") ?: "AUTO"
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

    // Live-updating clock & time-of-day greeting (Morning, Afternoon, Evening, Night)
    var currentNowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentNowMillis = System.currentTimeMillis()
            delay(10_000L)
        }
    }
    val greetingSnapshot = remember(currentNowMillis, tzMode, periodOverride) {
        FormatUtils.computeGreetingTimeSnapshot(
            nowMillis = currentNowMillis,
            timeZoneMode = tzMode,
            overridePeriod = periodOverride
        )
    }
    val greeting = greetingSnapshot.greetingEnglish
    val todayDateFormatted = "${greetingSnapshot.formattedTime} ${greetingSnapshot.timeZoneLabel} • ${greetingSnapshot.greetingGujarati} • ${greetingSnapshot.formattedDate}"

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
    var showGlobalSearchDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

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
                    Column(
                        modifier = Modifier
                            .clickable { navController.navigate("profile") }
                            .testTag("home_top_left_greeting")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = periodIcon,
                                contentDescription = greetingSnapshot.periodLabel,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                "$greeting, $userName",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            todayDateFormatted,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                actions = {
                    // Global Search across all modules
                    IconButton(
                        onClick = { showGlobalSearchDialog = true },
                        modifier = Modifier.testTag("home_global_search_btn")
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Global Ecosystem Search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Unified Today View shortcut
                    IconButton(
                        onClick = { navController.navigate("today_hub") },
                        modifier = Modifier.testTag("home_unified_today_btn")
                    ) {
                        Icon(
                            Icons.Default.Today,
                            contentDescription = "Unified Today View",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Privacy mode eye toggle
                    IconButton(
                        onClick = { viewModel.togglePrivacyMode() },
                        modifier = Modifier.testTag("home_privacy_toggle")
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
                            .padding(end = 4.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("home_profile_avatar_btn")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            val initial = if (userName.isNotBlank()) userName.first().uppercase() else "ક"
                            Text(
                                initial,
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
            // 0. Unified Today Hub Banner (Tasks + Habits + Bills Due + Expiring Vault Docs)
            item {
                UnifiedTodayDashboardBanner(
                    viewModel = viewModel,
                    onOpenUnifiedToday = { navController.navigate("today_hub") },
                    onOpenGlobalSearch = { showGlobalSearchDialog = true }
                )
            }

            // 0b. AI Assistant Quick Helpers Banner (if enabled)
            if ("ai_assistant" in enabledModules) {
                item {
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
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Column {
                                        Text(
                                            "Gemini AI Helpers",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                        Text(
                                            "Smart Entry • Receipt Scan • Ask Data • Insights",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                                TextButton(onClick = { navController.navigate("ai_assistant") }) {
                                    Text("Open AI Hub", fontWeight = FontWeight.Bold)
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

                        Text(
                            formatMoney(totalBalance),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
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
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFFE8F5E9)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Payments, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(18.dp))
                                    }
                                    Column {
                                        Text("Cash in Hand", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(formatMoney(cashBalance), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
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
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFFE3F2FD)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.AccountBalance, contentDescription = null, tint = Color(0xFF1565C0), modifier = Modifier.size(18.dp))
                                    }
                                    Column {
                                        Text("Bank Balance", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(formatMoney(bankBalance), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
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
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Today's Spend
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Today's Spend", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(4.dp))
                                Text(formatMoney(todaySpend), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            }

                            // This Month Income
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Month Income", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(4.dp))
                                Text(formatMoney(monthIncome), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                            }

                            // This Month Expense
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                Text("Month Expense", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(4.dp))
                                Text(formatMoney(monthExpense), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
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

                                Column {
                                    Text(
                                        if (isTransfer || isUdhaar) transaction.note.ifBlank { transaction.category } else transaction.category,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        "${transaction.account} • ${FormatUtils.formatDate(transaction.timestamp)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "$sign${formatMoney(transaction.amount)}",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = amountColor
                                )
                                if (transaction.paymentMethod.isNotBlank() && transaction.paymentMethod != "Transfer") {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            transaction.paymentMethod,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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

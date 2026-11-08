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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.utils.DocumentVaultUtils
import com.example.utils.EcosystemHubUtils
import com.example.utils.FormatUtils
import com.example.utils.GlobalSearchResultItem
import com.example.utils.HabitUtils
import com.example.viewmodel.FinanceViewModel
import kotlinx.coroutines.launch

/**
 * Global Search Dialog across all enabled modules.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSearchDialog(
    viewModel: FinanceViewModel,
    navController: NavController,
    onDismiss: () -> Unit
) {
    val enabledModules by viewModel.enabledModules.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val tasks by viewModel.allTasks.collectAsStateWithLifecycle()
    val events by viewModel.allScheduleEvents.collectAsStateWithLifecycle()
    val notes by viewModel.activeNotes.collectAsStateWithLifecycle()
    val habits by viewModel.activeHabits.collectAsStateWithLifecycle()
    val splitGroups by viewModel.splitGroups.collectAsStateWithLifecycle()
    val splitExpenses by viewModel.splitExpenses.collectAsStateWithLifecycle()
    val holdings by viewModel.holdings.collectAsStateWithLifecycle()
    val liabilities by viewModel.liabilities.collectAsStateWithLifecycle()
    val vaultDocuments by viewModel.vaultDocuments.collectAsStateWithLifecycle()
    val people by viewModel.people.collectAsStateWithLifecycle()
    val udhaarEntries by viewModel.udhaarEntries.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var selectedModuleFilter by remember { mutableStateOf("ALL") }

    val availableFilters = remember(enabledModules) {
        buildList {
            add("ALL" to "All Modules")
            add("money" to "Money")
            if ("tasks" in enabledModules) add("tasks" to "Tasks")
            if ("notes" in enabledModules) add("notes" to "Notes")
            if ("habits" in enabledModules) add("habits" to "Habits")
            if ("split_bills" in enabledModules) add("split_bills" to "Split")
            if ("investments" in enabledModules) add("investments" to "Investments")
            if ("document_vault" in enabledModules) add("document_vault" to "Vault")
            if ("udhaar" in enabledModules) add("udhaar" to "Udhaar")
        }
    }

    val results = remember(
        query,
        selectedModuleFilter,
        enabledModules,
        transactions,
        tasks,
        events,
        notes,
        habits,
        splitGroups,
        splitExpenses,
        holdings,
        liabilities,
        vaultDocuments,
        people,
        udhaarEntries
    ) {
        EcosystemHubUtils.searchAllModules(
            query = query,
            enabledModules = enabledModules,
            moduleFilter = selectedModuleFilter,
            transactions = transactions,
            tasks = tasks,
            events = events,
            notes = notes,
            habits = habits,
            splitGroups = splitGroups,
            splitExpenses = splitExpenses,
            holdings = holdings,
            liabilities = liabilities,
            vaultDocuments = vaultDocuments,
            people = people,
            udhaarEntries = udhaarEntries
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "Global Ecosystem Search",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Search across Money, Tasks, Notes, Habits, Split, Investments & Vault",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close Global Search")
                    }
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Type anything (e.g. rent, Goa, warranty, Nifty, task)...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("global_search_input")
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableFilters.forEach { (key, label) ->
                        FilterChip(
                            selected = selectedModuleFilter == key,
                            onClick = { selectedModuleFilter = key },
                            label = { Text(label) }
                        )
                    }
                }

                if (query.isBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.ManageSearch,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                "Instant Cross-Module Search",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Finds matching transactions, tasks, notes, habits, split bills, holdings, and vault documents.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (results.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No results found across enabled modules for \"$query\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        "${results.size} result${if (results.size == 1) "" else "s"} found",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(results, key = { it.key }) { item ->
                            GlobalSearchResultRow(
                                item = item,
                                onClick = {
                                    onDismiss()
                                    navController.navigate(item.targetRoute) {
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlobalSearchResultRow(
    item: GlobalSearchResultItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            item.moduleLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        item.badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.subtitle.isNotBlank()) {
                    Text(
                        item.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Compact Unified Today Banner for HomeScreen.
 */
@Composable
fun UnifiedTodayDashboardBanner(
    viewModel: FinanceViewModel,
    onOpenUnifiedToday: () -> Unit,
    onOpenGlobalSearch: () -> Unit
) {
    val enabledModules by viewModel.enabledModules.collectAsStateWithLifecycle()
    val tasks by viewModel.allTasks.collectAsStateWithLifecycle()
    val habits by viewModel.activeHabits.collectAsStateWithLifecycle()
    val habitLogs by viewModel.habitLogs.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val goalContributions by viewModel.allGoalContributions.collectAsStateWithLifecycle()
    val bills by viewModel.allBills.collectAsStateWithLifecycle()
    val vaultDocs by viewModel.vaultDocuments.collectAsStateWithLifecycle()
    val holdings by viewModel.holdings.collectAsStateWithLifecycle()

    val todayOverview = remember(
        enabledModules,
        tasks,
        habits,
        habitLogs,
        transactions,
        goalContributions,
        bills,
        vaultDocs,
        holdings
    ) {
        EcosystemHubUtils.buildUnifiedTodayOverview(
            enabledModules = enabledModules,
            tasks = tasks,
            habits = habits,
            habitLogs = habitLogs,
            transactions = transactions,
            goalContributions = goalContributions,
            bills = bills,
            vaultDocuments = vaultDocs,
            holdings = holdings
        )
    }

    val pendingHabits = todayOverview.habitsForToday.count { it.selectedDayStatus != "COMPLETED" && it.selectedDayStatus != "SKIPPED" }
    val alertsCount = todayOverview.expiringDocuments.size + todayOverview.maturingFds.size

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_unified_today_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onOpenUnifiedToday() }
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Today,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            "Unified Today Hub",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${todayOverview.tasksDueToday.size} Tasks • $pendingHabits Habits • ${todayOverview.billsDueSoon.size} Bills • $alertsCount Expiry Alerts",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalIconButton(
                        onClick = onOpenGlobalSearch,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("banner_global_search_btn")
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Global Search", modifier = Modifier.size(18.dp))
                    }
                    FilledTonalButton(
                        onClick = onOpenUnifiedToday,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Open Today", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

/**
 * Full Unified "Today" View Screen:
 * Tasks Due Today + Today's Habits + Bills Due + Expiring Documents & Maturing FDs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedTodayScreen(
    viewModel: FinanceViewModel,
    navController: NavController
) {
    val enabledModules by viewModel.enabledModules.collectAsStateWithLifecycle()
    val tasks by viewModel.allTasks.collectAsStateWithLifecycle()
    val habits by viewModel.activeHabits.collectAsStateWithLifecycle()
    val habitLogs by viewModel.habitLogs.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val goalContributions by viewModel.allGoalContributions.collectAsStateWithLifecycle()
    val bills by viewModel.allBills.collectAsStateWithLifecycle()
    val vaultDocs by viewModel.vaultDocuments.collectAsStateWithLifecycle()
    val holdings by viewModel.holdings.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val todayIso = remember { HabitUtils.getTodayIso() }

    val overview = remember(
        enabledModules,
        tasks,
        habits,
        habitLogs,
        transactions,
        goalContributions,
        bills,
        vaultDocs,
        holdings
    ) {
        EcosystemHubUtils.buildUnifiedTodayOverview(
            enabledModules = enabledModules,
            tasks = tasks,
            habits = habits,
            habitLogs = habitLogs,
            transactions = transactions,
            goalContributions = goalContributions,
            bills = bills,
            vaultDocuments = vaultDocs,
            holdings = holdings
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text("Unified Today View", fontWeight = FontWeight.Bold)
                        Text(
                            "Tasks • Habits • Bills Due • Expiring Vault Docs",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp)
        ) {
            // 1. Summary Header Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "TODAY'S FOCUS",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                            )
                            Text(
                                "${overview.totalActionableCount} Actionable Item${if (overview.totalActionableCount == 1) "" else "s"}",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Icon(
                            Icons.Default.FactCheck,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }

            // 2. Section: Today's Tasks (Isolated by "tasks" module toggle)
            if ("tasks" in enabledModules) {
                item {
                    SectionHeaderRow(
                        title = "Today's Tasks (${overview.tasksDueToday.size})",
                        actionText = "Open Tasks",
                        onAction = { navController.navigate("tasks") }
                    )
                }
                if (overview.tasksDueToday.isEmpty()) {
                    item {
                        EmptyModuleStrip("All tasks for today are completed!")
                    }
                } else {
                    items(overview.tasksDueToday, key = { "today_task_${it.id}" }) { task ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Checkbox(
                                        checked = task.status == "DONE",
                                        onCheckedChange = {
                                            viewModel.toggleTaskStatus(task, "DONE")
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("Completed task \"${task.title}\"")
                                            }
                                        }
                                    )
                                    Column {
                                        Text(
                                            task.title,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            textDecoration = if (task.status == "DONE") TextDecoration.LineThrough else null
                                        )
                                        Text(
                                            "${task.category} • Priority: ${task.priority}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (task.linkedAmount != null && task.linkedAmount > 0) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFFE8F5E9)
                                    ) {
                                        Text(
                                            FormatUtils.formatInr(task.linkedAmount),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF2E7D32),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Section: Today's Habits (Isolated by "habits" module toggle)
            if ("habits" in enabledModules) {
                item {
                    SectionHeaderRow(
                        title = "Today's Habits (${overview.habitsForToday.size})",
                        actionText = "Open Habits",
                        onAction = { navController.navigate("habits") }
                    )
                }
                if (overview.habitsForToday.isEmpty()) {
                    item {
                        EmptyModuleStrip("No habits scheduled for today.")
                    }
                } else {
                    items(overview.habitsForToday, key = { "today_habit_${it.habit.id}" }) { stat ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stat.habit.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                    Text(
                                        "Streak: ${stat.currentStreak}d • Progress: ${stat.selectedDayCount}/${stat.habit.targetCount} ${stat.habit.unitLabel}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (stat.habit.targetCount <= 1) {
                                    val isDone = stat.selectedDayStatus == "COMPLETED"
                                    FilledTonalButton(
                                        onClick = { viewModel.toggleHabitForDate(stat.habit, todayIso) }
                                    ) {
                                        Icon(
                                            if (isDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(if (isDone) "Done" else "Check Off")
                                    }
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { viewModel.adjustHabitCountForDate(stat.habit, todayIso, -1) }) {
                                            Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Decrement")
                                        }
                                        Text("${stat.selectedDayCount}", fontWeight = FontWeight.Bold)
                                        IconButton(onClick = { viewModel.adjustHabitCountForDate(stat.habit, todayIso, 1) }) {
                                            Icon(Icons.Default.AddCircleOutline, contentDescription = "Increment")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Section: Bills Due (Today / Overdue / Next 7 Days)
            item {
                SectionHeaderRow(
                    title = "Bills Due (${overview.billsDueSoon.size})",
                    actionText = "All Bills",
                    onAction = { navController.navigate("bills") }
                )
            }
            if (overview.billsDueSoon.isEmpty()) {
                item {
                    EmptyModuleStrip("No bills due in the next 7 days.")
                }
            } else {
                items(overview.billsDueSoon, key = { "today_bill_${it.id}" }) { bill ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(bill.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                Text(
                                    "Due: ${FormatUtils.formatDate(bill.dueDate)} • ${FormatUtils.formatInr(bill.amount)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFC62828)
                                )
                            }
                            Button(
                                onClick = {
                                    viewModel.markBillAsPaid(bill)
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Marked ${bill.title} as paid & logged expense")
                                    }
                                }
                            ) {
                                Text("Mark Paid")
                            }
                        }
                    }
                }
            }

            // 5. Section: Expiring Documents & Maturing FDs
            if ("document_vault" in enabledModules || "investments" in enabledModules) {
                item {
                    SectionHeaderRow(
                        title = "Expiring Warranties, IDs & FDs (${overview.expiringDocuments.size + overview.maturingFds.size})",
                        actionText = if ("document_vault" in enabledModules) "Open Vault" else "Investments",
                        onAction = {
                            if ("document_vault" in enabledModules) navController.navigate("document_vault")
                            else navController.navigate("investments")
                        }
                    )
                }

                if (overview.expiringDocuments.isEmpty() && overview.maturingFds.isEmpty()) {
                    item {
                        EmptyModuleStrip("No documents or FDs expiring within 30 days.")
                    }
                } else {
                    items(overview.expiringDocuments, key = { "exp_doc_${it.id}" }) { doc ->
                        val daysLeft = DocumentVaultUtils.daysUntilExpiry(doc.expiryDate) ?: 0L
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { navController.navigate("document_vault") },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                            border = BorderStroke(1.dp, Color(0xFFFFB74D))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        if (doc.isSensitive) "${doc.category} Document (PIN Protected)" else doc.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE65100)
                                    )
                                    Text(
                                        if (daysLeft < 0L) "Expired ${-daysLeft} days ago" else "Expires in $daysLeft days (${doc.category})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFFBF360C)
                                    )
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFFE65100))
                            }
                        }
                    }

                    items(overview.maturingFds, key = { "mat_fd_${it.holding.id}" }) { fd ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { navController.navigate("investments") },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                            border = BorderStroke(1.dp, Color(0xFF81C784))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "FD Maturity: ${fd.holding.name}",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1B5E20)
                                    )
                                    Text(
                                        "Matures in ${fd.daysToMaturity ?: 0} days • Value: ${FormatUtils.formatInr(fd.holding.currentValue)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF2E7D32)
                                    )
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFF1B5E20))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeaderRow(
    title: String,
    actionText: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        TextButton(onClick = onAction) {
            Text(actionText)
        }
    }
}

@Composable
private fun EmptyModuleStrip(text: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(14.dp)
        )
    }
}

/**
 * Dedicated "Money" Ecosystem Hub Screen for the "Money" Bottom-Nav / Sidebar item.
 * Combines quick sub-module pills (Accounts, Budgets, Goals, Bills, Recurring, Split, Investments, Udhaar, Reports)
 * with the full Transaction History & Add Transaction FAB.
 */
@Composable
fun MoneyHubScreen(
    viewModel: FinanceViewModel,
    navController: NavController
) {
    val enabledModules by viewModel.enabledModules.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { navController.navigate("add") },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add Transaction") },
                modifier = Modifier.testTag("money_hub_add_fab")
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Sub-navigation strip for all Money sub-modules
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MoneySubPill("Accounts", Icons.Default.AccountBalance) { navController.navigate("accounts") }
                    MoneySubPill("Budgets", Icons.Default.PieChart) { navController.navigate("budgets") }
                    MoneySubPill("Goals", Icons.Default.Savings) { navController.navigate("goals") }
                    MoneySubPill("Bills", Icons.Default.Receipt) { navController.navigate("bills") }
                    MoneySubPill("Recurring", Icons.Default.Autorenew) { navController.navigate("recurring") }
                    if ("split_bills" in enabledModules) {
                        MoneySubPill("Split Bills", Icons.Default.Groups) { navController.navigate("split_bills") }
                    }
                    if ("investments" in enabledModules) {
                        MoneySubPill("Investments", Icons.AutoMirrored.Filled.TrendingUp) { navController.navigate("investments") }
                    }
                    if ("udhaar" in enabledModules) {
                        MoneySubPill("Udhaar", Icons.Default.People) { navController.navigate("udhaar") }
                    }
                    if ("reports" in enabledModules) {
                        MoneySubPill("Reports", Icons.Default.Assessment) { navController.navigate("analytics") }
                    }
                    MoneySubPill("Fuel Mileage", Icons.Default.LocalGasStation) { navController.navigate("daily_toolkit?tab=1") }
                    MoneySubPill("Safe Spend & Subs", Icons.Default.Shield) { navController.navigate("daily_toolkit?tab=2") }
                    MoneySubPill("EMI & WhatsApp", Icons.Default.Calculate) { navController.navigate("daily_toolkit?tab=4") }
                }
            }

            // Full Transaction History inline
            Box(modifier = Modifier.weight(1f)) {
                HistoryScreen(viewModel = viewModel, navController = navController)
            }
        }
    }
}

@Composable
private fun MoneySubPill(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    AssistChip(
        onClick = onClick,
        leadingIcon = {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        },
        label = { Text(label, fontWeight = FontWeight.SemiBold) }
    )
}

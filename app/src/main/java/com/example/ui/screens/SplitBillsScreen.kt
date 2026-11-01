package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.data.*
import com.example.ui.components.AddSplitExpenseDialog
import com.example.ui.components.CreateSplitGroupDialog
import com.example.ui.components.SettleUpDialog
import com.example.utils.FormatUtils
import com.example.utils.ReportExportUtils
import com.example.utils.SplitExportUtils
import com.example.utils.SplitUtils
import com.example.viewmodel.FinanceViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SplitBillsScreen(viewModel: FinanceViewModel, navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val summaries by viewModel.splitGroupSummaries.collectAsStateWithLifecycle()
    val allExpenses by viewModel.splitExpenses.collectAsStateWithLifecycle()
    val allSettlements by viewModel.splitSettlements.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val people by viewModel.people.collectAsStateWithLifecycle()

    var selectedGroupId by remember { mutableStateOf<Long?>(null) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showAddExpenseDialog by remember { mutableStateOf(false) }
    var showSettleUpDialog by remember { mutableStateOf(false) }
    var settlePrefill by remember { mutableStateOf<SimplifiedDebt?>(null) }

    var groupPendingDelete by remember { mutableStateOf<SplitGroup?>(null) }
    var expensePendingDelete by remember { mutableStateOf<SplitExpense?>(null) }
    var settlementPendingDelete by remember { mutableStateOf<SplitSettlement?>(null) }

    val selectedSummary = remember(summaries, selectedGroupId) {
        summaries.firstOrNull { it.group.id == selectedGroupId }
    }

    if (selectedGroupId != null) {
        BackHandler {
            selectedGroupId = null
        }
    }

    if (showCreateGroupDialog) {
        CreateSplitGroupDialog(
            existingPeople = people,
            onDismiss = { showCreateGroupDialog = false },
            onCreate = { name, type, members, linkToUdhaar ->
                viewModel.createSplitGroup(name, type, members, linkToUdhaar) { createdId ->
                    selectedGroupId = createdId
                }
                showCreateGroupDialog = false
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Group '$name' created!")
                }
            }
        )
    }

    if (showAddExpenseDialog && selectedSummary != null) {
        AddSplitExpenseDialog(
            group = selectedSummary.group,
            members = selectedSummary.members,
            accounts = accounts,
            categories = categories,
            onDismiss = { showAddExpenseDialog = false },
            onSave = { title, totalAmount, category, paidBy, paymentAccount, splitType, splits, note ->
                viewModel.addSplitExpense(
                    groupId = selectedSummary.group.id,
                    groupName = selectedSummary.group.name,
                    title = title,
                    totalAmount = totalAmount,
                    category = category,
                    paidBy = paidBy,
                    paymentAccount = paymentAccount,
                    splitType = splitType,
                    splits = splits,
                    note = note
                )
                showAddExpenseDialog = false
                val myShare = splits.firstOrNull { it.memberName.equals("Me", ignoreCase = true) }?.amount ?: 0.0
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        "Saved '$title' (${FormatUtils.formatInr(totalAmount)}). Your expense share: ${FormatUtils.formatInr(myShare)}"
                    )
                }
            }
        )
    }

    if (showSettleUpDialog && selectedSummary != null) {
        SettleUpDialog(
            groupName = selectedSummary.group.name,
            members = selectedSummary.members,
            accounts = accounts,
            initialFromMember = settlePrefill?.fromMember,
            initialToMember = settlePrefill?.toMember,
            initialAmount = settlePrefill?.amount,
            onDismiss = {
                showSettleUpDialog = false
                settlePrefill = null
            },
            onSettle = { fromMember, toMember, amount, account, note ->
                viewModel.recordSplitSettlement(
                    groupId = selectedSummary.group.id,
                    groupName = selectedSummary.group.name,
                    fromMember = fromMember,
                    toMember = toMember,
                    amount = amount,
                    account = account,
                    note = note
                )
                showSettleUpDialog = false
                settlePrefill = null
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        "Settled ${FormatUtils.formatInr(amount)} ($fromMember → $toMember)"
                    )
                }
            }
        )
    }

    // Delete Confirmations
    if (groupPendingDelete != null) {
        AlertDialog(
            onDismissRequest = { groupPendingDelete = null },
            title = { Text("Delete Split Group?") },
            text = {
                Text("Delete '${groupPendingDelete?.name}' and all its shared expenses and linked transactions? This will restore account balances.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        groupPendingDelete?.let { grp ->
                            viewModel.deleteSplitGroup(grp.id)
                            if (selectedGroupId == grp.id) selectedGroupId = null
                        }
                        groupPendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Group")
                }
            },
            dismissButton = {
                TextButton(onClick = { groupPendingDelete = null }) { Text("Cancel") }
            }
        )
    }

    if (expensePendingDelete != null) {
        AlertDialog(
            onDismissRequest = { expensePendingDelete = null },
            title = { Text("Delete Shared Expense?") },
            text = {
                Text("Delete '${expensePendingDelete?.title}' (${FormatUtils.formatInr(expensePendingDelete?.totalAmount ?: 0.0)})? Linked account and report transactions will be restored.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        expensePendingDelete?.let { viewModel.deleteSplitExpense(it) }
                        expensePendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { expensePendingDelete = null }) { Text("Cancel") }
            }
        )
    }

    if (settlementPendingDelete != null) {
        AlertDialog(
            onDismissRequest = { settlementPendingDelete = null },
            title = { Text("Delete Settlement?") },
            text = {
                Text("Remove settlement of ${FormatUtils.formatInr(settlementPendingDelete?.amount ?: 0.0)} (${settlementPendingDelete?.fromMember} → ${settlementPendingDelete?.toMember})?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        settlementPendingDelete?.let { viewModel.deleteSplitSettlement(it) }
                        settlementPendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { settlementPendingDelete = null }) { Text("Cancel") }
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
                    if (selectedSummary != null) {
                        Column {
                            Text(selectedSummary.group.name, fontWeight = FontWeight.Bold)
                            Text(
                                "${selectedSummary.group.type} • ${selectedSummary.members.size} members",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        Column {
                            Text("Split Bills (Splitwise)", fontWeight = FontWeight.Bold)
                            Text(
                                "Group expenses, simplify debts & settle up",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (selectedSummary != null) {
                                selectedGroupId = null
                            } else {
                                navController.popBackStack()
                            }
                        },
                        modifier = Modifier.testTag("split_back_btn")
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (selectedSummary != null) {
                        IconButton(
                            onClick = {
                                val grpExpenses = allExpenses.filter { it.groupId == selectedSummary.group.id }
                                val grpSettlements = allSettlements.filter { it.groupId == selectedSummary.group.id }
                                SplitExportUtils.shareGroupWhatsAppText(
                                    context,
                                    selectedSummary,
                                    grpExpenses,
                                    grpSettlements
                                )
                            },
                            modifier = Modifier.testTag("split_share_whatsapp_btn")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share Summary for WhatsApp")
                        }
                        IconButton(
                            onClick = { groupPendingDelete = selectedSummary.group },
                            modifier = Modifier.testTag("split_delete_group_btn")
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete Group",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (selectedSummary != null) {
                ExtendedFloatingActionButton(
                    onClick = { showAddExpenseDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add Shared Expense") },
                    modifier = Modifier.testTag("split_add_expense_fab")
                )
            } else {
                ExtendedFloatingActionButton(
                    onClick = { showCreateGroupDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { Icon(Icons.Default.GroupAdd, contentDescription = null) },
                    text = { Text("New Split Group") },
                    modifier = Modifier.testTag("split_create_group_fab")
                )
            }
        }
    ) { innerPadding ->
        if (selectedSummary != null) {
            val grpExpenses = remember(allExpenses, selectedSummary.group.id) {
                allExpenses.filter { it.groupId == selectedSummary.group.id }
            }
            val grpSettlements = remember(allSettlements, selectedSummary.group.id) {
                allSettlements.filter { it.groupId == selectedSummary.group.id }
            }
            SplitGroupDetailContent(
                summary = selectedSummary,
                expenses = grpExpenses,
                settlements = grpSettlements,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                onOpenSettleDialog = { prefillDebt ->
                    settlePrefill = prefillDebt
                    showSettleUpDialog = true
                },
                onShareWhatsApp = {
                    SplitExportUtils.shareGroupWhatsAppText(context, selectedSummary, grpExpenses, grpSettlements)
                },
                onExportPdf = {
                    val file = SplitExportUtils.exportSplitGroupPdf(context, selectedSummary, grpExpenses, grpSettlements)
                    ReportExportUtils.shareFile(context, file, "application/pdf", "Export Group PDF")
                },
                onExportCsv = {
                    val file = SplitExportUtils.exportSplitGroupCsv(context, selectedSummary, grpExpenses, grpSettlements)
                    ReportExportUtils.shareFile(context, file, "text/csv", "Export Group CSV")
                },
                onSyncToUdhaar = {
                    viewModel.syncSplitGroupToUdhaar(selectedSummary) { msg ->
                        coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                    }
                },
                onDeleteExpense = { expensePendingDelete = it },
                onDeleteSettlement = { settlementPendingDelete = it }
            )
        } else {
            SplitGroupsOverviewContent(
                summaries = summaries,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                onSelectGroup = { selectedGroupId = it.group.id },
                onCreateGroupClick = { showCreateGroupDialog = true },
                onDeleteGroup = { groupPendingDelete = it.group }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SplitGroupsOverviewContent(
    summaries: List<SplitGroupSummary>,
    modifier: Modifier = Modifier,
    onSelectGroup: (SplitGroupSummary) -> Unit,
    onCreateGroupClick: () -> Unit,
    onDeleteGroup: (SplitGroupSummary) -> Unit
) {
    val totalOwedToMe = remember(summaries) {
        summaries.filter { it.myNetBalance > 0.01 }.sumOf { it.myNetBalance }
    }
    val totalIOwe = remember(summaries) {
        summaries.filter { it.myNetBalance < -0.01 }.sumOf { -it.myNetBalance }
    }
    val overallNet = totalOwedToMe - totalIOwe

    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        // Overall Split Balance Summary Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("split_overall_summary_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Overall Split Balance",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                        ) {
                            Text(
                                "${summaries.size} GROUPS",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    val netText = when {
                        overallNet > 0.01 -> "You are owed ${FormatUtils.formatInr(overallNet)} overall"
                        overallNet < -0.01 -> "You owe ${FormatUtils.formatInr(-overallNet)} overall"
                        else -> "All split groups settled up (₹0)"
                    }
                    Text(
                        text = netText,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFFE8F5E9),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    "You Get Back",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF2E7D32),
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    FormatUtils.formatInr(totalOwedToMe),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1B5E20)
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFFFFEBEE),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    "You Owe",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFC62828),
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    FormatUtils.formatInr(totalIOwe),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFB71C1C)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (summaries.isEmpty()) {
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
                            Icons.Default.Groups,
                            contentDescription = null,
                            modifier = Modifier.size(54.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "No Split Groups Yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Create a group for trips, flatmates, or dinners. Split bills equally, by exact amount, percentage, or shares — only your share counts in expense reports!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        Button(onClick = onCreateGroupClick) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Create First Split Group")
                        }
                    }
                }
            }
        } else {
            item {
                Text(
                    "Your Split Groups",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            items(summaries, key = { it.group.id }) { summary ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectGroup(summary) }
                        .testTag("split_group_card_${summary.group.id}"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            when (summary.group.type) {
                                                "Trip" -> Icons.Default.FlightTakeoff
                                                "Flatmates" -> Icons.Default.Home
                                                "Dinner" -> Icons.Default.Restaurant
                                                else -> Icons.Default.Groups
                                            },
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        summary.group.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "${summary.group.type} • ${summary.members.size} members • ${summary.expensesCount} bills",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            val statusColor = when {
                                summary.myNetBalance > 0.01 -> Color(0xFF2E7D32)
                                summary.myNetBalance < -0.01 -> Color(0xFFC62828)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = when {
                                        summary.myNetBalance > 0.01 -> "+${FormatUtils.formatInr(summary.myNetBalance)}"
                                        summary.myNetBalance < -0.01 -> "-${FormatUtils.formatInr(-summary.myNetBalance)}"
                                        else -> "Settled"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = statusColor
                                )
                                Text(
                                    text = when {
                                        summary.myNetBalance > 0.01 -> "you get back"
                                        summary.myNetBalance < -0.01 -> "you owe"
                                        else -> "all clear"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = statusColor
                                )
                            }
                        }

                        // Members chips
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            summary.members.forEach { member ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                ) {
                                    Text(
                                        text = member,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        if (summary.simplifiedDebts.isNotEmpty()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Text(
                                text = "Simplify Debts: " + summary.simplifiedDebts.joinToString(", ") {
                                    "${it.fromMember} → ${it.toMember} (${FormatUtils.formatInr(it.amount)})"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SplitGroupDetailContent(
    summary: SplitGroupSummary,
    expenses: List<SplitExpense>,
    settlements: List<SplitSettlement>,
    modifier: Modifier = Modifier,
    onOpenSettleDialog: (SimplifiedDebt?) -> Unit,
    onShareWhatsApp: () -> Unit,
    onExportPdf: () -> Unit,
    onExportCsv: () -> Unit,
    onSyncToUdhaar: () -> Unit,
    onDeleteExpense: (SplitExpense) -> Unit,
    onDeleteSettlement: (SplitSettlement) -> Unit
) {
    var activeTab by remember { mutableIntStateOf(0) } // 0: Balances & Simplify, 1: Expenses, 2: Settlements

    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        // Group Metrics Header Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                "Total Group Spend",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                            Text(
                                FormatUtils.formatInr(summary.totalGroupSpend),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "Your Net Position",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                            Text(
                                when {
                                    summary.myNetBalance > 0.01 -> "+${FormatUtils.formatInr(summary.myNetBalance)} (Owed to you)"
                                    summary.myNetBalance < -0.01 -> "-${FormatUtils.formatInr(-summary.myNetBalance)} (You owe)"
                                    else -> "Settled up (₹0)"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    summary.myNetBalance > 0.01 -> Color(0xFF1B5E20)
                                    summary.myNetBalance < -0.01 -> Color(0xFFB71C1C)
                                    else -> MaterialTheme.colorScheme.onPrimaryContainer
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("You Paid Total", style = MaterialTheme.typography.labelSmall)
                                Text(
                                    FormatUtils.formatInr(summary.myTotalPaid),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Your Expense Share", style = MaterialTheme.typography.labelSmall)
                                Text(
                                    FormatUtils.formatInr(summary.myTotalShare),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Export, Share & Udhaar Link Action Row
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = { onOpenSettleDialog(null) },
                    label = { Text("Settle Up") },
                    leadingIcon = { Icon(Icons.Default.Handshake, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("split_action_settle_up")
                )
                AssistChip(
                    onClick = onShareWhatsApp,
                    label = { Text("WhatsApp Summary") },
                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("split_action_whatsapp")
                )
                AssistChip(
                    onClick = onExportPdf,
                    label = { Text("Export PDF") },
                    leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("split_action_pdf")
                )
                AssistChip(
                    onClick = onExportCsv,
                    label = { Text("Export CSV") },
                    leadingIcon = { Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("split_action_csv")
                )
                AssistChip(
                    onClick = onSyncToUdhaar,
                    label = { Text(if (summary.group.linkToUdhaar) "Synced to Udhaar ✓" else "Link to Udhaar") },
                    leadingIcon = { Icon(Icons.Default.SyncAlt, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("split_action_sync_udhaar")
                )
            }
        }

        // Sub-navigation Tabs
        item {
            TabRow(
                selectedTabIndex = activeTab,
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.clip(RoundedCornerShape(14.dp))
            ) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    text = { Text("Balances & Debts", fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.testTag("split_tab_balances")
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    text = { Text("Expenses (${expenses.size})", fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.testTag("split_tab_expenses")
                )
                Tab(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    text = { Text("Settled (${settlements.size})", fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.testTag("split_tab_settlements")
                )
            }
        }

        when (activeTab) {
            0 -> {
                // Simplified Debts Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.AutoFixHigh,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column {
                                    Text(
                                        "Simplified Debts (Fewest Payments)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "Automatically minimizes total transfers needed to settle everyone",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (summary.simplifiedDebts.isEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFE8F5E9),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
                                        Text(
                                            "Everyone is settled up in this group!",
                                            color = Color(0xFF1B5E20),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            } else {
                                summary.simplifiedDebts.forEach { debt ->
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    "${debt.fromMember} pays ${debt.toMember}",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    FormatUtils.formatInr(debt.amount),
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            FilledTonalButton(
                                                onClick = { onOpenSettleDialog(debt) },
                                                modifier = Modifier.testTag("settle_debt_${debt.fromMember}_${debt.toMember}")
                                            ) {
                                                Text("Settle Up")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Per-Person Balances Section
                item {
                    Text(
                        "Per-Person Net Balances",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(summary.memberBalances, key = { it.memberName }) { mb ->
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
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (mb.memberName.equals("Me", ignoreCase = true)) "Me (You)" else mb.memberName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Paid: ${FormatUtils.formatInr(mb.totalPaid)} • Share: ${FormatUtils.formatInr(mb.totalShare)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            val color = when {
                                mb.netBalance > 0.01 -> Color(0xFF2E7D32)
                                mb.netBalance < -0.01 -> Color(0xFFC62828)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = when {
                                        mb.netBalance > 0.01 -> "+${FormatUtils.formatInr(mb.netBalance)}"
                                        mb.netBalance < -0.01 -> "-${FormatUtils.formatInr(-mb.netBalance)}"
                                        else -> "₹0"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = color
                                )
                                Text(
                                    text = when {
                                        mb.netBalance > 0.01 -> "gets back"
                                        mb.netBalance < -0.01 -> "owes"
                                        else -> "settled"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = color
                                )
                            }
                        }
                    }
                }
            }

            1 -> {
                if (expenses.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No shared expenses yet. Tap '+ Add Shared Expense' below.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(expenses, key = { it.id }) { exp ->
                        val splits = remember(exp.splitsJson) { SplitUtils.parseSplits(exp.splitsJson) }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            exp.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "${FormatUtils.formatDate(exp.timestamp)} • ${exp.category} • Paid by ${exp.paidBy} (${exp.splitType})",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            FormatUtils.formatInr(exp.totalAmount),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "Your share: ${FormatUtils.formatInr(exp.myShareAmount)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    splits.filter { it.amount > 0 }.forEach { sp ->
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                        ) {
                                            Text(
                                                "${sp.memberName}: ${FormatUtils.formatInr(sp.amount)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    IconButton(
                                        onClick = { onDeleteExpense(exp) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete Expense",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                if (settlements.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No settlements recorded yet.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(settlements, key = { it.id }) { st ->
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
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "${st.fromMember} paid ${st.toMember}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "${FormatUtils.formatDate(st.timestamp)} • Account: ${st.account}" +
                                                if (st.note.isNotBlank()) " • ${st.note}" else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        FormatUtils.formatInr(st.amount),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2E7D32)
                                    )
                                    IconButton(onClick = { onDeleteSettlement(st) }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete Settlement",
                                            tint = MaterialTheme.colorScheme.error,
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

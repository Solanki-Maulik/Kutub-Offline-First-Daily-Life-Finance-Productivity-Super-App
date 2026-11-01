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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.data.Person
import com.example.data.PersonUdhaarSummary
import com.example.ui.components.AddPersonDialog
import com.example.ui.components.AddUdhaarEntryDialog
import com.example.utils.FormatUtils
import com.example.viewmodel.FinanceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UdhaarOverviewScreen(
    viewModel: FinanceViewModel,
    navController: NavController
) {
    val people by viewModel.people.collectAsStateWithLifecycle()
    val peopleSummaries by viewModel.peopleSummaries.collectAsStateWithLifecycle()
    val overallToReceive by viewModel.overallToReceive.collectAsStateWithLifecycle()
    val overallToPay by viewModel.overallToPay.collectAsStateWithLifecycle()
    val overdueEntries by viewModel.overdueEntries.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val privacyMode by viewModel.privacyMode.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") } // All, To Receive, To Pay, Settled

    var showAddPersonDialog by remember { mutableStateOf(false) }
    var showAddEntryDialog by remember { mutableStateOf(false) }
    var addEntryType by remember { mutableStateOf("GAVE") }

    fun formatMoney(amount: Double): String =
        if (privacyMode) "₹****" else FormatUtils.formatInr(amount)

    val overallNet = overallToReceive - overallToPay

    // Filter people
    val filteredSummaries = peopleSummaries.filter { summary ->
        val matchesSearch = summary.person.name.contains(searchQuery, ignoreCase = true) ||
                summary.person.phone.contains(searchQuery) ||
                summary.person.note.contains(searchQuery, ignoreCase = true)

        val matchesFilter = when (selectedFilter) {
            "To Receive" -> summary.netBalance > 0
            "To Pay" -> summary.netBalance < 0
            "Settled" -> summary.netBalance == 0.0 && summary.totalGave + summary.totalTook > 0
            else -> true
        }

        matchesSearch && matchesFilter
    }

    // Dialogs
    if (showAddPersonDialog) {
        AddPersonDialog(
            onDismiss = { showAddPersonDialog = false },
            onSave = { name, phone, note ->
                viewModel.addPerson(name, phone, note) { newId ->
                    showAddPersonDialog = false
                    navController.navigate("person/$newId")
                }
            }
        )
    }

    if (showAddEntryDialog) {
        AddUdhaarEntryDialog(
            people = people,
            accounts = accounts,
            initialType = addEntryType,
            onDismiss = { showAddEntryDialog = false },
            onSave = { pId, type, amount, account, date, dueDate, note ->
                viewModel.addUdhaarEntry(pId, type, amount, account, date, dueDate, note)
                showAddEntryDialog = false
            },
            onQuickAddPerson = {
                showAddEntryDialog = false
                showAddPersonDialog = true
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text("People & Udhaar (Khata)", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.testTag("udhaar_back_button")
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    var showExportMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(
                            onClick = { showExportMenu = true },
                            modifier = Modifier.testTag("udhaar_export_button")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "Export Udhaar Ledger")
                        }
                        DropdownMenu(
                            expanded = showExportMenu,
                            onDismissRequest = { showExportMenu = false }
                        ) {
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color(0xFFC62828)) },
                                text = { Text("Export Ledger (PDF)") },
                                onClick = {
                                    showExportMenu = false
                                    try {
                                        val file = com.example.utils.ReportExportUtils.exportUdhaarPdf(
                                            context = navController.context,
                                            people = people,
                                            entries = viewModel.udhaarEntries.value,
                                            repayments = viewModel.udhaarRepayments.value
                                        )
                                        com.example.utils.ReportExportUtils.shareFile(navController.context, file, "application/pdf", "MoneyMate Udhaar Ledger PDF")
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(navController.context, "Export error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.Article, contentDescription = null, tint = Color(0xFF1565C0)) },
                                text = { Text("Export Ledger (CSV)") },
                                onClick = {
                                    showExportMenu = false
                                    try {
                                        val file = com.example.utils.ReportExportUtils.exportUdhaarCsv(
                                            context = navController.context,
                                            people = people,
                                            entries = viewModel.udhaarEntries.value,
                                            repayments = viewModel.udhaarRepayments.value
                                        )
                                        com.example.utils.ReportExportUtils.shareFile(navController.context, file, "text/csv", "MoneyMate Udhaar Ledger CSV")
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(navController.context, "Export error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }

                    IconButton(
                        onClick = { showAddPersonDialog = true },
                        modifier = Modifier.testTag("udhaar_add_person_top_button")
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = "Add Person")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (people.isEmpty()) {
                        showAddPersonDialog = true
                    } else {
                        showAddEntryDialog = true
                    }
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(if (people.isEmpty()) "Add Person" else "New Entry") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.testTag("udhaar_fab")
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 90.dp)
        ) {
            // Top Overview Cards: To Receive vs To Pay
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Udhaar Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = when {
                                    overallNet > 0 -> Color(0xFFE8F5E9)
                                    overallNet < 0 -> Color(0xFFFFEBEE)
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            ) {
                                Text(
                                    when {
                                        overallNet > 0 -> "Net: +${formatMoney(overallNet)} (To Receive)"
                                        overallNet < 0 -> "Net: -${formatMoney(kotlin.math.abs(overallNet))} (To Pay)"
                                        else -> "Net: Settled (₹0)"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        overallNet > 0 -> Color(0xFF2E7D32)
                                        overallNet < 0 -> Color(0xFFC62828)
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // To Receive Card
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = Color(0xFFE8F5E9),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedFilter = "To Receive" }
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier.size(28.dp).clip(CircleShape).background(Color(0xFFC8E6C9)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.ArrowOutward, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Text("To Receive", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Text(formatMoney(overallToReceive), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
                                    Text("They owe you", style = MaterialTheme.typography.labelSmall, color = Color(0xFF388E3C))
                                }
                            }

                            // To Pay Card
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = Color(0xFFFFEBEE),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedFilter = "To Pay" }
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier.size(28.dp).clip(CircleShape).background(Color(0xFFFFCDD2)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = Color(0xFFC62828), modifier = Modifier.size(16.dp))
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Text("To Pay", style = MaterialTheme.typography.labelSmall, color = Color(0xFFC62828), fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Text(formatMoney(overallToPay), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFFB71C1C))
                                    Text("You owe them", style = MaterialTheme.typography.labelSmall, color = Color(0xFFD32F2F))
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        // Quick action buttons: + Give / - Take
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    addEntryType = "GAVE"
                                    if (people.isEmpty()) showAddPersonDialog = true else showAddEntryDialog = true
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.ArrowOutward, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF2E7D32))
                                Spacer(Modifier.width(6.dp))
                                Text("+ Give Money", style = MaterialTheme.typography.labelMedium)
                            }

                            OutlinedButton(
                                onClick = {
                                    addEntryType = "TOOK"
                                    if (people.isEmpty()) showAddPersonDialog = true else showAddEntryDialog = true
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFC62828))
                                Spacer(Modifier.width(6.dp))
                                Text("- Borrow Money", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

            // Overdue Highlight Banner (if any entries are overdue)
            if (overdueEntries.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("overdue_alert_banner"),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                        border = BorderStroke(1.dp, Color(0xFFEF9A9A))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFC62828))
                                    Text(
                                        "${overdueEntries.size} Overdue Payment${if (overdueEntries.size > 1) "s" else ""}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFB71C1C)
                                    )
                                }
                            }

                            Spacer(Modifier.height(10.dp))

                            // List of overdue entries
                            overdueEntries.take(4).forEach { (person, entry) ->
                                val dueStatus = FormatUtils.calculateDueStatus(entry.dueDate)
                                val daysText = if (dueStatus is com.example.data.DueStatus.Overdue) "${dueStatus.days} days" else "today"
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable { navController.navigate("person/${person.id}") }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(person.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                            Text("Overdue by $daysText (${FormatUtils.formatDate(entry.dueDate ?: 0)})", style = MaterialTheme.typography.labelSmall, color = Color(0xFFC62828))
                                        }
                                        Text(formatMoney(entry.remainingAmount), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = Color(0xFFB71C1C))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Search Bar & Filter Chips
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("udhaar_search_input"),
                        placeholder = { Text("Search by name, phone, or note...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("All", "To Receive", "To Pay", "Settled").forEach { filter ->
                            FilterChip(
                                selected = selectedFilter == filter,
                                onClick = { selectedFilter = filter },
                                label = { Text(filter) },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.testTag("udhaar_filter_$filter")
                            )
                        }
                    }
                }
            }

            // People List
            if (filteredSummaries.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(36.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                if (people.isEmpty()) "No People in Ledger Yet" else "No matching results",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (people.isEmpty()) "Add a contact or friend to track money lent and borrowed." else "Try a different search or filter option.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (people.isEmpty()) {
                                Spacer(Modifier.height(16.dp))
                                Button(
                                    onClick = { showAddPersonDialog = true },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.PersonAdd, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Add First Person")
                                }
                            }
                        }
                    }
                }
            } else {
                items(filteredSummaries, key = { it.person.id }) { summary ->
                    val person = summary.person
                    val initial = person.name.trim().take(1).uppercase()
                    val avatarColor = when (kotlin.math.abs(person.name.hashCode()) % 5) {
                        0 -> Color(0xFF1565C0)
                        1 -> Color(0xFF2E7D32)
                        2 -> Color(0xFF7B1FA2)
                        3 -> Color(0xFFE65100)
                        else -> Color(0xFF00897B)
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { navController.navigate("person/${person.id}") }
                            .testTag("person_item_${person.id}"),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(
                            1.dp,
                            if (summary.hasOverdue) Color(0xFFEF9A9A) else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Avatar circle
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(avatarColor.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(initial, fontWeight = FontWeight.Bold, color = avatarColor, style = MaterialTheme.typography.titleMedium)
                                }

                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(person.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                        if (summary.hasOverdue) {
                                            Spacer(Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFFFFEBEE)
                                            ) {
                                                Text("OVERDUE", style = MaterialTheme.typography.labelSmall, color = Color(0xFFC62828), fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                            }
                                        }
                                    }

                                    if (person.phone.isNotBlank()) {
                                        Text(person.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    } else if (person.note.isNotBlank()) {
                                        Text(person.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }

                                    Text(
                                        "${summary.activeEntriesCount} active entry" + if (summary.activeEntriesCount != 1) "s" else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    )
                                }
                            }

                            // Net Amount Badge
                            Column(horizontalAlignment = Alignment.End) {
                                val net = summary.netBalance
                                val statusText = when {
                                    net > 0 -> "They owe"
                                    net < 0 -> "You owe"
                                    else -> "Settled"
                                }
                                val statusColor = when {
                                    net > 0 -> Color(0xFF2E7D32)
                                    net < 0 -> Color(0xFFC62828)
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }

                                Text(
                                    statusText,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = statusColor
                                )
                                Text(
                                    formatMoney(kotlin.math.abs(net)),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = statusColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

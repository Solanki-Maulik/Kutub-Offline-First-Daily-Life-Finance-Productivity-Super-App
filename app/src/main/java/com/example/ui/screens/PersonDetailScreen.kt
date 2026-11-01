package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.data.*
import com.example.ui.components.AddPersonDialog
import com.example.ui.components.AddUdhaarEntryDialog
import com.example.ui.components.RecordRepaymentDialog
import com.example.utils.FormatUtils
import com.example.utils.UdhaarShareUtils
import com.example.viewmodel.FinanceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonDetailScreen(
    personId: Long,
    viewModel: FinanceViewModel,
    navController: NavController
) {
    val context = LocalContext.current
    BackHandler { navController.popBackStack() }

    val people by viewModel.people.collectAsStateWithLifecycle()
    val allEntries by viewModel.udhaarEntries.collectAsStateWithLifecycle()
    val allRepayments by viewModel.udhaarRepayments.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val privacyMode by viewModel.privacyMode.collectAsStateWithLifecycle()

    val person = people.firstOrNull { it.id == personId }
    val entries = allEntries.filter { it.personId == personId }.sortedByDescending { it.date }
    val repayments = allRepayments.filter { it.personId == personId }

    var showEditPersonDialog by remember { mutableStateOf(false) }
    var showDeletePersonDialog by remember { mutableStateOf(false) }
    var showAddEntryDialog by remember { mutableStateOf(false) }
    var addEntryType by remember { mutableStateOf("GAVE") }
    var entryForRepayment by remember { mutableStateOf<UdhaarEntry?>(null) }
    var entryToDelete by remember { mutableStateOf<UdhaarEntry?>(null) }
    var repaymentToDelete by remember { mutableStateOf<UdhaarRepayment?>(null) }

    fun formatMoney(amount: Double): String =
        if (privacyMode) "₹****" else FormatUtils.formatInr(amount)

    // Calculations for this person
    val totalGave = entries.filter { it.type == "GAVE" }.sumOf { it.amount }
    val totalTook = entries.filter { it.type == "TOOK" }.sumOf { it.amount }
    val remainingToReceive = entries.filter { it.type == "GAVE" && !it.isSettled }.sumOf { it.remainingAmount }
    val remainingToPay = entries.filter { it.type == "TOOK" && !it.isSettled }.sumOf { it.remainingAmount }
    val netBalance = remainingToReceive - remainingToPay

    if (person == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Person Details") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("Person not found.")
            }
        }
        return
    }

    // Dialogs
    if (showEditPersonDialog) {
        AddPersonDialog(
            initialPerson = person,
            onDismiss = { showEditPersonDialog = false },
            onSave = { name, phone, note ->
                viewModel.updatePerson(person.id, name, phone, note)
                showEditPersonDialog = false
            }
        )
    }

    if (showDeletePersonDialog) {
        AlertDialog(
            onDismissRequest = { showDeletePersonDialog = false },
            title = { Text("Delete ${person.name}?", fontWeight = FontWeight.Bold) },
            text = {
                Text("This will permanently delete ${person.name} and all their lent, borrowed, and repayment records. Account balances will be updated accordingly.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePerson(person.id)
                        showDeletePersonDialog = false
                        navController.popBackStack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_person_button")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeletePersonDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddEntryDialog) {
        AddUdhaarEntryDialog(
            preselectedPerson = person,
            people = listOf(person),
            accounts = accounts,
            initialType = addEntryType,
            onDismiss = { showAddEntryDialog = false },
            onSave = { pId, type, amount, account, date, dueDate, note ->
                viewModel.addUdhaarEntry(pId, type, amount, account, date, dueDate, note)
                showAddEntryDialog = false
            }
        )
    }

    if (entryForRepayment != null) {
        RecordRepaymentDialog(
            entry = entryForRepayment!!,
            person = person,
            accounts = accounts,
            onDismiss = { entryForRepayment = null },
            onSave = { amount, account, date, note ->
                viewModel.recordRepayment(entryForRepayment!!.id, amount, account, date, note)
                entryForRepayment = null
            }
        )
    }

    if (entryToDelete != null) {
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            title = { Text("Delete Entry?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Delete this record of ${FormatUtils.formatInr(entryToDelete!!.amount)}? Any partial repayments linked to it and its account balance impact will also be removed.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteUdhaarEntry(entryToDelete!!.id)
                        entryToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { entryToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (repaymentToDelete != null) {
        AlertDialog(
            onDismissRequest = { repaymentToDelete = null },
            title = { Text("Delete Repayment?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Delete this repayment of ${FormatUtils.formatInr(repaymentToDelete!!.amount)}? The pending balance on the entry will be restored.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteUdhaarRepayment(repaymentToDelete!!.id)
                        repaymentToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { repaymentToDelete = null }) {
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
                        Text(person.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                        if (person.phone.isNotBlank()) {
                            Text(person.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.testTag("person_detail_back_button")
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            UdhaarShareUtils.shareSummary(context, person, entries, repayments)
                        },
                        modifier = Modifier.testTag("person_share_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share Ledger via WhatsApp/SMS")
                    }
                    IconButton(
                        onClick = { showEditPersonDialog = true },
                        modifier = Modifier.testTag("person_edit_button")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Person")
                    }
                    IconButton(
                        onClick = { showDeletePersonDialog = true },
                        modifier = Modifier.testTag("person_delete_button")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Person", tint = MaterialTheme.colorScheme.error)
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
            contentPadding = PaddingValues(bottom = 90.dp)
        ) {
            // Header Net Balance Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            netBalance > 0 -> Color(0xFFE8F5E9)
                            netBalance < 0 -> Color(0xFFFFEBEE)
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        }
                    ),
                    border = BorderStroke(
                        1.dp,
                        when {
                            netBalance > 0 -> Color(0xFF81C784)
                            netBalance < 0 -> Color(0xFFE57373)
                            else -> MaterialTheme.colorScheme.outlineVariant
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            when {
                                netBalance > 0 -> "${person.name} owes you"
                                netBalance < 0 -> "You owe ${person.name}"
                                else -> "All Settled with ${person.name}"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = when {
                                netBalance > 0 -> Color(0xFF2E7D32)
                                netBalance < 0 -> Color(0xFFC62828)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )

                        Spacer(Modifier.height(6.dp))

                        Text(
                            formatMoney(kotlin.math.abs(netBalance)),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                netBalance > 0 -> Color(0xFF1B5E20)
                                netBalance < 0 -> Color(0xFFB71C1C)
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )

                        if (person.note.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "“${person.note}”",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        // Quick Contact & Share row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (person.phone.isNotBlank()) {
                                FilledTonalButton(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${person.phone}"))
                                        context.startActivity(intent)
                                    },
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Call")
                                }

                                FilledTonalButton(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("sms:${person.phone}"))
                                        context.startActivity(intent)
                                    },
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.Message, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("SMS")
                                }
                            }

                            FilledTonalButton(
                                onClick = {
                                    UdhaarShareUtils.shareSummary(context, person, entries, repayments)
                                },
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("WhatsApp")
                            }
                        }
                    }
                }
            }

            // Summary Breakdown (Given vs Taken)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.size(28.dp).clip(CircleShape).background(Color(0xFFE8F5E9)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.ArrowOutward, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                                }
                                Spacer(Modifier.width(8.dp))
                                Text("Total Given", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(formatMoney(totalGave), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                            Text("Pending: ${formatMoney(remainingToReceive)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.size(28.dp).clip(CircleShape).background(Color(0xFFFFEBEE)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = Color(0xFFC62828), modifier = Modifier.size(16.dp))
                                }
                                Spacer(Modifier.width(8.dp))
                                Text("Total Taken", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(formatMoney(totalTook), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                            Text("Pending: ${formatMoney(remainingToPay)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Action Buttons: + I Gave / - I Took
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            addEntryType = "GAVE"
                            showAddEntryDialog = true
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("action_i_gave_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    ) {
                        Icon(Icons.Default.ArrowOutward, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("+ I Gave (Lent)", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            addEntryType = "TOOK"
                            showAddEntryDialog = true
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("action_i_took_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                    ) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("- I Took (Borrowed)", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Ledger Section Title
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Ledger History (${entries.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Ledger Entries
            if (entries.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(12.dp))
                            Text("No records with ${person.name} yet", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text("Tap '+ I Gave' or '- I Took' above to start tracking.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                items(entries, key = { it.id }) { entry ->
                    val isGave = entry.type == "GAVE"
                    val entryRepayments = repayments.filter { it.entryId == entry.id }.sortedByDescending { it.date }
                    val dueStatus = FormatUtils.calculateDueStatus(entry.dueDate)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("udhaar_entry_card_${entry.id}"),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(
                            1.dp,
                            if (dueStatus is DueStatus.Overdue && !entry.isSettled) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Top Row: Type Badge + Amount + Options
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(if (isGave) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            if (isGave) Icons.Default.ArrowOutward else Icons.Default.ArrowDownward,
                                            contentDescription = null,
                                            tint = if (isGave) Color(0xFF2E7D32) else Color(0xFFC62828),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Column {
                                        Text(
                                            if (isGave) "I Gave (Lent)" else "I Took (Borrowed)",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            FormatUtils.formatDate(entry.date) + " • ${entry.account}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            formatMoney(entry.amount),
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = if (isGave) Color(0xFF2E7D32) else Color(0xFFC62828)
                                        )
                                        if (!entry.isSettled && entry.remainingAmount < entry.amount) {
                                            Text(
                                                "Pending: ${formatMoney(entry.remainingAmount)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = { entryToDelete = entry },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Entry", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }

                            // Note
                            if (entry.note.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    entry.note,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // Badges Row: Settled vs Active & Due Date
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Status Badge
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (entry.isSettled) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.secondaryContainer
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (entry.isSettled) {
                                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF2E7D32))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Settled", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                                            } else {
                                                Text("Active", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    // Due Date Badge
                                    if (!entry.isSettled && entry.dueDate != null) {
                                        when (dueStatus) {
                                            is DueStatus.Overdue -> {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = Color(0xFFFFEBEE)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFFC62828))
                                                        Spacer(Modifier.width(4.dp))
                                                        Text("Overdue by ${dueStatus.days}d", style = MaterialTheme.typography.labelSmall, color = Color(0xFFC62828), fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                            DueStatus.DueToday -> {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = Color(0xFFFFF3E0)
                                                ) {
                                                    Text("Due Today", style = MaterialTheme.typography.labelSmall, color = Color(0xFFE65100), fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                                }
                                            }
                                            is DueStatus.DueSoon -> {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = MaterialTheme.colorScheme.surfaceVariant
                                                ) {
                                                    Text("Due in ${dueStatus.days}d", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                                }
                                            }
                                            is DueStatus.DueLater -> {
                                                Text("Due: ${dueStatus.dateString}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            DueStatus.None -> {}
                                        }
                                    }
                                }

                                // Record Repayment button if not settled
                                if (!entry.isSettled) {
                                    FilledTonalButton(
                                        onClick = { entryForRepayment = entry },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.testTag("record_repayment_button_${entry.id}")
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Repayment", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            // Repayments sub-list
                            if (entryRepayments.isNotEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                Spacer(Modifier.height(8.dp))
                                Text("Repayments (${entryRepayments.size}):", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(6.dp))

                                entryRepayments.forEach { rep ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                                                Column {
                                                    Text(
                                                        "${formatMoney(rep.amount)} on ${FormatUtils.formatDate(rep.date)}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    Text(
                                                        "Account: ${rep.account}" + if (rep.note.isNotBlank()) " • ${rep.note}" else "",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            IconButton(
                                                onClick = { repaymentToDelete = rep },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "Delete Repayment", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
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
    }
}

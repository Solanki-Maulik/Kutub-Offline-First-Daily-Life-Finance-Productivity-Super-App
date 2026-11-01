package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.example.data.RecurringTransaction
import com.example.utils.FormatUtils
import com.example.viewmodel.FinanceViewModel
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecurringTabContent(
    viewModel: FinanceViewModel
) {
    val recurringList by viewModel.recurringTransactions.collectAsStateWithLifecycle()
    val pendingApprovals by viewModel.pendingApprovalRecurring.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }
    var newAmount by remember { mutableStateOf("") }
    var newIsIncome by remember { mutableStateOf(false) }
    var newCategory by remember { mutableStateOf("Rent") }
    var newAccount by remember { mutableStateOf("Bank Account") }
    var newFrequency by remember { mutableStateOf("MONTHLY") }
    var newAutoCreate by remember { mutableStateOf(true) }
    var newPendingConfirmation by remember { mutableStateOf(false) }
    var newNote by remember { mutableStateOf("") }
    var newCustomDueDate by remember { mutableStateOf("") }

    var editingRecurring by remember { mutableStateOf<RecurringTransaction?>(null) }

    val frequencies = listOf("DAILY", "WEEKLY", "MONTHLY", "YEARLY")

    // Add Recurring Dialog
    if (showAddDialog) {
        val catList = categories.filter { it.isIncome == newIsIncome }.map { it.name }
            .ifEmpty { if (newIsIncome) listOf("Salary", "Business", "Investment") else listOf("Rent", "SIP", "Bills", "Subscription") }
        if (newCategory !in catList) {
            newCategory = catList.first()
        }
        val accList = accounts.map { it.name }.ifEmpty { listOf("Bank Account", "UPI Wallet", "Cash in Hand") }
        if (newAccount !in accList) {
            newAccount = accList.first()
        }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("New Recurring Transaction") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 450.dp)
                ) {
                    // Type Selector: Expense vs Income
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = !newIsIncome,
                            onClick = { newIsIncome = false },
                            label = { Text("Expense (Rent, SIP)") },
                            modifier = Modifier.weight(1f).testTag("recurring_type_expense")
                        )
                        FilterChip(
                            selected = newIsIncome,
                            onClick = { newIsIncome = true },
                            label = { Text("Income (Salary)") },
                            modifier = Modifier.weight(1f).testTag("recurring_type_income")
                        )
                    }

                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("Title (e.g. Flat Rent, Netflix, SIP)") },
                        modifier = Modifier.fillMaxWidth().testTag("recurring_title_input"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = newAmount,
                        onValueChange = { newAmount = it },
                        label = { Text("Amount (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("recurring_amount_input"),
                        singleLine = true
                    )

                    Text("Frequency", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        frequencies.forEach { freq ->
                            FilterChip(
                                selected = newFrequency == freq,
                                onClick = { newFrequency = freq },
                                label = { Text(freq.lowercase().replaceFirstChar { it.uppercase() }) },
                                modifier = Modifier.testTag("freq_chip_$freq")
                            )
                        }
                    }

                    Text("Account", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        accList.forEach { acc ->
                            FilterChip(
                                selected = newAccount == acc,
                                onClick = { newAccount = acc },
                                label = { Text(acc) },
                                modifier = Modifier.testTag("recurring_acc_$acc")
                            )
                        }
                    }

                    OutlinedTextField(
                        value = newCustomDueDate,
                        onValueChange = { newCustomDueDate = it },
                        label = { Text("Next Due Date (DD/MM/YYYY) - Blank for today") },
                        modifier = Modifier.fillMaxWidth().testTag("recurring_due_date_input"),
                        singleLine = true
                    )

                    // Auto-create & Pending confirmation options
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = newAutoCreate,
                            onCheckedChange = { newAutoCreate = it },
                            modifier = Modifier.testTag("recurring_autocreate_checkbox")
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Auto-create transaction on due date", style = MaterialTheme.typography.bodySmall)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = newPendingConfirmation,
                            onCheckedChange = { newPendingConfirmation = it },
                            modifier = Modifier.testTag("recurring_confirmation_checkbox")
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Ask for confirmation before creating", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = newAmount.toDoubleOrNull()
                        if (newTitle.isNotBlank() && amount != null && amount > 0) {
                            val nextDue = if (newCustomDueDate.isNotBlank()) {
                                FormatUtils.parseDate(newCustomDueDate) ?: System.currentTimeMillis()
                            } else System.currentTimeMillis()

                            viewModel.addRecurringTransaction(
                                title = newTitle.trim(),
                                amount = amount,
                                isIncome = newIsIncome,
                                category = newCategory,
                                account = newAccount,
                                frequency = newFrequency,
                                nextDueDate = nextDue,
                                autoCreate = newAutoCreate,
                                pendingConfirmation = newPendingConfirmation,
                                note = newNote.trim()
                            )
                            showAddDialog = false
                            newTitle = ""
                            newAmount = ""
                            newCustomDueDate = ""
                        }
                    },
                    modifier = Modifier.testTag("save_recurring_button")
                ) {
                    Text("Save Recurring")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp)
    ) {
        // 1. Pending Approvals Banner
        if (pendingApprovals.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pending_recurring_card"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                    border = BorderStroke(1.5.dp, Color(0xFFF57C00))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = Color(0xFFE65100))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Pending Recurring Approvals (${pendingApprovals.size})",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE65100),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "These scheduled payments are due and require your approval before adding to transactions.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))

                        pendingApprovals.forEach { pending ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(pending.title, fontWeight = FontWeight.Bold)
                                        Text(
                                            "${FormatUtils.formatInr(pending.amount)} • ${pending.frequency.lowercase()} • ${pending.account}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        TextButton(
                                            onClick = { viewModel.skipRecurringCycle(pending) },
                                            modifier = Modifier.testTag("skip_recurring_${pending.id}")
                                        ) {
                                            Text("Skip")
                                        }
                                        Button(
                                            onClick = { viewModel.executeRecurringTransaction(pending, createTx = true) },
                                            modifier = Modifier.testTag("confirm_recurring_${pending.id}")
                                        ) {
                                            Text("Confirm")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Summary & Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Recurring Payments",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Rent, SIPs, Subscriptions & Salary",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.testTag("add_recurring_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New")
                }
            }
        }

        // 3. Recurring Items List
        if (recurringList.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Repeat,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "No Recurring Transactions",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Schedule monthly rent, mutual fund SIPs, salary, or subscriptions like Netflix.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { showAddDialog = true },
                            modifier = Modifier.testTag("create_first_recurring_button")
                        ) {
                            Text("Create Recurring Payment")
                        }
                    }
                }
            }
        } else {
            items(recurringList, key = { it.id }) { item ->
                val isIncome = item.isIncome
                val amountColor = if (isIncome) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("recurring_item_${item.id}"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (item.isActive) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(amountColor.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        if (isIncome) Icons.Default.TrendingUp else Icons.Default.Repeat,
                                        contentDescription = null,
                                        tint = amountColor,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        item.title,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        "${item.frequency.lowercase().replaceFirstChar { it.uppercase() }} • ${item.account}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    (if (isIncome) "+" else "-") + FormatUtils.formatInr(item.amount),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = amountColor
                                )
                                Switch(
                                    checked = item.isActive,
                                    onCheckedChange = { viewModel.toggleRecurringActive(item.id, it) },
                                    modifier = Modifier.testTag("toggle_recurring_${item.id}")
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "Next Due: ${FormatUtils.formatDate(item.nextDueDate)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    if (item.pendingConfirmation) "Requires confirmation" else if (item.autoCreate) "Auto-creates on date" else "Manual execute",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(
                                    onClick = { viewModel.executeRecurringTransaction(item, createTx = true) },
                                    modifier = Modifier.testTag("execute_now_recurring_${item.id}")
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(2.dp))
                                    Text("Pay Now")
                                }
                                IconButton(
                                    onClick = { viewModel.deleteRecurringTransaction(item.id) },
                                    modifier = Modifier.testTag("delete_recurring_${item.id}")
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

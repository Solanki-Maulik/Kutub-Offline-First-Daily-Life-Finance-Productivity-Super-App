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
import com.example.data.BillReminder
import com.example.utils.FormatUtils
import com.example.viewmodel.FinanceViewModel
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BillsTabContent(
    viewModel: FinanceViewModel
) {
    val allBills by viewModel.allBills.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }
    var newAmount by remember { mutableStateOf("") }
    var newCategory by remember { mutableStateOf("Bills") }
    var newAccount by remember { mutableStateOf("Bank Account") }
    var newDueDateStr by remember { mutableStateOf("") }
    var newIsRecurring by remember { mutableStateOf(true) }
    var newFrequency by remember { mutableStateOf("MONTHLY") }
    var newNote by remember { mutableStateOf("") }

    var showPaidHistory by remember { mutableStateOf(false) }

    val now = System.currentTimeMillis()
    val pendingBills = remember(allBills) {
        allBills.filter { !it.isPaid }.sortedBy { it.dueDate }
    }
    val overdueBills = remember(pendingBills) {
        pendingBills.filter { it.dueDate < now }
    }
    val upcomingBills = remember(pendingBills) {
        pendingBills.filter { it.dueDate >= now }
    }
    val paidBills = remember(allBills) {
        allBills.filter { it.isPaid }.sortedByDescending { it.paidDate ?: 0L }
    }

    val totalPendingAmount = remember(pendingBills) {
        pendingBills.sumOf { it.amount }
    }

    // Add Bill Dialog
    if (showAddDialog) {
        val catList = categories.filter { !it.isIncome }.map { it.name }.ifEmpty { listOf("Bills", "Recharge", "Rent", "Utilities", "Other") }
        if (newCategory !in catList) {
            newCategory = catList.first()
        }
        val accList = accounts.map { it.name }.ifEmpty { listOf("Bank Account", "UPI Wallet", "Cash in Hand") }
        if (newAccount !in accList) {
            newAccount = accList.first()
        }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("New Bill Reminder") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 450.dp)
                ) {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("Bill Title (e.g. Electricity, WiFi, Credit Card)") },
                        modifier = Modifier.fillMaxWidth().testTag("bill_title_input"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = newAmount,
                        onValueChange = { newAmount = it },
                        label = { Text("Amount (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("bill_amount_input"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = newDueDateStr,
                        onValueChange = { newDueDateStr = it },
                        label = { Text("Due Date (DD/MM/YYYY) - Blank for in 7 days") },
                        modifier = Modifier.fillMaxWidth().testTag("bill_due_date_input"),
                        singleLine = true
                    )

                    Text("Category", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        catList.take(6).forEach { cat ->
                            FilterChip(
                                selected = newCategory == cat,
                                onClick = { newCategory = cat },
                                label = { Text(cat) },
                                modifier = Modifier.testTag("bill_cat_$cat")
                            )
                        }
                    }

                    Text("Default Payment Account", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        accList.forEach { acc ->
                            FilterChip(
                                selected = newAccount == acc,
                                onClick = { newAccount = acc },
                                label = { Text(acc) },
                                modifier = Modifier.testTag("bill_acc_$acc")
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = newIsRecurring,
                            onCheckedChange = { newIsRecurring = it },
                            modifier = Modifier.testTag("bill_recurring_checkbox")
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Recurring Monthly Bill", style = MaterialTheme.typography.bodySmall)
                    }

                    OutlinedTextField(
                        value = newNote,
                        onValueChange = { newNote = it },
                        label = { Text("Note (Optional, e.g. Consumer No)") },
                        modifier = Modifier.fillMaxWidth().testTag("bill_note_input"),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = newAmount.toDoubleOrNull()
                        if (newTitle.isNotBlank() && amount != null && amount > 0) {
                            val dueDate = if (newDueDateStr.isNotBlank()) {
                                FormatUtils.parseDate(newDueDateStr) ?: (System.currentTimeMillis() + 7 * 86400000L)
                            } else {
                                System.currentTimeMillis() + 7 * 86400000L
                            }

                            viewModel.addBillReminder(
                                title = newTitle.trim(),
                                amount = amount,
                                dueDate = dueDate,
                                category = newCategory,
                                account = newAccount,
                                isRecurring = newIsRecurring,
                                recurringFrequency = if (newIsRecurring) newFrequency else null,
                                note = newNote.trim()
                            )
                            showAddDialog = false
                            newTitle = ""
                            newAmount = ""
                            newDueDateStr = ""
                            newNote = ""
                        }
                    },
                    modifier = Modifier.testTag("save_bill_button")
                ) {
                    Text("Save Bill")
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
        // 1. Summary Hero Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bills_summary_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (overdueBills.isNotEmpty()) Color(0xFFFFEBEE) else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                ),
                border = BorderStroke(
                    1.dp,
                    if (overdueBills.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Bill Reminders",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${pendingBills.size} Pending • ${overdueBills.size} Overdue",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (overdueBills.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilledTonalButton(
                            onClick = { showAddDialog = true },
                            modifier = Modifier.testTag("add_bill_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("New Bill")
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text(
                                "Total Due Amount",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                FormatUtils.formatInr(totalPendingAmount),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (overdueBills.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }
                        FilterChip(
                            selected = showPaidHistory,
                            onClick = { showPaidHistory = !showPaidHistory },
                            label = { Text(if (showPaidHistory) "Show Pending" else "Paid History (${paidBills.size})") },
                            leadingIcon = { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.testTag("toggle_paid_history_button")
                        )
                    }
                }
            }
        }

        // 2. Overdue Bills (if any)
        if (!showPaidHistory && overdueBills.isNotEmpty()) {
            item {
                Text(
                    "⚠️ Overdue Bills",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            items(overdueBills, key = { it.id }) { bill ->
                BillCard(
                    bill = bill,
                    isOverdue = true,
                    onMarkPaid = { viewModel.markBillAsPaid(bill) },
                    onDelete = { viewModel.deleteBillReminder(bill.id) }
                )
            }
        }

        // 3. Upcoming Bills
        if (!showPaidHistory) {
            item {
                Text(
                    "Upcoming Bills",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            if (upcomingBills.isEmpty() && overdueBills.isEmpty()) {
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
                                Icons.Default.Receipt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "All Bills Are Paid!",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "No pending bills right now. Add your electricity, WiFi, or credit card bills.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = { showAddDialog = true },
                                modifier = Modifier.testTag("create_first_bill_button")
                            ) {
                                Text("Add Bill Reminder")
                            }
                        }
                    }
                }
            } else {
                items(upcomingBills, key = { it.id }) { bill ->
                    BillCard(
                        bill = bill,
                        isOverdue = false,
                        onMarkPaid = { viewModel.markBillAsPaid(bill) },
                        onDelete = { viewModel.deleteBillReminder(bill.id) }
                    )
                }
            }
        }

        // 4. Paid Bills History
        if (showPaidHistory) {
            item {
                Text(
                    "Paid Bills History",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            if (paidBills.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No paid bills recorded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(paidBills, key = { it.id }) { bill ->
                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("paid_bill_${bill.id}"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(bill.title, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "Paid on ${bill.paidDate?.let { FormatUtils.formatDate(it) } ?: "Recorded"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(
                                FormatUtils.formatInr(bill.amount),
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BillCard(
    bill: BillReminder,
    isOverdue: Boolean,
    onMarkPaid: () -> Unit,
    onDelete: () -> Unit
) {
    val now = System.currentTimeMillis()
    val dueCal = Calendar.getInstance().apply { timeInMillis = bill.dueDate }
    val nowCal = Calendar.getInstance().apply { timeInMillis = now }
    val diffDays = ((dueCal.timeInMillis - nowCal.timeInMillis) / 86400000L).toInt()

    val dueText = when {
        diffDays < 0 -> "Overdue by ${kotlin.math.abs(diffDays)} days (${FormatUtils.formatDate(bill.dueDate)})"
        diffDays == 0 -> "Due Today (${FormatUtils.formatDate(bill.dueDate)})"
        diffDays == 1 -> "Due Tomorrow"
        else -> "Due in $diffDays days (${FormatUtils.formatDate(bill.dueDate)})"
    }

    val statusColor = if (isOverdue) MaterialTheme.colorScheme.error else if (diffDays <= 2) Color(0xFFF57C00) else MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("bill_card_${bill.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isOverdue) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant)
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
                            .background(statusColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isOverdue) Icons.Default.Warning else Icons.Default.Receipt,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            bill.title,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            dueText,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = statusColor
                        )
                    }
                }

                Text(
                    FormatUtils.formatInr(bill.amount),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider()
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AccountBalance,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${bill.category} • ${bill.account}" + if (bill.isRecurring) " • Monthly" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.testTag("delete_bill_${bill.id}")
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                    Button(
                        onClick = onMarkPaid,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("mark_paid_bill_${bill.id}")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Mark Paid")
                    }
                }
            }
        }
    }
}

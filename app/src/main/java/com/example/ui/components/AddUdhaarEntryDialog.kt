package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.Account
import com.example.data.Person
import com.example.utils.FormatUtils
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddUdhaarEntryDialog(
    preselectedPerson: Person? = null,
    people: List<Person>,
    accounts: List<Account>,
    initialType: String = "GAVE", // "GAVE" or "TOOK"
    onDismiss: () -> Unit,
    onSave: (
        personId: Long,
        type: String,
        amount: Double,
        account: String,
        date: Long,
        dueDate: Long?,
        note: String
    ) -> Unit,
    onQuickAddPerson: (() -> Unit)? = null
) {
    var selectedPersonId by remember { mutableStateOf(preselectedPerson?.id ?: people.firstOrNull()?.id ?: 0L) }
    var entryType by remember { mutableStateOf(initialType) } // "GAVE" or "TOOK"
    var amountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    val defaultAccount = accounts.firstOrNull()?.name ?: "Cash in Hand"
    var selectedAccount by remember { mutableStateOf(defaultAccount) }
    var accountDropdownExpanded by remember { mutableStateOf(false) }
    var personDropdownExpanded by remember { mutableStateOf(false) }

    var dateText by remember { mutableStateOf(FormatUtils.formatDate(System.currentTimeMillis())) }
    var dueDateText by remember { mutableStateOf("") }

    var amountError by remember { mutableStateOf<String?>(null) }
    var dateError by remember { mutableStateOf<String?>(null) }
    var personError by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    fun submitUdhaarEntry() {
        val amount = amountText.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            amountError = "Enter a valid amount > 0"
            return
        }
        val targetPersonId = preselectedPerson?.id ?: selectedPersonId
        if (targetPersonId <= 0) {
            personError = true
            return
        }
        val dateParsed = FormatUtils.parseDate(dateText)
        if (dateParsed == null) {
            dateError = "Invalid date format. Use DD/MM/YYYY"
            return
        }

        val dueDateParsed = if (dueDateText.isNotBlank()) {
            FormatUtils.parseDate(dueDateText)
        } else null

        onSave(
            targetPersonId,
            entryType,
            amount,
            selectedAccount,
            dateParsed,
            dueDateParsed,
            note.trim()
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Spacer(Modifier.width(4.dp))
                    AutoSizeSingleLineText(
                        text = if (entryType == "GAVE") "I Gave Money" else "I Took Money",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { submitUdhaarEntry() },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Save Entry", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Save", fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(scrollState)
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Type selector: I Gave vs I Took
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilterChip(
                        selected = entryType == "GAVE",
                        onClick = { entryType = "GAVE" },
                        leadingIcon = {
                            Icon(Icons.Default.ArrowOutward, contentDescription = null, tint = if (entryType == "GAVE") Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        },
                        label = {
                            Text("I Gave (Lent)", fontWeight = FontWeight.Bold, maxLines = 1)
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFE8F5E9),
                            selectedLabelColor = Color(0xFF2E7D32)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("entry_type_chip_gave")
                    )

                    FilterChip(
                        selected = entryType == "TOOK",
                        onClick = { entryType = "TOOK" },
                        leadingIcon = {
                            Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = if (entryType == "TOOK") Color(0xFFC62828) else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        },
                        label = {
                            Text("I Took", fontWeight = FontWeight.Bold, maxLines = 1)
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFFFEBEE),
                            selectedLabelColor = Color(0xFFC62828)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("entry_type_chip_took")
                    )
                }

                // Person selector (if not preselected)
                if (preselectedPerson == null) {
                    if (people.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("No person added yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                                if (onQuickAddPerson != null) {
                                    TextButton(onClick = onQuickAddPerson) {
                                        Text("+ Add Person", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    } else {
                        val currentPerson = people.firstOrNull { it.id == selectedPersonId } ?: people.first()
                        ExposedDropdownMenuBox(
                            expanded = personDropdownExpanded,
                            onExpandedChange = { personDropdownExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = currentPerson.name + if (currentPerson.phone.isNotBlank()) " (${currentPerson.phone})" else "",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Person *") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = personDropdownExpanded) },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                isError = personError,
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                                    .testTag("entry_person_dropdown")
                            )
                            ExposedDropdownMenu(
                                expanded = personDropdownExpanded,
                                onDismissRequest = { personDropdownExpanded = false }
                            ) {
                                people.forEach { p ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(p.name, fontWeight = FontWeight.SemiBold)
                                                if (p.phone.isNotBlank()) {
                                                    Text(p.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        },
                                        onClick = {
                                            selectedPersonId = p.id
                                            personError = false
                                            personDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(preselectedPerson.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                if (preselectedPerson.phone.isNotBlank()) {
                                    Text(preselectedPerson.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                // Amount
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        amountError = null
                    },
                    label = { Text("Amount (₹) *") },
                    placeholder = { Text("e.g. 5000") },
                    leadingIcon = { Text("₹", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = amountError != null,
                    supportingText = {
                        if (amountError != null) {
                            Text(amountError!!, color = MaterialTheme.colorScheme.error)
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("entry_amount_input")
                )

                // Account (Cash in Hand / Bank Account / UPI)
                val accountList = if (accounts.isNotEmpty()) accounts.map { it.name } else listOf("Cash in Hand", "Bank Account", "UPI Wallet")
                ExposedDropdownMenuBox(
                    expanded = accountDropdownExpanded,
                    onExpandedChange = { accountDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedAccount,
                        onValueChange = {},
                        readOnly = true,
                        label = {
                            Text(if (entryType == "GAVE") "Paid From Account *" else "Received Into Account *")
                        },
                        leadingIcon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                            .testTag("entry_account_dropdown")
                    )
                    ExposedDropdownMenu(
                        expanded = accountDropdownExpanded,
                        onDismissRequest = { accountDropdownExpanded = false }
                    ) {
                        accountList.forEach { acc ->
                            DropdownMenuItem(
                                text = { Text(acc) },
                                onClick = {
                                    selectedAccount = acc
                                    accountDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Date
                OutlinedTextField(
                    value = dateText,
                    onValueChange = {
                        dateText = it
                        dateError = null
                    },
                    label = { Text("Entry Date (DD/MM/YYYY) *") },
                    leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
                    isError = dateError != null,
                    supportingText = {
                        if (dateError != null) {
                            Text(dateError!!, color = MaterialTheme.colorScheme.error)
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("entry_date_input")
                )

                // Due Date (Optional)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = dueDateText,
                        onValueChange = { dueDateText = it },
                        label = { Text("Due Date (Optional, DD/MM/YYYY)") },
                        placeholder = { Text("e.g. 15/11/2026") },
                        leadingIcon = { Icon(Icons.Default.Event, contentDescription = null) },
                        trailingIcon = {
                            if (dueDateText.isNotBlank()) {
                                IconButton(onClick = { dueDateText = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear due date")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("entry_due_date_input")
                    )

                    // Quick presets (+7d, +15d, +30d)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(7 to "+7 Days", 15 to "+15 Days", 30 to "+30 Days").forEach { (days, label) ->
                            SuggestionChip(
                                onClick = {
                                    val cal = Calendar.getInstance().apply {
                                        val base = FormatUtils.parseDate(dateText) ?: System.currentTimeMillis()
                                        timeInMillis = base
                                        add(Calendar.DAY_OF_YEAR, days)
                                    }
                                    dueDateText = FormatUtils.formatDate(cal.timeInMillis)
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }

                // Note
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note / Reason (Optional)") },
                    placeholder = { Text("e.g. For dinner bill, Emergency, Project fee") },
                    leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null) },
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("entry_note_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { submitUdhaarEntry() },
                modifier = Modifier.testTag("save_entry_button")
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Save Entry", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_entry_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Back")
            }
        }
    )
}

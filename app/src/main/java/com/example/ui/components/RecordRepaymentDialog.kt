package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.Account
import com.example.data.Person
import com.example.data.UdhaarEntry
import com.example.utils.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordRepaymentDialog(
    entry: UdhaarEntry,
    person: Person,
    accounts: List<Account>,
    onDismiss: () -> Unit,
    onSave: (amount: Double, account: String, date: Long, note: String) -> Unit
) {
    val isGave = entry.type == "GAVE"
    var amountText by remember { mutableStateOf(entry.remainingAmount.toString().removeSuffix(".0")) }
    var note by remember { mutableStateOf("") }

    val defaultAccount = entry.account.ifBlank { accounts.firstOrNull()?.name ?: "Cash in Hand" }
    var selectedAccount by remember { mutableStateOf(defaultAccount) }
    var accountDropdownExpanded by remember { mutableStateOf(false) }

    var dateText by remember { mutableStateOf(FormatUtils.formatDate(System.currentTimeMillis())) }
    var amountError by remember { mutableStateOf<String?>(null) }
    var dateError by remember { mutableStateOf<String?>(null) }

    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                if (isGave) "Repayment Received" else "Record Repayment",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Info banner
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isGave) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            if (isGave) "Money received back from ${person.name}"
                            else "Money paid back to ${person.name}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isGave) Color(0xFF2E7D32) else Color(0xFFC62828)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Pending Balance: ${FormatUtils.formatInr(entry.remainingAmount)} (Original: ${FormatUtils.formatInr(entry.amount)})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Amount
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        amountError = null
                    },
                    label = { Text("Repayment Amount (₹) *") },
                    leadingIcon = { Text("₹", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = amountError != null,
                    supportingText = {
                        if (amountError != null) {
                            Text(amountError!!, color = MaterialTheme.colorScheme.error)
                        } else {
                            Text("Partial repayments will update the remaining balance automatically.")
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("repayment_amount_input")
                )

                // Quick chips: Full amount vs 50%
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SuggestionChip(
                        onClick = {
                            amountText = entry.remainingAmount.toString().removeSuffix(".0")
                            amountError = null
                        },
                        label = { Text("Full (${FormatUtils.formatInr(entry.remainingAmount)})") }
                    )
                    if (entry.remainingAmount > 100) {
                        val half = (entry.remainingAmount / 2.0).toInt().toDouble()
                        SuggestionChip(
                            onClick = {
                                amountText = half.toString().removeSuffix(".0")
                                amountError = null
                            },
                            label = { Text("Half (${FormatUtils.formatInr(half)})") }
                        )
                    }
                }

                // Account
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
                            Text(if (isGave) "Deposit Into Account *" else "Paid From Account *")
                        },
                        leadingIcon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                            .testTag("repayment_account_dropdown")
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
                    label = { Text("Repayment Date (DD/MM/YYYY) *") },
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
                        .testTag("repayment_date_input")
                )

                // Note
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (Optional)") },
                    placeholder = { Text("e.g. Paid via UPI / GPay, Cash handed over") },
                    leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null) },
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("repayment_note_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull()
                    if (amount == null || amount <= 0) {
                        amountError = "Enter an amount greater than 0"
                        return@Button
                    }
                    if (amount > entry.remainingAmount) {
                        amountError = "Amount cannot exceed remaining ${FormatUtils.formatInr(entry.remainingAmount)}"
                        return@Button
                    }
                    val dateParsed = FormatUtils.parseDate(dateText)
                    if (dateParsed == null) {
                        dateError = "Invalid date format. Use DD/MM/YYYY"
                        return@Button
                    }

                    onSave(amount, selectedAccount, dateParsed, note.trim())
                },
                modifier = Modifier.testTag("save_repayment_button")
            ) {
                Text("Confirm Repayment")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_repayment_button")
            ) {
                Text("Cancel")
            }
        }
    )
}

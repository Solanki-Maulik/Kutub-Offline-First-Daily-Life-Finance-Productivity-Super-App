package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.utils.CategoryUtils
import com.example.utils.FormatUtils
import com.example.viewmodel.FinanceViewModel

import androidx.compose.foundation.verticalScroll

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddBottomSheet(
    viewModel: FinanceViewModel,
    initialIsIncome: Boolean = false,
    onDismiss: () -> Unit,
    onSuccess: (String) -> Unit
) {
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()

    var amount by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(if (initialIsIncome) "Salary" else "Chai/Snacks") }
    var selectedAccount by remember { mutableStateOf(accounts.firstOrNull()?.name ?: "Cash in Hand") }
    var isIncome by remember { mutableStateOf(initialIsIncome) }
    var note by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(accounts) {
        if (accounts.isNotEmpty() && accounts.none { it.name == selectedAccount }) {
            selectedAccount = accounts.first().name
        }
    }

    val performQuickSave = {
        val parsed = amount.toDoubleOrNull()
        if (parsed == null || parsed <= 0) {
            errorMessage = "Please enter a valid amount greater than 0"
        } else {
            val paymentMethod = if (selectedAccount.contains("Cash", ignoreCase = true)) "Cash" else "UPI"
            viewModel.addTransaction(
                amount = parsed,
                isIncome = isIncome,
                category = selectedCategory,
                account = selectedAccount,
                note = note,
                timestamp = System.currentTimeMillis(),
                paymentMethod = paymentMethod
            )
            onSuccess("Added ${if (isIncome) "+" else "-"}${FormatUtils.formatInr(parsed)} ($selectedCategory)")
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("quick_add_back_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    AutoSizeSingleLineText(
                        text = if (isIncome) "Add Income" else "Add Expense",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Top-Right Checkmark ("Right Sign") Save Button
                Button(
                    onClick = performQuickSave,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .height(40.dp)
                        .testTag("quick_add_top_check_btn")
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Save", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Save", fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !isIncome,
                    onClick = {
                        isIncome = false
                        selectedCategory = "Chai/Snacks"
                    },
                    label = { Text("Expense", fontWeight = FontWeight.SemiBold) },
                    leadingIcon = if (!isIncome) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .testTag("quick_expense_chip")
                )
                FilterChip(
                    selected = isIncome,
                    onClick = {
                        isIncome = true
                        selectedCategory = "Salary"
                    },
                    label = { Text("Income", fontWeight = FontWeight.SemiBold) },
                    leadingIcon = if (isIncome) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .testTag("quick_income_chip")
                )
            }

            // Amount Field
            OutlinedTextField(
                value = amount,
                onValueChange = {
                    amount = it
                    errorMessage = null
                },
                label = { Text("Amount (₹)") },
                prefix = { Text("₹ ", fontWeight = FontWeight.Bold) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                isError = errorMessage != null,
                supportingText = { if (errorMessage != null) Text(errorMessage!!, color = MaterialTheme.colorScheme.error) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("quick_add_amount_input"),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            )

            // Fast Amount Presets
            val presets = if (!isIncome) listOf(20, 50, 100, 200, 500) else listOf(500, 1000, 2000, 5000, 10000)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.forEach { preset ->
                    SuggestionChip(
                        onClick = {
                            amount = preset.toString()
                            errorMessage = null
                        },
                        label = { Text("+₹$preset", fontWeight = FontWeight.Medium) },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // Categories
            Text("Category", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            val availableCategories = categories.filter { it.isIncome == isIncome }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                availableCategories.take(10).forEach { cat ->
                    val isSelected = selectedCategory == cat.name
                    val catColor = CategoryUtils.parseColor(cat.colorHex)
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategory = cat.name },
                        leadingIcon = {
                            Icon(
                                CategoryUtils.getIcon(cat.iconName),
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else catColor,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        label = { Text(cat.name) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("quick_cat_${cat.name}")
                    )
                }
            }

            // Account selection row
            if (accounts.size > 1) {
                Text("Account", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    accounts.forEach { acc ->
                        FilterChip(
                            selected = selectedAccount == acc.name,
                            onClick = { selectedAccount = acc.name },
                            label = { Text(acc.name) },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            // Quick Note (Optional)
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (Optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )

            // Bottom Back + Save Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(0.38f)
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Back", fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
                Button(
                    onClick = performQuickSave,
                    modifier = Modifier
                        .weight(0.62f)
                        .height(50.dp)
                        .testTag("quick_add_save_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (isIncome) "Save Income" else "Save Expense",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

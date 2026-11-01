package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.example.data.BudgetStatus
import com.example.utils.FormatUtils
import com.example.viewmodel.FinanceViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BudgetsTabContent(
    viewModel: FinanceViewModel,
    onNavigateToAddExpense: () -> Unit = {}
) {
    val overallStatus by viewModel.overallBudgetStatus.collectAsStateWithLifecycle()
    val budgetStatuses by viewModel.budgetStatuses.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()

    var showEditMonthlyDialog by remember { mutableStateOf(false) }
    var monthlyLimitInput by remember { mutableStateOf("") }

    var showAddCategoryBudgetDialog by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("") }
    var categoryLimitInput by remember { mutableStateOf("") }

    var editingBudget by remember { mutableStateOf<BudgetStatus?>(null) }
    var editCategoryLimitInput by remember { mutableStateOf("") }

    val categoryBudgets = remember(budgetStatuses) {
        budgetStatuses.filter { !it.category.equals("Monthly", ignoreCase = true) }
    }

    val exceededCount = remember(budgetStatuses) {
        budgetStatuses.count { it.isExceeded100 }
    }
    val warningCount = remember(budgetStatuses) {
        budgetStatuses.count { it.isWarning80 }
    }

    // Edit Monthly Budget Dialog
    if (showEditMonthlyDialog) {
        AlertDialog(
            onDismissRequest = { showEditMonthlyDialog = false },
            title = { Text("Set Overall Monthly Budget") },
            text = {
                Column {
                    Text(
                        "Set your total spending limit across all categories for this month.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = monthlyLimitInput,
                        onValueChange = { monthlyLimitInput = it },
                        label = { Text("Monthly Limit (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("monthly_budget_input"),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = monthlyLimitInput.toDoubleOrNull()
                        if (amount != null && amount >= 0) {
                            viewModel.saveBudget("Monthly", amount)
                        }
                        showEditMonthlyDialog = false
                    },
                    modifier = Modifier.testTag("save_monthly_budget_button")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditMonthlyDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add Category Budget Dialog
    if (showAddCategoryBudgetDialog) {
        val expenseCats = categories.filter { !it.isIncome }.map { it.name }
        val availableCats = if (expenseCats.isNotEmpty()) expenseCats else listOf("Food", "Chai/Snacks", "Travel/Petrol", "Rent", "Bills", "Shopping", "Medical", "Education", "Other")

        AlertDialog(
            onDismissRequest = { showAddCategoryBudgetDialog = false },
            title = { Text("Add Category Budget") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Select a category and assign its monthly limit.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Category selector chips
                    Text("Category", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableCats.take(8).forEach { cat ->
                            FilterChip(
                                selected = selectedCategory.equals(cat, ignoreCase = true),
                                onClick = { selectedCategory = cat },
                                label = { Text(cat) },
                                modifier = Modifier.testTag("budget_chip_$cat")
                            )
                        }
                    }

                    OutlinedTextField(
                        value = selectedCategory,
                        onValueChange = { selectedCategory = it },
                        label = { Text("Or Type Category Name") },
                        modifier = Modifier.fillMaxWidth().testTag("budget_category_input"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = categoryLimitInput,
                        onValueChange = { categoryLimitInput = it },
                        label = { Text("Monthly Limit (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("budget_amount_input"),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = categoryLimitInput.toDoubleOrNull()
                        if (selectedCategory.isNotBlank() && amount != null && amount > 0) {
                            viewModel.saveBudget(selectedCategory.trim(), amount)
                            showAddCategoryBudgetDialog = false
                            selectedCategory = ""
                            categoryLimitInput = ""
                        }
                    },
                    modifier = Modifier.testTag("confirm_add_budget_button")
                ) {
                    Text("Set Budget")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCategoryBudgetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Edit Category Budget Dialog
    if (editingBudget != null) {
        val item = editingBudget!!
        AlertDialog(
            onDismissRequest = { editingBudget = null },
            title = { Text("Edit Budget: ${item.category}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Spent this month: ${FormatUtils.formatInr(item.spentAmount)}")
                    OutlinedTextField(
                        value = editCategoryLimitInput,
                        onValueChange = { editCategoryLimitInput = it },
                        label = { Text("New Monthly Limit (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("edit_budget_amount_input"),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newAmount = editCategoryLimitInput.toDoubleOrNull()
                        if (newAmount != null && newAmount >= 0) {
                            viewModel.saveBudget(item.category, newAmount)
                        }
                        editingBudget = null
                    },
                    modifier = Modifier.testTag("save_edit_budget_button")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            viewModel.deleteBudget(item.category)
                            editingBudget = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.testTag("delete_budget_button")
                    ) {
                        Text("Delete")
                    }
                    TextButton(onClick = { editingBudget = null }) {
                        Text("Cancel")
                    }
                }
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
        // 1. Overall Monthly Budget Hero Card with 80% & 100% Alert Indicators
        item {
            val progress = overallStatus.progress
            val isExceeded = overallStatus.isExceeded100
            val isWarning = overallStatus.isWarning80

            val cardContainerColor = when {
                isExceeded -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                isWarning -> Color(0xFFFFF3E0)
                else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            }
            val cardBorderColor = when {
                isExceeded -> MaterialTheme.colorScheme.error
                isWarning -> Color(0xFFF57C00)
                else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            }
            val progressColor = when {
                isExceeded -> MaterialTheme.colorScheme.error
                isWarning -> Color(0xFFF57C00)
                else -> MaterialTheme.colorScheme.primary
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("monthly_budget_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = cardContainerColor),
                border = BorderStroke(1.5.dp, cardBorderColor)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Overall Monthly Budget",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Current Month Expenses",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = {
                                monthlyLimitInput = overallStatus.limitAmount.toInt().toString()
                                showEditMonthlyDialog = true
                            },
                            modifier = Modifier.testTag("edit_monthly_budget_icon_button")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Monthly Budget")
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
                                "Spent",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                FormatUtils.formatInr(overallStatus.spentAmount),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = progressColor
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "Limit",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                FormatUtils.formatInr(overallStatus.limitAmount),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(CircleShape),
                        color = progressColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Spacer(Modifier.height(12.dp))

                    // Alert Banner
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = when {
                            isExceeded -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                            isWarning -> Color(0xFFFFE0B2)
                            else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when {
                                    isExceeded -> Icons.Default.Error
                                    isWarning -> Icons.Default.Warning
                                    else -> Icons.Default.CheckCircle
                                },
                                contentDescription = null,
                                tint = progressColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = when {
                                    isExceeded -> "🚨 Exceeded 100%! Over budget by ${FormatUtils.formatInr(overallStatus.spentAmount - overallStatus.limitAmount)}"
                                    isWarning -> "⚠️ Warning: ${(progress * 100).toInt()}% used (Only ${FormatUtils.formatInr(overallStatus.remainingAmount)} left)"
                                    else -> "✅ On track: ${(progress * 100).toInt()}% used (${FormatUtils.formatInr(overallStatus.remainingAmount)} remaining)"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = progressColor
                            )
                        }
                    }
                }
            }
        }

        // 2. Summary stats pills
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text("Total: ${categoryBudgets.size + 1}") },
                    leadingIcon = { Icon(Icons.Default.PieChart, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.weight(1f)
                )
                AssistChip(
                    onClick = {},
                    label = { Text("Warning (80%): $warningCount") },
                    leadingIcon = { Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFF57C00), modifier = Modifier.size(16.dp)) },
                    colors = AssistChipDefaults.assistChipColors(labelColor = if (warningCount > 0) Color(0xFFE65100) else MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.weight(1.3f)
                )
                AssistChip(
                    onClick = {},
                    label = { Text("Exceeded: $exceededCount") },
                    leadingIcon = { Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp)) },
                    colors = AssistChipDefaults.assistChipColors(labelColor = if (exceededCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.weight(1.2f)
                )
            }
        }

        // 3. Category Budgets Header with Add Button
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Category Budgets",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Track limits for Food, Travel, Rent, etc.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalButton(
                    onClick = { showAddCategoryBudgetDialog = true },
                    modifier = Modifier.testTag("add_category_budget_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add")
                }
            }
        }

        // 4. Category Budget Cards
        if (categoryBudgets.isEmpty()) {
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
                            Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "No Category Budgets Yet",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Set limits for Food, Shopping, or Travel to avoid overspending.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { showAddCategoryBudgetDialog = true },
                            modifier = Modifier.testTag("create_first_budget_button")
                        ) {
                            Text("Create Category Budget")
                        }
                    }
                }
            }
        } else {
            items(categoryBudgets, key = { it.category }) { item ->
                val progress = item.progress
                val isExceeded = item.isExceeded100
                val isWarning = item.isWarning80

                val borderColor = when {
                    isExceeded -> MaterialTheme.colorScheme.error
                    isWarning -> Color(0xFFF57C00)
                    else -> MaterialTheme.colorScheme.outlineVariant
                }
                val statusColor = when {
                    isExceeded -> MaterialTheme.colorScheme.error
                    isWarning -> Color(0xFFF57C00)
                    else -> MaterialTheme.colorScheme.primary
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            editingBudget = item
                            editCategoryLimitInput = item.limitAmount.toInt().toString()
                        }
                        .testTag("category_budget_card_${item.category}"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            isExceeded -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                            isWarning -> Color(0xFFFFF3E0).copy(alpha = 0.6f)
                            else -> MaterialTheme.colorScheme.surface
                        }
                    ),
                    border = BorderStroke(1.dp, borderColor)
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
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(statusColor.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Category,
                                        contentDescription = null,
                                        tint = statusColor,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        item.category,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        "${FormatUtils.formatInr(item.spentAmount)} of ${FormatUtils.formatInr(item.limitAmount)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Alert Chip
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = statusColor.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = when {
                                        isExceeded -> "🚨 Exceeded 100%"
                                        isWarning -> "⚠️ 80%+ Used"
                                        else -> "${(progress * 100).toInt()}% Used"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = statusColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape),
                            color = statusColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )

                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isExceeded) {
                                    "Over by ${FormatUtils.formatInr(item.spentAmount - item.limitAmount)}"
                                } else {
                                    "${FormatUtils.formatInr(item.remainingAmount)} left"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isExceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Tap to edit limit",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}

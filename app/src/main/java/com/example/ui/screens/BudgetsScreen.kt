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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.data.Budget
import com.example.viewmodel.FinanceViewModel
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(viewModel: FinanceViewModel, navController: NavController) {
    val budgets by viewModel.budgets.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val format = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    format.maximumFractionDigits = 0

    var showEditDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf("") }
    var editingAmount by remember { mutableStateOf("") }
    
    var showAddDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }
    var newCategoryAmount by remember { mutableStateOf("") }

    val expenses = transactions.filter { !it.isIncome && it.category != "Transfer" }
    
    val defaultMonthlyBudget = 20000.0
    val monthlyBudgetRecord = budgets.find { it.category == "Monthly" }
    val monthlyLimit = monthlyBudgetRecord?.limitAmount ?: defaultMonthlyBudget
    val totalSpent = expenses.sumOf { it.amount }
    val monthlyProgress = if (monthlyLimit > 0) (totalSpent / monthlyLimit).toFloat() else 0f
    
    val customBudgets = budgets.filter { it.category != "Monthly" }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Category Budget") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        label = { Text("Category Name (e.g. Shopping)") },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = newCategoryAmount,
                        onValueChange = { newCategoryAmount = it },
                        label = { Text("Monthly Limit") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val amount = newCategoryAmount.toDoubleOrNull()
                    if (newCategoryName.isNotBlank() && amount != null && amount >= 0) {
                        viewModel.saveBudget(newCategoryName.trim(), amount)
                    }
                    showAddDialog = false
                    newCategoryName = ""
                    newCategoryAmount = ""
                }) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Budget: $editingCategory") },
            text = {
                OutlinedTextField(
                    value = editingAmount,
                    onValueChange = { editingAmount = it },
                    label = { Text("Limit Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val newLimit = editingAmount.toDoubleOrNull()
                    if (newLimit != null && newLimit >= 0) {
                        viewModel.saveBudget(editingCategory, newLimit)
                    }
                    showEditDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Budgets", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Budget")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        editingCategory = "Monthly"
                        editingAmount = monthlyLimit.toLong().toString()
                        showEditDialog = true
                    },
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Monthly Budget", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.height(8.dp))
                        Text(format.format(monthlyLimit), style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Spent", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha=0.7f))
                                Text(format.format(totalSpent), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Remaining", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha=0.7f))
                                Text(format.format(maxOf(0.0, monthlyLimit - totalSpent)), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        LinearProgressIndicator(progress = { monthlyProgress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha=0.2f))
                    }
                }
            }
            
            item {
                Spacer(Modifier.height(8.dp))
                Text("Category Budgets", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            
            if (customBudgets.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No custom category budgets set yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(customBudgets) { budget ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            editingCategory = budget.category
                            editingAmount = budget.limitAmount.toLong().toString()
                            showEditDialog = true
                        },
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Category, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                    }
                                    Spacer(Modifier.width(16.dp))
                                    Text(budget.category, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                                }
                                val catSpent = expenses.filter { it.category.equals(budget.category, ignoreCase = true) }.sumOf { it.amount }
                                Text("${format.format(catSpent)} / ${format.format(budget.limitAmount)}", style = MaterialTheme.typography.bodyMedium)
                            }
                            Spacer(Modifier.height(16.dp))
                            val catSpent = expenses.filter { it.category.equals(budget.category, ignoreCase = true) }.sumOf { it.amount }
                            val catProgress = if (budget.limitAmount > 0) (catSpent / budget.limitAmount).toFloat() else 0f
                            LinearProgressIndicator(progress = { catProgress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Transaction
import com.example.viewmodel.FinanceViewModel
import java.util.*

enum class TransactionType { EXPENSE, INCOME, TRANSFER }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    viewModel: FinanceViewModel,
    transactionId: Int,
    onBack: () -> Unit
) {
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    
    var transactionType by remember { mutableStateOf(TransactionType.EXPENSE) }
    var amount by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Food") }
    var selectedAccount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var toAccount by remember { mutableStateOf("") }
    
    var showAccountMenu by remember { mutableStateOf(false) }
    var showToAccountMenu by remember { mutableStateOf(false) }

    LaunchedEffect(accounts) {
        if (selectedAccount.isEmpty() && accounts.isNotEmpty()) {
            selectedAccount = accounts.first().name
        }
        if (toAccount.isEmpty() && accounts.size > 1) {
            toAccount = accounts[1].name
        } else if (toAccount.isEmpty() && accounts.isNotEmpty()) {
            toAccount = accounts.first().name
        }
    }

    LaunchedEffect(transactionId) {
        if (transactionId != -1) {
            viewModel.getTransactionById(transactionId)?.let { tx ->
                amount = tx.amount.toInt().toString()
                selectedCategory = tx.category
                transactionType = if (tx.isIncome) TransactionType.INCOME else if (tx.category == "Transfer") TransactionType.TRANSFER else TransactionType.EXPENSE
                selectedAccount = tx.account
                note = tx.note
            }
        }
    }

    val categories = listOf(
        "Food" to Icons.Default.Restaurant,
        "Transport" to Icons.Default.DirectionsCar,
        "Health" to Icons.Default.LocalHospital,
        "Entertainment" to Icons.Default.Movie,
        "Shopping" to Icons.Default.ShoppingBag,
        "Bills" to Icons.Default.Receipt,
        "Education" to Icons.Default.School,
        "Groceries" to Icons.Default.LocalGroceryStore,
        "Travel" to Icons.Default.Flight,
        "Family" to Icons.Default.FamilyRestroom,
        "Gym" to Icons.Default.FitnessCenter,
        "Other" to Icons.Default.MoreHoriz
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text(if (transactionId != -1) "Edit Transaction" else "Add Transaction", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.Close, contentDescription = "Close") }
                },
                actions = {
                    if (transactionId != -1) {
                        IconButton(onClick = { viewModel.deleteTransaction(transactionId); onBack() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (transactionId == -1) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(24.dp)).padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf(TransactionType.EXPENSE to "Expense", TransactionType.INCOME to "Income", TransactionType.TRANSFER to "Transfer").forEach { (type, label) ->
                        val isSelected = transactionType == type
                        Box(
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(if (isSelected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent).clickable { transactionType = type }.padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(32.dp))
            
            Row(verticalAlignment = Alignment.Bottom) {
                Text("₹", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextField(
                    value = amount,
                    onValueChange = { amount = it },
                    textStyle = MaterialTheme.typography.displayLarge.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    placeholder = { Text("0", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                    modifier = Modifier.width(IntrinsicSize.Min)
                )
            }
            
            Spacer(Modifier.height(32.dp))

            // Account selection - simpler dropdown design
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("ACCOUNT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable { showAccountMenu = true }.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (selectedAccount.isEmpty()) "Select Account" else selectedAccount, color = MaterialTheme.colorScheme.onSurface)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(expanded = showAccountMenu, onDismissRequest = { showAccountMenu = false }) {
                        accounts.forEach { acc ->
                            DropdownMenuItem(text = { Text(acc.name) }, onClick = { selectedAccount = acc.name; showAccountMenu = false })
                        }
                    }
                }
            }

            if (transactionType == TransactionType.TRANSFER) {
                Spacer(Modifier.height(16.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("TO ACCOUNT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable { showToAccountMenu = true }.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(if (toAccount.isEmpty()) "Select Account" else toAccount, color = MaterialTheme.colorScheme.onSurface)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        DropdownMenu(expanded = showToAccountMenu, onDismissRequest = { showToAccountMenu = false }) {
                            accounts.forEach { acc ->
                                DropdownMenuItem(text = { Text(acc.name) }, onClick = { toAccount = acc.name; showToAccountMenu = false })
                            }
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(24.dp))
                Text("CATEGORY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.Start))
                Spacer(Modifier.height(8.dp))
                
                val displayCategories = if (transactionType == TransactionType.INCOME) {
                    listOf("Salary" to Icons.Default.AttachMoney, "Transfer" to Icons.Default.SwapHoriz, "Gift" to Icons.Default.CardGiftcard, "Other" to Icons.Default.MoreHoriz)
                } else {
                    categories
                }
                
                // Draw categories in rows of 4
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    displayCategories.chunked(4).forEach { rowItems ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            rowItems.forEach { (name, icon) ->
                                val isSelected = selectedCategory == name
                                Column(
                                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant).clickable { selectedCategory = name }.padding(vertical = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(icon, contentDescription = null, tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(4.dp))
                                    Text(name, style = MaterialTheme.typography.labelSmall, color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            // fill empty spaces in row
                            repeat(4 - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            )

            Spacer(Modifier.height(32.dp))
            
            Button(
                onClick = {
                    val parsedAmount = amount.toDoubleOrNull() ?: 0.0
                    if (parsedAmount > 0) {
                        if (transactionType == TransactionType.TRANSFER) {
                            viewModel.addTransfer(parsedAmount, selectedAccount, toAccount, System.currentTimeMillis())
                        } else {
                            val tx = Transaction(
                                id = if (transactionId != -1) transactionId else 0,
                                amount = parsedAmount,
                                isIncome = transactionType == TransactionType.INCOME,
                                category = if (transactionType == TransactionType.TRANSFER) "Transfer" else selectedCategory,
                                account = selectedAccount,
                                note = note.trim(),
                                timestamp = System.currentTimeMillis()
                            )
                            if (transactionId != -1) {
                                viewModel.updateTransaction(tx.id, tx.amount, tx.isIncome, tx.category, tx.account, tx.note, tx.timestamp)
                            } else {
                                viewModel.addTransaction(tx.amount, tx.isIncome, tx.category, tx.account, tx.note, tx.timestamp)
                            }
                        }
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(if (transactionId != -1) "Save Changes" else "Save Transaction", fontSize = MaterialTheme.typography.titleMedium.fontSize)
            }
            
            Spacer(Modifier.height(32.dp))
        }
    }
}

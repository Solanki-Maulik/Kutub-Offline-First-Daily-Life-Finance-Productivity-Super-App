package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
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
import com.example.data.Debt
import com.example.viewmodel.FinanceViewModel
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtsScreen(viewModel: FinanceViewModel, navController: NavController) {
    val debts by viewModel.debts.collectAsStateWithLifecycle()
    val format = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    format.maximumFractionDigits = 0

    var showAddDialog by remember { mutableStateOf(false) }
    var newPersonName by remember { mutableStateOf("") }
    var newAmount by remember { mutableStateOf("") }
    var newIsOwedToMe by remember { mutableStateOf(true) }
    var showActionDialog by remember { mutableStateOf<Debt?>(null) }
    
    var selectedTab by remember { mutableStateOf(0) }
    val iOwe = debts.filter { !it.isOwedToMe && !it.isSettled }
    val owedToMe = debts.filter { it.isOwedToMe && !it.isSettled }
    
    val totalIOwe = iOwe.sumOf { it.amount }
    val totalOwedToMe = owedToMe.sumOf { it.amount }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Debt / Loan") },
            text = {
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        FilterChip(selected = newIsOwedToMe, onClick = { newIsOwedToMe = true }, label = { Text("Owed to me") })
                        FilterChip(selected = !newIsOwedToMe, onClick = { newIsOwedToMe = false }, label = { Text("I owe them") })
                    }
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = newPersonName,
                        onValueChange = { newPersonName = it },
                        label = { Text("Person Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newAmount,
                        onValueChange = { newAmount = it },
                        label = { Text("Amount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val amount = newAmount.toDoubleOrNull()
                    if (newPersonName.isNotBlank() && amount != null && amount > 0) {
                        viewModel.addDebt(newPersonName.trim(), amount, newIsOwedToMe)
                    }
                    showAddDialog = false
                    newPersonName = ""
                    newAmount = ""
                }) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showActionDialog != null) {
        val debt = showActionDialog!!
        AlertDialog(
            onDismissRequest = { showActionDialog = null },
            title = { Text("Settle Debt") },
            text = { Text("Mark this debt of ${format.format(debt.amount)} with ${debt.personName} as settled?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.settleDebt(debt)
                    showActionDialog = null
                }) { Text("Settle") }
            },
            dismissButton = {
                TextButton(onClick = { showActionDialog = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text("Debts & Loans", fontWeight = FontWeight.Bold) },
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
                Icon(Icons.Default.Add, contentDescription = "Add Debt")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Owed to Me", fontWeight = FontWeight.Bold) })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("I Owe", fontWeight = FontWeight.Bold) })
            }
            
            Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(16.dp), contentAlignment = Alignment.Center) {
                if (selectedTab == 0) {
                    Text("Total Owed to You: ${format.format(totalOwedToMe)}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                } else {
                    Text("Total You Owe: ${format.format(totalIOwe)}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            }
            
            val displayList = if (selectedTab == 0) owedToMe else iOwe
            
            if (displayList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No active debts here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(displayList) { debt ->
                        val color = if (debt.isOwedToMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.clip(RoundedCornerShape(16.dp)),
                            headlineContent = { Text(debt.personName, fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text(if (debt.isOwedToMe) "Owes you" else "You owe") },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(format.format(debt.amount), color = color, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.width(16.dp))
                                    IconButton(onClick = { showActionDialog = debt }) {
                                        Icon(Icons.Default.Check, contentDescription = "Settle", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            },
                            leadingContent = {
                                Box(
                                    modifier = Modifier.size(48.dp).clip(CircleShape).background(if (debt.isOwedToMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(if (debt.isOwedToMe) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward, contentDescription = null, tint = color)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

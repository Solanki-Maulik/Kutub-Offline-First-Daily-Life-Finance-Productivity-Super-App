package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.viewmodel.FinanceViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import android.content.Intent
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: FinanceViewModel, navController: NavController) {
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val format = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    format.maximumFractionDigits = 0
    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    var filterMode by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    
    val filteredTransactions = transactions.filter {
        val matchesMode = when (filterMode) {
            "Income" -> it.isIncome
            "Expenses" -> !it.isIncome
            else -> true
        }
        val matchesSearch = if (searchQuery.isBlank()) {
            true
        } else {
            it.category.contains(searchQuery, ignoreCase = true) ||
            it.note.contains(searchQuery, ignoreCase = true) ||
            it.account.contains(searchQuery, ignoreCase = true) ||
            it.amount.toString().contains(searchQuery)
        }
        matchesMode && matchesSearch
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text("History", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { exportTransactions(context, transactions) }) {
                        Icon(Icons.Default.Share, contentDescription = "Export")
                    }
                    IconButton(onClick = { /*TODO*/ }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search transactions...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )
            
            Spacer(Modifier.height(16.dp))
            
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = filterMode == "All", onClick = { filterMode = "All" }, label = { Text("All") }, shape = RoundedCornerShape(16.dp))
                FilterChip(selected = filterMode == "Income", onClick = { filterMode = "Income" }, label = { Text("Income") }, shape = RoundedCornerShape(16.dp))
                FilterChip(selected = filterMode == "Expenses", onClick = { filterMode = "Expenses" }, label = { Text("Expenses") }, shape = RoundedCornerShape(16.dp))
            }
            
            Spacer(Modifier.height(16.dp))
            
            if (filteredTransactions.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No transactions found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredTransactions, key = { it.id }) { transaction ->
                        val sign = if (transaction.isIncome) "+" else "-"
                        val color = if (transaction.isIncome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { navController.navigate("add?id=${transaction.id}") },
                            headlineContent = { Text(transaction.category, fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text("${dateFormat.format(Date(transaction.timestamp))} • ${transaction.account}") },
                            trailingContent = { Text("$sign${format.format(transaction.amount)}", color = color, fontWeight = FontWeight.Bold) },
                            leadingContent = {
                                Box(
                                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(
                                        if (transaction.isIncome) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                                    ), 
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        if (transaction.isIncome) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward, 
                                        contentDescription = null, 
                                        tint = if (transaction.isIncome) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

fun exportTransactions(context: Context, transactions: List<com.example.data.Transaction>) {
    try {
        val file = File(context.cacheDir, "transactions_export.csv")
        file.printWriter().use { out ->
            out.println("Date,Type,Category,Account,Amount,Note")
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            transactions.forEach { t ->
                val type = if (t.isIncome) "Income" else "Expense"
                val date = dateFormat.format(Date(t.timestamp))
                out.println("$date,$type,${t.category},${t.account},${t.amount},${t.note}")
            }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "Finance Transactions Export")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export Transactions"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

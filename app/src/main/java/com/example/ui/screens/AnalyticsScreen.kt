package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.viewmodel.FinanceViewModel
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(viewModel: FinanceViewModel, navController: NavController) {
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val privacyMode by viewModel.privacyMode.collectAsStateWithLifecycle()
    
    val format = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    format.maximumFractionDigits = 0
    fun formatMoney(amount: Double): String = if (privacyMode) "₹****" else format.format(amount)

    val expenses = transactions.filter { !it.isIncome && it.category != "Transfer" }
    val totalExpense = expenses.sumOf { it.amount }
    
    val categoryTotals = expenses.groupBy { it.category }
        .mapValues { it.value.sumOf { t -> t.amount } }
        .toList()
        .sortedByDescending { it.second }

    val colors = listOf(
        Color(0xFFE57373), Color(0xFF81C784), Color(0xFF64B5F6), Color(0xFFFFD54F),
        Color(0xFFBA68C8), Color(0xFF4DB6AC), Color(0xFFFF8A65), Color(0xFFA1887F)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Analytics & Reports", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                if (totalExpense > 0) {
                    Box(modifier = Modifier.fillMaxWidth().height(250.dp).padding(16.dp), contentAlignment = Alignment.Center) {
                        Canvas(modifier = Modifier.size(200.dp)) {
                            var startAngle = -90f
                            categoryTotals.forEachIndexed { index, pair ->
                                val sweepAngle = (pair.second.toFloat() / totalExpense.toFloat()) * 360f
                                val color = colors[index % colors.size]
                                drawArc(
                                    color = color,
                                    startAngle = startAngle,
                                    sweepAngle = sweepAngle,
                                    useCenter = true,
                                    size = Size(size.width, size.height)
                                )
                                startAngle += sweepAngle
                            }
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        Text("No expenses to analyze yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            
            if (totalExpense > 0) {
                item {
                    Text("Category Breakdown", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                }
                
                items(categoryTotals.size) { index ->
                    val (category, amount) = categoryTotals[index]
                    val color = colors[index % colors.size]
                    val percentage = (amount / totalExpense) * 100
                    
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                        leadingContent = {
                            Surface(color = color, shape = MaterialTheme.shapes.small, modifier = Modifier.size(24.dp)) {}
                        },
                        headlineContent = { Text(category, fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text(String.format("%.1f%% of total", percentage)) },
                        trailingContent = { Text(formatMoney(amount), fontWeight = FontWeight.Bold) }
                    )
                }
            }
        }
    }
}

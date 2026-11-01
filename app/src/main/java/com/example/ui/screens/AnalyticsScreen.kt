package com.example.ui.screens

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.data.*
import com.example.ui.components.*
import com.example.utils.CategoryUtils
import com.example.utils.FormatUtils
import com.example.utils.ReportExportUtils
import com.example.utils.ReportUtils
import com.example.viewmodel.FinanceViewModel
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(viewModel: FinanceViewModel, navController: NavController) {
    val context = LocalContext.current
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val people by viewModel.people.collectAsStateWithLifecycle()
    val udhaarEntries by viewModel.udhaarEntries.collectAsStateWithLifecycle()
    val udhaarRepayments by viewModel.udhaarRepayments.collectAsStateWithLifecycle()
    val privacyMode by viewModel.privacyMode.collectAsStateWithLifecycle()

    fun formatMoney(amount: Double): String =
        if (privacyMode) "₹****" else FormatUtils.formatInr(amount)

    // Filter States
    var selectedRange by remember { mutableStateOf(ReportDateRange.THIS_MONTH) }
    var customStartDate by remember {
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -30) }
        mutableStateOf(cal.timeInMillis)
    }
    var customEndDate by remember { mutableStateOf(System.currentTimeMillis()) }

    var selectedAccountFilter by remember { mutableStateOf("All") }
    var selectedCategoryFilter by remember { mutableStateOf("All") }

    var showAccountMenu by remember { mutableStateOf(false) }
    var showCategoryMenu by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }

    // Date range bounds
    val (startTime, endTime) = remember(selectedRange, customStartDate, customEndDate) {
        ReportUtils.getDateRangeBounds(selectedRange, customStartDate, customEndDate)
    }

    // Filtered Transactions
    val filteredTransactions = remember(transactions, startTime, endTime, selectedAccountFilter, selectedCategoryFilter) {
        ReportUtils.filterTransactions(
            transactions,
            startTime,
            endTime,
            selectedAccountFilter,
            selectedCategoryFilter
        )
    }

    // Calculations
    val summary = remember(filteredTransactions, startTime, endTime) {
        ReportUtils.calculateSummary(filteredTransactions, startTime, endTime)
    }

    val monthComparison = remember(transactions) {
        ReportUtils.calculateMonthComparison(transactions)
    }

    val cashBankFlow = remember(filteredTransactions, startTime, endTime) {
        ReportUtils.calculateCashBankFlow(filteredTransactions, startTime, endTime)
    }

    val topCategories = remember(filteredTransactions, startTime, endTime) {
        ReportUtils.calculateTopCategories(filteredTransactions, startTime, endTime)
    }

    val monthlyBarData = remember(transactions) {
        ReportUtils.calculateLast6MonthsFlow(transactions)
    }

    val dailyTrendPoints = remember(filteredTransactions, startTime, endTime) {
        ReportUtils.calculateDailyTrend(filteredTransactions, startTime, endTime)
    }

    // Category breakdown for donut chart
    val categoryTotals = remember(filteredTransactions) {
        val nonTransferExpenses = filteredTransactions.filter {
            !it.isIncome && it.category != "Transfer" && it.category != "Adjustment" && it.category != "Udhaar" && it.category != "Investment"
        }
        nonTransferExpenses.groupBy { it.category }
            .mapValues { it.value.sumOf { tx -> tx.amount } }
            .toList()
            .sortedByDescending { it.second }
    }

    val dateRangeLabel = remember(selectedRange, startTime, endTime) {
        if (selectedRange == ReportDateRange.CUSTOM) {
            "${FormatUtils.formatDate(startTime)} - ${FormatUtils.formatDate(endTime)}"
        } else {
            "${selectedRange.label} (${FormatUtils.formatDate(startTime)} - ${FormatUtils.formatDate(endTime)})"
        }
    }

    // Date pickers for custom range
    fun pickDate(isStart: Boolean) {
        val initialCal = Calendar.getInstance().apply {
            timeInMillis = if (isStart) customStartDate else customEndDate
        }
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val newCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    if (isStart) {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                    } else {
                        set(Calendar.HOUR_OF_DAY, 23)
                        set(Calendar.MINUTE, 59)
                        set(Calendar.SECOND, 59)
                    }
                }
                if (isStart) customStartDate = newCal.timeInMillis
                else customEndDate = newCal.timeInMillis
            },
            initialCal.get(Calendar.YEAR),
            initialCal.get(Calendar.MONTH),
            initialCal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text("Reports & Analytics", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.testTag("reports_back_button")
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Privacy mode toggle
                    IconButton(onClick = { viewModel.togglePrivacyMode() }) {
                        Icon(
                            if (privacyMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle Privacy"
                        )
                    }

                    // Export Menu Box
                    Box {
                        IconButton(
                            onClick = { showExportMenu = true },
                            modifier = Modifier.testTag("reports_export_menu_button")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "Export Report")
                        }

                        DropdownMenu(
                            expanded = showExportMenu,
                            onDismissRequest = { showExportMenu = false }
                        ) {
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color(0xFFC62828)) },
                                text = { Text("Export PDF Report") },
                                onClick = {
                                    showExportMenu = false
                                    try {
                                        val pdfFile = ReportExportUtils.exportTransactionsPdf(
                                            context = context,
                                            transactions = filteredTransactions,
                                            summary = summary,
                                            topCategories = topCategories,
                                            dateRangeLabel = dateRangeLabel
                                        )
                                        ReportExportUtils.shareFile(context, pdfFile, "application/pdf", "MoneyMate Financial PDF Report")
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )

                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.TableChart, contentDescription = null, tint = Color(0xFF2E7D32)) },
                                text = { Text("Export Excel (.xls)") },
                                onClick = {
                                    showExportMenu = false
                                    try {
                                        val xlsFile = ReportExportUtils.exportTransactionsExcel(
                                            context = context,
                                            transactions = filteredTransactions,
                                            summary = summary,
                                            dateRangeLabel = dateRangeLabel
                                        )
                                        ReportExportUtils.shareFile(context, xlsFile, "application/vnd.ms-excel", "MoneyMate Excel Report")
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )

                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.Article, contentDescription = null, tint = Color(0xFF1565C0)) },
                                text = { Text("Export CSV") },
                                onClick = {
                                    showExportMenu = false
                                    try {
                                        val csvFile = ReportExportUtils.exportTransactionsCsv(
                                            context = context,
                                            transactions = filteredTransactions,
                                            dateRangeLabel = dateRangeLabel
                                        )
                                        ReportExportUtils.shareFile(context, csvFile, "text/csv", "MoneyMate CSV Report")
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )

                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.Print, contentDescription = null) },
                                text = { Text("Print Report Layout") },
                                onClick = {
                                    showExportMenu = false
                                    try {
                                        val pdfFile = ReportExportUtils.exportTransactionsPdf(
                                            context = context,
                                            transactions = filteredTransactions,
                                            summary = summary,
                                            topCategories = topCategories,
                                            dateRangeLabel = dateRangeLabel
                                        )
                                        ReportExportUtils.printPdf(context, pdfFile, "MoneyMate_Financial_Report")
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Print error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )

                            HorizontalDivider()

                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.People, contentDescription = null, tint = Color(0xFF7B1FA2)) },
                                text = { Text("Export Udhaar Ledger (PDF)") },
                                onClick = {
                                    showExportMenu = false
                                    try {
                                        val file = ReportExportUtils.exportUdhaarPdf(context, people, udhaarEntries, udhaarRepayments)
                                        ReportExportUtils.shareFile(context, file, "application/pdf", "MoneyMate Udhaar Ledger PDF")
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )

                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.PeopleOutline, contentDescription = null, tint = Color(0xFF7B1FA2)) },
                                text = { Text("Export Udhaar Ledger (CSV)") },
                                onClick = {
                                    showExportMenu = false
                                    try {
                                        val file = ReportExportUtils.exportUdhaarCsv(context, people, udhaarEntries, udhaarRepayments)
                                        ReportExportUtils.shareFile(context, file, "text/csv", "MoneyMate Udhaar Ledger CSV")
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp)
        ) {
            // 0. AI Monthly Insights & Ask My Data Helper
            item {
                var showInlineAiInsights by remember { mutableStateOf(false) }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Column {
                                    Text("AI Monthly Insights & Ask Data", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text(
                                        "Analyze spending patterns, unusual spikes & saving tips",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = { navController.navigate("ai_assistant?tab=2") },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text("Ask Data", style = MaterialTheme.typography.labelSmall)
                                }
                                Button(
                                    onClick = {
                                        showInlineAiInsights = !showInlineAiInsights
                                        if (showInlineAiInsights) {
                                            viewModel.generateMonthlyInsightsWithAi()
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("reports_ai_insights_toggle_btn")
                                ) {
                                    Text(if (showInlineAiInsights) "Hide" else "Insights", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                    if (showInlineAiInsights) {
                        MonthlyInsightsHelperSection(viewModel = viewModel)
                    }
                }
            }

            // 1. Time Filters
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReportDateRange.entries.forEach { range ->
                            FilterChip(
                                selected = selectedRange == range,
                                onClick = { selectedRange = range },
                                label = { Text(range.label) },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.testTag("filter_range_${range.name}")
                            )
                        }
                    }

                    // Custom Date Range Picker Row (if CUSTOM selected)
                    if (selectedRange == ReportDateRange.CUSTOM) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.clickable { pickDate(true) },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                    Column {
                                        Text("From", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(FormatUtils.formatDate(customStartDate), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Text("→", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                Row(
                                    modifier = Modifier.clickable { pickDate(false) },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                    Column {
                                        Text("To", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(FormatUtils.formatDate(customEndDate), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // Filter By Account & Category row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Account Filter Box
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { showAccountMenu = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Acc: $selectedAccountFilter",
                                    maxLines = 1,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                            DropdownMenu(
                                expanded = showAccountMenu,
                                onDismissRequest = { showAccountMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("All Accounts") },
                                    onClick = {
                                        selectedAccountFilter = "All"
                                        showAccountMenu = false
                                    }
                                )
                                accounts.forEach { acc ->
                                    DropdownMenuItem(
                                        text = { Text(acc.name) },
                                        onClick = {
                                            selectedAccountFilter = acc.name
                                            showAccountMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        // Category Filter Box
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { showCategoryMenu = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Category, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Cat: $selectedCategoryFilter",
                                    maxLines = 1,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                            DropdownMenu(
                                expanded = showCategoryMenu,
                                onDismissRequest = { showCategoryMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("All Categories") },
                                    onClick = {
                                        selectedCategoryFilter = "All"
                                        showCategoryMenu = false
                                    }
                                )
                                categories.forEach { cat ->
                                    DropdownMenuItem(
                                        text = { Text(cat.name) },
                                        onClick = {
                                            selectedCategoryFilter = cat.name
                                            showCategoryMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Summary Table / Cards
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Financial Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    "${summary.totalDays} Days",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        // Grid 2x2
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Total Income
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFFE8F5E9),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Total Income", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32))
                                    Spacer(Modifier.height(4.dp))
                                    Text(formatMoney(summary.totalIncome), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
                                }
                            }
                            // Total Expense
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFFFFEBEE),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Total Expense", style = MaterialTheme.typography.labelSmall, color = Color(0xFFC62828))
                                    Spacer(Modifier.height(4.dp))
                                    Text(formatMoney(summary.totalExpense), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFFB71C1C))
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Net Savings
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (summary.netSavings >= 0) Color(0xFFE3F2FD) else Color(0xFFFFEBEE),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Net Savings (${summary.savingsPercent}%)", style = MaterialTheme.typography.labelSmall, color = if (summary.netSavings >= 0) Color(0xFF1565C0) else Color(0xFFC62828))
                                    Spacer(Modifier.height(4.dp))
                                    Text(formatMoney(summary.netSavings), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (summary.netSavings >= 0) Color(0xFF0D47A1) else Color(0xFFB71C1C))
                                }
                            }
                            // Avg Daily Spend
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Avg Daily Spend", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(4.dp))
                                    Text(formatMoney(summary.avgDailySpend), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }

                        if (summary.highestExpenseDay != "None") {
                            Spacer(Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Highest Expense Day", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        "${summary.highestExpenseDay} • ${formatMoney(summary.highestExpenseDayAmount)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Month Comparison: This Month vs Last Month
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("This Month vs Last Month", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Icon(Icons.Default.CompareArrows, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }

                        Spacer(Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Income comparison
                            ComparisonItem(
                                label = "Income",
                                current = formatMoney(monthComparison.currentMonthIncome),
                                changePercent = monthComparison.incomeChangePercent,
                                isPositiveGood = true
                            )

                            // Expense comparison
                            ComparisonItem(
                                label = "Expense",
                                current = formatMoney(monthComparison.currentMonthExpense),
                                changePercent = monthComparison.expenseChangePercent,
                                isPositiveGood = false // Lower expense is good
                            )

                            // Savings comparison
                            ComparisonItem(
                                label = "Savings",
                                current = formatMoney(monthComparison.currentMonthSavings),
                                changePercent = monthComparison.savingsChangePercent,
                                isPositiveGood = true
                            )
                        }
                    }
                }
            }

            // 4. Category Donut / Pie Chart
            item {
                CategoryDonutChart(
                    categoryTotals = categoryTotals,
                    totalExpense = summary.totalExpense,
                    privacyMode = privacyMode
                )
            }

            // 5. Top 5 Categories Breakdown
            if (topCategories.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text("Top 5 Spending Categories", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(14.dp))

                            topCategories.forEachIndexed { index, cat ->
                                val color = reportChartColors[index % reportChartColors.size]
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
                                            Text(cat.category, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                            Text("(${cat.transactionCount} txs)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Text(formatMoney(cat.totalAmount), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = { (cat.percentageOfTotal / 100.0).toFloat().coerceIn(0f, 1f) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(CircleShape),
                                        color = color,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    Spacer(Modifier.height(4.dp))
                                }
                            }
                        }
                    }
                }
            }

            // 6. Monthly 6-Month Income vs Expense Bar Chart
            item {
                IncomeExpenseMonthlyBarChart(
                    monthlyData = monthlyBarData,
                    privacyMode = privacyMode
                )
            }

            // 7. Daily Trend Line Chart
            item {
                DailyTrendLineChart(
                    trendPoints = dailyTrendPoints,
                    privacyMode = privacyMode
                )
            }

            // 8. Cash vs Bank Flow
            item {
                CashBankFlowCard(
                    flow = cashBankFlow,
                    privacyMode = privacyMode
                )
            }

            // 9. Quick Actions for Exports
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Export & Print Options", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Download print-ready PDF reports with summaries and full transactions table, or export CSV and Excel files for analysis.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = {
                                    try {
                                        val pdfFile = ReportExportUtils.exportTransactionsPdf(
                                            context = context,
                                            transactions = filteredTransactions,
                                            summary = summary,
                                            topCategories = topCategories,
                                            dateRangeLabel = dateRangeLabel
                                        )
                                        ReportExportUtils.shareFile(context, pdfFile, "application/pdf", "MoneyMate Financial PDF Report")
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("PDF Report", style = MaterialTheme.typography.labelSmall)
                            }

                            FilledTonalButton(
                                onClick = {
                                    try {
                                        val xlsFile = ReportExportUtils.exportTransactionsExcel(
                                            context = context,
                                            transactions = filteredTransactions,
                                            summary = summary,
                                            dateRangeLabel = dateRangeLabel
                                        )
                                        ReportExportUtils.shareFile(context, xlsFile, "application/vnd.ms-excel", "MoneyMate Excel Report")
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Excel (.xls)", style = MaterialTheme.typography.labelSmall)
                            }

                            FilledTonalButton(
                                onClick = {
                                    try {
                                        val pdfFile = ReportExportUtils.exportTransactionsPdf(
                                            context = context,
                                            transactions = filteredTransactions,
                                            summary = summary,
                                            topCategories = topCategories,
                                            dateRangeLabel = dateRangeLabel
                                        )
                                        ReportExportUtils.printPdf(context, pdfFile, "MoneyMate_Print")
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Print error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Print", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            // 10. Filtered Transactions List (Top 15 preview)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Transactions in Range (${filteredTransactions.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (filteredTransactions.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("No transactions match the selected filters.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                items(filteredTransactions.take(20), key = { it.id }) { tx ->
                    val isTransfer = tx.category == "Transfer"
                    val isUdhaar = tx.category == "Udhaar"
                    val sign = if (isTransfer) "" else if (tx.isIncome) "+" else "-"
                    val amountColor = when {
                        isTransfer -> MaterialTheme.colorScheme.tertiary
                        isUdhaar -> if (tx.isIncome) Color(0xFF2E7D32) else Color(0xFFC62828)
                        tx.isIncome -> Color(0xFF2E7D32)
                        else -> MaterialTheme.colorScheme.error
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (isTransfer || isUdhaar) tx.note.ifBlank { tx.category } else tx.category,
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    "${FormatUtils.formatDate(tx.timestamp)} • ${tx.account}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                "$sign${formatMoney(tx.amount)}",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = amountColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComparisonItem(
    label: String,
    current: String,
    changePercent: Double,
    isPositiveGood: Boolean
) {
    val isPositive = changePercent >= 0
    val isGood = if (isPositiveGood) isPositive else !isPositive
    val badgeColor = if (isGood) Color(0xFF2E7D32) else Color(0xFFC62828)
    val badgeBg = if (isGood) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(current, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Surface(shape = RoundedCornerShape(6.dp), color = badgeBg) {
            Text(
                "${if (isPositive) "+" else ""}${String.format(java.util.Locale.US, "%.1f%%", changePercent)}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = badgeColor,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

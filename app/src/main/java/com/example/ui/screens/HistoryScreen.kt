package com.example.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.data.Transaction
import com.example.utils.CategoryUtils
import com.example.utils.FormatUtils
import com.example.viewmodel.FinanceViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

enum class SortOption { DATE_DESC, DATE_ASC, AMOUNT_DESC, AMOUNT_ASC }
enum class DateRangeOption { ALL_TIME, TODAY, THIS_WEEK, THIS_MONTH }

/**
 * Sealed virtual-list row model used to flatten day headers and transactions into a single
 * high-performance LazyColumn stream for 5,000+ transactions.
 */
sealed class HistoryVirtualItem {
    abstract val stableKey: String
    abstract val contentType: String

    data class DayHeader(
        val dayKey: Int,
        val title: String,
        val dayIncome: Double,
        val dayExpense: Double
    ) : HistoryVirtualItem() {
        override val stableKey: String = "header_$dayKey"
        override val contentType: String = "day_header"
    }

    data class TransactionRow(
        val transaction: Transaction,
        val formattedTime: String
    ) : HistoryVirtualItem() {
        override val stableKey: String = "tx_${transaction.id}"
        override val contentType: String = "tx_row"
    }
}

/**
 * High-performance filtering, sorting, and virtual-list pagination helper for 5,000+ transactions.
 * Avoids per-item SimpleDateFormat allocations during filtering/grouping.
 */
object TransactionPaginationUtils {
    const val DEFAULT_PAGE_SIZE = 100

    fun computeDayKey(timestamp: Long, cal: Calendar): Int {
        cal.timeInMillis = timestamp
        return cal.get(Calendar.YEAR) * 10000 +
            (cal.get(Calendar.MONTH) + 1) * 100 +
            cal.get(Calendar.DAY_OF_MONTH)
    }

    fun filterAndSortTransactions(
        transactions: List<Transaction>,
        filterType: String = "All",
        selectedCategoryFilter: String = "All",
        selectedAccountFilter: String = "All",
        selectedDateRange: DateRangeOption = DateRangeOption.ALL_TIME,
        minAmountFilter: String = "",
        maxAmountFilter: String = "",
        searchQuery: String = "",
        sortOption: SortOption = SortOption.DATE_DESC,
        nowMillis: Long = System.currentTimeMillis()
    ): List<Transaction> {
        if (transactions.isEmpty()) return emptyList()

        val minVal = minAmountFilter.toDoubleOrNull()
        val maxVal = maxAmountFilter.toDoubleOrNull()
        val trimmedQuery = searchQuery.trim().lowercase(Locale.US)
        val hasQuery = trimmedQuery.isNotEmpty()
        val filterAllCat = selectedCategoryFilter == "All"
        val filterAllAcc = selectedAccountFilter == "All"

        val calNow = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val nowYear = calNow.get(Calendar.YEAR)
        val nowDayOfYear = calNow.get(Calendar.DAY_OF_YEAR)
        val nowWeekOfYear = calNow.get(Calendar.WEEK_OF_YEAR)
        val nowMonth = calNow.get(Calendar.MONTH)
        val calScratch = Calendar.getInstance()

        val filtered = ArrayList<Transaction>(minOf(transactions.size, 512))

        for (tx in transactions) {
            val isNonOperating = com.example.utils.ReportUtils.isExcludedFromIncomeExpense(tx.category)
            val matchesType = when (filterType) {
                "Income" -> tx.isIncome && !isNonOperating
                "Expenses" -> !tx.isIncome && !isNonOperating
                "Transfers" -> tx.category == "Transfer"
                "Udhaar" -> tx.category == "Udhaar"
                "Investments" -> tx.category == "Investment"
                else -> true
            }
            if (!matchesType) continue

            if (!filterAllCat && !tx.category.equals(selectedCategoryFilter, ignoreCase = true)) continue
            if (!filterAllAcc && !tx.account.equals(selectedAccountFilter, ignoreCase = true)) continue

            if (minVal != null && tx.amount < minVal) continue
            if (maxVal != null && tx.amount > maxVal) continue

            if (selectedDateRange != DateRangeOption.ALL_TIME) {
                calScratch.timeInMillis = tx.timestamp
                val matchesDate = when (selectedDateRange) {
                    DateRangeOption.TODAY ->
                        calScratch.get(Calendar.YEAR) == nowYear &&
                            calScratch.get(Calendar.DAY_OF_YEAR) == nowDayOfYear
                    DateRangeOption.THIS_WEEK ->
                        calScratch.get(Calendar.YEAR) == nowYear &&
                            calScratch.get(Calendar.WEEK_OF_YEAR) == nowWeekOfYear
                    DateRangeOption.THIS_MONTH ->
                        calScratch.get(Calendar.YEAR) == nowYear &&
                            calScratch.get(Calendar.MONTH) == nowMonth
                    DateRangeOption.ALL_TIME -> true
                }
                if (!matchesDate) continue
            }

            if (hasQuery) {
                val matchesSearch =
                    tx.category.lowercase(Locale.US).contains(trimmedQuery) ||
                        tx.subcategory.lowercase(Locale.US).contains(trimmedQuery) ||
                        tx.note.lowercase(Locale.US).contains(trimmedQuery) ||
                        tx.account.lowercase(Locale.US).contains(trimmedQuery) ||
                        tx.tags.lowercase(Locale.US).contains(trimmedQuery) ||
                        tx.paymentMethod.lowercase(Locale.US).contains(trimmedQuery) ||
                        tx.amount.toString().contains(trimmedQuery) ||
                        FormatUtils.formatDate(tx.timestamp).contains(trimmedQuery)
                if (!matchesSearch) continue
            }

            filtered.add(tx)
        }

        return when (sortOption) {
            SortOption.DATE_DESC -> filtered.sortedByDescending { it.timestamp }
            SortOption.DATE_ASC -> filtered.sortedBy { it.timestamp }
            SortOption.AMOUNT_DESC -> filtered.sortedByDescending { it.amount }
            SortOption.AMOUNT_ASC -> filtered.sortedBy { it.amount }
        }
    }

    /**
     * Builds a flat list of [HistoryVirtualItem] for the visible page slice ([visibleCount])
     * while computing accurate daily income/expense totals across the full day group.
     */
    fun buildPaginatedVirtualItems(
        filteredTransactions: List<Transaction>,
        visibleCount: Int,
        nowMillis: Long = System.currentTimeMillis()
    ): List<HistoryVirtualItem> {
        if (filteredTransactions.isEmpty() || visibleCount <= 0) return emptyList()
        val limit = minOf(visibleCount, filteredTransactions.size)
        val calScratch = Calendar.getInstance()
        val todayKey = computeDayKey(nowMillis, calScratch)
        val yesterdayKey = computeDayKey(nowMillis - 86_400_000L, calScratch)

        // Precompute full-day income/expense totals so headers stay accurate even if a day spans a page boundary
        val dayIncomeMap = HashMap<Int, Double>()
        val dayExpenseMap = HashMap<Int, Double>()
        for (tx in filteredTransactions) {
            if (tx.category == "Transfer" || tx.category == "Adjustment" || tx.category == "Udhaar" || tx.category == "Investment") {
                continue
            }
            val dKey = computeDayKey(tx.timestamp, calScratch)
            if (tx.isIncome) {
                dayIncomeMap[dKey] = (dayIncomeMap[dKey] ?: 0.0) + tx.amount
            } else {
                dayExpenseMap[dKey] = (dayExpenseMap[dKey] ?: 0.0) + tx.amount
            }
        }

        val dayTitleFormat = SimpleDateFormat("dd MMMM yyyy (EEEE)", Locale.getDefault())
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val result = ArrayList<HistoryVirtualItem>(limit + 32)
        var lastDayKey = Int.MIN_VALUE

        for (i in 0 until limit) {
            val tx = filteredTransactions[i]
            val dayKey = computeDayKey(tx.timestamp, calScratch)
            if (dayKey != lastDayKey) {
                lastDayKey = dayKey
                val dateText = dayTitleFormat.format(tx.timestamp)
                val title = when (dayKey) {
                    todayKey -> "Today • $dateText"
                    yesterdayKey -> "Yesterday • $dateText"
                    else -> dateText
                }
                result.add(
                    HistoryVirtualItem.DayHeader(
                        dayKey = dayKey,
                        title = title,
                        dayIncome = FormatUtils.round2(dayIncomeMap[dayKey] ?: 0.0),
                        dayExpense = FormatUtils.round2(dayExpenseMap[dayKey] ?: 0.0)
                    )
                )
            }
            result.add(
                HistoryVirtualItem.TransactionRow(
                    transaction = tx,
                    formattedTime = timeFormat.format(tx.timestamp)
                )
            )
        }
        return result
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: FinanceViewModel, navController: NavController) {
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var filterType by remember { mutableStateOf("All") } // All, Expenses, Income, Transfers, Udhaar
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("All") }
    var selectedAccountFilter by remember { mutableStateOf("All") }
    var selectedDateRange by remember { mutableStateOf(DateRangeOption.ALL_TIME) }
    var minAmountFilter by remember { mutableStateOf("") }
    var maxAmountFilter by remember { mutableStateOf("") }
    var sortOption by remember { mutableStateOf(SortOption.DATE_DESC) }

    // Virtual pagination state for 5,000+ transactions
    var currentPage by remember(
        filterType,
        searchQuery,
        selectedCategoryFilter,
        selectedAccountFilter,
        selectedDateRange,
        minAmountFilter,
        maxAmountFilter,
        sortOption
    ) { mutableIntStateOf(1) }

    var showFilterSheet by remember { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }
    var transactionToDelete by remember { mutableStateOf<Transaction?>(null) }
    var previewPhotoUri by remember { mutableStateOf<String?>(null) }

    // Memoized O(N) filtered & sorted transactions
    val filteredTransactions = remember(
        transactions,
        filterType,
        selectedCategoryFilter,
        selectedAccountFilter,
        selectedDateRange,
        minAmountFilter,
        maxAmountFilter,
        searchQuery,
        sortOption
    ) {
        TransactionPaginationUtils.filterAndSortTransactions(
            transactions = transactions,
            filterType = filterType,
            selectedCategoryFilter = selectedCategoryFilter,
            selectedAccountFilter = selectedAccountFilter,
            selectedDateRange = selectedDateRange,
            minAmountFilter = minAmountFilter,
            maxAmountFilter = maxAmountFilter,
            searchQuery = searchQuery,
            sortOption = sortOption
        )
    }

    val visibleLimit = minOf(
        filteredTransactions.size,
        currentPage * TransactionPaginationUtils.DEFAULT_PAGE_SIZE
    )
    val hasMorePages = visibleLimit < filteredTransactions.size

    val virtualItems = remember(filteredTransactions, visibleLimit) {
        TransactionPaginationUtils.buildPaginatedVirtualItems(
            filteredTransactions = filteredTransactions,
            visibleCount = visibleLimit
        )
    }

    val listState = rememberLazyListState()
    val shouldAutoLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleIndex >= totalItems - 8
        }
    }

    LaunchedEffect(shouldAutoLoadMore, hasMorePages) {
        if (shouldAutoLoadMore && hasMorePages) {
            currentPage++
        }
    }

    // O(1) category lookup map
    val categoryLookup = remember(categories) {
        categories.associateBy { it.name.lowercase(Locale.US) }
    }

    // Bill Photo Preview Dialog
    if (previewPhotoUri != null) {
        Dialog(onDismissRequest = { previewPhotoUri = null }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Bill Photo Receipt", fontWeight = FontWeight.Bold)
                        IconButton(onClick = { previewPhotoUri = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close bill photo preview")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    AsyncImage(
                        model = previewPhotoUri,
                        contentDescription = "Bill Receipt",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }
    }

    // Confirmation dialog before delete
    if (transactionToDelete != null) {
        val tx = transactionToDelete!!
        AlertDialog(
            onDismissRequest = { transactionToDelete = null },
            title = { Text("Delete Transaction?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Are you sure you want to delete this ${FormatUtils.formatInr(tx.amount)} ${tx.category} transaction? All account balances will update automatically."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val deletedTx = tx
                        viewModel.deleteTransaction(deletedTx.id)
                        transactionToDelete = null

                        coroutineScope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = "Deleted ${FormatUtils.formatInr(deletedTx.amount)} (${deletedTx.category})",
                                actionLabel = "UNDO",
                                duration = SnackbarDuration.Short
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                viewModel.restoreTransaction(deletedTx)
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_button")
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { transactionToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Filter Sheet
    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Filter Transactions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    TextButton(onClick = {
                        filterType = "All"
                        selectedCategoryFilter = "All"
                        selectedAccountFilter = "All"
                        selectedDateRange = DateRangeOption.ALL_TIME
                        minAmountFilter = ""
                        maxAmountFilter = ""
                    }) {
                        Text("Reset All")
                    }
                }

                // Date Range
                Text("Date Range", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        DateRangeOption.ALL_TIME to "All Time",
                        DateRangeOption.TODAY to "Today",
                        DateRangeOption.THIS_WEEK to "This Week",
                        DateRangeOption.THIS_MONTH to "This Month"
                    ).forEach { (opt, label) ->
                        FilterChip(
                            selected = selectedDateRange == opt,
                            onClick = { selectedDateRange = opt },
                            label = { Text(label) }
                        )
                    }
                }

                // Account Filter
                Text("Account", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedAccountFilter == "All",
                        onClick = { selectedAccountFilter = "All" },
                        label = { Text("All Accounts") }
                    )
                    accounts.forEach { acc ->
                        FilterChip(
                            selected = selectedAccountFilter == acc.name,
                            onClick = { selectedAccountFilter = acc.name },
                            label = { Text(acc.name) }
                        )
                    }
                }

                // Category Filter
                Text("Category", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedCategoryFilter == "All",
                        onClick = { selectedCategoryFilter = "All" },
                        label = { Text("All Categories") }
                    )
                    categories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategoryFilter == cat.name,
                            onClick = { selectedCategoryFilter = cat.name },
                            label = { Text(cat.name) }
                        )
                    }
                }

                // Amount Range
                Text("Amount Range (${FormatUtils.currentCurrencySymbol})", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = minAmountFilter,
                        onValueChange = { minAmountFilter = it },
                        label = { Text("Min Amount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = maxAmountFilter,
                        onValueChange = { maxAmountFilter = it },
                        label = { Text("Max Amount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Button(
                    onClick = { showFilterSheet = false },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Apply Filters")
                }
            }
        }
    }

    // Sort Sheet
    if (showSortSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSortSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Sort Transactions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                listOf(
                    SortOption.DATE_DESC to "Date: Newest First",
                    SortOption.DATE_ASC to "Date: Oldest First",
                    SortOption.AMOUNT_DESC to "Amount: Highest First",
                    SortOption.AMOUNT_ASC to "Amount: Lowest First"
                ).forEach { (opt, label) ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                sortOption = opt
                                showSortSheet = false
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (sortOption == opt) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label, fontWeight = if (sortOption == opt) FontWeight.Bold else FontWeight.Normal)
                            if (sortOption == opt) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {
                    Column {
                        Text("Transactions", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                        if (filteredTransactions.isNotEmpty()) {
                            Text(
                                "${filteredTransactions.size} total",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showSortSheet = true },
                        modifier = Modifier.testTag("sort_button")
                    ) {
                        Icon(Icons.Default.Sort, contentDescription = "Sort transactions")
                    }
                    IconButton(
                        onClick = { showFilterSheet = true },
                        modifier = Modifier.testTag("filter_button")
                    ) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filter transactions")
                    }
                    IconButton(
                        onClick = { exportTransactions(context, transactions) },
                        modifier = Modifier.testTag("export_transactions_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Export transactions as CSV")
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
            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search note, tag, category, subcategory...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("history_search_input"),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )

            Spacer(Modifier.height(10.dp))

            // Quick Type Filter Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("All", "Expenses", "Income", "Transfers", "Udhaar").forEach { mode ->
                    FilterChip(
                        selected = filterType == mode,
                        onClick = { filterType = mode },
                        label = { Text(mode) },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.testTag("filter_chip_$mode")
                    )
                }

                if (selectedDateRange != DateRangeOption.ALL_TIME) {
                    FilterChip(
                        selected = true,
                        onClick = { selectedDateRange = DateRangeOption.ALL_TIME },
                        label = { Text("Range: ${selectedDateRange.name.replace("_", " ")}") },
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Clear date range filter", modifier = Modifier.size(14.dp)) }
                    )
                }

                if (selectedAccountFilter != "All") {
                    FilterChip(
                        selected = true,
                        onClick = { selectedAccountFilter = "All" },
                        label = { Text("Acc: $selectedAccountFilter") },
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Clear account filter", modifier = Modifier.size(14.dp)) }
                    )
                }

                if (selectedCategoryFilter != "All") {
                    FilterChip(
                        selected = true,
                        onClick = { selectedCategoryFilter = "All" },
                        label = { Text("Cat: $selectedCategoryFilter") },
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Clear category filter", modifier = Modifier.size(14.dp)) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            if (filteredTransactions.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.ReceiptLong,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            if (searchQuery.isNotBlank() || filterType != "All") "No matching transactions found" else "No transactions recorded yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .testTag("transactions_virtual_list"),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 84.dp)
                ) {
                    items(
                        items = virtualItems,
                        key = { it.stableKey },
                        contentType = { it.contentType }
                    ) { vItem ->
                        when (vItem) {
                            is HistoryVirtualItem.DayHeader -> {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            vItem.title,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            if (vItem.dayIncome > 0) {
                                                Text(
                                                    "+${FormatUtils.formatInr(vItem.dayIncome)}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            if (vItem.dayExpense > 0) {
                                                Text(
                                                    "-${FormatUtils.formatInr(vItem.dayExpense)}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.error,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            is HistoryVirtualItem.TransactionRow -> {
                                val transaction = vItem.transaction
                                val isTransfer = transaction.category == "Transfer"
                                val isUdhaar = transaction.category == "Udhaar"
                                val sign = if (isTransfer) "" else if (transaction.isIncome) "+" else "-"
                                val amountColor = when {
                                    isTransfer -> MaterialTheme.colorScheme.tertiary
                                    isUdhaar -> if (transaction.isIncome) androidx.compose.ui.graphics.Color(0xFF2E7D32) else androidx.compose.ui.graphics.Color(0xFFD32F2F)
                                    transaction.isIncome -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.error
                                }

                                val categoryObj = categoryLookup[transaction.category.lowercase(Locale.US)]
                                val categoryColor = when {
                                    isTransfer -> MaterialTheme.colorScheme.tertiary
                                    isUdhaar -> androidx.compose.ui.graphics.Color(0xFF1565C0)
                                    categoryObj != null -> CategoryUtils.parseColor(categoryObj.colorHex)
                                    else -> MaterialTheme.colorScheme.primary
                                }
                                val categoryIcon = when {
                                    isTransfer -> Icons.Default.SwapHoriz
                                    isUdhaar -> Icons.Default.People
                                    categoryObj != null -> CategoryUtils.getIcon(categoryObj.iconName)
                                    else -> Icons.Default.Category
                                }

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { navController.navigate("add?id=${transaction.id}") }
                                        .testTag("transaction_item_${transaction.id}"),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(44.dp)
                                                        .clip(CircleShape)
                                                        .background(categoryColor.copy(alpha = 0.16f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        categoryIcon,
                                                        contentDescription = null,
                                                        tint = categoryColor,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }

                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        if (isTransfer || isUdhaar) transaction.note.ifBlank { transaction.category } else transaction.category,
                                                        fontWeight = FontWeight.Bold,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        maxLines = 1,
                                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                    )
                                                    if (transaction.subcategory.isNotBlank()) {
                                                        Text(
                                                            transaction.subcategory,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.primary,
                                                            fontWeight = FontWeight.Medium,
                                                            maxLines = 1,
                                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    Text(
                                                        "${vItem.formattedTime} • ${transaction.account}${if (transaction.paymentMethod.isNotBlank()) " • ${transaction.paymentMethod}" else ""}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                    )
                                                }
                                            }

                                            Spacer(Modifier.width(8.dp))

                                            Column(horizontalAlignment = Alignment.End) {
                                                com.example.ui.components.AutoSizeSingleLineText(
                                                    text = "$sign${FormatUtils.formatInr(transaction.amount)}",
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = amountColor,
                                                    minFontSizeSp = 11f
                                                )
                                                if (transaction.photoUri != null) {
                                                    Spacer(Modifier.height(4.dp))
                                                    AssistChip(
                                                        onClick = { previewPhotoUri = transaction.photoUri },
                                                        label = { Text("Bill", style = MaterialTheme.typography.labelSmall) },
                                                        leadingIcon = {
                                                            Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(14.dp))
                                                        },
                                                        modifier = Modifier.minimumInteractiveComponentSize()
                                                    )
                                                }
                                            }
                                        }

                                        if (transaction.note.isNotBlank() && !isTransfer) {
                                            Spacer(Modifier.height(6.dp))
                                            Text(
                                                transaction.note,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                                            )
                                        }

                                        if (transaction.tags.isNotBlank()) {
                                            Spacer(Modifier.height(6.dp))
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                transaction.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { tag ->
                                                    SuggestionChip(
                                                        onClick = { searchQuery = tag },
                                                        label = { Text("#$tag", style = MaterialTheme.typography.labelSmall) },
                                                        shape = RoundedCornerShape(8.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(Modifier.height(8.dp))
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                        Spacer(Modifier.height(4.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            TextButton(
                                                onClick = { navController.navigate("add?id=${transaction.id}") },
                                                modifier = Modifier
                                                    .minimumInteractiveComponentSize()
                                                    .testTag("edit_tx_${transaction.id}")
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Edit")
                                            }

                                            TextButton(
                                                onClick = {
                                                    viewModel.duplicateTransaction(transaction.id) {
                                                        coroutineScope.launch {
                                                            snackbarHostState.showSnackbar("Transaction duplicated")
                                                        }
                                                    }
                                                },
                                                modifier = Modifier
                                                    .minimumInteractiveComponentSize()
                                                    .testTag("duplicate_tx_${transaction.id}")
                                            ) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Duplicate")
                                            }

                                            IconButton(
                                                onClick = { transactionToDelete = transaction },
                                                modifier = Modifier
                                                    .minimumInteractiveComponentSize()
                                                    .testTag("delete_tx_${transaction.id}")
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "Delete transaction",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Pagination footer for 5,000+ transactions
                    if (hasMorePages) {
                        item(key = "pagination_load_more") {
                            OutlinedButton(
                                onClick = { currentPage++ },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .padding(vertical = 8.dp)
                                    .testTag("load_more_transactions_button")
                                    .semantics {
                                        contentDescription = "Load more transactions, showing $visibleLimit of ${filteredTransactions.size}"
                                    },
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Default.ExpandMore, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Load More ($visibleLimit of ${filteredTransactions.size})")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun exportTransactions(context: Context, transactions: List<Transaction>) {
    try {
        val file = File(context.cacheDir, "transactions_${System.currentTimeMillis()}.csv")
        file.bufferedWriter().use { writer ->
            writer.write("Date,Time,Type,Category,Subcategory,Account,PaymentMethod,Amount,Note,Tags\n")
            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            for (t in transactions) {
                val type = if (t.category == "Transfer") "Transfer" else if (t.isIncome) "Income" else "Expense"
                val date = dateFormat.format(t.timestamp)
                val time = timeFormat.format(t.timestamp)
                val safeNote = t.note.replace(",", ";").replace("\n", " ")
                val safeTags = t.tags.replace(",", ";")
                writer.write("$date,$time,$type,${t.category},${t.subcategory},${t.account},${t.paymentMethod},${t.amount},$safeNote,$safeTags\n")
            }
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(sendIntent, "Share Transactions CSV"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

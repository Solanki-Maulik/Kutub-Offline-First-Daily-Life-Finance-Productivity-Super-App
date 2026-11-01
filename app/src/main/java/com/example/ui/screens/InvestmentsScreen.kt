package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.data.*
import com.example.utils.FormatUtils
import com.example.utils.InvestmentUtils
import com.example.viewmodel.FinanceViewModel
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvestmentsScreen(
    viewModel: FinanceViewModel,
    navController: NavController
) {
    val holdings by viewModel.holdings.collectAsStateWithLifecycle()
    val liabilities by viewModel.liabilities.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val netWorthOverview by viewModel.netWorthOverview.collectAsStateWithLifecycle()
    val privacyMode by viewModel.privacyMode.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Overview", "Holdings", "SIP & FD/RD", "Loans/EMIs")

    var showAddHoldingDialog by remember { mutableStateOf(false) }
    var editingHolding by remember { mutableStateOf<InvestmentHolding?>(null) }
    var defaultAssetTypeForDialog by remember { mutableStateOf("Stocks") }

    var showUpdateValueHolding by remember { mutableStateOf<InvestmentHolding?>(null) }
    var showAddLiabilityDialog by remember { mutableStateOf(false) }
    var editingLiability by remember { mutableStateOf<LiabilityItem?>(null) }
    var payingLiability by remember { mutableStateOf<LiabilityItem?>(null) }

    fun formatMoney(amount: Double): String =
        if (privacyMode) "₹****" else FormatUtils.formatInr(amount)

    val holdingPerformances = remember(holdings) {
        holdings.map { InvestmentUtils.calculateHoldingPerformance(it) }
    }

    val sipHoldings = remember(holdingPerformances) {
        holdingPerformances.filter {
            it.holding.assetType == "Mutual Fund/SIP" || it.holding.isSipEnabled || it.holding.sipMonthlyAmount > 0.0
        }
    }

    val fdRdHoldings = remember(holdingPerformances) {
        holdingPerformances.filter { it.holding.assetType == "FD/RD" }
    }

    val maturingSoonCount = remember(fdRdHoldings) {
        fdRdHoldings.count { it.isMaturityAlert }
    }

    if (showAddHoldingDialog || editingHolding != null) {
        AddOrEditHoldingDialog(
            initialHolding = editingHolding,
            defaultAssetType = defaultAssetTypeForDialog,
            accounts = accounts.map { it.name },
            onDismiss = {
                showAddHoldingDialog = false
                editingHolding = null
            },
            onSave = { holding, deductFromAccount, accountName ->
                viewModel.addOrUpdateHolding(holding, deductFromAccount, accountName)
                showAddHoldingDialog = false
                editingHolding = null
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Saved holding \"${holding.name}\"")
                }
            }
        )
    }

    showUpdateValueHolding?.let { target ->
        UpdateCurrentValueDialog(
            holding = target,
            onDismiss = { showUpdateValueHolding = null },
            onConfirm = { newValue ->
                viewModel.updateHoldingCurrentValue(target, newValue)
                showUpdateValueHolding = null
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Updated ${target.name} value to ${FormatUtils.formatInr(newValue)}")
                }
            }
        )
    }

    if (showAddLiabilityDialog || editingLiability != null) {
        AddOrEditLiabilityDialog(
            initialLiability = editingLiability,
            accounts = accounts.map { it.name },
            onDismiss = {
                showAddLiabilityDialog = false
                editingLiability = null
            },
            onSave = { liability ->
                viewModel.addOrUpdateLiability(liability)
                showAddLiabilityDialog = false
                editingLiability = null
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Saved liability \"${liability.name}\"")
                }
            }
        )
    }

    payingLiability?.let { target ->
        PayEmiDialog(
            liability = target,
            accounts = accounts.map { it.name },
            onDismiss = { payingLiability = null },
            onPay = { amount, accountName ->
                viewModel.payLiabilityEmi(target, amount, accountName)
                payingLiability = null
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Paid ${FormatUtils.formatInr(amount)} towards ${target.name}")
                }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text("Investments & Net Worth", fontWeight = FontWeight.Bold)
                        Text(
                            "Manual Valuation • Excluded from Expense Reports",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (selectedTab == 3) {
                                editingLiability = null
                                showAddLiabilityDialog = true
                            } else {
                                editingHolding = null
                                defaultAssetTypeForDialog = when (selectedTab) {
                                    2 -> "Mutual Fund/SIP"
                                    else -> "Stocks"
                                }
                                showAddHoldingDialog = true
                            }
                        },
                        modifier = Modifier.testTag("investments_top_add_btn")
                    ) {
                        Icon(Icons.Default.AddCircle, contentDescription = "Add Holding or Liability", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (selectedTab == 3) {
                        editingLiability = null
                        showAddLiabilityDialog = true
                    } else {
                        editingHolding = null
                        defaultAssetTypeForDialog = when (selectedTab) {
                            2 -> "Mutual Fund/SIP"
                            else -> "Stocks"
                        }
                        showAddHoldingDialog = true
                    }
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(if (selectedTab == 3) "Add Loan / Liability" else "Add Holding") },
                modifier = Modifier.testTag("investments_fab")
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            PrimaryScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.background
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(title, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium)
                                if (index == 2 && maturingSoonCount > 0) {
                                    Badge(containerColor = Color(0xFFD32F2F)) {
                                        Text("$maturingSoonCount", color = Color.White)
                                    }
                                }
                            }
                        },
                        modifier = Modifier.testTag("investments_tab_$index")
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp)
            ) {
                item {
                    NetWorthHeroCard(
                        overview = netWorthOverview,
                        formatMoney = ::formatMoney
                    )
                }

                when (selectedTab) {
                    0 -> {
                        item {
                            AssetAllocationDonutCard(
                                slices = netWorthOverview.assetAllocation,
                                totalAssets = netWorthOverview.totalAssets
                            )
                        }

                        item {
                            MonthlyNetWorthTrendCard(
                                points = netWorthOverview.monthlyTrend,
                                formatMoney = ::formatMoney
                            )
                        }

                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Text(
                                        "Zero Report Distortion: SIP installments and investment transfers deduct from your linked bank account without counting as expenses in reports.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }

                    1 -> {
                        if (holdingPerformances.isEmpty()) {
                            item {
                                EmptyStateBox(
                                    title = "No holdings added yet",
                                    subtitle = "Track Stocks, Mutual Funds, Gold, PPF/EPF, Crypto, Property & FD/RD with manual valuations.",
                                    buttonText = "Add First Holding",
                                    onAction = {
                                        defaultAssetTypeForDialog = "Stocks"
                                        showAddHoldingDialog = true
                                    }
                                )
                            }
                        } else {
                            items(holdingPerformances, key = { it.holding.id }) { perf ->
                                HoldingPerformanceCard(
                                    perf = perf,
                                    formatMoney = ::formatMoney,
                                    onUpdateValue = { showUpdateValueHolding = perf.holding },
                                    onEdit = { editingHolding = perf.holding },
                                    onDelete = { viewModel.deleteHolding(perf.holding.id) }
                                )
                            }
                        }
                    }

                    2 -> {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Monthly SIP Tracker",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                TextButton(
                                    onClick = {
                                        defaultAssetTypeForDialog = "Mutual Fund/SIP"
                                        showAddHoldingDialog = true
                                    }
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("New SIP")
                                }
                            }
                        }

                        if (sipHoldings.isEmpty()) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "No active SIPs. Add a 'Mutual Fund/SIP' holding with a monthly SIP amount to log 1-tap investment transfers.",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            }
                        } else {
                            items(sipHoldings, key = { "sip_${it.holding.id}" }) { perf ->
                                SipTrackerCard(
                                    perf = perf,
                                    formatMoney = ::formatMoney,
                                    onRecordSip = {
                                        viewModel.recordSipInstallment(perf.holding)
                                        coroutineScope.launch {
                                            val amt = perf.holding.sipMonthlyAmount
                                            snackbarHostState.showSnackbar(
                                                "Recorded SIP of ${FormatUtils.formatInr(amt)} for ${perf.holding.name} (Investment transfer)"
                                            )
                                        }
                                    },
                                    onEdit = { editingHolding = perf.holding }
                                )
                            }
                        }

                        item {
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "FD / RD Maturity Tracker",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                TextButton(
                                    onClick = {
                                        defaultAssetTypeForDialog = "FD/RD"
                                        showAddHoldingDialog = true
                                    }
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("New FD/RD")
                                }
                            }
                        }

                        if (fdRdHoldings.isEmpty()) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "No FD/RD deposits tracked yet. Add an 'FD/RD' holding with interest rate and maturity date for maturity alerts.",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            }
                        } else {
                            items(fdRdHoldings, key = { "fd_${it.holding.id}" }) { perf ->
                                FdRdTrackerCard(
                                    perf = perf,
                                    formatMoney = ::formatMoney,
                                    onEdit = { editingHolding = perf.holding }
                                )
                            }
                        }
                    }

                    3 -> {
                        if (liabilities.isEmpty()) {
                            item {
                                EmptyStateBox(
                                    title = "Zero liabilities recorded",
                                    subtitle = "Track home/car loans, EMIs, and credit card dues to see your true Net Worth and payoff dates.",
                                    buttonText = "Add Loan / Liability",
                                    onAction = { showAddLiabilityDialog = true }
                                )
                            }
                        } else {
                            items(liabilities, key = { it.id }) { liability ->
                                LiabilityTrackerCard(
                                    liability = liability,
                                    formatMoney = ::formatMoney,
                                    onPayEmi = { payingLiability = liability },
                                    onEdit = { editingLiability = liability },
                                    onDelete = { viewModel.deleteLiability(liability.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NetWorthHeroCard(
    overview: NetWorthOverview,
    formatMoney: (Double) -> String
) {
    val isProfit = overview.totalPortfolioProfitLoss >= 0.0
    val plColor = if (isProfit) Color(0xFF2E7D32) else Color(0xFFC62828)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("net_worth_hero_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "TOTAL NET WORTH",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                    )
                    Text(
                        formatMoney(overview.netWorth),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            "Portfolio Return",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val sign = if (isProfit) "+" else ""
                        Text(
                            "$sign${String.format(Locale.US, "%.1f", overview.totalPortfolioReturnPercent)}%",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = plColor
                        )
                        val xirr = overview.totalPortfolioXirrPercent
                        val xSign = if (xirr >= 0.0) "+" else ""
                        Text(
                            "XIRR: $xSign${String.format(Locale.US, "%.1f", xirr)}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = plColor
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "Total Assets",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                    )
                    Text(
                        formatMoney(overview.totalAssets),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        "Holdings: ${formatMoney(overview.totalInvestmentsCurrentValue)} • Liquid: ${formatMoney(overview.liquidCashBankBalance)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "Total Liabilities",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                    )
                    Text(
                        formatMoney(overview.totalLiabilities),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFB71C1C)
                    )
                    val plPrefix = if (isProfit) "+P&L: " else "-P&L: "
                    Text(
                        "$plPrefix${formatMoney(abs(overview.totalPortfolioProfitLoss))}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = plColor
                    )
                }
            }
        }
    }
}

@Composable
private fun AssetAllocationDonutCard(
    slices: List<AssetAllocationSlice>,
    totalAssets: Double
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                "Asset Allocation Donut",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (slices.isEmpty() || totalAssets <= 0.0) {
                Text(
                    "Add holdings or account balances to visualize your asset allocation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Box(
                        modifier = Modifier.size(136.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(120.dp)) {
                            val strokeWidth = 24.dp.toPx()
                            var startAngle = -90f
                            slices.forEach { slice ->
                                val sweep = ((slice.percentage / 100.0) * 360.0).toFloat()
                                val color = try {
                                    Color(android.graphics.Color.parseColor(slice.colorHex))
                                } catch (_: Exception) {
                                    Color(0xFF1565C0)
                                }
                                drawArc(
                                    color = color,
                                    startAngle = startAngle,
                                    sweepAngle = sweep.coerceAtLeast(2f),
                                    useCenter = false,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                                    size = Size(size.width, size.height)
                                )
                                startAngle += sweep
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Assets", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "${slices.size} classes",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        slices.forEach { slice ->
                            val dotColor = try {
                                Color(android.graphics.Color.parseColor(slice.colorHex))
                            } catch (_: Exception) {
                                Color(0xFF1565C0)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(dotColor)
                                    )
                                    Text(
                                        slice.assetType,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    "${String.format(Locale.US, "%.1f", slice.percentage)}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthlyNetWorthTrendCard(
    points: List<MonthlyNetWorthPoint>,
    formatMoney: (Double) -> String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Monthly Net Worth Trend (6M)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (points.isEmpty()) {
                Text("Not enough data points yet.", style = MaterialTheme.typography.bodySmall)
            } else {
                val maxVal = points.maxOf { maxOf(it.netWorth, it.assets, 1.0) }
                val minVal = points.minOf { minOf(it.netWorth, 0.0) }
                val range = (maxVal - minVal).coerceAtLeast(1.0)
                val primaryColor = MaterialTheme.colorScheme.primary

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .padding(horizontal = 8.dp, vertical = 10.dp)
                ) {
                    val stepX = if (points.size > 1) size.width / (points.size - 1) else size.width
                    val path = Path()

                    points.forEachIndexed { idx, pt ->
                        val x = idx * stepX
                        val normY = ((pt.netWorth - minVal) / range).toFloat().coerceIn(0f, 1f)
                        val y = size.height - (normY * size.height)
                        if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }

                    drawPath(
                        path = path,
                        color = primaryColor,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )

                    points.forEachIndexed { idx, pt ->
                        val x = idx * stepX
                        val normY = ((pt.netWorth - minVal) / range).toFloat().coerceIn(0f, 1f)
                        val y = size.height - (normY * size.height)
                        drawCircle(
                            color = primaryColor,
                            radius = 5.dp.toPx(),
                            center = Offset(x, y)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    points.forEach { pt ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                pt.monthLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                formatMoney(pt.netWorth),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HoldingPerformanceCard(
    perf: HoldingPerformance,
    formatMoney: (Double) -> String,
    onUpdateValue: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val h = perf.holding
    val isProfit = perf.profitLoss >= 0.0
    val plColor = if (isProfit) Color(0xFF2E7D32) else Color(0xFFC62828)
    val badgeColor = try {
        Color(android.graphics.Color.parseColor(InvestmentUtils.getColorForAssetType(h.assetType)))
    } catch (_: Exception) {
        Color(0xFF1565C0)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("holding_card_${h.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = badgeColor.copy(alpha = 0.14f)
                        ) {
                            Text(
                                h.assetType,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = badgeColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        if (h.units > 0.0 && h.units != 1.0) {
                            Text(
                                "${h.units} units",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        h.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Bought: ${FormatUtils.formatDate(h.buyDate)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        formatMoney(h.currentValue),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    val sign = if (isProfit) "+" else ""
                    Text(
                        "$sign${formatMoney(perf.profitLoss)} ($sign${String.format(Locale.US, "%.1f", perf.simpleReturnPercent)}%)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = plColor
                    )
                    val xirr = perf.annualizedReturnXirrPercent
                    val xSign = if (xirr >= 0.0) "+" else ""
                    Text(
                        "XIRR: $xSign${String.format(Locale.US, "%.1f", xirr)}% p.a.",
                        style = MaterialTheme.typography.labelSmall,
                        color = plColor
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Invested: ${formatMoney(h.investedAmount)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (h.notes.isNotBlank()) {
                    Text(
                        h.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 180.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = onUpdateValue,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Update Manual Value", style = MaterialTheme.typography.labelMedium)
                }

                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Settings, contentDescription = "Edit Holding", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Holding", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SipTrackerCard(
    perf: HoldingPerformance,
    formatMoney: (Double) -> String,
    onRecordSip: () -> Unit,
    onEdit: () -> Unit
) {
    val h = perf.holding
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(h.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "SIP Day: ${h.sipDayOfMonth}th of month • Account: ${h.sipLinkedAccount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "${formatMoney(h.sipMonthlyAmount)}/mo",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Invested: ${formatMoney(h.investedAmount)}", style = MaterialTheme.typography.bodySmall)
                Text("Current: ${formatMoney(h.currentValue)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onRecordSip,
                    modifier = Modifier.testTag("record_sip_btn_${h.id}")
                ) {
                    Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Log SIP Installment")
                }

                TextButton(onClick = onEdit) {
                    Text("Edit SIP")
                }
            }
        }
    }
}

@Composable
private fun FdRdTrackerCard(
    perf: HoldingPerformance,
    formatMoney: (Double) -> String,
    onEdit: () -> Unit
) {
    val h = perf.holding
    val estMaturity = remember(h) {
        val mat = h.maturityDate ?: (h.buyDate + 365L * 24 * 3600 * 1000)
        val durationDays = maxOf(1, ((mat - h.buyDate) / (24L * 3600 * 1000)).toInt())
        val rate = if (h.interestRate > 0.0) h.interestRate else 7.0
        InvestmentUtils.estimateFdMaturity(h.investedAmount, rate, durationDays)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            if (perf.isMaturityAlert) Color(0xFFEF6C00) else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (perf.isMaturityAlert) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFFF3E0)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(15.dp))
                        Text(
                            if ((perf.daysToMaturity ?: 0L) < 0L) "Matured! Review renewal/payout"
                            else "Maturity Alert: Matures in ${perf.daysToMaturity} days",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE65100)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(h.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Principal: ${formatMoney(h.investedAmount)} • Rate: ${h.interestRate}% p.a.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit FD")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Start: ${FormatUtils.formatDate(h.buyDate)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Maturity: ${h.maturityDate?.let { FormatUtils.formatDate(it) } ?: "Not set"}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                "Est. Maturity Value: ${formatMoney(estMaturity)}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2E7D32)
            )
        }
    }
}

@Composable
private fun LiabilityTrackerCard(
    liability: LiabilityItem,
    formatMoney: (Double) -> String,
    onPayEmi: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val paidRatio = if (liability.principalAmount > 0) {
        ((liability.principalAmount - liability.remainingBalance) / liability.principalAmount)
            .toFloat()
            .coerceIn(0f, 1f)
    } else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFFEBEE)
                    ) {
                        Text(
                            liability.type.replace("_", " "),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFC62828),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(liability.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Interest: ${liability.interestRate}% p.a. • EMI: ${formatMoney(liability.emiAmount)}/mo",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        formatMoney(liability.remainingBalance),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFC62828)
                    )
                    Text(
                        "of ${formatMoney(liability.principalAmount)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            LinearProgressIndicator(
                progress = { paidRatio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = Color(0xFF2E7D32),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "${(paidRatio * 100).toInt()}% paid off",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF2E7D32),
                    fontWeight = FontWeight.Bold
                )
                liability.payoffDate?.let {
                    Text(
                        "Est. Payoff: ${FormatUtils.formatDate(it)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = onPayEmi,
                    enabled = liability.remainingBalance > 0.0
                ) {
                    Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Pay EMI / Due")
                }

                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Liability")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Liability", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStateBox(
    title: String,
    subtitle: String,
    buttonText: String,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.TrendingUp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onAction) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(buttonText)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddOrEditHoldingDialog(
    initialHolding: InvestmentHolding?,
    defaultAssetType: String,
    accounts: List<String>,
    onDismiss: () -> Unit,
    onSave: (InvestmentHolding, Boolean, String) -> Unit
) {
    var name by remember { mutableStateOf(initialHolding?.name ?: "") }
    var assetType by remember { mutableStateOf(initialHolding?.assetType ?: defaultAssetType) }
    var unitsText by remember { mutableStateOf((initialHolding?.units ?: 1.0).toString()) }
    var buyPriceText by remember {
        mutableStateOf(
            if (initialHolding != null && initialHolding.units > 0)
                (initialHolding.investedAmount / initialHolding.units).toString()
            else ""
        )
    }
    var investedText by remember { mutableStateOf(initialHolding?.investedAmount?.toString() ?: "") }
    var currentValueText by remember { mutableStateOf(initialHolding?.currentValue?.toString() ?: "") }
    var notes by remember { mutableStateOf(initialHolding?.notes ?: "") }

    // SIP fields
    var sipMonthlyText by remember { mutableStateOf(initialHolding?.sipMonthlyAmount?.toString() ?: "") }
    var sipDayText by remember { mutableStateOf((initialHolding?.sipDayOfMonth ?: 5).toString()) }
    var sipAccount by remember {
        mutableStateOf(initialHolding?.sipLinkedAccount ?: accounts.firstOrNull() ?: "Bank Account")
    }

    // FD/RD fields
    var interestRateText by remember { mutableStateOf(initialHolding?.interestRate?.toString() ?: "7.1") }
    var maturityMonthsText by remember { mutableStateOf("12") }

    var deductFromAccount by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialHolding == null) "Add Investment Holding" else "Edit Holding", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Asset Class", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InvestmentUtils.ASSET_TYPES.forEach { type ->
                        FilterChip(
                            selected = assetType == type,
                            onClick = { assetType = type },
                            label = { Text(type) }
                        )
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Holding Name (e.g., Nifty 50 Index, HDFC FD)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("holding_name_input")
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = unitsText,
                        onValueChange = { unitsText = it },
                        label = { Text("Units / Qty") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = buyPriceText,
                        onValueChange = {
                            buyPriceText = it
                            val u = unitsText.toDoubleOrNull() ?: 1.0
                            val bp = it.toDoubleOrNull()
                            if (bp != null && investedText.isBlank()) {
                                investedText = (u * bp).toString()
                            }
                        },
                        label = { Text("Buy Price (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = investedText,
                        onValueChange = {
                            investedText = it
                            if (currentValueText.isBlank()) currentValueText = it
                        },
                        label = { Text("Total Invested (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("holding_invested_input")
                    )
                    OutlinedTextField(
                        value = currentValueText,
                        onValueChange = { currentValueText = it },
                        label = { Text("Current Value (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("holding_current_value_input")
                    )
                }

                if (assetType == "Mutual Fund/SIP" || assetType == "Stocks") {
                    HorizontalDivider()
                    Text("Optional SIP Configuration", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = sipMonthlyText,
                            onValueChange = { sipMonthlyText = it },
                            label = { Text("Monthly SIP (₹)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = sipDayText,
                            onValueChange = { sipDayText = it },
                            label = { Text("SIP Day (1-28)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (assetType == "FD/RD" || assetType == "PPF/EPF") {
                    HorizontalDivider()
                    Text("FD / RD Interest & Maturity", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = interestRateText,
                            onValueChange = { interestRateText = it },
                            label = { Text("Interest Rate (% p.a.)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = maturityMonthsText,
                            onValueChange = { maturityMonthsText = it },
                            label = { Text("Tenure (Months)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes / Folio / Broker") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (initialHolding == null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = deductFromAccount,
                            onCheckedChange = { deductFromAccount = it }
                        )
                        Text(
                            "Deduct purchase from $sipAccount (as Investment transfer, not expense)",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val inv = investedText.toDoubleOrNull() ?: 0.0
                    val cur = currentValueText.toDoubleOrNull() ?: inv
                    val units = unitsText.toDoubleOrNull() ?: 1.0
                    val now = System.currentTimeMillis()
                    val matMonths = maturityMonthsText.toIntOrNull() ?: 12
                    val matDate = if (assetType == "FD/RD") {
                        initialHolding?.maturityDate ?: (now + matMonths * 30L * 24L * 3600L * 1000L)
                    } else initialHolding?.maturityDate
                    val sipAmt = sipMonthlyText.toDoubleOrNull() ?: 0.0

                    val holding = InvestmentHolding(
                        id = initialHolding?.id ?: 0L,
                        name = name.trim().ifBlank { assetType },
                        assetType = assetType,
                        units = units,
                        investedAmount = inv,
                        currentValue = cur,
                        buyDate = initialHolding?.buyDate ?: now,
                        isSipEnabled = sipAmt > 0.0,
                        sipMonthlyAmount = sipAmt,
                        sipDayOfMonth = sipDayText.toIntOrNull() ?: 5,
                        sipLinkedAccount = sipAccount,
                        interestRate = interestRateText.toDoubleOrNull() ?: 0.0,
                        maturityDate = matDate,
                        notes = notes.trim()
                    )
                    onSave(holding, deductFromAccount, sipAccount)
                },
                enabled = name.isNotBlank() && (investedText.toDoubleOrNull() ?: 0.0) >= 0.0,
                modifier = Modifier.testTag("save_holding_btn")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun UpdateCurrentValueDialog(
    holding: InvestmentHolding,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var valueText by remember { mutableStateOf(holding.currentValue.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update Manual Value", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${holding.name} (${holding.assetType})",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Invested: ${FormatUtils.formatInr(holding.investedAmount)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = valueText,
                    onValueChange = { valueText = it },
                    label = { Text("New Current Market Value (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val v = valueText.toDoubleOrNull() ?: holding.currentValue
                    onConfirm(v)
                }
            ) {
                Text("Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun AddOrEditLiabilityDialog(
    initialLiability: LiabilityItem?,
    accounts: List<String>,
    onDismiss: () -> Unit,
    onSave: (LiabilityItem) -> Unit
) {
    var name by remember { mutableStateOf(initialLiability?.name ?: "") }
    var type by remember { mutableStateOf(initialLiability?.type ?: "LOAN_EMI") }
    var principalText by remember { mutableStateOf(initialLiability?.principalAmount?.toString() ?: "") }
    var rateText by remember { mutableStateOf(initialLiability?.interestRate?.toString() ?: "8.5") }
    var tenureMonthsText by remember { mutableStateOf("60") }
    var emiText by remember { mutableStateOf(initialLiability?.emiAmount?.toString() ?: "") }
    var remainingText by remember { mutableStateOf(initialLiability?.remainingBalance?.toString() ?: "") }
    val linkedAcc = initialLiability?.linkedAccount ?: accounts.firstOrNull() ?: "Bank Account"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialLiability == null) "Add Loan / Liability" else "Edit Liability", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InvestmentUtils.LIABILITY_TYPES.forEach { (code, label) ->
                        FilterChip(
                            selected = type == code,
                            onClick = { type = code },
                            label = { Text(label) }
                        )
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (e.g., Home Loan, Car EMI, Card Due)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = principalText,
                        onValueChange = {
                            principalText = it
                            if (remainingText.isBlank()) remainingText = it
                            val p = it.toDoubleOrNull() ?: 0.0
                            val r = rateText.toDoubleOrNull() ?: 8.5
                            val m = tenureMonthsText.toIntOrNull() ?: 60
                            if (p > 0) {
                                emiText = InvestmentUtils.calculateMonthlyEmi(p, r, m).toInt().toString()
                            }
                        },
                        label = { Text("Total Principal (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = remainingText,
                        onValueChange = { remainingText = it },
                        label = { Text("Remaining Balance (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = rateText,
                        onValueChange = { rateText = it },
                        label = { Text("Interest (% p.a.)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = emiText,
                        onValueChange = { emiText = it },
                        label = { Text("Monthly EMI (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = principalText.toDoubleOrNull() ?: 0.0
                    val rem = remainingText.toDoubleOrNull() ?: p
                    val r = rateText.toDoubleOrNull() ?: 0.0
                    val emi = emiText.toDoubleOrNull() ?: 0.0
                    val monthsLeft = if (emi > 0) (rem / emi).toInt().coerceAtLeast(1) else 12
                    val payoff = System.currentTimeMillis() + monthsLeft * 30L * 24L * 3600L * 1000L

                    onSave(
                        LiabilityItem(
                            id = initialLiability?.id ?: 0L,
                            name = name.trim().ifBlank { "Loan" },
                            type = type,
                            principalAmount = p,
                            interestRate = r,
                            emiAmount = emi,
                            remainingBalance = rem,
                            startDate = initialLiability?.startDate ?: System.currentTimeMillis(),
                            payoffDate = payoff,
                            linkedAccount = linkedAcc
                        )
                    )
                },
                enabled = name.isNotBlank() && (principalText.toDoubleOrNull() ?: 0.0) > 0.0
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun PayEmiDialog(
    liability: LiabilityItem,
    accounts: List<String>,
    onDismiss: () -> Unit,
    onPay: (Double, String) -> Unit
) {
    var amountText by remember {
        mutableStateOf(
            if (liability.emiAmount > 0.0) liability.emiAmount.toString()
            else liability.remainingBalance.toString()
        )
    }
    var selectedAccount by remember { mutableStateOf(liability.linkedAccount.ifBlank { accounts.firstOrNull() ?: "Bank Account" }) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pay EMI / Liability", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${liability.name} • Remaining: ${FormatUtils.formatInr(liability.remainingBalance)}")
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Payment Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Pay From Account", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    accounts.forEach { acc ->
                        FilterChip(
                            selected = selectedAccount == acc,
                            onClick = { selectedAccount = acc },
                            label = { Text(acc) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountText.toDoubleOrNull() ?: 0.0
                    if (amt > 0.0) onPay(amt, selectedAccount)
                }
            ) {
                Text("Record Payment")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

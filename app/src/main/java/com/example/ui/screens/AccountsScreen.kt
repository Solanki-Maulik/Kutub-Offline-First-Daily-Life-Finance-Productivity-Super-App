package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.data.Account
import com.example.data.AccountType
import com.example.utils.AccountUtils
import com.example.utils.FormatUtils
import com.example.viewmodel.FinanceViewModel
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(viewModel: FinanceViewModel, navController: NavController) {
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var accountToAdjust by remember { mutableStateOf<Account?>(null) }
    var accountToTransferFrom by remember { mutableStateOf<Account?>(null) }
    var accountToDelete by remember { mutableStateOf<Account?>(null) }
    var showQuickTransferDialog by remember { mutableStateOf(false) }

    // O(N) single-pass balance snapshot for all accounts and 5,000+ transactions
    val balanceSnapshot = remember(accounts, transactions) {
        AccountUtils.buildAccountBalanceSnapshot(accounts, transactions)
    }
    val totalBalance = balanceSnapshot.totalBalance
    val cashBalance = balanceSnapshot.cashBalance
    val bankBalance = balanceSnapshot.bankBalance
    val upiBalance = FormatsOrUpiAndCard(balanceSnapshot.upiBalance + balanceSnapshot.creditCardBalance)

    // Delete Confirmation Dialog (handles accounts with existing transactions cleanly)
    if (accountToDelete != null) {
        val acc = accountToDelete!!
        val txCount = balanceSnapshot.getTransactionCount(acc.name)
        val otherAccounts = accounts.filterNot { it.name.equals(acc.name, ignoreCase = true) }
        var deleteAssociatedTxs by remember(acc.name) { mutableStateOf(false) }
        val fallbackTarget = otherAccounts.firstOrNull()?.name ?: "Cash in Hand"

        AlertDialog(
            onDismissRequest = { accountToDelete = null },
            icon = {
                Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            },
            title = { Text("Delete Account?", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (otherAccounts.isEmpty()) {
                        Text(
                            "\"${acc.name}\" is your only active account. Add another account before deleting this one."
                        )
                    } else if (txCount > 0) {
                        Text(
                            "\"${acc.name}\" has $txCount recorded transaction(s). How would you like to handle them so your balances stay reconciled?"
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { deleteAssociatedTxs = false }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = !deleteAssociatedTxs,
                                onClick = { deleteAssociatedTxs = false }
                            )
                            Text(
                                "Move $txCount transaction(s) to \"$fallbackTarget\"",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { deleteAssociatedTxs = true }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = deleteAssociatedTxs,
                                onClick = { deleteAssociatedTxs = true }
                            )
                            Text(
                                "Delete account and its $txCount transaction(s)",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else {
                        Text("Are you sure you want to delete \"${acc.name}\"?")
                    }
                }
            },
            confirmButton = {
                if (otherAccounts.isNotEmpty()) {
                    Button(
                        onClick = {
                            viewModel.deleteAccount(
                                name = acc.name,
                                reassignToAccount = fallbackTarget,
                                deleteAssociatedTransactions = deleteAssociatedTxs
                            )
                            accountToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.testTag("confirm_delete_account_button")
                    ) {
                        Text("Delete")
                    }
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { accountToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add Account Dialog
    if (showAddDialog) {
        AddAccountDialog(
            existingAccounts = accounts,
            onDismiss = { showAddDialog = false },
            onAdd = { name, type, openingBalance, colorHex, iconName ->
                viewModel.addAccount(
                    name = name,
                    type = type.id,
                    openingBalance = openingBalance,
                    colorHex = colorHex,
                    iconName = iconName
                )
                showAddDialog = false
            }
        )
    }

    // Balance Adjustment Dialog
    if (accountToAdjust != null) {
        val targetAccount = accountToAdjust!!
        val currentBalance = AccountUtils.calculateAccountBalance(targetAccount, transactions)
        BalanceAdjustmentDialog(
            account = targetAccount,
            currentBalance = currentBalance,
            onDismiss = { accountToAdjust = null },
            onApply = { actualBalance, note ->
                viewModel.adjustAccountBalance(
                    accountName = targetAccount.name,
                    targetBalance = actualBalance,
                    note = note
                )
                accountToAdjust = null
            }
        )
    }

    // Transfer Dialog
    if (showQuickTransferDialog || accountToTransferFrom != null) {
        val initialFrom = accountToTransferFrom?.name ?: accounts.firstOrNull()?.name ?: ""
        TransferDialog(
            accounts = accounts,
            transactions = transactions,
            initialFromAccount = initialFrom,
            onDismiss = {
                showQuickTransferDialog = false
                accountToTransferFrom = null
            },
            onTransfer = { amount, from, to ->
                viewModel.addTransfer(
                    amount = amount,
                    fromAccount = from,
                    toAccount = to,
                    timestamp = System.currentTimeMillis()
                )
                showQuickTransferDialog = false
                accountToTransferFrom = null
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text("Accounts & Wallets", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }, modifier = Modifier.testTag("back_button")) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showQuickTransferDialog = true },
                        modifier = Modifier.testTag("transfer_top_button")
                    ) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = "Transfer")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add Account", fontWeight = FontWeight.Bold) },
                modifier = Modifier.testTag("add_account_fab")
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Dashboard Summary Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Total Net Worth",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    "${accounts.size} ACCOUNTS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            FormatUtils.formatInr(totalBalance),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.height(16.dp))

                        // Quick summary breakdown
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Cash
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Payments,
                                            contentDescription = null,
                                            tint = Color(0xFF2E7D32),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            "Cash",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        FormatUtils.formatInr(cashBalance),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Bank
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.AccountBalance,
                                            contentDescription = null,
                                            tint = Color(0xFF1565C0),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            "Bank",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        FormatUtils.formatInr(bankBalance),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // UPI / Others
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.QrCode,
                                            contentDescription = null,
                                            tint = Color(0xFF7B1FA2),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            "UPI",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        FormatUtils.formatInr(upiBalance),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        Button(
                            onClick = { showQuickTransferDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("quick_transfer_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                contentColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Transfer Between Accounts", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Your Accounts",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${accounts.size} Total",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (accounts.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "No accounts found.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = { showAddDialog = true }) {
                                Text("+ Add your first account")
                            }
                        }
                    }
                }
            } else {
                items(accounts, key = { it.name }) { account ->
                    val accType = AccountType.fromId(account.type)
                    val computedBalance = balanceSnapshot.getBalance(account.name)
                    val accTxCount = balanceSnapshot.getTransactionCount(account.name)
                    val accountColor = AccountUtils.parseColor(account.colorHex)
                    val accountIcon = AccountUtils.getIconForName(account.iconName, accType)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("account_card_${account.name}"),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Top Row: Icon, Name, Type, Balance
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(accountColor.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        accountIcon,
                                        contentDescription = null,
                                        tint = accountColor,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }

                                Spacer(Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        account.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Text(
                                                accType.displayName,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "$accTxCount txns",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        FormatUtils.formatInr(computedBalance),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = if (computedBalance < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    )
                                    if (account.balance > 0.0) {
                                        Text(
                                            "Opening: ${FormatUtils.formatInr(account.balance)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(14.dp))
                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Spacer(Modifier.height(8.dp))

                            // Action Row: Adjust Balance, Transfer, Delete
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    // Adjust Balance Button
                                    OutlinedButton(
                                        onClick = { accountToAdjust = account },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.testTag("adjust_balance_${account.name}")
                                    ) {
                                        Icon(
                                            Icons.Default.Tune,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text("Adjust", style = MaterialTheme.typography.labelMedium)
                                    }

                                    // Transfer Button
                                    OutlinedButton(
                                        onClick = { accountToTransferFrom = account },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.testTag("transfer_from_${account.name}")
                                    ) {
                                        Icon(
                                            Icons.Default.SwapHoriz,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text("Transfer", style = MaterialTheme.typography.labelMedium)
                                    }
                                }

                                IconButton(
                                    onClick = { accountToDelete = account },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("delete_account_${account.name}")
                                ) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "Delete account ${account.name}",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

private fun FormatsOrUpiAndCard(value: Double): Double = FormatUtils.round2(value)

@Composable
fun AddAccountDialog(
    existingAccounts: List<Account>,
    onDismiss: () -> Unit,
    onAdd: (name: String, type: AccountType, openingBalance: Double, colorHex: String, iconName: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(AccountType.BANK) }
    var openingBalanceStr by remember { mutableStateOf("") }
    var selectedColorHex by remember { mutableStateOf(AccountUtils.availableColors[1].first) }
    var selectedIconName by remember { mutableStateOf(AccountUtils.availableIcons[0].first) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var balanceError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Account", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Account Name
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = null
                    },
                    label = { Text("Account Name") },
                    placeholder = { Text("e.g., HDFC Salary, SBI Bank, Cash, GPay") },
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_name_input")
                )

                // Account Type Selector
                Column {
                    Text(
                        "ACCOUNT TYPE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AccountType.entries.forEach { type ->
                            FilterChip(
                                selected = selectedType == type,
                                onClick = {
                                    selectedType = type
                                    // Set default icon and color according to type
                                    when (type) {
                                        AccountType.CASH -> {
                                            selectedColorHex = "#2E7D32"
                                            selectedIconName = "Payments"
                                        }
                                        AccountType.BANK -> {
                                            selectedColorHex = "#1565C0"
                                            selectedIconName = "AccountBalance"
                                        }
                                        AccountType.UPI -> {
                                            selectedColorHex = "#7B1FA2"
                                            selectedIconName = "QrCode"
                                        }
                                        AccountType.CREDIT_CARD -> {
                                            selectedColorHex = "#E65100"
                                            selectedIconName = "CreditCard"
                                        }
                                    }
                                },
                                label = { Text(type.displayName) },
                                leadingIcon = {
                                    Icon(
                                        AccountUtils.getIconForName(selectedIconName, type),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                modifier = Modifier.testTag("chip_type_${type.id}")
                            )
                        }
                    }
                }

                // Opening Balance
                OutlinedTextField(
                    value = openingBalanceStr,
                    onValueChange = { input ->
                        if (input.isEmpty() || input.matches(Regex("""^\d*(\.\d{0,2})?$"""))) {
                            openingBalanceStr = input
                            balanceError = null
                        }
                    },
                    label = { Text("Opening Balance (Optional)") },
                    placeholder = { Text("0") },
                    prefix = { Text("₹ ") },
                    isError = balanceError != null,
                    supportingText = balanceError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_opening_balance_input")
                )

                // Color Selection
                Column {
                    Text(
                        "COLOR THEME",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AccountUtils.availableColors.forEach { (hex, _) ->
                            val color = AccountUtils.parseColor(hex)
                            val isSelected = selectedColorHex.equals(hex, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .clickable { selectedColorHex = hex }
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Icon Selection
                Column {
                    Text(
                        "ACCOUNT ICON",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AccountUtils.availableIcons.forEach { (iconId, iconVector) ->
                            val isSelected = selectedIconName == iconId
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable { selectedIconName = iconId }
                                    .border(
                                        width = if (isSelected) 2.dp else 0.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    iconVector,
                                    contentDescription = iconId,
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedName = name.trim()
                    if (trimmedName.isBlank()) {
                        nameError = "Account name is required"
                        return@Button
                    }
                    if (existingAccounts.any { it.name.equals(trimmedName, ignoreCase = true) }) {
                        nameError = "An account with this name already exists"
                        return@Button
                    }
                    val opening = openingBalanceStr.toDoubleOrNull() ?: 0.0
                    if (opening < 0.0) {
                        balanceError = "Opening balance cannot be negative"
                        return@Button
                    }
                    onAdd(trimmedName, selectedType, opening, selectedColorHex, selectedIconName)
                },
                modifier = Modifier.testTag("save_account_button")
            ) {
                Text("Add Account")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun BalanceAdjustmentDialog(
    account: Account,
    currentBalance: Double,
    onDismiss: () -> Unit,
    onApply: (actualBalance: Double, note: String) -> Unit
) {
    var actualBalanceStr by remember {
        mutableStateOf(if (currentBalance % 1.0 == 0.0) currentBalance.toLong().toString() else currentBalance.toString())
    }
    var note by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }

    val actual = actualBalanceStr.toDoubleOrNull()
    val diff = if (actual != null) actual - currentBalance else null

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        title = { Text("Balance Adjustment", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "Fix mismatches between your recorded balance and actual bank/cash balance for \"${account.name}\". An adjustment entry will be recorded with your reason.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Current Balance Banner
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Current App Balance:", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            FormatUtils.formatInr(currentBalance),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Actual / Real Balance Input
                OutlinedTextField(
                    value = actualBalanceStr,
                    onValueChange = { input ->
                        if (input.isEmpty() || input.matches(Regex("""^-?\d*(\.\d{0,2})?$"""))) {
                            actualBalanceStr = input
                            inputError = null
                        }
                    },
                    label = { Text("Actual / Real Balance") },
                    prefix = { Text("₹ ") },
                    isError = inputError != null,
                    supportingText = inputError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("actual_balance_input")
                )

                // Dynamic Difference Display
                if (diff != null && abs(diff) >= 0.01) {
                    val isPositive = diff > 0
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isPositive) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (isPositive) Icons.Default.AddCircle else Icons.Default.RemoveCircle,
                                contentDescription = null,
                                tint = if (isPositive) Color(0xFF2E7D32) else Color(0xFFC62828),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (isPositive)
                                    "+${FormatUtils.formatInr(diff)} will be credited as adjustment"
                                else
                                    "-${FormatUtils.formatInr(abs(diff))} will be debited as adjustment",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isPositive) Color(0xFF2E7D32) else Color(0xFFC62828)
                            )
                        }
                    }
                } else if (diff != null) {
                    Text(
                        "Actual balance matches current balance (No change).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Reason / Note Input
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Reason / Note (Optional)") },
                    placeholder = { Text("e.g. ATM withdrawal unrecorded, Bank fee, Interest") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("adjustment_note_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (actual == null) {
                        inputError = "Please enter a valid balance"
                        return@Button
                    }
                    onApply(actual, note.trim())
                },
                modifier = Modifier.testTag("confirm_adjust_button")
            ) {
                Text("Apply Adjustment")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun TransferDialog(
    accounts: List<Account>,
    transactions: List<com.example.data.Transaction>,
    initialFromAccount: String,
    onDismiss: () -> Unit,
    onTransfer: (amount: Double, from: String, to: String) -> Unit
) {
    var fromAccount by remember {
        mutableStateOf(
            if (accounts.any { it.name == initialFromAccount }) initialFromAccount
            else accounts.firstOrNull()?.name ?: ""
        )
    }
    var toAccount by remember {
        mutableStateOf(
            accounts.firstOrNull { it.name != fromAccount }?.name ?: ""
        )
    }
    var amountStr by remember { mutableStateOf("") }
    var amountError by remember { mutableStateOf<String?>(null) }
    var transferError by remember { mutableStateOf<String?>(null) }
    var showNegativeOverrideWarning by remember { mutableStateOf(false) }

    val srcAccountObj = accounts.firstOrNull { it.name == fromAccount }
    val srcBalance = if (srcAccountObj != null) AccountUtils.calculateAccountBalance(srcAccountObj, transactions) else 0.0
    val parsedAmount = amountStr.toDoubleOrNull() ?: 0.0

    if (showNegativeOverrideWarning) {
        AlertDialog(
            onDismissRequest = { showNegativeOverrideWarning = false },
            icon = {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            },
            title = { Text("Negative Balance Warning", fontWeight = FontWeight.Bold) },
            text = {
                val projected = srcBalance - parsedAmount
                Text(
                    "Transferring ${FormatUtils.formatInr(parsedAmount)} from \"$fromAccount\" will make its balance negative.\n\n" +
                    "Current Balance: ${FormatUtils.formatInr(srcBalance)}\n" +
                    "Projected Balance: -${FormatUtils.formatInr(abs(projected))}\n\n" +
                    "Do you want to proceed anyway?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showNegativeOverrideWarning = false
                        onTransfer(parsedAmount, fromAccount, toAccount)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Proceed Anyway")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showNegativeOverrideWarning = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Transfer Between Accounts", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "Move money between your accounts (e.g., ATM cash withdrawal, bank deposit). Transfers do not count as income or expense in reports.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // From Account
                Column {
                    Text("FROM ACCOUNT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        accounts.forEach { acc ->
                            FilterChip(
                                selected = fromAccount == acc.name,
                                onClick = {
                                    fromAccount = acc.name
                                    if (toAccount == acc.name) {
                                        toAccount = accounts.firstOrNull { it.name != acc.name }?.name ?: ""
                                    }
                                },
                                label = { Text(acc.name) }
                            )
                        }
                    }
                    Text(
                        "Available: ${FormatUtils.formatInr(srcBalance)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                    )
                }

                // To Account
                Column {
                    Text("TO ACCOUNT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        accounts.filter { it.name != fromAccount }.forEach { acc ->
                            FilterChip(
                                selected = toAccount == acc.name,
                                onClick = { toAccount = acc.name },
                                label = { Text(acc.name) }
                            )
                        }
                    }
                }

                if (transferError != null) {
                    Text(
                        transferError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // Amount
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { input ->
                        if (input.isEmpty() || input.matches(Regex("""^\d*(\.\d{0,2})?$"""))) {
                            amountStr = input
                            amountError = null
                        }
                    },
                    label = { Text("Transfer Amount") },
                    prefix = { Text("₹ ") },
                    isError = amountError != null,
                    supportingText = amountError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("transfer_amount_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountStr.toDoubleOrNull()
                    if (amount == null || amount <= 0.0) {
                        amountError = "Amount must be greater than ₹0"
                        return@Button
                    }
                    if (toAccount.isBlank() || fromAccount == toAccount) {
                        transferError = "Please select distinct From and To accounts"
                        return@Button
                    }

                    val srcType = AccountType.fromId(srcAccountObj?.type)
                    if ((srcType == AccountType.CASH || srcType == AccountType.BANK) && (srcBalance - amount < 0.0)) {
                        showNegativeOverrideWarning = true
                        return@Button
                    }

                    onTransfer(amount, fromAccount, toAccount)
                },
                modifier = Modifier.testTag("confirm_transfer_button")
            ) {
                Text("Transfer")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

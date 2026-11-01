package com.example.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.Transaction
import com.example.ui.components.CategoryManagementDialog
import com.example.utils.CategoryUtils
import com.example.utils.FormatUtils
import com.example.viewmodel.FinanceViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

enum class TransactionType { EXPENSE, INCOME, TRANSFER }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    viewModel: FinanceViewModel,
    transactionId: Int,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val defaultAccountPref by viewModel.defaultAccount.collectAsStateWithLifecycle()

    var transactionType by remember { mutableStateOf(TransactionType.EXPENSE) }
    var amount by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Food") }
    var subcategory by remember { mutableStateOf("") }
    var selectedAccount by remember { mutableStateOf("") }
    var toAccount by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf("UPI") }
    var note by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf<String?>(null) }
    var selectedTimestamp by remember { mutableStateOf(System.currentTimeMillis()) }

    // Dialog & UI states
    var showCategoryManager by remember { mutableStateOf(false) }
    var showPhotoPreview by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showNegativeWarning by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var existingTx by remember { mutableStateOf<Transaction?>(null) }
    var isLoadingExisting by remember { mutableStateOf(transactionId != -1) }

    // Validation error states
    var amountError by remember { mutableStateOf<String?>(null) }
    var categoryError by remember { mutableStateOf<String?>(null) }
    var accountError by remember { mutableStateOf<String?>(null) }
    var transferError by remember { mutableStateOf<String?>(null) }

    // Negative balance calculation helpers
    var pendingSaveAmount by remember { mutableStateOf(0.0) }
    var projectedNegativeBalance by remember { mutableStateOf(0.0) }
    var currentBalanceBeforeExpense by remember { mutableStateOf(0.0) }

    // Zero-permission Android Photo Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            photoUri = uri.toString()
        }
    }

    LaunchedEffect(accounts, defaultAccountPref) {
        if (accounts.isNotEmpty() && transactionId == -1) {
            if (selectedAccount.isEmpty() || accounts.none { it.name == selectedAccount }) {
                val preferred = accounts.find { it.name.equals(defaultAccountPref, ignoreCase = true) }
                selectedAccount = preferred?.name ?: accounts.first().name
            }
            if (toAccount.isEmpty() || accounts.none { it.name == toAccount }) {
                toAccount = accounts.firstOrNull { it.name != selectedAccount }?.name ?: accounts.first().name
            }
        }
    }

    LaunchedEffect(transactionId) {
        if (transactionId != -1) {
            val tx = viewModel.getTransactionById(transactionId)
            if (tx != null) {
                existingTx = tx
                amount = if (tx.amount % 1.0 == 0.0) tx.amount.toLong().toString() else tx.amount.toString()
                selectedCategory = tx.category
                subcategory = tx.subcategory
                transactionType = when {
                    tx.category == "Transfer" -> TransactionType.TRANSFER
                    tx.isIncome -> TransactionType.INCOME
                    else -> TransactionType.EXPENSE
                }
                selectedAccount = tx.account
                paymentMethod = if (tx.paymentMethod.isNotBlank()) tx.paymentMethod else "UPI"
                note = tx.note
                tags = tx.tags
                photoUri = tx.photoUri
                selectedTimestamp = tx.timestamp
            }
            isLoadingExisting = false
        }
    }

    fun formattedDateTime(): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy • hh:mm a", Locale.getDefault())
        return sdf.format(selectedTimestamp)
    }

    fun showDateTimePicker() {
        val cal = Calendar.getInstance().apply { timeInMillis = selectedTimestamp }
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                TimePickerDialog(
                    context,
                    { _, hourOfDay, minute ->
                        val newCal = Calendar.getInstance().apply {
                            set(Calendar.YEAR, year)
                            set(Calendar.MONTH, month)
                            set(Calendar.DAY_OF_MONTH, dayOfMonth)
                            set(Calendar.HOUR_OF_DAY, hourOfDay)
                            set(Calendar.MINUTE, minute)
                        }
                        selectedTimestamp = newCal.timeInMillis
                    },
                    cal.get(Calendar.HOUR_OF_DAY),
                    cal.get(Calendar.MINUTE),
                    false
                ).show()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    fun executeSave() {
        if (transactionType == TransactionType.TRANSFER) {
            viewModel.addTransfer(
                amount = pendingSaveAmount,
                fromAccount = selectedAccount,
                toAccount = toAccount,
                timestamp = selectedTimestamp
            )
        } else {
            val isIncome = transactionType == TransactionType.INCOME
            if (transactionId != -1) {
                viewModel.updateTransaction(
                    id = transactionId,
                    amount = pendingSaveAmount,
                    isIncome = isIncome,
                    category = selectedCategory,
                    subcategory = subcategory.trim(),
                    account = selectedAccount,
                    paymentMethod = paymentMethod,
                    note = note.trim(),
                    tags = tags.trim(),
                    photoUri = photoUri,
                    timestamp = selectedTimestamp
                )
            } else {
                viewModel.addTransaction(
                    amount = pendingSaveAmount,
                    isIncome = isIncome,
                    category = selectedCategory,
                    subcategory = subcategory.trim(),
                    account = selectedAccount,
                    paymentMethod = paymentMethod,
                    note = note.trim(),
                    tags = tags.trim(),
                    photoUri = photoUri,
                    timestamp = selectedTimestamp
                )
            }
        }
        onBack()
    }

    fun validateAndProceed() {
        if (isSubmitting) return

        var hasError = false
        val parsedAmount = amount.toDoubleOrNull()
        if (parsedAmount == null || parsedAmount <= 0.0) {
            amountError = "Amount must be greater than ₹0"
            hasError = true
        } else {
            amountError = null
        }

        if (transactionType != TransactionType.TRANSFER && selectedCategory.isBlank()) {
            categoryError = "Category is required"
            hasError = true
        } else {
            categoryError = null
        }

        if (selectedAccount.isBlank()) {
            accountError = "Please select an account"
            hasError = true
        } else {
            accountError = null
        }

        if (transactionType == TransactionType.TRANSFER) {
            if (toAccount.isBlank()) {
                transferError = "Please select a destination account"
                hasError = true
            } else if (toAccount.equals(selectedAccount, ignoreCase = true)) {
                transferError = "Source and destination accounts must be different"
                hasError = true
            } else {
                transferError = null
            }
        }

        if (hasError || parsedAmount == null) return

        pendingSaveAmount = parsedAmount

        if (transactionType == TransactionType.EXPENSE || transactionType == TransactionType.TRANSFER) {
            val srcAcc = accounts.firstOrNull { it.name.equals(selectedAccount, ignoreCase = true) }
            val currentBal = if (srcAcc != null) {
                com.example.utils.AccountUtils.calculateAccountBalance(srcAcc, transactions)
            } else 0.0
            val existingAmount = if (transactionId != -1 && existingTx != null && existingTx!!.account == selectedAccount && !existingTx!!.isIncome) {
                existingTx!!.amount
            } else 0.0

            val effectiveBal = currentBal + existingAmount
            val projected = effectiveBal - parsedAmount
            if (projected < 0.0) {
                currentBalanceBeforeExpense = currentBal
                projectedNegativeBalance = projected
                showNegativeWarning = true
                return
            }
        }

        isSubmitting = true
        executeSave()
    }

    // Category Management Dialog
    if (showCategoryManager) {
        CategoryManagementDialog(
            viewModel = viewModel,
            onDismiss = { showCategoryManager = false },
            onCategorySelected = { cat ->
                selectedCategory = cat.name
            }
        )
    }

    // Bill Photo Full Preview Dialog
    if (showPhotoPreview && photoUri != null) {
        Dialog(onDismissRequest = { showPhotoPreview = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Bill Receipt Photo", fontWeight = FontWeight.Bold)
                        IconButton(onClick = { showPhotoPreview = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    AsyncImage(
                        model = photoUri,
                        contentDescription = "Bill Photo",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            photoUri = null
                            showPhotoPreview = false
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Remove Photo")
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Transaction", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete this transaction? All related balances will update automatically.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        viewModel.deleteTransaction(transactionId)
                        onBack()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Negative Balance Warning Dialog with override
    if (showNegativeWarning) {
        AlertDialog(
            onDismissRequest = {
                showNegativeWarning = false
                isSubmitting = false
            },
            icon = {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            },
            title = { Text("Negative Balance Warning", fontWeight = FontWeight.Bold) },
            text = {
                val projectedAbs = kotlin.math.abs(projectedNegativeBalance)
                Text(
                    "This ${if (transactionType == TransactionType.TRANSFER) "transfer" else "expense"} of " +
                    "${FormatUtils.formatInr(pendingSaveAmount)} will make \"$selectedAccount\" balance negative.\n\n" +
                    "Current Balance: ${FormatUtils.formatInr(currentBalanceBeforeExpense)}\n" +
                    "Projected Balance: -${FormatUtils.formatInr(projectedAbs)}\n\n" +
                    "Do you want to proceed anyway?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showNegativeWarning = false
                        isSubmitting = true
                        executeSave()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Proceed Anyway")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showNegativeWarning = false
                        isSubmitting = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {
                    Text(
                        if (transactionId != -1) "Edit Transaction" else "Add Transaction",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("close_expense_button")) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    if (transactionId != -1) {
                        IconButton(
                            onClick = {
                                viewModel.duplicateTransaction(transactionId) {
                                    onBack()
                                }
                            },
                            modifier = Modifier.testTag("duplicate_transaction_button")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate Transaction")
                        }
                        IconButton(
                            onClick = { showDeleteConfirmDialog = true },
                            modifier = Modifier.testTag("delete_transaction_button")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    // Always-visible Top-Right Checkmark ("Right Sign") Save Button
                    Button(
                        onClick = { validateAndProceed() },
                        enabled = !isSubmitting,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .height(40.dp)
                            .testTag("top_save_expense_check_button")
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Save Transaction",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 6.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier
                            .height(48.dp)
                            .weight(0.35f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Cancel", fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                    Button(
                        onClick = { validateAndProceed() },
                        enabled = !isSubmitting,
                        modifier = Modifier
                            .height(48.dp)
                            .weight(0.65f)
                            .testTag("sticky_save_expense_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (transactionId != -1) "Save Changes" else "Save Transaction",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        if (isLoadingExisting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            var showAiSmartEntryDialog by remember { mutableStateOf(false) }
            var showAiReceiptScanDialog by remember { mutableStateOf(false) }

            if (showAiSmartEntryDialog) {
                Dialog(onDismissRequest = { showAiSmartEntryDialog = false }) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.background,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 640.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("AI Smart Entry", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                IconButton(onClick = { showAiSmartEntryDialog = false }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close")
                                }
                            }
                            SmartEntryHelperSection(
                                viewModel = viewModel,
                                onSavedCallback = { count ->
                                    if (count > 0) {
                                        showAiSmartEntryDialog = false
                                        onBack()
                                    }
                                }
                            )
                        }
                    }
                }
            }

            if (showAiReceiptScanDialog) {
                Dialog(onDismissRequest = { showAiReceiptScanDialog = false }) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.background,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 640.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("AI Receipt & Bill Scanner", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                IconButton(onClick = { showAiReceiptScanDialog = false }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close")
                                }
                            }
                            ReceiptScanHelperSection(
                                viewModel = viewModel,
                                onPrefillExpense = { scanned, scannedUri ->
                                    transactionType = TransactionType.EXPENSE
                                    if (scanned.amount > 0.0) {
                                        amount = if (scanned.amount % 1.0 == 0.0) scanned.amount.toLong().toString() else scanned.amount.toString()
                                    }
                                    selectedCategory = scanned.category.ifBlank { "Shopping" }
                                    paymentMethod = scanned.paymentMethod.ifBlank { "UPI" }
                                    note = buildString {
                                        append(scanned.shopName)
                                        if (scanned.itemsSummary.isNotBlank()) {
                                            append(" - ")
                                            append(scanned.itemsSummary)
                                        }
                                    }
                                    selectedTimestamp = scanned.timestamp
                                    if (scannedUri != null) {
                                        photoUri = scannedUri
                                    }
                                    showAiReceiptScanDialog = false
                                }
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Contextual AI Helpers Bar (Smart Entry & Receipt Scan)
                if (transactionId == -1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { showAiSmartEntryDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("add_expense_ai_smart_entry_btn"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Smart Entry", fontWeight = FontWeight.Bold)
                        }
                        FilledTonalButton(
                            onClick = { showAiReceiptScanDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("add_expense_ai_receipt_scan_btn"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.DocumentScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Scan Receipt", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Type Selector (Expense / Income / Transfer)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(24.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf(
                        TransactionType.EXPENSE to "Expense",
                        TransactionType.INCOME to "Income",
                        TransactionType.TRANSFER to "Transfer"
                    ).forEach { (type, label) ->
                        val isSelected = transactionType == type
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent)
                                .clickable {
                                    transactionType = type
                                    transferError = null
                                    if (type == TransactionType.INCOME && selectedCategory == "Food") {
                                        selectedCategory = "Salary"
                                    } else if (type == TransactionType.EXPENSE && selectedCategory == "Salary") {
                                        selectedCategory = "Food"
                                    }
                                }
                                .padding(vertical = 12.dp)
                                .testTag("tab_${label.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }

                // Amount Section
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "ENTER AMOUNT",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "₹",
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = amount,
                            onValueChange = { input ->
                                if (input.isEmpty() || input.matches(Regex("""^\d*(\.\d{0,2})?$"""))) {
                                    amount = input
                                    amountError = null
                                }
                            },
                            textStyle = MaterialTheme.typography.headlineLarge.copy(
                                textAlign = TextAlign.Start,
                                fontWeight = FontWeight.Bold
                            ),
                            placeholder = { Text("0", style = MaterialTheme.typography.headlineLarge) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            isError = amountError != null,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .testTag("amount_input")
                        )
                    }
                    if (amountError != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = amountError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // Date & Time Picker Card
                OutlinedCard(
                    onClick = { showDateTimePicker() },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("date_picker_button")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Date & Time", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    formattedDateTime(),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Text(
                            "Change",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Category Selection & Management (for Expense & Income)
                if (transactionType != TransactionType.TRANSFER) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "CATEGORY",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(
                                onClick = { showCategoryManager = true },
                                modifier = Modifier.testTag("manage_categories_button")
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Manage / Add", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        val activeCategories = categories.filter { it.isIncome == (transactionType == TransactionType.INCOME) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            activeCategories.forEach { cat ->
                                val isSelected = selectedCategory == cat.name
                                val catColor = CategoryUtils.parseColor(cat.colorHex)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedCategory = cat.name
                                        categoryError = null
                                    },
                                    leadingIcon = {
                                        Icon(
                                            CategoryUtils.getIcon(cat.iconName),
                                            contentDescription = null,
                                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else catColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    label = { Text(cat.name) },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.testTag("cat_chip_${cat.name}")
                                )
                            }
                        }

                        if (categoryError != null) {
                            Text(
                                text = categoryError!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        // Subcategory Input
                        OutlinedTextField(
                            value = subcategory,
                            onValueChange = { subcategory = it },
                            label = { Text("Subcategory (e.g. Dinner, Fuel, Groceries, WiFi)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("subcategory_input"),
                            shape = RoundedCornerShape(14.dp)
                        )
                    }
                }

                // Account Selection
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (transactionType == TransactionType.TRANSFER) "FROM ACCOUNT" else "ACCOUNT",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        accounts.forEach { acc ->
                            val isSelected = selectedAccount == acc.name
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedAccount = acc.name
                                    accountError = null
                                },
                                label = { Text(acc.name) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("acc_chip_${acc.name}")
                            )
                        }
                    }
                    if (accountError != null) {
                        Text(accountError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }

                // Transfer Destination Account
                if (transactionType == TransactionType.TRANSFER) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "TO ACCOUNT",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            accounts.forEach { acc ->
                                val isSelected = toAccount == acc.name
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        toAccount = acc.name
                                        transferError = null
                                    },
                                    label = { Text(acc.name) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("to_acc_chip_${acc.name}")
                                )
                            }
                        }
                        if (transferError != null) {
                            Text(transferError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                // Payment Method Selector
                if (transactionType != TransactionType.TRANSFER) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "PAYMENT METHOD",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CategoryUtils.paymentMethods.forEach { method ->
                                FilterChip(
                                    selected = paymentMethod == method,
                                    onClick = { paymentMethod = method },
                                    label = { Text(method) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("pm_chip_$method")
                                )
                            }
                        }
                    }
                }

                // Note Input
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note / Description (Optional)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("note_input"),
                    shape = RoundedCornerShape(14.dp),
                    minLines = 2
                )

                // Tags Input
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = tags,
                        onValueChange = { tags = it },
                        label = { Text("Tags (comma separated, e.g. Personal, Trip, Tax)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("tags_input"),
                        shape = RoundedCornerShape(14.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Personal", "Office", "Tax", "Trip", "Shared").forEach { tag ->
                            SuggestionChip(
                                onClick = {
                                    val current = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                    if (tag !in current) {
                                        tags = if (tags.isBlank()) tag else "$tags, $tag"
                                    }
                                },
                                label = { Text("#$tag") },
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                }

                // Bill Photo Section (Zero-permission Android Photo Picker)
                OutlinedCard(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Receipt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text("Bill / Receipt Photo", fontWeight = FontWeight.SemiBold)
                            }
                            Button(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("pick_bill_photo_button")
                            ) {
                                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (photoUri == null) "Attach Bill" else "Change")
                            }
                        }

                        if (photoUri != null) {
                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showPhotoPreview = true },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                AsyncImage(
                                    model = photoUri,
                                    contentDescription = "Bill Preview",
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Bill photo attached", fontWeight = FontWeight.Medium)
                                    Text("Tap to view full receipt", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick = { photoUri = null }) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove photo", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Primary Save Button
                Button(
                    onClick = { validateAndProceed() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("save_expense_button"),
                    enabled = !isSubmitting,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            if (transactionId != -1) "Save Changes" else "Save Transaction",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

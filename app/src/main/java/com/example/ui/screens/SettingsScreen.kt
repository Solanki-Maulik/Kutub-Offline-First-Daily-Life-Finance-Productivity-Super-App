package com.example.ui.screens

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.R
import com.example.data.BackupValidationResult
import com.example.ui.BackupHelper
import com.example.ui.BiometricHelper
import com.example.ui.components.CategoryManagementDialog
import com.example.utils.DocumentVaultUtils
import com.example.utils.EcosystemHubUtils
import com.example.utils.FormatUtils
import com.example.utils.authStateFlow
import com.example.viewmodel.FinanceViewModel
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val TAG = "SettingsCloudSync"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController, viewModel: FinanceViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // App Lock & Privacy states
    val privacyMode by viewModel.privacyMode.collectAsStateWithLifecycle()
    val appLockEnabled by viewModel.appLockEnabled.collectAsStateWithLifecycle()
    val appPin by viewModel.appPin.collectAsStateWithLifecycle()
    val biometricUnlockEnabled by viewModel.biometricUnlockEnabled.collectAsStateWithLifecycle()
    val autoLockTimeoutMinutes by viewModel.autoLockTimeoutMinutes.collectAsStateWithLifecycle()

    // App Settings states
    val enabledModules by viewModel.enabledModules.collectAsStateWithLifecycle()
    val currencyCode by viewModel.currencyCode.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val defaultAccount by viewModel.defaultAccount.collectAsStateWithLifecycle()
    val weekStartDay by viewModel.weekStartDay.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()

    // Notification preferences
    val notifDailyExpense by viewModel.notifDailyExpense.collectAsStateWithLifecycle()
    val notifBillReminders by viewModel.notifBillReminders.collectAsStateWithLifecycle()
    val notifBudgetAlerts by viewModel.notifBudgetAlerts.collectAsStateWithLifecycle()
    val notifTaskHabitReminders by viewModel.notifTaskHabitReminders.collectAsStateWithLifecycle()
    val notifVaultExpiry by viewModel.notifVaultExpiry.collectAsStateWithLifecycle()
    val weeklyBackupReminderEnabled by viewModel.weeklyBackupReminderEnabled.collectAsStateWithLifecycle()

    // Backup & Cloud Sync states
    val vaultStorageSummary by viewModel.vaultStorageSummary.collectAsStateWithLifecycle()
    val lastBackupTimestamp by viewModel.lastBackupTimestamp.collectAsStateWithLifecycle()
    val isWeeklyBackupDue by viewModel.isWeeklyBackupDue.collectAsStateWithLifecycle()
    val lastCloudSyncTimestamp by viewModel.lastCloudSyncTimestamp.collectAsStateWithLifecycle()
    val autoCloudSyncEnabled by viewModel.autoCloudSyncEnabled.collectAsStateWithLifecycle()
    val isCloudSyncing by viewModel.isCloudSyncing.collectAsStateWithLifecycle()
    val cloudSyncStatusMessage by viewModel.cloudSyncStatusMessage.collectAsStateWithLifecycle()

    // Firebase Auth state via authStateFlow
    val auth = remember { FirebaseAuth.getInstance() }
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val credentialManager = remember { CredentialManager.create(context) }
    var isAuthLoading by remember { mutableStateOf(false) }

    // Dialog states
    var showCategoryManager by remember { mutableStateOf(false) }
    var showGlobalSearch by remember { mutableStateOf(false) }
    var showConfigurePinDialog by remember { mutableStateOf(false) }
    var enableLockOnPinSave by remember { mutableStateOf(false) }
    var showAutoLockMenu by remember { mutableStateOf(false) }
    var showCurrencyMenu by remember { mutableStateOf(false) }
    var showThemeMenu by remember { mutableStateOf(false) }
    var showDefaultAccountMenu by remember { mutableStateOf(false) }
    var showWeekStartMenu by remember { mutableStateOf(false) }
    var showResetDataDialog by remember { mutableStateOf(false) }
    var showYearEndCloseDialog by remember { mutableStateOf(false) }

    // Backup preview dialog state
    var pendingRestoreValidation by remember { mutableStateOf<BackupValidationResult?>(null) }
    var pendingRestoreSourceLabel by remember { mutableStateOf("Local JSON File") }

    LaunchedEffect(cloudSyncStatusMessage) {
        cloudSyncStatusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearCloudSyncMessage()
        }
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val backupData = viewModel.buildFullBackupData()
                val ok = BackupHelper.exportData(context, uri, backupData)
                if (ok) {
                    viewModel.recordBackupCompleted()
                    val preview = BackupHelper.buildPreviewSummary(backupData)
                    snackbarHostState.showSnackbar("Exported ${preview.totalRecordsCount} records to JSON backup!")
                    if (autoCloudSyncEnabled && currentUser != null) {
                        viewModel.syncBackupToCloud()
                    }
                } else {
                    snackbarHostState.showSnackbar("Failed to export JSON backup.")
                }
            }
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val validation = BackupHelper.readAndValidateBackupUri(context, uri)
                pendingRestoreSourceLabel = "Local JSON File"
                pendingRestoreValidation = validation
            }
        }
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        coroutineScope.launch {
            if (granted) {
                com.example.ui.NotificationHelper.scheduleDailyReminder(context)
                snackbarHostState.showSnackbar("Notification permission granted!")
            } else {
                snackbarHostState.showSnackbar("Notification permission not granted.")
            }
        }
    }

    fun ensureNotificationPermissionIfNeeded(enabled: Boolean) {
        if (enabled && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (!com.example.ui.NotificationHelper.hasNotificationPermission(context)) {
                notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    if (showCategoryManager) {
        CategoryManagementDialog(
            viewModel = viewModel,
            onDismiss = { showCategoryManager = false }
        )
    }

    if (showGlobalSearch) {
        GlobalSearchDialog(
            viewModel = viewModel,
            navController = navController,
            onDismiss = { showGlobalSearch = false }
        )
    }

    if (showConfigurePinDialog) {
        ConfigureAppPinDialog(
            currentPin = appPin,
            requireCurrentPinVerification = appLockEnabled && !enableLockOnPinSave,
            title = if (enableLockOnPinSave) "Set 4–6 Digit App Lock PIN" else "Change 4–6 Digit App PIN",
            onDismiss = {
                showConfigurePinDialog = false
                enableLockOnPinSave = false
            },
            onPinSaved = { newPin ->
                viewModel.setVaultPin(newPin)
                if (enableLockOnPinSave) {
                    viewModel.toggleAppLock(true)
                }
                showConfigurePinDialog = false
                enableLockOnPinSave = false
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Saved ${newPin.length}-digit App PIN!")
                }
            }
        )
    }

    if (pendingRestoreValidation != null) {
        BackupRestorePreviewDialog(
            validationResult = pendingRestoreValidation!!,
            sourceLabel = pendingRestoreSourceLabel,
            onDismiss = { pendingRestoreValidation = null },
            onConfirmRestore = { clearFirst ->
                val dataToRestore = pendingRestoreValidation?.parsedData
                pendingRestoreValidation = null
                if (dataToRestore != null) {
                    viewModel.restoreBackup(
                        data = dataToRestore,
                        clearExistingFirst = clearFirst
                    ) { count ->
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Restored $count records successfully!")
                        }
                    }
                }
            }
        )
    }

    if (showResetDataDialog) {
        DoubleConfirmResetDataDialog(
            onDismiss = { showResetDataDialog = false },
            onExportBackupFirst = {
                showResetDataDialog = false
                exportBackupLauncher.launch("rupeestory_backup_${System.currentTimeMillis()}.json")
            },
            onConfirmFinalReset = {
                showResetDataDialog = false
                viewModel.resetAllAppData {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("All app data has been reset to factory defaults.")
                    }
                }
            }
        )
    }

    if (showYearEndCloseDialog) {
        val prevYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) - 1
        val cutoffMillis = remember(prevYear) {
            java.util.Calendar.getInstance().apply {
                set(prevYear, java.util.Calendar.DECEMBER, 31, 23, 59, 59)
                set(java.util.Calendar.MILLISECOND, 999)
            }.timeInMillis
        }
        AlertDialog(
            onDismissRequest = { showYearEndCloseDialog = false },
            title = { Text("Year-End Close (Through Dec 31, $prevYear)", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This will archive and remove transactions on or before 31/12/$prevYear and roll their net cash flows into each account's Opening Balance.\n\n" +
                    "Every account's current balance and your Total Balance will remain 100% identical."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showYearEndCloseDialog = false
                        viewModel.performYearEndClose(cutoffMillis) { res ->
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    "Closed ${res.archivedCount} historical transactions. Opening balances updated; Total Balance unchanged (${FormatUtils.formatInr(res.totalBalanceAfter)})."
                                )
                            }
                        }
                    },
                    modifier = Modifier.testTag("confirm_year_end_close_btn")
                ) {
                    Text("Close & Roll Opening Balances")
                }
            },
            dismissButton = {
                TextButton(onClick = { showYearEndCloseDialog = false }) {
                    Text("Cancel")
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
                title = {
                    Column {
                        Text("More & Settings", fontWeight = FontWeight.Bold)
                        Text(
                            "Modules • App Lock • Backup • Cloud Sync • Settings",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.togglePrivacyMode() },
                        modifier = Modifier.testTag("more_hide_balances_btn")
                    ) {
                        Icon(
                            if (privacyMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle Hide Balances"
                        )
                    }
                    IconButton(
                        onClick = { showGlobalSearch = true },
                        modifier = Modifier.testTag("more_global_search_btn")
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Global Search")
                    }
                    IconButton(
                        onClick = { navController.navigate("today_hub") },
                        modifier = Modifier.testTag("more_unified_today_btn")
                    ) {
                        Icon(Icons.Default.Today, contentDescription = "Unified Today View")
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
            contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)
        ) {
            // Weekly Backup Reminder Banner
            if (isWeeklyBackupDue) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("weekly_backup_reminder_banner"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Backup,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Weekly Backup Reminder",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    Text(
                                        if (lastBackupTimestamp == 0L) "You haven't backed up your data yet. Export a JSON backup to stay safe."
                                        else "Last backup was on ${FormatUtils.formatDate(lastBackupTimestamp)}. Back up weekly to protect your records.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { viewModel.snoozeWeeklyBackupReminder() }) {
                                    Text("Remind Later")
                                }
                                Spacer(Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        exportBackupLauncher.launch("rupeestory_backup_${System.currentTimeMillis()}.json")
                                    },
                                    modifier = Modifier.testTag("weekly_backup_now_btn")
                                ) {
                                    Text("Back Up Now", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // 1. Unified Today & Global Search Quick Bar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = { navController.navigate("today_hub") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Today, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Unified Today View", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { showGlobalSearch = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Global Search", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 2. Ecosystem Hub Modules
            item {
                Text(
                    "Ecosystem Modules",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column {
                        ListItem(
                            colors = ListItemDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                            ),
                            modifier = Modifier
                                .clickable {
                                    viewModel.setModuleEnabled("ai_assistant", true)
                                    navController.navigate("ai_assistant")
                                }
                                .testTag("more_item_ai_assistant"),
                            leadingContent = {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            headlineContent = {
                                Text(
                                    "Gemini AI Assistant & Smart Helpers",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            },
                            supportingContent = {
                                Text("Smart Entry • Receipt Scan • Ask My Data • Monthly Insights • Notes & Task AI")
                            },
                            trailingContent = {
                                Button(
                                    onClick = {
                                        viewModel.setModuleEnabled("ai_assistant", true)
                                        navController.navigate("ai_assistant")
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text("Open AI", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                        ListItem(
                            colors = ListItemDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier
                                .clickable { navController.navigate("daily_toolkit?tab=0") }
                                .testTag("more_item_daily_toolkit"),
                            leadingContent = {
                                Icon(
                                    Icons.Default.SelfImprovement,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            headlineContent = {
                                Text(
                                    "Daily Life & Pro Toolkit (Free)",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            },
                            supportingContent = {
                                Text("Live Meditation Timer • Vehicle Fuel Mileage • Safe-to-Spend • Smart Grocery • EMI & WhatsApp")
                            },
                            trailingContent = {
                                Icon(Icons.Default.ChevronRight, contentDescription = null)
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .clickable { navController.navigate("daily_toolkit?tab=1") }
                                .testTag("more_item_vehicle_fuel"),
                            leadingContent = { Icon(Icons.Default.LocalGasStation, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            headlineContent = { Text("Vehicle Fuel & Mileage Tracker", fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text("Track odometer km, km/L mileage, ₹/km cost & next service alert") },
                            trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .clickable { navController.navigate("profile") }
                                .testTag("more_item_profile_permissions"),
                            leadingContent = { Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            headlineContent = { Text("My Profile (કુતુબ)", fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text("Edit your name and notification reminders") },
                            trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        if ("habits" in enabledModules) {
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier
                                    .clickable { navController.navigate("habits") }
                                    .testTag("more_item_habits"),
                                leadingContent = { Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                headlineContent = { Text("Habits & Consistency", fontWeight = FontWeight.SemiBold) },
                                supportingContent = { Text("Daily habits, streaks, heatmaps & money-linked auto-tracking") },
                                trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }

                        if ("split_bills" in enabledModules) {
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier
                                    .clickable { navController.navigate("split_bills") }
                                    .testTag("more_item_split_bills"),
                                leadingContent = { Icon(Icons.Default.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                headlineContent = { Text("Split Bills (Splitwise)", fontWeight = FontWeight.SemiBold) },
                                supportingContent = { Text("Group bills, simplify debts, settle up & export PDF/CSV") },
                                trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }

                        if ("investments" in enabledModules) {
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier
                                    .clickable { navController.navigate("investments") }
                                    .testTag("more_item_investments"),
                                leadingContent = { Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                headlineContent = { Text("Investments & Net Worth", fontWeight = FontWeight.SemiBold) },
                                supportingContent = { Text("Stocks, Mutual Funds/SIP, FD/RD, Gold, PPF, Loans & XIRR") },
                                trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }

                        if ("document_vault" in enabledModules) {
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier
                                    .clickable { navController.navigate("document_vault") }
                                    .testTag("more_item_document_vault"),
                                leadingContent = { Icon(Icons.Default.FolderSpecial, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                headlineContent = { Text("Document Vault (Warranties & IDs)", fontWeight = FontWeight.SemiBold) },
                                supportingContent = { Text("Local compressed bills, warranties, IDs, PIN lock & expiry alerts") },
                                trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }

                        if ("reports" in enabledModules) {
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier
                                    .clickable { navController.navigate("analytics") }
                                    .testTag("more_item_reports"),
                                leadingContent = { Icon(Icons.Default.Assessment, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                headlineContent = { Text("Reports & Analytics", fontWeight = FontWeight.SemiBold) },
                                supportingContent = { Text("Spending insights, monthly comparisons & PDF/Excel/CSV export") },
                                trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }

                        if ("udhaar" in enabledModules) {
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier
                                    .clickable { navController.navigate("udhaar") }
                                    .testTag("more_item_udhaar"),
                                leadingContent = { Icon(Icons.Default.People, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                headlineContent = { Text("People & Udhaar (Khata)", fontWeight = FontWeight.SemiBold) },
                                supportingContent = { Text("Track money lent, borrowed, and repayments") },
                                trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }

                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.clickable { navController.navigate("budgets") },
                            leadingContent = { Icon(Icons.Default.PieChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            headlineContent = { Text("Budgets, Goals, Bills & Recurring", fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text("Monthly budgets, savings goals, bill reminders & auto-recurring") },
                            trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.clickable { navController.navigate("accounts") },
                            leadingContent = { Icon(Icons.Default.AccountBalance, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            headlineContent = { Text("Accounts & Balances", fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text("Manage Cash, Bank, and UPI accounts") },
                            trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) }
                        )
                    }
                }
            }

            // 3. App Lock & Privacy (4-6 digit PIN, Biometrics, Auto-lock after inactivity, Hide balances)
            item {
                Text(
                    "App Lock & Privacy",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("app_lock_privacy_card"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                            leadingContent = { Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            headlineContent = { Text("Hide Balances Across App", fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text("Mask amounts (${FormatUtils.currentCurrencySymbol}••••) on dashboards and lists") },
                            trailingContent = {
                                Switch(
                                    checked = privacyMode,
                                    onCheckedChange = { viewModel.setPrivacyMode(it) },
                                    modifier = Modifier.testTag("switch_hide_balances")
                                )
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                            leadingContent = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            headlineContent = { Text("App Lock (4–6 Digit PIN)", fontWeight = FontWeight.SemiBold) },
                            supportingContent = {
                                Text("Require ${appPin.length}-digit PIN (and optional biometric) to unlock")
                            },
                            trailingContent = {
                                Switch(
                                    checked = appLockEnabled,
                                    onCheckedChange = { enabled ->
                                        if (enabled) {
                                            enableLockOnPinSave = true
                                            showConfigurePinDialog = true
                                        } else {
                                            viewModel.toggleAppLock(false)
                                        }
                                    },
                                    modifier = Modifier.testTag("switch_app_lock")
                                )
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .clickable {
                                    enableLockOnPinSave = false
                                    showConfigurePinDialog = true
                                }
                                .testTag("change_app_pin_item"),
                            leadingContent = { Icon(Icons.Default.Pin, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            headlineContent = { Text("Change 4–6 Digit PIN", fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text("Current PIN length: ${appPin.length} digits (shared with Document Vault)") },
                            trailingContent = {
                                if (appLockEnabled) {
                                    FilledTonalButton(
                                        onClick = { viewModel.lockSessionNow() },
                                        modifier = Modifier.testTag("lock_now_btn")
                                    ) {
                                        Text("Lock Now")
                                    }
                                } else {
                                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                                }
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                        val biometricSupported = remember(context) { BiometricHelper.isBiometricAvailable(context) }
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                            leadingContent = { Icon(Icons.Default.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            headlineContent = { Text("Biometric Unlock", fontWeight = FontWeight.SemiBold) },
                            supportingContent = {
                                Text(
                                    if (biometricSupported) "Allow Fingerprint or Face unlock alongside PIN"
                                    else "Biometric hardware not enrolled on this device (PIN active)"
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = biometricUnlockEnabled && biometricSupported,
                                    enabled = biometricSupported,
                                    onCheckedChange = { viewModel.setBiometricUnlockEnabled(it) },
                                    modifier = Modifier.testTag("switch_biometric_unlock")
                                )
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                        val timeoutLabel = when (autoLockTimeoutMinutes) {
                            0 -> "Immediately when backgrounded"
                            1 -> "After 1 minute of inactivity"
                            5 -> "After 5 minutes of inactivity"
                            15 -> "After 15 minutes of inactivity"
                            30 -> "After 30 minutes of inactivity"
                            else -> "Never (Only on cold start)"
                        }
                        Box {
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier
                                    .clickable { showAutoLockMenu = true }
                                    .testTag("auto_lock_timeout_item"),
                                leadingContent = { Icon(Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                headlineContent = { Text("Auto-Lock After Inactivity", fontWeight = FontWeight.SemiBold) },
                                supportingContent = { Text(timeoutLabel) },
                                trailingContent = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
                            )
                            DropdownMenu(
                                expanded = showAutoLockMenu,
                                onDismissRequest = { showAutoLockMenu = false }
                            ) {
                                listOf(
                                    0 to "Immediately when backgrounded",
                                    1 to "After 1 minute",
                                    5 to "After 5 minutes",
                                    15 to "After 15 minutes",
                                    30 to "After 30 minutes",
                                    -1 to "Never (On app restart only)"
                                ).forEach { (mins, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            viewModel.setAutoLockTimeoutMinutes(mins)
                                            showAutoLockMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Backup & Restore (On-Device JSON with Preview & Validation + Weekly Reminder)
            item {
                Text(
                    "Backup & Restore (JSON with Preview & Validation)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("backup_restore_card"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .clickable {
                                    exportBackupLauncher.launch("rupeestory_backup_${System.currentTimeMillis()}.json")
                                }
                                .testTag("export_json_backup_item"),
                            leadingContent = { Icon(Icons.Default.UploadFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            headlineContent = { Text("Export ALL Data as JSON File", fontWeight = FontWeight.SemiBold) },
                            supportingContent = {
                                val lastBackupStr = if (lastBackupTimestamp > 0L) {
                                    "Last backup: ${FormatUtils.formatDateTime(lastBackupTimestamp)}"
                                } else {
                                    "Never backed up yet"
                                }
                                Text(
                                    "$lastBackupStr • Includes all modules, settings & ${vaultStorageSummary.totalDocuments} vault records (${DocumentVaultUtils.formatFileSize(vaultStorageSummary.totalBytesUsed)})."
                                )
                            },
                            trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .clickable {
                                    importBackupLauncher.launch(arrayOf("application/json", "*/*"))
                                }
                                .testTag("restore_json_backup_item"),
                            leadingContent = { Icon(Icons.Default.RestorePage, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            headlineContent = { Text("Restore from JSON (Preview & Validate)", fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text("Validates schema and shows record counts before merging or overwriting") },
                            trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                            leadingContent = { Icon(Icons.Default.EventRepeat, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            headlineContent = { Text("Weekly Backup Reminder", fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text("Prompt to back up if more than 7 days have passed") },
                            trailingContent = {
                                Switch(
                                    checked = weeklyBackupReminderEnabled,
                                    onCheckedChange = { viewModel.setWeeklyBackupReminderEnabled(it) },
                                    modifier = Modifier.testTag("switch_weekly_backup_reminder")
                                )
                            }
                        )
                    }
                }
            }

            // 5. Optional Cloud Sync with Firebase (Auth + Firestore)
            item {
                Text(
                    "Optional Cloud Sync (Firebase Auth & Firestore)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("firebase_cloud_sync_card"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val user = currentUser
                        if (user == null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.CloudQueue,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Cloud Sync is Optional (Offline-First)", fontWeight = FontWeight.Bold)
                                    Text(
                                        "All features work 100% offline. Sign in with Google to back up and sync your encrypted JSON snapshot across devices via Firebase Firestore.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Button(
                                onClick = {
                                    if (!isAuthLoading) {
                                        isAuthLoading = true
                                        coroutineScope.launch {
                                            try {
                                                val webClientId = context.getString(R.string.default_web_client_id)
                                                val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(webClientId)
                                                    .build()
                                                val request = GetCredentialRequest.Builder()
                                                    .addCredentialOption(signInWithGoogleOption)
                                                    .build()
                                                val result = credentialManager.getCredential(
                                                    request = request,
                                                    context = context
                                                )
                                                val credential = result.credential
                                                if (credential is CustomCredential &&
                                                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                                                ) {
                                                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                                                    val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                                                    auth.signInWithCredential(firebaseCredential).await()
                                                    viewModel.refreshCloudBackups()
                                                    snackbarHostState.showSnackbar("Signed in with Google for Cloud Sync!")
                                                }
                                            } catch (e: GetCredentialCancellationException) {
                                                Log.w(TAG, "Sign-in was cancelled by the user or unconfigured SHA-1.", e)
                                                snackbarHostState.showSnackbar("Google Sign-In was cancelled.")
                                            } catch (e: Exception) {
                                                Log.e(TAG, "Google Sign-In failed", e)
                                                snackbarHostState.showSnackbar("Sign-in failed: ${e.localizedMessage ?: "Error"}")
                                            } finally {
                                                isAuthLoading = false
                                            }
                                        }
                                    }
                                },
                                enabled = !isAuthLoading,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("google_sign_in_cloud_sync_btn"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (isAuthLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                } else {
                                    Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text("Sign in with Google for Cloud Sync", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.CloudDone,
                                    contentDescription = null,
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = user.displayName ?: "Google Account Connected",
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = user.email ?: "UID: ${user.uid.take(10)}...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (lastCloudSyncTimestamp > 0L) {
                                        Text(
                                            text = "Last Cloud Sync: ${FormatUtils.formatDateTime(lastCloudSyncTimestamp)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF2E7D32)
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            auth.signOut()
                                            try {
                                                credentialManager.clearCredentialState(ClearCredentialStateRequest())
                                            } catch (e: Exception) {
                                                Log.w(TAG, "Failed to clear credential state", e)
                                            }
                                            snackbarHostState.showSnackbar("Signed out of Cloud Sync.")
                                        }
                                    },
                                    modifier = Modifier.testTag("cloud_sync_sign_out_btn")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign Out")
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.syncBackupToCloud() },
                                    enabled = !isCloudSyncing,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("cloud_sync_push_btn"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    if (isCloudSyncing) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    Text("Sync to Cloud", fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        viewModel.fetchCloudBackupForRestorePreview("latest") { validation ->
                                            pendingRestoreSourceLabel = "Firebase Cloud Firestore"
                                            pendingRestoreValidation = validation
                                        }
                                    },
                                    enabled = !isCloudSyncing,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("cloud_sync_pull_btn"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Restore Cloud", fontWeight = FontWeight.Bold)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Auto-sync to Firestore when exporting local backup",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                Switch(
                                    checked = autoCloudSyncEnabled,
                                    onCheckedChange = { viewModel.setAutoCloudSyncEnabled(it) }
                                )
                            }
                        }
                    }
                }
            }

            // 6. Comprehensive Settings (Currency, Theme, Default Account, Week Start Day, Category Manager)
            item {
                Text(
                    "Preferences & Configuration",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("general_preferences_card"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column {
                        // Currency Selector
                        val activeCurr = FormatUtils.SUPPORTED_CURRENCIES.find { it.code == currencyCode }
                            ?: FormatUtils.SUPPORTED_CURRENCIES.first()
                        Box {
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier
                                    .clickable { showCurrencyMenu = true }
                                    .testTag("setting_currency_item"),
                                leadingContent = { Icon(Icons.Default.CurrencyExchange, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                headlineContent = { Text("Currency", fontWeight = FontWeight.SemiBold) },
                                supportingContent = { Text(activeCurr.label) },
                                trailingContent = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
                            )
                            DropdownMenu(
                                expanded = showCurrencyMenu,
                                onDismissRequest = { showCurrencyMenu = false }
                            ) {
                                FormatUtils.SUPPORTED_CURRENCIES.forEach { curr ->
                                    DropdownMenuItem(
                                        text = { Text(curr.label) },
                                        onClick = {
                                            viewModel.setCurrencyCode(curr.code)
                                            showCurrencyMenu = false
                                        }
                                    )
                                }
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                        // Theme Selector
                        Box {
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier
                                    .clickable { showThemeMenu = true }
                                    .testTag("setting_theme_item"),
                                leadingContent = { Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                headlineContent = { Text("App Theme", fontWeight = FontWeight.SemiBold) },
                                supportingContent = { Text("$themeMode Mode") },
                                trailingContent = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
                            )
                            DropdownMenu(
                                expanded = showThemeMenu,
                                onDismissRequest = { showThemeMenu = false }
                            ) {
                                listOf("System", "Light", "Dark").forEach { mode ->
                                    DropdownMenuItem(
                                        text = { Text("$mode Mode") },
                                        onClick = {
                                            viewModel.setThemeMode(mode)
                                            showThemeMenu = false
                                        }
                                    )
                                }
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                        // Default Account Selector
                        Box {
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier
                                    .clickable { showDefaultAccountMenu = true }
                                    .testTag("setting_default_account_item"),
                                leadingContent = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                headlineContent = { Text("Default Account", fontWeight = FontWeight.SemiBold) },
                                supportingContent = { Text(defaultAccount) },
                                trailingContent = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
                            )
                            DropdownMenu(
                                expanded = showDefaultAccountMenu,
                                onDismissRequest = { showDefaultAccountMenu = false }
                            ) {
                                val accountNames = accounts.map { it.name }.ifEmpty {
                                    listOf("Cash in Hand", "Bank Account", "UPI Wallet")
                                }
                                accountNames.forEach { accName ->
                                    DropdownMenuItem(
                                        text = { Text(accName) },
                                        onClick = {
                                            viewModel.setDefaultAccount(accName)
                                            showDefaultAccountMenu = false
                                        }
                                    )
                                }
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                        // Week Start Day Selector
                        Box {
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier
                                    .clickable { showWeekStartMenu = true }
                                    .testTag("setting_week_start_item"),
                                leadingContent = { Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                headlineContent = { Text("Week Start Day", fontWeight = FontWeight.SemiBold) },
                                supportingContent = { Text(weekStartDay) },
                                trailingContent = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
                            )
                            DropdownMenu(
                                expanded = showWeekStartMenu,
                                onDismissRequest = { showWeekStartMenu = false }
                            ) {
                                listOf("Monday", "Sunday", "Saturday").forEach { day ->
                                    DropdownMenuItem(
                                        text = { Text(day) },
                                        onClick = {
                                            viewModel.setWeekStartDay(day)
                                            showWeekStartMenu = false
                                        }
                                    )
                                }
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                        // Category Manager
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .clickable { showCategoryManager = true }
                                .testTag("setting_category_manager_item"),
                            leadingContent = { Icon(Icons.Default.Category, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            headlineContent = { Text("Category Manager", fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text("Create, edit, or delete custom Expense & Income categories") },
                            trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) }
                        )
                    }
                }
            }

            // 7. Module On/Off Toggles
            item {
                Text(
                    "Module On/Off Toggles (Customize Your App)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("module_toggles_card"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column {
                        EcosystemHubUtils.OPTIONAL_MODULES.forEachIndexed { index, mod ->
                            val isEnabled = mod.key in enabledModules
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                                headlineContent = { Text(mod.title, fontWeight = FontWeight.SemiBold) },
                                supportingContent = { Text(mod.subtitle) },
                                trailingContent = {
                                    Switch(
                                        checked = isEnabled,
                                        onCheckedChange = { checked ->
                                            viewModel.setModuleEnabled(mod.key, checked)
                                        },
                                        modifier = Modifier.testTag("module_toggle_${mod.key}")
                                    )
                                }
                            )
                            if (index < EcosystemHubUtils.OPTIONAL_MODULES.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }

            // 8. Notification Preferences
            item {
                Text(
                    "Notification Preferences",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("notification_preferences_card"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                            headlineContent = { Text("Daily Expense Log Reminder", fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text("Evening reminder at 8:00 PM to log daily transactions") },
                            trailingContent = {
                                Switch(
                                    checked = notifDailyExpense,
                                    onCheckedChange = {
                                        viewModel.setNotifDailyExpense(it)
                                        ensureNotificationPermissionIfNeeded(it)
                                        if (it) com.example.ui.NotificationHelper.scheduleDailyReminder(context)
                                    }
                                )
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                            headlineContent = { Text("Bill & EMI Due Alerts", fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text("Notifications for upcoming and overdue bills") },
                            trailingContent = {
                                Switch(
                                    checked = notifBillReminders,
                                    onCheckedChange = {
                                        viewModel.setNotifBillReminders(it)
                                        ensureNotificationPermissionIfNeeded(it)
                                    }
                                )
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                            headlineContent = { Text("Budget Threshold Warnings", fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text("Alert when monthly or category budget reaches 80% and 100%") },
                            trailingContent = {
                                Switch(
                                    checked = notifBudgetAlerts,
                                    onCheckedChange = {
                                        viewModel.setNotifBudgetAlerts(it)
                                        ensureNotificationPermissionIfNeeded(it)
                                    }
                                )
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                            headlineContent = { Text("Tasks, Schedule & Habit Reminders", fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text("Alarms for scheduled tasks, events, and daily habits") },
                            trailingContent = {
                                Switch(
                                    checked = notifTaskHabitReminders,
                                    onCheckedChange = {
                                        viewModel.setNotifTaskHabitReminders(it)
                                        ensureNotificationPermissionIfNeeded(it)
                                    }
                                )
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                            headlineContent = { Text("Warranty & Document Expiry Alerts", fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text("Remind 30, 7, and 1 day before Document Vault items expire") },
                            trailingContent = {
                                Switch(
                                    checked = notifVaultExpiry,
                                    onCheckedChange = {
                                        viewModel.setNotifVaultExpiry(it)
                                        ensureNotificationPermissionIfNeeded(it)
                                    }
                                )
                            }
                        )
                    }
                }
            }

            // 9. Offline-First & Danger Zone (Reset Data with Double Confirmation)
            item {
                Text(
                    "Offline Support & Data Management",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                            leadingContent = { Icon(Icons.Default.OfflinePin, contentDescription = null, tint = Color(0xFF2E7D32)) },
                            headlineContent = { Text("Offline-First Storage Ready", fontWeight = FontWeight.SemiBold) },
                            supportingContent = {
                                Text("100% of your Money, Tasks, Notes, Split Bills, Habits, Investments & Vault data is stored locally on-device and works without internet.")
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .clickable { showYearEndCloseDialog = true }
                                .testTag("year_end_close_item"),
                            leadingContent = { Icon(Icons.Default.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            headlineContent = {
                                Text(
                                    "Year-End Close & Roll Opening Balances",
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            supportingContent = { Text("Archive prior-year transactions into account opening balances while preserving exact current balances") },
                            trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .clickable { showResetDataDialog = true }
                                .testTag("reset_all_data_item"),
                            leadingContent = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            headlineContent = {
                                Text(
                                    "Reset All Data (Double Confirmation)",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                            supportingContent = { Text("Permanently wipe all records and restore default accounts & categories") },
                            trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                        )
                    }
                }
            }
        }
    }
}

package com.example.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.utils.CategoryUtils
import com.example.utils.TaskUtils
import com.example.ui.NotificationHelper
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class FinanceViewModel(private val repository: TransactionRepository, private val context: Context) : ViewModel() {
    private val prefs = context.getSharedPreferences("rupee_story_prefs", Context.MODE_PRIVATE)
    private val userPrefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

    // --- Privacy & App Lock (4-6 Digit PIN + Biometric + Inactivity Auto-Lock) ---
    private val _privacyMode = MutableStateFlow(prefs.getBoolean("privacy_mode", false))
    val privacyMode: StateFlow<Boolean> = _privacyMode.asStateFlow()

    private val _appLockEnabled = MutableStateFlow(prefs.getBoolean("app_lock", false))
    val appLockEnabled: StateFlow<Boolean> = _appLockEnabled.asStateFlow()

    private val _appPin = MutableStateFlow(prefs.getString("vault_pin", "1234") ?: "1234")
    val appPin: StateFlow<String> = _appPin.asStateFlow()

    private val _biometricUnlockEnabled = MutableStateFlow(prefs.getBoolean("biometric_unlock", true))
    val biometricUnlockEnabled: StateFlow<Boolean> = _biometricUnlockEnabled.asStateFlow()

    // Auto-lock timeout in minutes: 0 = Immediately on background, 1, 5, 15, 30, -1 = Never
    private val _autoLockTimeoutMinutes = MutableStateFlow(prefs.getInt("auto_lock_timeout_minutes", 5))
    val autoLockTimeoutMinutes: StateFlow<Int> = _autoLockTimeoutMinutes.asStateFlow()

    private val _isSessionLocked = MutableStateFlow(prefs.getBoolean("app_lock", false))
    val isSessionLocked: StateFlow<Boolean> = _isSessionLocked.asStateFlow()

    @Volatile
    private var lastInteractionTimestamp: Long = System.currentTimeMillis()

    // --- App Settings (Currency, Theme, Default Account, Week Start Day, Notifications) ---
    private val _currencyCode = MutableStateFlow(prefs.getString("currency_code", "INR") ?: "INR")
    val currencyCode: StateFlow<String> = _currencyCode.asStateFlow()

    private val _themeMode = MutableStateFlow(
        prefs.getString("theme_mode", userPrefs.getString("theme_mode", "System") ?: "System") ?: "System"
    )
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _defaultAccount = MutableStateFlow(prefs.getString("default_account", "Cash in Hand") ?: "Cash in Hand")
    val defaultAccount: StateFlow<String> = _defaultAccount.asStateFlow()

    private val _weekStartDay = MutableStateFlow(prefs.getString("week_start_day", "Monday") ?: "Monday")
    val weekStartDay: StateFlow<String> = _weekStartDay.asStateFlow()

    // Granular Notification Preferences
    private val _notifDailyExpense = MutableStateFlow(prefs.getBoolean("notif_daily_expense", userPrefs.getBoolean("daily_reminder", true)))
    val notifDailyExpense: StateFlow<Boolean> = _notifDailyExpense.asStateFlow()

    private val _notifBillReminders = MutableStateFlow(prefs.getBoolean("notif_bill_reminders", true))
    val notifBillReminders: StateFlow<Boolean> = _notifBillReminders.asStateFlow()

    private val _notifBudgetAlerts = MutableStateFlow(prefs.getBoolean("notif_budget_alerts", true))
    val notifBudgetAlerts: StateFlow<Boolean> = _notifBudgetAlerts.asStateFlow()

    private val _notifTaskHabitReminders = MutableStateFlow(prefs.getBoolean("notif_task_habit", true))
    val notifTaskHabitReminders: StateFlow<Boolean> = _notifTaskHabitReminders.asStateFlow()

    private val _notifVaultExpiry = MutableStateFlow(prefs.getBoolean("notif_vault_expiry", true))
    val notifVaultExpiry: StateFlow<Boolean> = _notifVaultExpiry.asStateFlow()

    private val _weeklyBackupReminderEnabled = MutableStateFlow(prefs.getBoolean("weekly_backup_reminder", true))
    val weeklyBackupReminderEnabled: StateFlow<Boolean> = _weeklyBackupReminderEnabled.asStateFlow()

    private val _lastBackupTimestamp = MutableStateFlow(prefs.getLong("last_backup_timestamp", 0L))
    val lastBackupTimestamp: StateFlow<Long> = _lastBackupTimestamp.asStateFlow()

    private val _lastCloudSyncTimestamp = MutableStateFlow(prefs.getLong("last_cloud_sync_timestamp", 0L))
    val lastCloudSyncTimestamp: StateFlow<Long> = _lastCloudSyncTimestamp.asStateFlow()

    private val _autoCloudSyncEnabled = MutableStateFlow(prefs.getBoolean("auto_cloud_sync", false))
    val autoCloudSyncEnabled: StateFlow<Boolean> = _autoCloudSyncEnabled.asStateFlow()

    fun togglePrivacyMode() {
        setPrivacyMode(!_privacyMode.value)
    }

    fun setPrivacyMode(enabled: Boolean) {
        _privacyMode.value = enabled
        com.example.utils.FormatUtils.hideBalances = enabled
        prefs.edit().putBoolean("privacy_mode", enabled).apply()
    }

    fun toggleAppLock(enabled: Boolean) {
        _appLockEnabled.value = enabled
        prefs.edit().putBoolean("app_lock", enabled).apply()
        if (!enabled) {
            _isSessionLocked.value = false
        } else {
            lastInteractionTimestamp = System.currentTimeMillis()
        }
    }

    fun setBiometricUnlockEnabled(enabled: Boolean) {
        _biometricUnlockEnabled.value = enabled
        prefs.edit().putBoolean("biometric_unlock", enabled).apply()
    }

    fun setAutoLockTimeoutMinutes(minutes: Int) {
        _autoLockTimeoutMinutes.value = minutes
        prefs.edit().putInt("auto_lock_timeout_minutes", minutes).apply()
        lastInteractionTimestamp = System.currentTimeMillis()
    }

    fun unlockSession() {
        lastInteractionTimestamp = System.currentTimeMillis()
        _isSessionLocked.value = false
    }

    fun lockSessionNow() {
        if (_appLockEnabled.value) {
            _isSessionLocked.value = true
        }
    }

    fun recordUserInteraction() {
        lastInteractionTimestamp = System.currentTimeMillis()
    }

    fun checkInactivityAutoLock(nowMillis: Long = System.currentTimeMillis(), onAppBackgrounded: Boolean = false) {
        if (!_appLockEnabled.value) return
        val timeoutMins = _autoLockTimeoutMinutes.value
        if (onAppBackgrounded && timeoutMins == 0) {
            _isSessionLocked.value = true
            return
        }
        if (timeoutMins > 0) {
            val elapsedMs = nowMillis - lastInteractionTimestamp
            if (elapsedMs >= timeoutMins * 60_000L) {
                _isSessionLocked.value = true
            }
        }
    }

    fun setCurrencyCode(code: String) {
        _currencyCode.value = code
        com.example.utils.FormatUtils.setCurrency(code)
        prefs.edit().putString("currency_code", code).apply()
    }

    fun setThemeMode(mode: String) {
        _themeMode.value = mode
        prefs.edit().putString("theme_mode", mode).apply()
        userPrefs.edit().putString("theme_mode", mode).apply()
    }

    fun setDefaultAccount(accountName: String) {
        _defaultAccount.value = accountName
        prefs.edit().putString("default_account", accountName).apply()
    }

    fun setWeekStartDay(dayName: String) {
        _weekStartDay.value = dayName
        prefs.edit().putString("week_start_day", dayName).apply()
    }

    fun setNotifDailyExpense(enabled: Boolean) {
        _notifDailyExpense.value = enabled
        prefs.edit().putBoolean("notif_daily_expense", enabled).apply()
        userPrefs.edit().putBoolean("daily_reminder", enabled).apply()
        if (enabled) NotificationHelper.scheduleDailyReminder(context)
    }

    fun setNotifBillReminders(enabled: Boolean) {
        _notifBillReminders.value = enabled
        prefs.edit().putBoolean("notif_bill_reminders", enabled).apply()
    }

    fun setNotifBudgetAlerts(enabled: Boolean) {
        _notifBudgetAlerts.value = enabled
        prefs.edit().putBoolean("notif_budget_alerts", enabled).apply()
    }

    fun setNotifTaskHabitReminders(enabled: Boolean) {
        _notifTaskHabitReminders.value = enabled
        prefs.edit().putBoolean("notif_task_habit", enabled).apply()
    }

    fun setNotifVaultExpiry(enabled: Boolean) {
        _notifVaultExpiry.value = enabled
        prefs.edit().putBoolean("notif_vault_expiry", enabled).apply()
    }

    fun setWeeklyBackupReminderEnabled(enabled: Boolean) {
        _weeklyBackupReminderEnabled.value = enabled
        prefs.edit().putBoolean("weekly_backup_reminder", enabled).apply()
    }

    fun recordBackupCompleted(timestamp: Long = System.currentTimeMillis()) {
        _lastBackupTimestamp.value = timestamp
        prefs.edit().putLong("last_backup_timestamp", timestamp).apply()
    }

    fun snoozeWeeklyBackupReminder() {
        // Snooze for 3 days by advancing last_backup_timestamp to (now - 4 days)
        val snoozedTimestamp = System.currentTimeMillis() - (4L * 24L * 60L * 60L * 1000L)
        _lastBackupTimestamp.value = snoozedTimestamp
        prefs.edit().putLong("last_backup_timestamp", snoozedTimestamp).apply()
    }

    fun setAutoCloudSyncEnabled(enabled: Boolean) {
        _autoCloudSyncEnabled.value = enabled
        prefs.edit().putBoolean("auto_cloud_sync", enabled).apply()
    }

    init {
        com.example.utils.FormatUtils.hideBalances = _privacyMode.value
        com.example.utils.FormatUtils.setCurrency(_currencyCode.value)
        processAutoRecurringTransactions()
    }

    fun restoreBackup(
        data: BackupData,
        clearExistingFirst: Boolean = false,
        onComplete: (Int) -> Unit = {}
    ) {
        viewModelScope.launch {
            if (clearExistingFirst) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    AppDatabase.getDatabase(context).clearAllTables()
                }
            }
            data.accounts.forEach { repository.insertAccount(it) }
            data.budgets.forEach { repository.insertBudget(it) }
            data.debts.forEach { repository.insertDebt(it) }
            data.goals.forEach { repository.insertGoal(it) }
            data.goalContributions.forEach { repository.insertGoalContribution(it) }
            data.moneyNotes.forEach { repository.insertNote(it) }
            data.categories.forEach { repository.insertCategory(it) }
            data.people.forEach { repository.insertPerson(it) }
            data.udhaarEntries.forEach { repository.insertUdhaarEntry(it) }
            data.udhaarRepayments.forEach { repository.insertUdhaarRepayment(it) }
            data.recurringTransactions.forEach { repository.insertRecurring(it) }
            data.billReminders.forEach { repository.insertBill(it) }
            data.tasks.forEach { repository.insertTask(it) }
            data.scheduleEvents.forEach { repository.insertEvent(it) }
            data.taskStreak?.let { repository.insertOrUpdateStreak(it) }
            data.splitGroups.forEach { repository.insertSplitGroup(it) }
            data.splitExpenses.forEach { repository.insertSplitExpense(it) }
            data.splitSettlements.forEach { repository.insertSplitSettlement(it) }
            data.habits.forEach { repository.insertHabit(it) }
            data.habitLogs.forEach { repository.insertOrUpdateHabitLog(it) }
            data.investmentHoldings.forEach { repository.insertHolding(it) }
            data.liabilityItems.forEach { repository.insertLiability(it) }
            val restoredVaultDocs = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.example.utils.DocumentVaultUtils.restoreVaultFilesFromBase64(
                    context = context,
                    documents = data.vaultDocuments,
                    payloadsBase64 = data.vaultFilePayloadsBase64
                )
            }
            restoredVaultDocs.forEach { repository.insertVaultDocument(it) }
            data.transactions.forEach { repository.insert(it) }

            // Restore settings if present
            data.userSettings["currency_code"]?.let { setCurrencyCode(it) }
            data.userSettings["theme_mode"]?.let { setThemeMode(it) }
            data.userSettings["default_account"]?.let { setDefaultAccount(it) }
            data.userSettings["week_start_day"]?.let { setWeekStartDay(it) }
            data.userSettings["notif_daily_expense"]?.toBooleanStrictOrNull()?.let { setNotifDailyExpense(it) }
            data.userSettings["notif_bill_reminders"]?.toBooleanStrictOrNull()?.let { setNotifBillReminders(it) }
            data.userSettings["notif_budget_alerts"]?.toBooleanStrictOrNull()?.let { setNotifBudgetAlerts(it) }
            data.userSettings["notif_task_habit"]?.toBooleanStrictOrNull()?.let { setNotifTaskHabitReminders(it) }
            data.userSettings["notif_vault_expiry"]?.toBooleanStrictOrNull()?.let { setNotifVaultExpiry(it) }
            data.userSettings["weekly_backup_reminder"]?.toBooleanStrictOrNull()?.let { setWeeklyBackupReminderEnabled(it) }
            data.userSettings["enabled_modules"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()?.let { mods ->
                if (mods.isNotEmpty()) {
                    _enabledModules.value = mods
                    prefs.edit().putStringSet("enabled_modules", mods).apply()
                }
            }

            val summary = com.example.ui.BackupHelper.buildPreviewSummary(data)
            onComplete(summary.totalRecordsCount)
        }
    }

    fun resetAllAppData(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            vaultDocuments.value.forEach { doc ->
                com.example.utils.DocumentVaultUtils.deleteLocalFileIfExists(doc.localFilePath)
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                AppDatabase.getDatabase(context).clearAllTables()
            }
            val defaultAccs = listOf(
                Account(name = "Cash in Hand", balance = 0.0, type = "CASH", colorHex = "#2E7D32", iconName = "Payments"),
                Account(name = "Bank Account", balance = 0.0, type = "BANK", colorHex = "#1565C0", iconName = "AccountBalance"),
                Account(name = "UPI Wallet", balance = 0.0, type = "UPI", colorHex = "#7B1FA2", iconName = "QrCode")
            )
            defaultAccs.forEach { repository.insertAccount(it) }
            CategoryUtils.defaultCategories.forEach { repository.insertCategory(it) }
            onComplete()
        }
    }

    companion object {
        val ALL_MODULE_KEYS = setOf(
            "tasks",
            "notes",
            "habits",
            "split_bills",
            "investments",
            "document_vault",
            "udhaar",
            "reports"
        )
    }

    private val _enabledModules = MutableStateFlow(
        prefs.getStringSet("enabled_modules", ALL_MODULE_KEYS)?.toSet() ?: ALL_MODULE_KEYS
    )
    val enabledModules: StateFlow<Set<String>> = _enabledModules.asStateFlow()

    fun isModuleEnabled(moduleKey: String): Boolean {
        return _enabledModules.value.contains(moduleKey)
    }

    fun setModuleEnabled(moduleKey: String, enabled: Boolean) {
        val updated = _enabledModules.value.toMutableSet()
        if (enabled) updated.add(moduleKey) else updated.remove(moduleKey)
        _enabledModules.value = updated.toSet()
        prefs.edit().putStringSet("enabled_modules", updated).apply()
    }

    fun buildFullBackupData(): BackupData {
        val settingsMap = mapOf(
            "currency_code" to _currencyCode.value,
            "theme_mode" to _themeMode.value,
            "default_account" to _defaultAccount.value,
            "week_start_day" to _weekStartDay.value,
            "enabled_modules" to _enabledModules.value.joinToString(","),
            "notif_daily_expense" to _notifDailyExpense.value.toString(),
            "notif_bill_reminders" to _notifBillReminders.value.toString(),
            "notif_budget_alerts" to _notifBudgetAlerts.value.toString(),
            "notif_task_habit" to _notifTaskHabitReminders.value.toString(),
            "notif_vault_expiry" to _notifVaultExpiry.value.toString(),
            "weekly_backup_reminder" to _weeklyBackupReminderEnabled.value.toString()
        )
        val currentVaultDocs = vaultDocuments.value
        val vaultFilesBase64 = com.example.utils.DocumentVaultUtils.encodeVaultFilesToBase64(currentVaultDocs)
        return BackupData(
            transactions = transactions.value,
            accounts = accounts.value,
            budgets = budgets.value,
            debts = debts.value,
            goals = goals.value,
            goalContributions = allGoalContributions.value,
            moneyNotes = moneyNotes.value,
            categories = categories.value,
            people = people.value,
            udhaarEntries = udhaarEntries.value,
            udhaarRepayments = udhaarRepayments.value,
            recurringTransactions = recurringTransactions.value,
            billReminders = allBills.value,
            tasks = allTasks.value,
            scheduleEvents = allScheduleEvents.value,
            splitGroups = splitGroups.value,
            splitExpenses = splitExpenses.value,
            splitSettlements = splitSettlements.value,
            habits = allHabits.value.ifEmpty { activeHabits.value },
            habitLogs = habitLogs.value,
            investmentHoldings = holdings.value,
            liabilityItems = liabilities.value,
            vaultDocuments = currentVaultDocs,
            vaultFilePayloadsBase64 = vaultFilesBase64,
            taskStreak = taskStreak.value,
            userSettings = settingsMap,
            schemaVersion = 2,
            exportedAt = System.currentTimeMillis(),
            appName = "RupeeStory"
        )
    }

    val transactions: StateFlow<List<Transaction>> = repository.allTransactions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val isWeeklyBackupDue: StateFlow<Boolean> = combine(
        weeklyBackupReminderEnabled,
        lastBackupTimestamp,
        transactions
    ) { enabled, lastTs, txs ->
        if (!enabled) return@combine false
        val sevenDaysMs = 7L * 24L * 60L * 60L * 1000L
        val now = System.currentTimeMillis()
        if (lastTs == 0L) {
            txs.isNotEmpty()
        } else {
            (now - lastTs) >= sevenDaysMs
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val accounts: StateFlow<List<Account>> = repository.allAccounts.map { 
        if (it.isEmpty()) { 
            val defaults = listOf(
                Account(name = "Cash in Hand", balance = 0.0, type = "CASH", colorHex = "#2E7D32", iconName = "Payments"),
                Account(name = "Bank Account", balance = 0.0, type = "BANK", colorHex = "#1565C0", iconName = "AccountBalance"),
                Account(name = "UPI Wallet", balance = 0.0, type = "UPI", colorHex = "#7B1FA2", iconName = "QrCode")
            )
            defaults.forEach { acc -> repository.insertAccount(acc) }
            defaults 
        } else it 
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = listOf(
                Account(name = "Cash in Hand", balance = 0.0, type = "CASH", colorHex = "#2E7D32", iconName = "Payments"),
                Account(name = "Bank Account", balance = 0.0, type = "BANK", colorHex = "#1565C0", iconName = "AccountBalance"),
                Account(name = "UPI Wallet", balance = 0.0, type = "UPI", colorHex = "#7B1FA2", iconName = "QrCode")
            )
        )

    val categories: StateFlow<List<Category>> = repository.allCategories.map {
        if (it.isEmpty()) {
            val defaults = CategoryUtils.defaultCategories
            defaults.forEach { cat -> repository.insertCategory(cat) }
            defaults
        } else it
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = CategoryUtils.defaultCategories
        )

    val moneyNotes: StateFlow<List<MoneyNote>> = repository.allNotes
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val activeNotes: StateFlow<List<MoneyNote>> = repository.activeNotes
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val archivedNotes: StateFlow<List<MoneyNote>> = repository.archivedNotes
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val splitGroups: StateFlow<List<SplitGroup>> = repository.allSplitGroups
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val splitExpenses: StateFlow<List<SplitExpense>> = repository.allSplitExpenses
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val splitSettlements: StateFlow<List<SplitSettlement>> = repository.allSplitSettlements
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val splitGroupSummaries: StateFlow<List<SplitGroupSummary>> = combine(
        splitGroups,
        splitExpenses,
        splitSettlements
    ) { groups, expenses, settlements ->
        groups.map { grp ->
            val grpExpenses = expenses.filter { it.groupId == grp.id }
            val grpSettlements = settlements.filter { it.groupId == grp.id }
            com.example.utils.SplitUtils.calculateGroupSummary(grp, grpExpenses, grpSettlements)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val activeHabits: StateFlow<List<Habit>> = repository.activeHabits
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allHabits: StateFlow<List<Habit>> = repository.allHabits
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val habitLogs: StateFlow<List<HabitLog>> = repository.allHabitLogs
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val holdings: StateFlow<List<InvestmentHolding>> = repository.allHoldings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val liabilities: StateFlow<List<LiabilityItem>> = repository.allLiabilities
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val netWorthOverview: StateFlow<NetWorthOverview> = combine(
        accounts,
        transactions,
        holdings,
        liabilities
    ) { accs, txs, hlds, liabs ->
        runCatching {
            com.example.utils.InvestmentUtils.calculateNetWorthOverview(
                accounts = accs,
                transactions = txs,
                holdings = hlds,
                liabilities = liabs
            )
        }.getOrElse {
            NetWorthOverview(
                liquidCashBankBalance = 0.0,
                totalInvestmentsCurrentValue = 0.0,
                totalInvestedCost = 0.0,
                totalPortfolioProfitLoss = 0.0,
                totalPortfolioReturnPercent = 0.0,
                totalPortfolioXirrPercent = 0.0,
                totalAssets = 0.0,
                totalLiabilities = 0.0,
                netWorth = 0.0,
                holdingsPerformance = emptyList(),
                assetAllocation = emptyList(),
                monthlyTrend = emptyList()
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = NetWorthOverview(
            liquidCashBankBalance = 0.0,
            totalInvestmentsCurrentValue = 0.0,
            totalInvestedCost = 0.0,
            totalPortfolioProfitLoss = 0.0,
            totalPortfolioReturnPercent = 0.0,
            totalPortfolioXirrPercent = 0.0,
            totalAssets = 0.0,
            totalLiabilities = 0.0,
            netWorth = 0.0,
            holdingsPerformance = emptyList(),
            assetAllocation = emptyList(),
            monthlyTrend = emptyList()
        )
    )

    val vaultDocuments: StateFlow<List<VaultDocument>> = repository.allVaultDocuments
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val vaultStorageSummary: StateFlow<VaultStorageSummary> = vaultDocuments.map { docs ->
        runCatching {
            com.example.utils.DocumentVaultUtils.calculateStorageSummary(docs)
        }.getOrElse {
            VaultStorageSummary(
                totalDocuments = 0,
                totalBytesUsed = 0L,
                maxRecommendedBytes = com.example.utils.DocumentVaultUtils.MAX_RECOMMENDED_STORAGE_BYTES,
                usagePercent = 0f,
                isStorageWarning = false,
                expiringSoonCount = 0,
                expiredCount = 0,
                activeWarrantiesCount = 0
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = VaultStorageSummary(
            totalDocuments = 0,
            totalBytesUsed = 0L,
            maxRecommendedBytes = com.example.utils.DocumentVaultUtils.MAX_RECOMMENDED_STORAGE_BYTES,
            usagePercent = 0f,
            isStorageWarning = false,
            expiringSoonCount = 0,
            expiredCount = 0,
            activeWarrantiesCount = 0
        )
    )

    val budgets: StateFlow<List<Budget>> = repository.allBudgets
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val goals: StateFlow<List<Goal>> = repository.allGoals
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val debts: StateFlow<List<Debt>> = repository.allDebts
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val people: StateFlow<List<Person>> = repository.allPeople
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val udhaarEntries: StateFlow<List<UdhaarEntry>> = repository.allUdhaarEntries
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val udhaarRepayments: StateFlow<List<UdhaarRepayment>> = repository.allUdhaarRepayments
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val peopleSummaries: StateFlow<List<PersonUdhaarSummary>> = combine(
        people,
        udhaarEntries
    ) { personList, entriesList ->
        val now = System.currentTimeMillis()
        personList.map { person ->
            val pEntries = entriesList.filter { it.personId == person.id }
            val totalGave = pEntries.filter { it.type == "GAVE" }.sumOf { it.amount }
            val totalTook = pEntries.filter { it.type == "TOOK" }.sumOf { it.amount }
            val remainingToReceive = pEntries.filter { it.type == "GAVE" && !it.isSettled }.sumOf { it.remainingAmount }
            val remainingToPay = pEntries.filter { it.type == "TOOK" && !it.isSettled }.sumOf { it.remainingAmount }
            val net = remainingToReceive - remainingToPay
            val activeCount = pEntries.count { !it.isSettled }
            val hasOverdue = pEntries.any { !it.isSettled && it.dueDate != null && it.dueDate < now }
            val lastDate = pEntries.maxOfOrNull { it.date } ?: person.createdAt

            PersonUdhaarSummary(
                person = person,
                totalGave = totalGave,
                totalTook = totalTook,
                remainingToReceive = remainingToReceive,
                remainingToPay = remainingToPay,
                netBalance = net,
                activeEntriesCount = activeCount,
                hasOverdue = hasOverdue,
                lastActivityDate = lastDate
            )
        }.sortedWith(
            compareByDescending<PersonUdhaarSummary> { it.hasOverdue }
                .thenByDescending { kotlin.math.abs(it.netBalance) }
                .thenBy { it.person.name.lowercase() }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val overallToReceive: StateFlow<Double> = udhaarEntries.map { entries ->
        entries.filter { it.type == "GAVE" && !it.isSettled }.sumOf { it.remainingAmount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val overallToPay: StateFlow<Double> = udhaarEntries.map { entries ->
        entries.filter { it.type == "TOOK" && !it.isSettled }.sumOf { it.remainingAmount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val overdueEntries: StateFlow<List<Pair<Person, UdhaarEntry>>> = combine(
        people,
        udhaarEntries
    ) { personList, entriesList ->
        val now = System.currentTimeMillis()
        val personMap = personList.associateBy { it.id }
        entriesList
            .filter { !it.isSettled && it.dueDate != null && it.dueDate < now }
            .sortedBy { it.dueDate }
            .mapNotNull { entry ->
                personMap[entry.personId]?.let { person -> person to entry }
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recurringTransactions: StateFlow<List<RecurringTransaction>> = repository.allRecurring
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val activeRecurring: StateFlow<List<RecurringTransaction>> = repository.activeRecurring
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val pendingApprovalRecurring: StateFlow<List<RecurringTransaction>> = activeRecurring.map { list ->
        val now = System.currentTimeMillis()
        list.filter { it.nextDueDate <= now && it.pendingConfirmation }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allBills: StateFlow<List<BillReminder>> = repository.allBills
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val pendingBills: StateFlow<List<BillReminder>> = repository.pendingBills
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val sortedPendingBills: StateFlow<List<BillReminder>> = pendingBills.map { list ->
        val now = System.currentTimeMillis()
        list.sortedWith(
            compareByDescending<BillReminder> { it.dueDate < now }
                .thenBy { it.dueDate }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allGoalContributions: StateFlow<List<GoalContribution>> = repository.allGoalContributions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val budgetStatuses: StateFlow<List<BudgetStatus>> = combine(
        budgets,
        transactions
    ) { budgetList, txList ->
        val now = java.util.Calendar.getInstance()
        val currentMonth = now.get(java.util.Calendar.MONTH)
        val currentYear = now.get(java.util.Calendar.YEAR)
        val expenses = txList.filter {
            !it.isIncome && !com.example.utils.ReportUtils.isExcludedFromIncomeExpense(it.category) && run {
                val cal = java.util.Calendar.getInstance().apply { timeInMillis = it.timestamp }
                cal.get(java.util.Calendar.MONTH) == currentMonth && cal.get(java.util.Calendar.YEAR) == currentYear
            }
        }
        budgetList.map { b ->
            val spent = if (b.category.equals("Monthly", ignoreCase = true)) {
                expenses.sumOf { it.amount }
            } else {
                expenses.filter { it.category.equals(b.category, ignoreCase = true) }.sumOf { it.amount }
            }
            val limit = b.limitAmount
            val progress = if (limit > 0) (spent / limit).toFloat() else 0f
            BudgetStatus(
                category = b.category,
                limitAmount = limit,
                spentAmount = spent,
                remainingAmount = maxOf(0.0, limit - spent),
                progress = progress,
                isWarning80 = progress >= 0.80f && progress < 1.0f,
                isExceeded100 = progress >= 1.0f
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val overallBudgetStatus: StateFlow<BudgetStatus> = combine(
        budgets,
        transactions
    ) { budgetList, txList ->
        val now = java.util.Calendar.getInstance()
        val currentMonth = now.get(java.util.Calendar.MONTH)
        val currentYear = now.get(java.util.Calendar.YEAR)
        val expenses = txList.filter {
            !it.isIncome && !com.example.utils.ReportUtils.isExcludedFromIncomeExpense(it.category) && run {
                val cal = java.util.Calendar.getInstance().apply { timeInMillis = it.timestamp }
                cal.get(java.util.Calendar.MONTH) == currentMonth && cal.get(java.util.Calendar.YEAR) == currentYear
            }
        }
        val monthlyBudgetRecord = budgetList.find { it.category.equals("Monthly", ignoreCase = true) }
        val limit = monthlyBudgetRecord?.limitAmount ?: 20000.0
        val totalSpent = expenses.sumOf { it.amount }
        val progress = if (limit > 0) (totalSpent / limit).toFloat() else 0f
        BudgetStatus(
            category = "Monthly",
            limitAmount = limit,
            spentAmount = totalSpent,
            remainingAmount = maxOf(0.0, limit - totalSpent),
            progress = progress,
            isWarning80 = progress >= 0.80f && progress < 1.0f,
            isExceeded100 = progress >= 1.0f
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        BudgetStatus("Monthly", 20000.0, 0.0, 20000.0, 0f, isWarning80 = false, isExceeded100 = false)
    )

    val allTasks: StateFlow<List<TaskItem>> = repository.allTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeTasks: StateFlow<List<TaskItem>> = repository.activeTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedTasks: StateFlow<List<TaskItem>> = repository.completedTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val taskStreak: StateFlow<TaskStreak?> = repository.taskStreak
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allScheduleEvents: StateFlow<List<ScheduleEvent>> = repository.allEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val taskStats: StateFlow<TaskStats> = combine(
        allTasks,
        taskStreak
    ) { tasks, streak ->
        val total = tasks.size
        val pending = tasks.count { it.status == "TODO" }
        val doing = tasks.count { it.status == "DOING" }
        val completed = tasks.count { it.status == "DONE" }
        val completionRate = if (total > 0) ((completed.toDouble() / total) * 100).toInt() else 0
        val completedToday = tasks.count { it.status == "DONE" && TaskUtils.isToday(it.completedAt) }
        TaskStats(
            totalTasks = total,
            pendingTasks = pending,
            doingTasks = doing,
            completedTasks = completed,
            completionRatePercent = completionRate,
            currentStreak = streak?.currentStreak ?: 0,
            maxStreak = streak?.maxStreak ?: 0,
            completedTodayCount = completedToday
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        TaskStats(0, 0, 0, 0, 0, 0, 0, 0)
    )

    suspend fun getTransactionById(id: Int): Transaction? {
        return viewModelScope.async { repository.getTransactionById(id) }.await()
    }

    fun addTransaction(
        amount: Double,
        isIncome: Boolean,
        category: String,
        account: String,
        note: String,
        timestamp: Long,
        subcategory: String = "",
        paymentMethod: String = "UPI",
        tags: String = "",
        photoUri: String? = null
    ) {
        viewModelScope.launch {
            repository.insert(
                Transaction(
                    amount = amount,
                    isIncome = isIncome,
                    category = category,
                    subcategory = subcategory,
                    account = account,
                    paymentMethod = paymentMethod,
                    note = note,
                    tags = tags,
                    photoUri = photoUri,
                    timestamp = timestamp
                )
            )
        }
    }

    fun updateTransaction(
        id: Int,
        amount: Double,
        isIncome: Boolean,
        category: String,
        account: String,
        note: String,
        timestamp: Long,
        subcategory: String = "",
        paymentMethod: String = "UPI",
        tags: String = "",
        photoUri: String? = null
    ) {
        viewModelScope.launch {
            repository.update(
                Transaction(
                    id = id,
                    amount = amount,
                    isIncome = isIncome,
                    category = category,
                    subcategory = subcategory,
                    account = account,
                    paymentMethod = paymentMethod,
                    note = note,
                    tags = tags,
                    photoUri = photoUri,
                    timestamp = timestamp
                )
            )
        }
    }

    fun duplicateTransaction(id: Int, onComplete: ((Transaction) -> Unit)? = null) {
        viewModelScope.launch {
            val original = repository.getTransactionById(id)
            if (original != null) {
                val duplicate = original.copy(
                    id = 0,
                    timestamp = System.currentTimeMillis()
                )
                repository.insert(duplicate)
                onComplete?.invoke(duplicate)
            }
        }
    }

    fun restoreTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.insert(transaction)
        }
    }

    fun deleteTransaction(id: Int) {
        viewModelScope.launch {
            val tx = repository.getTransactionById(id)
            if (tx != null && tx.category == "Transfer") {
                val counterpart = transactions.value.firstOrNull {
                    it.id != id &&
                    it.category == "Transfer" &&
                    it.amount == tx.amount &&
                    it.timestamp == tx.timestamp &&
                    it.isIncome != tx.isIncome
                }
                repository.deleteById(id)
                if (counterpart != null) {
                    repository.deleteById(counterpart.id)
                }
            } else {
                repository.deleteById(id)
            }
        }
    }

    fun addTransfer(amount: Double, fromAccount: String, toAccount: String, timestamp: Long) {
        viewModelScope.launch {
            // Expense from source account
            repository.insert(
                Transaction(
                    amount = amount,
                    isIncome = false,
                    category = "Transfer",
                    account = fromAccount,
                    paymentMethod = "Transfer",
                    note = "Transfer to $toAccount",
                    timestamp = timestamp
                )
            )
            // Income to destination account
            repository.insert(
                Transaction(
                    amount = amount,
                    isIncome = true,
                    category = "Transfer",
                    account = toAccount,
                    paymentMethod = "Transfer",
                    note = "Transfer from $fromAccount",
                    timestamp = timestamp
                )
            )
        }
    }

    // Category Management
    fun addCategory(name: String, isIncome: Boolean, colorHex: String, iconName: String) {
        viewModelScope.launch {
            repository.insertCategory(
                Category(
                    name = name.trim(),
                    isIncome = isIncome,
                    colorHex = colorHex,
                    iconName = iconName,
                    isDefault = false
                )
            )
        }
    }

    fun updateCategory(id: Int, name: String, isIncome: Boolean, colorHex: String, iconName: String) {
        viewModelScope.launch {
            repository.updateCategory(
                Category(
                    id = id,
                    name = name.trim(),
                    isIncome = isIncome,
                    colorHex = colorHex,
                    iconName = iconName,
                    isDefault = false
                )
            )
        }
    }

    fun deleteCategory(id: Int) {
        viewModelScope.launch {
            repository.deleteCategoryById(id)
        }
    }

    fun saveBudget(category: String, limitAmount: Double) {
        viewModelScope.launch {
            repository.insertBudget(Budget(category, limitAmount))
        }
    }

    fun deleteBudget(category: String) {
        viewModelScope.launch {
            repository.deleteBudget(category)
        }
    }

    fun addGoal(
        name: String,
        targetAmount: Double,
        targetDateMillis: Long = 0L,
        category: String = "Savings",
        note: String = ""
    ) {
        viewModelScope.launch {
            repository.insertGoal(
                Goal(
                    name = name.trim(),
                    targetAmount = targetAmount,
                    savedAmount = 0.0,
                    targetDateMillis = targetDateMillis,
                    category = category,
                    note = note.trim()
                )
            )
        }
    }

    fun updateGoal(goal: Goal) {
        viewModelScope.launch {
            repository.updateGoal(goal)
        }
    }

    fun addSavedAmountToGoal(goal: Goal, amountToAdd: Double, note: String = "", account: String? = null) {
        viewModelScope.launch {
            repository.insertGoalContribution(
                GoalContribution(
                    goalId = goal.id,
                    amount = amountToAdd,
                    date = System.currentTimeMillis(),
                    note = note.trim(),
                    account = account
                )
            )
            repository.updateGoal(goal.copy(savedAmount = goal.savedAmount + amountToAdd))
        }
    }

    fun deleteGoalContribution(contribution: GoalContribution) {
        viewModelScope.launch {
            repository.deleteGoalContributionById(contribution.id)
            val goal = repository.getGoalByIdSync(contribution.goalId)
            if (goal != null) {
                repository.updateGoal(goal.copy(savedAmount = maxOf(0.0, goal.savedAmount - contribution.amount)))
            }
        }
    }

    fun getContributionsForGoal(goalId: Int): Flow<List<GoalContribution>> =
        repository.getContributionsForGoal(goalId)

    fun deleteGoal(id: Int) {
        viewModelScope.launch {
            repository.deleteGoalById(id)
        }
    }

    // Recurring Transactions (month-end aware)
    fun advanceDueDate(currentDue: Long, frequency: String): Long {
        return com.example.utils.FormatUtils.advanceRecurringDate(currentDue, frequency)
    }

    fun addRecurringTransaction(
        title: String,
        amount: Double,
        isIncome: Boolean,
        category: String,
        account: String,
        frequency: String,
        nextDueDate: Long,
        autoCreate: Boolean,
        pendingConfirmation: Boolean,
        note: String = ""
    ) {
        viewModelScope.launch {
            repository.insertRecurring(
                RecurringTransaction(
                    title = title.trim(),
                    amount = amount,
                    isIncome = isIncome,
                    category = category,
                    account = account,
                    frequency = frequency,
                    nextDueDate = nextDueDate,
                    autoCreate = autoCreate,
                    pendingConfirmation = pendingConfirmation,
                    note = note.trim()
                )
            )
        }
    }

    fun updateRecurringTransaction(recurring: RecurringTransaction) {
        viewModelScope.launch {
            repository.updateRecurring(recurring)
        }
    }

    fun deleteRecurringTransaction(id: Long) {
        viewModelScope.launch {
            repository.deleteRecurringById(id)
        }
    }

    fun toggleRecurringActive(id: Long, isActive: Boolean) {
        viewModelScope.launch {
            val item = repository.getRecurringById(id)
            if (item != null) {
                repository.updateRecurring(item.copy(isActive = isActive))
            }
        }
    }

    fun executeRecurringTransaction(recurring: RecurringTransaction, createTx: Boolean = true) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            if (createTx) {
                repository.insert(
                    Transaction(
                        amount = recurring.amount,
                        isIncome = recurring.isIncome,
                        category = recurring.category,
                        account = recurring.account,
                        paymentMethod = "Recurring",
                        note = "Recurring: ${recurring.title}${if (recurring.note.isNotBlank()) " - " + recurring.note else ""}",
                        timestamp = now
                    )
                )
            }
            val nextDue = advanceDueDate(recurring.nextDueDate, recurring.frequency)
            repository.updateRecurring(
                recurring.copy(
                    nextDueDate = nextDue,
                    lastExecutedDate = now
                )
            )
        }
    }

    fun skipRecurringCycle(recurring: RecurringTransaction) {
        viewModelScope.launch {
            val nextDue = advanceDueDate(recurring.nextDueDate, recurring.frequency)
            repository.updateRecurring(recurring.copy(nextDueDate = nextDue))
        }
    }

    fun processAutoRecurringTransactions() {
        viewModelScope.launch {
            val active = repository.getActiveRecurringSync()
            val now = System.currentTimeMillis()
            active.filter { it.autoCreate && !it.pendingConfirmation && it.nextDueDate <= now }.forEach { rec ->
                executeRecurringTransaction(rec, createTx = true)
            }
        }
    }

    // Bill Reminders
    fun addBillReminder(
        title: String,
        amount: Double,
        dueDate: Long,
        category: String = "Bills",
        account: String = "Bank Account",
        isRecurring: Boolean = false,
        recurringFrequency: String? = "MONTHLY",
        note: String = ""
    ) {
        viewModelScope.launch {
            repository.insertBill(
                BillReminder(
                    title = title.trim(),
                    amount = amount,
                    dueDate = dueDate,
                    category = category,
                    account = account,
                    isRecurring = isRecurring,
                    recurringFrequency = recurringFrequency,
                    note = note.trim()
                )
            )
        }
    }

    fun updateBillReminder(bill: BillReminder) {
        viewModelScope.launch {
            repository.updateBill(bill)
        }
    }

    fun deleteBillReminder(id: Long) {
        viewModelScope.launch {
            repository.deleteBillById(id)
        }
    }

    fun markBillAsPaid(bill: BillReminder) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            // 1. Automatically create expense transaction
            repository.insert(
                Transaction(
                    amount = bill.amount,
                    isIncome = false,
                    category = bill.category,
                    account = bill.account,
                    paymentMethod = "Bill Payment",
                    note = "Bill: ${bill.title}${if (bill.note.isNotBlank()) " - " + bill.note else ""}",
                    timestamp = now
                )
            )
            // 2. If recurring, advance dueDate for next cycle; otherwise mark paid
            if (bill.isRecurring && !bill.recurringFrequency.isNullOrBlank()) {
                val nextDue = advanceDueDate(bill.dueDate, bill.recurringFrequency)
                repository.updateBill(
                    bill.copy(
                        dueDate = nextDue,
                        isPaid = false,
                        paidDate = now
                    )
                )
            } else {
                repository.updateBill(
                    bill.copy(
                        isPaid = true,
                        paidDate = now
                    )
                )
            }
        }
    }

    fun addDebt(personName: String, amount: Double, isOwedToMe: Boolean, note: String = "") {
        viewModelScope.launch {
            repository.insertDebt(Debt(personName = personName, amount = amount, isOwedToMe = isOwedToMe, note = note))
        }
    }

    fun settleDebt(debt: Debt) {
        viewModelScope.launch {
            repository.updateDebt(debt.copy(isSettled = true))
        }
    }

    fun deleteDebt(id: Int) {
        viewModelScope.launch {
            repository.deleteDebtById(id)
        }
    }

    fun addAccount(
        name: String,
        type: String = "BANK",
        openingBalance: Double = 0.0,
        colorHex: String = "#1565C0",
        iconName: String = "AccountBalance"
    ) {
        viewModelScope.launch {
            repository.insertAccount(
                Account(
                    name = name.trim(),
                    balance = openingBalance,
                    type = type,
                    colorHex = colorHex,
                    iconName = iconName
                )
            )
        }
    }

    fun addAccount(name: String, initialBalance: Double) {
        addAccount(name = name, type = "BANK", openingBalance = initialBalance)
    }

    fun adjustAccountBalance(accountName: String, targetBalance: Double, note: String = "") {
        val currentAccount = accounts.value.firstOrNull { it.name.equals(accountName, ignoreCase = true) }
            ?: Account(accountName)
        val currentBalance = com.example.utils.AccountUtils.calculateAccountBalance(currentAccount, transactions.value)
        val diff = targetBalance - currentBalance
        if (kotlin.math.abs(diff) < 0.01) return

        val isIncome = diff > 0
        val adjustAmount = kotlin.math.abs(diff)
        val noteText = if (note.isNotBlank()) "Adjustment: $note" else "Balance adjustment"

        viewModelScope.launch {
            repository.insert(
                Transaction(
                    amount = adjustAmount,
                    isIncome = isIncome,
                    category = "Adjustment",
                    account = accountName,
                    paymentMethod = "Adjustment",
                    note = noteText,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    /**
     * Deletes an account and guarantees zero orphaned transactions so that
     * sum(account balances) == totalBalance always holds.
     */
    fun deleteAccount(
        name: String,
        reassignToAccount: String? = null,
        deleteAssociatedTransactions: Boolean = false
    ) {
        viewModelScope.launch {
            val remainingAccounts = accounts.value.filterNot { it.name.equals(name, ignoreCase = true) }
            val fallbackAccount = reassignToAccount?.takeIf { it.isNotBlank() }
                ?: remainingAccounts.firstOrNull()?.name
                ?: "Cash in Hand"

            if (deleteAssociatedTransactions) {
                repository.deleteTransactionsForAccount(name)
            } else {
                repository.reassignAccountForTransactions(name, fallbackAccount)
            }
            repository.deleteAccountByName(name)

            if (_defaultAccount.value.equals(name, ignoreCase = true)) {
                setDefaultAccount(fallbackAccount)
            }
        }
    }

    /**
     * Archives/closes all transactions on or before [cutoffTimestamp] and rolls their
     * net cash flow into each account's opening balance (`account.balance`).
     * Guarantees that every account's current balance and totalBalance stay 100% identical.
     */
    fun performYearEndClose(
        cutoffTimestamp: Long,
        onComplete: (com.example.utils.YearEndCloseResult) -> Unit = {}
    ) {
        viewModelScope.launch {
            val result = com.example.utils.AccountUtils.calculateYearEndClose(
                accounts = accounts.value,
                transactions = transactions.value,
                cutoffTimestamp = cutoffTimestamp
            )
            result.updatedAccounts.forEach { updatedAcc ->
                repository.insertAccount(updatedAcc)
            }
            result.archivedTransactions.forEach { oldTx ->
                repository.deleteById(oldTx.id)
            }
            onComplete(result)
        }
    }

    fun addNote(
        title: String,
        content: String,
        timestamp: Long = System.currentTimeMillis(),
        colorHex: String = "#DEFAULT",
        isPinned: Boolean = false,
        tagsJson: String = "[]",
        checklistsJson: String = "[]",
        attachedType: String? = null,
        attachedId: Long? = null,
        attachedTitle: String? = null
    ) {
        viewModelScope.launch {
            repository.insertNote(
                MoneyNote(
                    title = title,
                    content = content,
                    isPinned = isPinned,
                    colorHex = colorHex,
                    tagsJson = tagsJson,
                    checklistsJson = checklistsJson,
                    attachedType = attachedType,
                    attachedId = attachedId,
                    attachedTitle = attachedTitle,
                    timestamp = timestamp,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun saveOrUpdateNote(note: MoneyNote, onSaved: ((Long) -> Unit)? = null) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            if (note.id == 0L) {
                val newId = repository.insertNote(note.copy(timestamp = now, updatedAt = now))
                onSaved?.invoke(newId)
            } else {
                repository.updateNote(note.copy(updatedAt = now))
                onSaved?.invoke(note.id)
            }
        }
    }

    fun toggleNotePin(note: MoneyNote) {
        viewModelScope.launch {
            repository.updateNotePinStatus(note.id, !note.isPinned)
        }
    }

    fun toggleNoteArchive(note: MoneyNote) {
        viewModelScope.launch {
            repository.updateNoteArchiveStatus(note.id, !note.isArchived)
        }
    }

    fun toggleNoteChecklistItem(note: MoneyNote, itemId: String) {
        viewModelScope.launch {
            val items = com.example.utils.NoteUtils.parseChecklist(note.checklistsJson)
            val updated = items.map { if (it.id == itemId) it.copy(isChecked = !it.isChecked) else it }
            repository.updateNote(
                note.copy(
                    checklistsJson = com.example.utils.NoteUtils.serializeChecklist(updated),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun deleteNote(id: Long) {
        viewModelScope.launch {
            repository.deleteNoteById(id)
        }
    }

    // --- People & Udhaar Operations ---

    fun addPerson(name: String, phone: String = "", note: String = "", onComplete: ((Long) -> Unit)? = null) {
        viewModelScope.launch {
            val id = repository.insertPerson(
                Person(
                    name = name.trim(),
                    phone = phone.trim(),
                    note = note.trim()
                )
            )
            onComplete?.invoke(id)
        }
    }

    fun updatePerson(id: Long, name: String, phone: String, note: String) {
        viewModelScope.launch {
            val existing = repository.getPersonByIdSync(id)
            if (existing != null) {
                repository.updatePerson(
                    existing.copy(
                        name = name.trim(),
                        phone = phone.trim(),
                        note = note.trim()
                    )
                )
            }
        }
    }

    fun deletePerson(personId: Long) {
        viewModelScope.launch {
            // Delete all linked transactions, repayments, and Udhaar entries for this person
            val entries = repository.getEntriesForPersonSync(personId)
            entries.forEach { entry ->
                entry.transactionId?.let { repository.deleteById(it) }
                val repayments = repository.getRepaymentsForEntrySync(entry.id)
                repayments.forEach { rep ->
                    rep.transactionId?.let { repository.deleteById(it) }
                    repository.deleteUdhaarRepaymentById(rep.id)
                }
                repository.deleteUdhaarEntryById(entry.id)
            }
            repository.deletePersonById(personId)
        }
    }

    /**
     * Edits an existing Udhaar entry (including historical entries) and updates its linked
     * account Transaction in lockstep so account balances and Udhaar balances stay reconciled.
     */
    fun updateUdhaarEntryDetails(
        entryId: Long,
        newAmount: Double,
        newAccount: String,
        newDate: Long,
        newDueDate: Long?,
        newNote: String
    ) {
        viewModelScope.launch {
            val existing = repository.getUdhaarEntryById(entryId) ?: return@launch
            val person = repository.getPersonByIdSync(existing.personId)
            val personName = person?.name ?: "Someone"
            val repayments = repository.getRepaymentsForEntrySync(entryId)
            val repaidSoFar = repayments.sumOf { it.amount }
            val cleanNewAmount = com.example.utils.FormatUtils.round2(newAmount.coerceAtLeast(repaidSoFar))
            val newRemaining = com.example.utils.FormatUtils.round2((cleanNewAmount - repaidSoFar).coerceAtLeast(0.0))
            val isSettled = newRemaining <= 0.001

            existing.transactionId?.let { txId ->
                val isIncome = existing.type.equals("TOOK", ignoreCase = true)
                val actionLabel = if (isIncome) "Borrowed from" else "Lent to"
                val txNote = "Udhaar: $actionLabel $personName" + if (newNote.isNotBlank()) " ($newNote)" else ""
                repository.update(
                    Transaction(
                        id = txId,
                        amount = cleanNewAmount,
                        isIncome = isIncome,
                        category = "Udhaar",
                        account = newAccount,
                        paymentMethod = newAccount,
                        note = txNote,
                        timestamp = newDate
                    )
                )
            }

            repository.updateUdhaarEntry(
                existing.copy(
                    amount = cleanNewAmount,
                    remainingAmount = newRemaining,
                    account = newAccount,
                    date = newDate,
                    dueDate = newDueDate,
                    note = newNote.trim(),
                    isSettled = isSettled
                )
            )
        }
    }

    fun addUdhaarEntry(
        personId: Long,
        type: String, // "GAVE" or "TOOK"
        amount: Double,
        account: String,
        date: Long,
        dueDate: Long?,
        note: String
    ) {
        viewModelScope.launch {
            val person = repository.getPersonByIdSync(personId)
            val personName = person?.name ?: "Someone"

            // GAVE: money lent (outflow, isIncome = false)
            // TOOK: money borrowed (inflow, isIncome = true)
            val isIncome = type.equals("TOOK", ignoreCase = true)
            val actionLabel = if (isIncome) "Borrowed from" else "Lent to"
            val txNote = "Udhaar: $actionLabel $personName" + if (note.isNotBlank()) " ($note)" else ""

            val txId = repository.insert(
                Transaction(
                    amount = amount,
                    isIncome = isIncome,
                    category = "Udhaar",
                    account = account,
                    paymentMethod = account,
                    note = txNote,
                    timestamp = date
                )
            )

            repository.insertUdhaarEntry(
                UdhaarEntry(
                    personId = personId,
                    type = type.uppercase(),
                    amount = amount,
                    remainingAmount = amount,
                    account = account,
                    date = date,
                    dueDate = dueDate,
                    note = note.trim(),
                    isSettled = false,
                    transactionId = txId.toInt()
                )
            )
        }
    }

    fun recordRepayment(
        entryId: Long,
        amount: Double,
        account: String,
        date: Long,
        note: String
    ) {
        viewModelScope.launch {
            val entry = repository.getUdhaarEntryById(entryId) ?: return@launch
            val person = repository.getPersonByIdSync(entry.personId)
            val personName = person?.name ?: "Someone"

            // If entry was GAVE: user gets money back -> isIncome = true
            // If entry was TOOK: user pays back -> isIncome = false
            val isIncome = entry.type.equals("GAVE", ignoreCase = true)
            val actionLabel = if (isIncome) "Repayment from" else "Repaid to"
            val txNote = "Udhaar: $actionLabel $personName" + if (note.isNotBlank()) " ($note)" else ""

            val txId = repository.insert(
                Transaction(
                    amount = amount,
                    isIncome = isIncome,
                    category = "Udhaar",
                    account = account,
                    paymentMethod = account,
                    note = txNote,
                    timestamp = date
                )
            )

            repository.insertUdhaarRepayment(
                UdhaarRepayment(
                    entryId = entryId,
                    personId = entry.personId,
                    amount = amount,
                    account = account,
                    date = date,
                    note = note.trim(),
                    transactionId = txId.toInt()
                )
            )

            val newRemaining = maxOf(0.0, entry.remainingAmount - amount)
            val isSettled = newRemaining <= 0.001
            repository.updateUdhaarEntry(
                entry.copy(
                    remainingAmount = newRemaining,
                    isSettled = isSettled
                )
            )
        }
    }

    fun deleteUdhaarEntry(entryId: Long) {
        viewModelScope.launch {
            val entry = repository.getUdhaarEntryById(entryId) ?: return@launch
            entry.transactionId?.let { repository.deleteById(it) }

            val repayments = repository.getRepaymentsForEntrySync(entryId)
            repayments.forEach { rep ->
                rep.transactionId?.let { repository.deleteById(it) }
                repository.deleteUdhaarRepaymentById(rep.id)
            }

            repository.deleteUdhaarEntryById(entryId)
        }
    }

    fun deleteUdhaarRepayment(repaymentId: Long) {
        viewModelScope.launch {
            val repayments = repository.allUdhaarRepayments.firstOrNull() ?: emptyList()
            val rep = repayments.firstOrNull { it.id == repaymentId } ?: return@launch
            rep.transactionId?.let { repository.deleteById(it) }

            val entry = repository.getUdhaarEntryById(rep.entryId)
            if (entry != null) {
                val restoredRemaining = minOf(entry.amount, entry.remainingAmount + rep.amount)
                repository.updateUdhaarEntry(
                    entry.copy(
                        remainingAmount = restoredRemaining,
                        isSettled = false
                    )
                )
            }
            repository.deleteUdhaarRepaymentById(repaymentId)
        }
    }

    // --- Tasks & Schedule Operations ---

    fun addTask(
        title: String,
        description: String = "",
        priority: String = "MEDIUM",
        category: String = "Personal",
        dueDateTime: Long? = null,
        repeatFrequency: String = "NONE",
        subtasks: List<Subtask> = emptyList(),
        isLinkedToMoney: Boolean = false,
        linkedAmount: Double? = null,
        linkedAccount: String? = null,
        linkedCategory: String? = null,
        reminderEnabled: Boolean = false
    ) {
        viewModelScope.launch {
            val task = TaskItem(
                title = title.trim(),
                description = description.trim(),
                priority = priority,
                category = category,
                dueDateTime = dueDateTime,
                repeatFrequency = repeatFrequency,
                subtasksJson = TaskUtils.serializeSubtasks(subtasks),
                isLinkedToMoney = isLinkedToMoney,
                linkedAmount = linkedAmount,
                linkedAccount = linkedAccount,
                linkedCategory = linkedCategory,
                reminderEnabled = reminderEnabled,
                reminderTime = if (reminderEnabled) dueDateTime else null
            )
            val id = repository.insertTask(task)
            if (reminderEnabled && dueDateTime != null && dueDateTime > System.currentTimeMillis()) {
                NotificationHelper.scheduleTaskReminder(
                    context,
                    id,
                    "Task: ${task.title}",
                    if (task.description.isNotBlank()) task.description else "Task is due now.",
                    dueDateTime
                )
            }
        }
    }

    fun updateTask(task: TaskItem) {
        viewModelScope.launch {
            repository.updateTask(task)
        }
    }

    fun deleteTask(id: Long) {
        viewModelScope.launch {
            val task = repository.getTaskByIdSync(id)
            if (task?.linkedTransactionId != null) {
                repository.deleteById(task.linkedTransactionId.toInt())
            }
            repository.deleteTaskById(id)
        }
    }

    fun toggleTaskStatus(task: TaskItem, newStatus: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            var createdTxId = task.linkedTransactionId

            if (newStatus == "DONE") {
                // Link to Money: if linked to money and no transaction created yet
                if (task.isLinkedToMoney && task.linkedAmount != null && task.linkedAmount > 0 && task.linkedTransactionId == null) {
                    val txId = repository.insert(
                        Transaction(
                            amount = task.linkedAmount,
                            isIncome = false,
                            category = task.linkedCategory ?: "Bills",
                            account = task.linkedAccount ?: "Bank Account",
                            paymentMethod = "Task Payment",
                            note = "Completed Task: ${task.title}",
                            timestamp = now
                        )
                    )
                    createdTxId = txId
                }

                // Repeat task logic (Daily/Weekly/Monthly) — avoid creating duplicate next occurrence if already created
                if (task.repeatFrequency != "NONE" && task.StatusOrNotDoneYet(newStatus)) {
                    val baseDue = task.dueDateTime ?: now
                    val nextDue = advanceDueDate(baseDue, task.repeatFrequency)
                    val existingTasks = allTasks.value
                    val alreadyExists = existingTasks.any {
                        it.id != task.id &&
                            it.title.equals(task.title, ignoreCase = true) &&
                            it.status != "DONE" &&
                            it.dueDateTime != null &&
                            kotlin.math.abs(it.dueDateTime - nextDue) < 12 * 60 * 60 * 1000L
                    }
                    if (!alreadyExists) {
                        repository.insertTask(
                            task.copy(
                                id = 0L,
                                status = "TODO",
                                dueDateTime = nextDue,
                                completedAt = null,
                                linkedTransactionId = null,
                                createdAt = now
                            )
                        )
                    }
                }
            } else if (task.status == "DONE" && newStatus != "DONE") {
                // If undone and transaction was created, restore balance by removing transaction
                if (createdTxId != null) {
                    repository.deleteById(createdTxId.toInt())
                    createdTxId = null
                }
            }

            val updatedTask = task.copy(
                status = newStatus,
                completedAt = if (newStatus == "DONE") now else null,
                linkedTransactionId = createdTxId
            )
            repository.updateTask(updatedTask)

            // Recalculate and persist TaskStreak immediately so both Home and Tasks screens show exact streak
            val currentStreakRec = repository.getStreakSync() ?: TaskStreak(id = 1)
            val updatedList = allTasks.value.map { if (it.id == updatedTask.id) updatedTask else it }
                .let { list -> if (list.any { it.id == updatedTask.id }) list else list + updatedTask }

            if (newStatus == "DONE") {
                val (newCurrent, newMax) = TaskUtils.calculateNewStreak(
                    currentStreakRec.lastCompletedDate,
                    currentStreakRec.currentStreak,
                    currentStreakRec.maxStreak
                )
                val doneCount = updatedList.count { it.status == "DONE" }
                repository.insertOrUpdateStreak(
                    currentStreakRec.copy(
                        currentStreak = newCurrent,
                        maxStreak = newMax,
                        lastCompletedDate = TaskUtils.getTodayIsoDate(),
                        totalCompletedTasks = maxOf(doneCount, currentStreakRec.totalCompletedTasks + if (task.status != "DONE") 1 else 0)
                    )
                )
            } else {
                val (recalcStreak, recalcMax, lastDate) = TaskUtils.computeStreakFromTasks(
                    updatedList,
                    currentStreakRec.maxStreak
                )
                val doneCount = updatedList.count { it.status == "DONE" }
                repository.insertOrUpdateStreak(
                    currentStreakRec.copy(
                        currentStreak = recalcStreak,
                        maxStreak = recalcMax,
                        lastCompletedDate = lastDate,
                        totalCompletedTasks = doneCount
                    )
                )
            }
        }
    }

    private fun TaskItem.StatusOrNotDoneYet(newStatus: String): Boolean =
        this.status != "DONE" && newStatus == "DONE"

    fun toggleSubtask(task: TaskItem, subtaskId: String) {
        viewModelScope.launch {
            val list = TaskUtils.parseSubtasks(task.subtasksJson)
            val updated = list.map { if (it.id == subtaskId) it.copy(isCompleted = !it.isCompleted) else it }
            repository.updateTask(task.copy(subtasksJson = TaskUtils.serializeSubtasks(updated)))
        }
    }

    // Schedule Events
    fun addScheduleEvent(
        title: String,
        description: String = "",
        startTime: Long,
        endTime: Long,
        location: String = "",
        category: String = "Meeting",
        reminderEnabled: Boolean = false,
        reminderOffsetMinutes: Int = 15
    ) {
        viewModelScope.launch {
            val event = ScheduleEvent(
                title = title.trim(),
                description = description.trim(),
                startTime = startTime,
                endTime = endTime,
                location = location.trim(),
                category = category,
                reminderEnabled = reminderEnabled,
                reminderOffsetMinutes = reminderOffsetMinutes
            )
            val id = repository.insertEvent(event)
            if (reminderEnabled) {
                val trigger = startTime - (reminderOffsetMinutes * 60 * 1000L)
                if (trigger > System.currentTimeMillis()) {
                    NotificationHelper.scheduleTaskReminder(
                        context,
                        id + 50000,
                        "Upcoming Event: ${event.title}",
                        "Starts in $reminderOffsetMinutes minutes" + if (event.location.isNotBlank()) " at ${event.location}" else "",
                        trigger
                    )
                }
            }
        }
    }

    fun updateScheduleEvent(event: ScheduleEvent) {
        viewModelScope.launch {
            repository.updateEvent(event)
        }
    }

    fun deleteScheduleEvent(id: Long) {
        viewModelScope.launch {
            repository.deleteEventById(id)
        }
    }

    // --- Split Bills Operations ---

    fun createSplitGroup(
        name: String,
        type: String,
        members: List<String>,
        linkToUdhaar: Boolean = false,
        onCreated: ((Long) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val group = SplitGroup(
                name = name.trim(),
                type = type,
                membersJson = com.example.utils.SplitUtils.serializeMembers(members),
                linkToUdhaar = linkToUdhaar
            )
            val id = repository.insertSplitGroup(group)
            if (linkToUdhaar) {
                // Ensure group members exist in People & Udhaar
                val existingPeople = people.value
                members.filter { !it.equals("Me", ignoreCase = true) }.forEach { memberName ->
                    if (existingPeople.none { it.name.equals(memberName, ignoreCase = true) }) {
                        repository.insertPerson(Person(name = memberName, note = "Split Group: ${group.name}"))
                    }
                }
            }
            onCreated?.invoke(id)
        }
    }

    fun updateSplitGroup(group: SplitGroup) {
        viewModelScope.launch {
            repository.updateSplitGroup(group)
        }
    }

    fun deleteSplitGroup(groupId: Long) {
        viewModelScope.launch {
            val group = repository.getSplitGroupByIdSync(groupId)
            val grpExpenses = repository.getSplitExpensesForGroupSync(groupId)
            grpExpenses.forEach { exp ->
                exp.myExpenseTxId?.let { repository.deleteById(it.toInt()) }
                exp.receivableTxId?.let { repository.deleteById(it.toInt()) }
            }
            val grpSettlements = repository.getSplitSettlementsForGroupSync(groupId)
            grpSettlements.forEach { st ->
                st.transactionId?.let { repository.deleteById(it.toInt()) }
            }
            if (group != null) {
                val syncedNote = "Split Group [${group.name}] Net Balance"
                udhaarEntries.value.filter { it.note == syncedNote }.forEach { linkedEntry ->
                    linkedEntry.transactionId?.let { repository.deleteById(it) }
                    repository.deleteUdhaarEntryById(linkedEntry.id)
                }
            }
            repository.deleteSplitGroupById(groupId)
        }
    }

    fun addSplitExpense(
        groupId: Long,
        groupName: String,
        title: String,
        totalAmount: Double,
        category: String,
        paidBy: String,
        paymentAccount: String,
        splitType: String,
        splits: List<MemberSplit>,
        note: String = "",
        timestamp: Long = System.currentTimeMillis()
    ) {
        viewModelScope.launch {
            val myShare = com.example.utils.SplitUtils.round2(
                splits.firstOrNull { it.memberName.equals("Me", ignoreCase = true) }?.amount ?: 0.0
            )
            val othersShare = com.example.utils.SplitUtils.round2((totalAmount - myShare).coerceAtLeast(0.0))
            val resolvedAccount = paymentAccount.takeIf { accName ->
                accounts.value.any { it.name.equals(accName, ignoreCase = true) }
            } ?: _defaultAccount.value.takeIf { accName ->
                accounts.value.any { it.name.equals(accName, ignoreCase = true) }
            } ?: accounts.value.firstOrNull()?.name ?: "Bank Account"

            var myExpenseTxId: Long? = null
            var receivableTxId: Long? = null

            if (paidBy.equals("Me", ignoreCase = true)) {
                // I paid:
                // 1. Only MY share counts as my expense in reports (deducted from resolvedAccount)
                if (myShare > 0.0) {
                    myExpenseTxId = repository.insert(
                        Transaction(
                            amount = myShare,
                            isIncome = false,
                            category = category,
                            account = resolvedAccount,
                            paymentMethod = if (resolvedAccount.contains("Cash", ignoreCase = true)) "Cash" else "UPI",
                            note = "[Split: $groupName] $title (My Share)",
                            timestamp = timestamp
                        )
                    )
                }
                // 2. The rest is money owed to me (deducted from resolvedAccount under "Udhaar" so it does NOT count as expense in reports)
                if (othersShare > 0.0) {
                    receivableTxId = repository.insert(
                        Transaction(
                            amount = othersShare,
                            isIncome = false,
                            category = "Udhaar",
                            account = resolvedAccount,
                            paymentMethod = if (resolvedAccount.contains("Cash", ignoreCase = true)) "Cash" else "UPI",
                            note = "[Split: $groupName] $title (Owed to me by group)",
                            timestamp = timestamp
                        )
                    )
                }
            } else {
                // Someone else paid:
                // 1. Only MY share counts as my expense in reports (on resolvedAccount)
                // 2. Offset by a balancing "Udhaar" inflow on resolvedAccount so my liquid account balance
                //    is unchanged (-myShare + myShare = 0) until Settle Up, with zero orphaned accounts!
                if (myShare > 0.0) {
                    myExpenseTxId = repository.insert(
                        Transaction(
                            amount = myShare,
                            isIncome = false,
                            category = category,
                            account = resolvedAccount,
                            paymentMethod = "Split",
                            note = "[Split: $groupName] $title (Paid by $paidBy, My Share)",
                            timestamp = timestamp
                        )
                    )
                    receivableTxId = repository.insert(
                        Transaction(
                            amount = myShare,
                            isIncome = true,
                            category = "Udhaar",
                            account = resolvedAccount,
                            paymentMethod = "Split",
                            note = "[Split: $groupName] $title (Funded by $paidBy, Owed by Me)",
                            timestamp = timestamp
                        )
                    )
                }
            }

            val splitExpense = SplitExpense(
                groupId = groupId,
                title = title.trim(),
                totalAmount = com.example.utils.SplitUtils.round2(totalAmount),
                category = category,
                paidBy = paidBy,
                paymentAccount = resolvedAccount,
                splitType = splitType,
                splitsJson = com.example.utils.SplitUtils.serializeSplits(splits),
                myShareAmount = myShare,
                myExpenseTxId = myExpenseTxId,
                receivableTxId = receivableTxId,
                note = note.trim(),
                timestamp = timestamp
            )
            repository.insertSplitExpense(splitExpense)
        }
    }

    fun deleteSplitExpense(expense: SplitExpense) {
        viewModelScope.launch {
            expense.myExpenseTxId?.let { repository.deleteById(it.toInt()) }
            expense.receivableTxId?.let { repository.deleteById(it.toInt()) }
            repository.deleteSplitExpenseById(expense.id)
        }
    }

    fun recordSplitSettlement(
        groupId: Long,
        groupName: String,
        fromMember: String,
        toMember: String,
        amount: Double,
        account: String,
        note: String = "",
        timestamp: Long = System.currentTimeMillis()
    ) {
        viewModelScope.launch {
            val cleanAmt = com.example.utils.SplitUtils.round2(amount)
            var txId: Long? = null

            if (fromMember.equals("Me", ignoreCase = true)) {
                // I am paying toMember -> deducts from my account, category = "Udhaar" (never double-counted as expense!)
                txId = repository.insert(
                    Transaction(
                        amount = cleanAmt,
                        isIncome = false,
                        category = "Udhaar",
                        account = account,
                        paymentMethod = if (account.contains("Cash", ignoreCase = true)) "Cash" else "UPI",
                        note = "[Split Settle: $groupName] Paid to $toMember" + if (note.isNotBlank()) " ($note)" else "",
                        timestamp = timestamp
                    )
                )
            } else if (toMember.equals("Me", ignoreCase = true)) {
                // fromMember is paying Me -> adds to my account, category = "Udhaar" (never double-counted as income!)
                txId = repository.insert(
                    Transaction(
                        amount = cleanAmt,
                        isIncome = true,
                        category = "Udhaar",
                        account = account,
                        paymentMethod = if (account.contains("Cash", ignoreCase = true)) "Cash" else "UPI",
                        note = "[Split Settle: $groupName] Received from $fromMember" + if (note.isNotBlank()) " ($note)" else "",
                        timestamp = timestamp
                    )
                )
            }

            repository.insertSplitSettlement(
                SplitSettlement(
                    groupId = groupId,
                    fromMember = fromMember,
                    toMember = toMember,
                    amount = cleanAmt,
                    account = account,
                    transactionId = txId,
                    note = note.trim(),
                    timestamp = timestamp
                )
            )
        }
    }

    fun deleteSplitSettlement(settlement: SplitSettlement) {
        viewModelScope.launch {
            settlement.transactionId?.let { repository.deleteById(it.toInt()) }
            repository.deleteSplitSettlementById(settlement.id)
        }
    }

    fun syncSplitGroupToUdhaar(summary: SplitGroupSummary, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val myDebts = summary.simplifiedDebts.filter {
                it.fromMember.equals("Me", ignoreCase = true) || it.toMember.equals("Me", ignoreCase = true)
            }
            if (myDebts.isEmpty()) {
                onResult("No unsettled balances involving you in ${summary.group.name} to link.")
                return@launch
            }
            var syncedCount = 0
            val currentPeople = repository.allPeople
            val existingPeopleList = people.value.toMutableList()

            myDebts.forEach { debt ->
                val otherName = if (debt.fromMember.equals("Me", ignoreCase = true)) debt.toMember else debt.fromMember
                val isOwedToMe = debt.toMember.equals("Me", ignoreCase = true)
                var person = existingPeopleList.firstOrNull { it.name.equals(otherName, ignoreCase = true) }
                if (person == null) {
                    val newId = repository.insertPerson(Person(name = otherName, note = "Linked from Split Group: ${summary.group.name}"))
                    person = Person(id = newId, name = otherName, note = "Linked from Split Group: ${summary.group.name}")
                    existingPeopleList.add(person)
                }
                val entryNote = "Split Group [${summary.group.name}] Net Balance"
                val existingEntries = repository.getEntriesForPersonSync(person.id)
                val existingSplitEntry = existingEntries.firstOrNull { it.note == entryNote && !it.isSettled }
                val type = if (isOwedToMe) "GAVE" else "TOOK"
                if (existingSplitEntry != null) {
                    repository.updateUdhaarEntry(
                        existingSplitEntry.copy(
                            type = type,
                            amount = debt.amount,
                            remainingAmount = debt.amount,
                            date = System.currentTimeMillis()
                        )
                    )
                } else {
                    repository.insertUdhaarEntry(
                        UdhaarEntry(
                            personId = person.id,
                            type = type,
                            amount = debt.amount,
                            remainingAmount = debt.amount,
                            account = "Split Group",
                            date = System.currentTimeMillis(),
                            note = entryNote
                        )
                    )
                }
                syncedCount++
            }
            repository.updateSplitGroup(summary.group.copy(linkToUdhaar = true))
            onResult("Synced $syncedCount balance(s) from '${summary.group.name}' to People & Udhaar ledger!")
        }
    }

    // --- Habits Operations ---

    fun addHabit(habit: Habit) {
        viewModelScope.launch {
            val id = repository.insertHabit(habit)
            if (habit.reminderEnabled) {
                val cal = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, habit.reminderHour)
                    set(java.util.Calendar.MINUTE, habit.reminderMinute)
                    set(java.util.Calendar.SECOND, 0)
                    if (timeInMillis <= System.currentTimeMillis()) {
                        add(java.util.Calendar.DAY_OF_YEAR, 1)
                    }
                }
                NotificationHelper.scheduleTaskReminder(
                    context,
                    id + 80000L,
                    "Habit Reminder: ${habit.name}",
                    if (habit.targetCount > 1) "Target: ${habit.targetCount} ${habit.unitLabel}" else "Keep your streak alive today!",
                    cal.timeInMillis
                )
            }
        }
    }

    fun updateHabit(habit: Habit) {
        viewModelScope.launch {
            repository.updateHabit(habit)
        }
    }

    fun deleteHabit(habitId: Long) {
        viewModelScope.launch {
            repository.deleteHabitById(habitId)
        }
    }

    fun toggleHabitForDate(habit: Habit, dateIso: String) {
        viewModelScope.launch {
            val existing = repository.getLogForHabitAndDate(habit.id, dateIso)
            val target = habit.targetCount.coerceAtLeast(1)
            val isCurrentlyDone = existing?.status == "COMPLETED" || (existing?.currentCount ?: 0) >= target
            if (isCurrentlyDone) {
                repository.insertOrUpdateHabitLog(
                    HabitLog(
                        id = existing?.id ?: 0L,
                        habitId = habit.id,
                        dateIso = dateIso,
                        currentCount = 0,
                        status = "PENDING",
                        updatedAt = System.currentTimeMillis()
                    )
                )
            } else {
                repository.insertOrUpdateHabitLog(
                    HabitLog(
                        id = existing?.id ?: 0L,
                        habitId = habit.id,
                        dateIso = dateIso,
                        currentCount = target,
                        status = "COMPLETED",
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun adjustHabitCountForDate(habit: Habit, dateIso: String, delta: Int) {
        viewModelScope.launch {
            val existing = repository.getLogForHabitAndDate(habit.id, dateIso)
            val target = habit.targetCount.coerceAtLeast(1)
            val newCount = ((existing?.currentCount ?: 0) + delta).coerceAtLeast(0)
            val newStatus = if (newCount >= target) "COMPLETED" else "PENDING"
            repository.insertOrUpdateHabitLog(
                HabitLog(
                    id = existing?.id ?: 0L,
                    habitId = habit.id,
                    dateIso = dateIso,
                    currentCount = newCount,
                    status = newStatus,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun skipHabitDay(habit: Habit, dateIso: String) {
        viewModelScope.launch {
            val existing = repository.getLogForHabitAndDate(habit.id, dateIso)
            val newStatus = if (existing?.status == "SKIPPED") "PENDING" else "SKIPPED"
            repository.insertOrUpdateHabitLog(
                HabitLog(
                    id = existing?.id ?: 0L,
                    habitId = habit.id,
                    dateIso = dateIso,
                    currentCount = 0,
                    status = newStatus,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    // ==================== INVESTMENTS & NET WORTH OPERATIONS ====================

    fun addOrUpdateHolding(
        holding: InvestmentHolding,
        deductFromAccount: Boolean = false,
        accountName: String = "Bank Account"
    ) {
        viewModelScope.launch {
            val isNew = holding.id == 0L
            repository.insertHolding(holding.copy(updatedAt = System.currentTimeMillis()))
            if (isNew && deductFromAccount && holding.investedAmount > 0.0) {
                repository.insert(
                    Transaction(
                        amount = holding.investedAmount,
                        isIncome = false,
                        category = "Investment",
                        subcategory = holding.name,
                        account = accountName,
                        note = "Asset purchase (${holding.assetType}) — excluded from expense reports",
                        timestamp = holding.buyDate
                    )
                )
            }
        }
    }

    /**
     * Records a SIP installment:
     * 1. Increases the holding's investedAmount and currentValue by sipMonthlyAmount.
     * 2. Creates a transfer/investment entry (`category = "Investment"`, `isIncome = false`)
     *    on `sipLinkedAccount` so the bank balance is updated without counting as an expense in reports.
     */
    fun recordSipInstallment(holding: InvestmentHolding, customAmount: Double? = null) {
        val sipAmt = customAmount ?: holding.sipMonthlyAmount
        if (sipAmt <= 0.0) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val acct = holding.sipLinkedAccount.ifBlank { "Bank Account" }
            val updated = holding.copy(
                investedAmount = holding.investedAmount + sipAmt,
                currentValue = holding.currentValue + sipAmt,
                updatedAt = now
            )
            repository.updateHolding(updated)
            repository.insert(
                Transaction(
                    amount = sipAmt,
                    isIncome = false,
                    category = "Investment",
                    subcategory = "SIP: ${holding.name}",
                    account = acct,
                    note = "Monthly SIP transfer to ${holding.name} (excluded from expense reports)",
                    timestamp = now
                )
            )
        }
    }

    fun updateHoldingCurrentValue(holding: InvestmentHolding, newValue: Double) {
        viewModelScope.launch {
            repository.updateHolding(
                holding.copy(
                    currentValue = newValue.coerceAtLeast(0.0),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun deleteHolding(id: Long) {
        viewModelScope.launch {
            repository.deleteHoldingById(id)
        }
    }

    fun addOrUpdateLiability(liability: LiabilityItem) {
        viewModelScope.launch {
            repository.insertLiability(liability.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    fun payLiabilityEmi(liability: LiabilityItem, amount: Double, accountName: String) {
        if (amount <= 0.0) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val newRemaining = (liability.remainingBalance - amount).coerceAtLeast(0.0)
            repository.updateLiability(
                liability.copy(
                    remainingBalance = newRemaining,
                    updatedAt = now
                )
            )
            repository.insert(
                Transaction(
                    amount = amount,
                    isIncome = false,
                    category = "Bills",
                    subcategory = "EMI: ${liability.name}",
                    account = accountName,
                    note = "Loan/EMI payment for ${liability.name} (Remaining: ₹${newRemaining.toInt()})",
                    timestamp = now
                )
            )
        }
    }

    fun deleteLiability(id: Long) {
        viewModelScope.launch {
            repository.deleteLiabilityById(id)
        }
    }

    // ==================== DOCUMENT VAULT OPERATIONS ====================

    fun addOrUpdateVaultDocument(doc: VaultDocument) {
        viewModelScope.launch {
            val savedId = repository.insertVaultDocument(doc)
            val effectiveId = if (doc.id != 0L) doc.id else savedId
            val exp = doc.expiryDate
            if (doc.reminderEnabled && exp != null) {
                val dayMs = 24L * 60L * 60L * 1000L
                listOf(30, 7, 1).forEachIndexed { idx, daysBefore ->
                    val triggerAt = exp - (daysBefore * dayMs)
                    NotificationHelper.scheduleTaskReminder(
                        context = context,
                        id = 900000L + (effectiveId * 10L) + idx,
                        title = "${doc.category} Expiring in $daysBefore Day${if (daysBefore > 1) "s" else ""}",
                        message = "${doc.title} expires soon. Tap to review in Document Vault.",
                        triggerAtMillis = triggerAt
                    )
                }
            }
        }
    }

    fun deleteVaultDocument(doc: VaultDocument) {
        viewModelScope.launch {
            com.example.utils.DocumentVaultUtils.deleteLocalFileIfExists(doc.localFilePath)
            repository.deleteVaultDocumentById(doc.id)
        }
    }

    fun getVaultPin(): String {
        return _appPin.value
    }

    fun setVaultPin(newPin: String) {
        val clean = newPin.filter { it.isDigit() }
        if (clean.length in 4..6) {
            _appPin.value = clean
            prefs.edit().putString("vault_pin", clean).apply()
        }
    }

    fun verifyVaultPin(inputPin: String): Boolean {
        return inputPin == _appPin.value
    }

    // ==================== OPTIONAL FIREBASE CLOUD SYNC (AUTH + FIRESTORE) ====================

    private val cloudSyncRepository by lazy {
        com.example.utils.CloudSyncRepository(context)
    }

    private val _cloudSyncStatusMessage = MutableStateFlow<String?>(null)
    val cloudSyncStatusMessage: StateFlow<String?> = _cloudSyncStatusMessage.asStateFlow()

    private val _isCloudSyncing = MutableStateFlow(false)
    val isCloudSyncing: StateFlow<Boolean> = _isCloudSyncing.asStateFlow()

    private val _cloudBackups = MutableStateFlow<List<com.example.utils.CloudBackupSnapshot>>(emptyList())
    val cloudBackups: StateFlow<List<com.example.utils.CloudBackupSnapshot>> = _cloudBackups.asStateFlow()

    fun clearCloudSyncMessage() {
        _cloudSyncStatusMessage.value = null
    }

    fun refreshCloudBackups() {
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser ?: return
        viewModelScope.launch {
            val result = cloudSyncRepository.getUserCloudBackups()
            result.onSuccess { list ->
                _cloudBackups.value = list
            }.onFailure { err ->
                _cloudSyncStatusMessage.value = "Failed to load cloud backups: ${err.localizedMessage ?: "Permission or network error"}"
            }
        }
    }

    fun syncBackupToCloud(onComplete: (Boolean, String) -> Unit = { _, _ -> }) {
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (user == null) {
            val msg = "Please sign in with Google first to sync with Firebase Cloud."
            _cloudSyncStatusMessage.value = msg
            onComplete(false, msg)
            return
        }
        viewModelScope.launch {
            _isCloudSyncing.value = true
            try {
                val backupData = buildFullBackupData()
                val preview = com.example.ui.BackupHelper.buildPreviewSummary(backupData)
                val jsonPayload = com.example.ui.BackupHelper.serializeBackupToJson(backupData)
                val summaryText = "${preview.transactionsCount} txns, ${preview.accountsCount} accounts, ${preview.notesCount} notes, ${preview.tasksAndEventsCount} tasks, ${preview.habitsAndLogsCount} habits"

                val result = cloudSyncRepository.uploadCloudBackup(
                    backupId = "latest",
                    summaryText = summaryText,
                    transactionCount = preview.transactionsCount,
                    totalItemsCount = preview.totalRecordsCount,
                    backupPayload = jsonPayload
                )
                _isCloudSyncing.value = false
                result.onSuccess {
                    val now = System.currentTimeMillis()
                    _lastCloudSyncTimestamp.value = now
                    prefs.edit().putLong("last_cloud_sync_timestamp", now).apply()
                    recordBackupCompleted(now)
                    val msg = "Synced ${preview.totalRecordsCount} records to Firebase Cloud!"
                    _cloudSyncStatusMessage.value = msg
                    refreshCloudBackups()
                    onComplete(true, msg)
                }.onFailure { err ->
                    val msg = "Cloud sync failed: ${err.localizedMessage ?: "Check connection"}"
                    _cloudSyncStatusMessage.value = msg
                    onComplete(false, msg)
                }
            } catch (e: Exception) {
                _isCloudSyncing.value = false
                val msg = "Cloud sync error: ${e.localizedMessage ?: "Unexpected error"}"
                _cloudSyncStatusMessage.value = msg
                onComplete(false, msg)
            }
        }
    }

    fun fetchCloudBackupForRestorePreview(
        backupId: String = "latest",
        onResult: (BackupValidationResult) -> Unit
    ) {
        viewModelScope.launch {
            _isCloudSyncing.value = true
            val res = cloudSyncRepository.getCloudBackupById(backupId)
            _isCloudSyncing.value = false
            res.onSuccess { snapshot ->
                if (snapshot == null || snapshot.backupPayload.isBlank()) {
                    onResult(
                        BackupValidationResult(
                            isValid = false,
                            errors = listOf("No cloud backup found on Firestore for this account yet."),
                            warnings = emptyList(),
                            preview = null,
                            parsedData = null
                        )
                    )
                } else {
                    val validation = com.example.ui.BackupHelper.parseAndValidateBackupJson(snapshot.backupPayload)
                    onResult(validation)
                }
            }.onFailure { err ->
                onResult(
                    BackupValidationResult(
                        isValid = false,
                        errors = listOf("Failed to fetch cloud backup: ${err.localizedMessage ?: "Error"}"),
                        warnings = emptyList(),
                        preview = null,
                        parsedData = null
                    )
                )
            }
        }
    }

    // ==================== GEMINI AI ASSISTANT & HELPERS ====================

    private val geminiAiService by lazy {
        com.example.utils.GeminiAiService(context)
    }

    private val _smartEntryState = MutableStateFlow<com.example.utils.AiUiState<com.example.utils.SmartEntryResult>>(
        com.example.utils.AiUiState.Idle
    )
    val smartEntryState: StateFlow<com.example.utils.AiUiState<com.example.utils.SmartEntryResult>> =
        _smartEntryState.asStateFlow()

    private val _receiptScanState = MutableStateFlow<com.example.utils.AiUiState<com.example.utils.ReceiptScanResult>>(
        com.example.utils.AiUiState.Idle
    )
    val receiptScanState: StateFlow<com.example.utils.AiUiState<com.example.utils.ReceiptScanResult>> =
        _receiptScanState.asStateFlow()

    private val _askMyDataState = MutableStateFlow<com.example.utils.AiUiState<com.example.utils.AskMyDataResult>>(
        com.example.utils.AiUiState.Idle
    )
    val askMyDataState: StateFlow<com.example.utils.AiUiState<com.example.utils.AskMyDataResult>> =
        _askMyDataState.asStateFlow()

    private val _monthlyInsightsState = MutableStateFlow<com.example.utils.AiUiState<com.example.utils.MonthlyInsightsResult>>(
        com.example.utils.AiUiState.Idle
    )
    val monthlyInsightsState: StateFlow<com.example.utils.AiUiState<com.example.utils.MonthlyInsightsResult>> =
        _monthlyInsightsState.asStateFlow()

    private val _noteHelperState = MutableStateFlow<com.example.utils.AiUiState<com.example.utils.NoteHelperResult>>(
        com.example.utils.AiUiState.Idle
    )
    val noteHelperState: StateFlow<com.example.utils.AiUiState<com.example.utils.NoteHelperResult>> =
        _noteHelperState.asStateFlow()

    private val _taskHelperState = MutableStateFlow<com.example.utils.AiUiState<com.example.utils.TaskHelperResult>>(
        com.example.utils.AiUiState.Idle
    )
    val taskHelperState: StateFlow<com.example.utils.AiUiState<com.example.utils.TaskHelperResult>> =
        _taskHelperState.asStateFlow()

    fun getComputedFinancialSummaryPreview(): String {
        return com.example.utils.AiDataSummaryBuilder.buildComputedFinancialSummary(
            accounts = accounts.value,
            transactions = transactions.value,
            budgets = budgets.value,
            goals = goals.value,
            bills = allBills.value,
            overallToReceive = overallToReceive.value,
            overallToPay = overallToPay.value
        )
    }

    fun parseSmartEntryWithAi(userInput: String) {
        viewModelScope.launch {
            _smartEntryState.value = com.example.utils.AiUiState.Loading("Parsing smart entry with Gemini...")
            _smartEntryState.value = geminiAiService.parseSmartEntry(
                userInput = userInput,
                accounts = accounts.value,
                categories = categories.value
            )
        }
    }

    fun resetSmartEntryState() {
        _smartEntryState.value = com.example.utils.AiUiState.Idle
    }

    fun confirmAndSaveSmartEntries(
        entries: List<com.example.utils.SmartParsedEntry>,
        onComplete: (Int) -> Unit = {}
    ) {
        val selectedEntries = entries.filter { it.isReadyToSave }
        if (selectedEntries.isEmpty()) {
            onComplete(0)
            return
        }
        viewModelScope.launch {
            val savedCount = persistSmartEntriesSuspend(selectedEntries)
            _smartEntryState.value = com.example.utils.AiUiState.Idle
            onComplete(savedCount)
        }
    }

    fun quickSaveSmartEntryWithAi(
        userInput: String,
        onComplete: (Int, String) -> Unit = { _, _ -> }
    ) {
        if (userInput.isBlank()) {
            onComplete(0, "Please enter text to add.")
            return
        }
        viewModelScope.launch {
            _smartEntryState.value = com.example.utils.AiUiState.Loading("Saving with Gemini AI...")
            val resultState = geminiAiService.parseSmartEntry(
                userInput = userInput,
                accounts = accounts.value,
                categories = categories.value
            )
            when (resultState) {
                is com.example.utils.AiUiState.Success -> {
                    val ready = resultState.data.entries.filter { it.isReadyToSave }
                    if (ready.isEmpty()) {
                        _smartEntryState.value = com.example.utils.AiUiState.Idle
                        onComplete(0, "Could not detect valid entries. Try e.g. 'chai 20 cash, task pay bill'.")
                    } else {
                        val savedCount = persistSmartEntriesSuspend(ready)
                        val summaryLabels = ready.take(4).joinToString(", ") { entry ->
                            when (entry.entryKind.uppercase()) {
                                "TASK" -> "Task: ${entry.note}"
                                "SCHEDULE" -> "Schedule: ${entry.note}"
                                "HABIT" -> "Habit: ${entry.note}"
                                "NOTE" -> "Note: ${entry.note}"
                                else -> "₹${entry.amount.toLong()} ${entry.note}"
                            }
                        }
                        _smartEntryState.value = com.example.utils.AiUiState.Idle
                        onComplete(savedCount, "Saved $savedCount item(s): $summaryLabels")
                    }
                }
                is com.example.utils.AiUiState.Error -> {
                    _smartEntryState.value = com.example.utils.AiUiState.Idle
                    onComplete(0, resultState.message)
                }
                else -> {
                    _smartEntryState.value = com.example.utils.AiUiState.Idle
                    onComplete(0, "Could not complete request.")
                }
            }
        }
    }

    private suspend fun persistSmartEntriesSuspend(
        selectedEntries: List<com.example.utils.SmartParsedEntry>
    ): Int {
        var savedCount = 0
        selectedEntries.forEach { item ->
            when (item.entryKind.uppercase()) {
                "TASK" -> {
                    repository.insertTask(
                        TaskItem(
                            title = item.note.trim().ifBlank { "New Task" },
                            description = "Added via Gemini AI",
                            priority = "MEDIUM",
                            category = item.category.ifBlank { "Personal" },
                            dueDateTime = item.timestamp + (24L * 60L * 60L * 1000L),
                            status = "TODO"
                        )
                    )
                    savedCount++
                }
                "SCHEDULE" -> {
                    val startMs = item.timestamp + (60L * 60L * 1000L)
                    repository.insertEvent(
                        ScheduleEvent(
                            title = item.note.trim().ifBlank { "Scheduled Event" },
                            description = "Added via Gemini AI",
                            startTime = startMs,
                            endTime = startMs + (60L * 60L * 1000L),
                            location = "",
                            category = item.category.ifBlank { "Personal" }
                        )
                    )
                    savedCount++
                }
                "HABIT" -> {
                    repository.insertHabit(
                        Habit(
                            name = item.note.trim().ifBlank { "Daily Habit" },
                            frequencyType = "DAILY",
                            timesPerWeek = 7,
                            createdAt = item.timestamp
                        )
                    )
                    savedCount++
                }
                "NOTE" -> {
                    val now = System.currentTimeMillis()
                    repository.insertNote(
                        MoneyNote(
                            title = item.note.trim().take(45).ifBlank { "Quick Note" },
                            content = item.note.trim(),
                            timestamp = now,
                            updatedAt = now
                        )
                    )
                    savedCount++
                }
                "UDHAAR_GOT", "UDHAAR_GAVE" -> {
                    if (item.amount > 0.0) {
                        val pName = item.personName.trim()
                        if (pName.isNotBlank()) {
                            val udhaarType = if (item.entryKind.uppercase() == "UDHAAR_GOT") "TOOK" else "GAVE"
                            val existingPerson = people.value.firstOrNull { it.name.equals(pName, ignoreCase = true) }
                            val personId = existingPerson?.id ?: repository.insertPerson(
                                Person(name = pName, phone = "", note = "Created via AI Smart Entry")
                            )
                            val isIncome = udhaarType == "TOOK"
                            val actionLabel = if (isIncome) "Borrowed/Received from" else "Lent/Gave to"
                            val txNote = "Udhaar: $actionLabel $pName (${item.note})"
                            val txId = repository.insert(
                                Transaction(
                                    amount = item.amount,
                                    isIncome = isIncome,
                                    category = "Udhaar",
                                    account = item.account,
                                    paymentMethod = item.account,
                                    note = txNote,
                                    timestamp = item.timestamp
                                )
                            )
                            repository.insertUdhaarEntry(
                                UdhaarEntry(
                                    personId = personId,
                                    type = udhaarType,
                                    amount = item.amount,
                                    remainingAmount = item.amount,
                                    account = item.account,
                                    date = item.timestamp,
                                    dueDate = null,
                                    note = item.note,
                                    isSettled = false,
                                    transactionId = txId.toInt()
                                )
                            )
                        } else {
                            repository.insert(
                                Transaction(
                                    amount = item.amount,
                                    isIncome = item.entryKind.uppercase() == "UDHAAR_GOT",
                                    category = item.category.ifBlank { "Other" },
                                    account = item.account,
                                    paymentMethod = item.account,
                                    note = item.note,
                                    timestamp = item.timestamp
                                )
                            )
                        }
                        savedCount++
                    }
                }
                "TRANSFER" -> {
                    if (item.amount > 0.0) {
                        val dest = item.toAccount.ifBlank {
                            accounts.value.firstOrNull { !it.name.equals(item.account, ignoreCase = true) }?.name ?: "Bank Account"
                        }
                        addTransfer(
                            amount = item.amount,
                            fromAccount = item.account,
                            toAccount = dest,
                            timestamp = item.timestamp
                        )
                        savedCount++
                    }
                }
                "INCOME" -> {
                    if (item.amount > 0.0) {
                        repository.insert(
                            Transaction(
                                amount = item.amount,
                                isIncome = true,
                                category = item.category.ifBlank { "Salary" },
                                account = item.account,
                                paymentMethod = item.account,
                                note = item.note,
                                timestamp = item.timestamp
                            )
                        )
                        savedCount++
                    }
                }
                else -> {
                    if (item.amount > 0.0) {
                        repository.insert(
                            Transaction(
                                amount = item.amount,
                                isIncome = false,
                                category = item.category.ifBlank { "Other" },
                                account = item.account,
                                paymentMethod = item.account,
                                note = item.note,
                                timestamp = item.timestamp
                            )
                        )
                        savedCount++
                    }
                }
            }
        }
        return savedCount
    }

    fun scanReceiptWithAi(imageUri: android.net.Uri) {
        viewModelScope.launch {
            _receiptScanState.value = com.example.utils.AiUiState.Loading("Scanning receipt with Gemini Vision...")
            _receiptScanState.value = geminiAiService.scanReceiptPhoto(
                imageUri = imageUri,
                categories = categories.value
            )
        }
    }

    fun resetReceiptScanState() {
        _receiptScanState.value = com.example.utils.AiUiState.Idle
    }

    fun askMyDataWithAi(question: String) {
        viewModelScope.launch {
            _askMyDataState.value = com.example.utils.AiUiState.Loading("Analyzing your computed financial summaries...")
            val summaryJson = getComputedFinancialSummaryPreview()
            _askMyDataState.value = geminiAiService.askMyData(
                question = question,
                computedSummaryJson = summaryJson
            )
        }
    }

    fun generateMonthlyInsightsWithAi() {
        viewModelScope.launch {
            _monthlyInsightsState.value = com.example.utils.AiUiState.Loading("Generating monthly spending insights...")
            val summaryJson = getComputedFinancialSummaryPreview()
            _monthlyInsightsState.value = geminiAiService.generateMonthlyInsights(
                computedSummaryJson = summaryJson
            )
        }
    }

    fun analyzeNoteWithAi(
        title: String,
        content: String,
        checklistItems: List<String> = emptyList()
    ) {
        viewModelScope.launch {
            _noteHelperState.value = com.example.utils.AiUiState.Loading("Summarizing, fixing grammar & extracting tasks...")
            _noteHelperState.value = geminiAiService.analyzeNote(
                noteTitle = title,
                noteContent = content,
                checklistItems = checklistItems
            )
        }
    }

    fun resetNoteHelperState() {
        _noteHelperState.value = com.example.utils.AiUiState.Idle
    }

    fun saveAiExtractedTasks(
        tasks: List<com.example.utils.AiSuggestedTask>,
        onComplete: (Int) -> Unit = {}
    ) {
        val selected = tasks.filter { it.selected && it.title.isNotBlank() }
        if (selected.isEmpty()) {
            onComplete(0)
            return
        }
        viewModelScope.launch {
            val dayMs = 24L * 60L * 60L * 1000L
            val now = System.currentTimeMillis()
            selected.forEach { item ->
                val due = now + (item.dueDaysFromNow.coerceAtLeast(0) * dayMs)
                repository.insertTask(
                    TaskItem(
                        title = item.title.trim(),
                        description = "Created via AI Notes Helper",
                        priority = item.priority,
                        category = item.category,
                        dueDateTime = due,
                        status = "TODO"
                    )
                )
            }
            onComplete(selected.size)
        }
    }

    fun breakGoalWithAi(goalPrompt: String) {
        viewModelScope.launch {
            _taskHelperState.value = com.example.utils.AiUiState.Loading("Breaking goal into subtasks & schedule...")
            _taskHelperState.value = geminiAiService.breakGoalIntoTasksAndSchedule(goalPrompt)
        }
    }

    fun resetTaskHelperState() {
        _taskHelperState.value = com.example.utils.AiUiState.Idle
    }

    fun saveAiGoalPlan(
        result: com.example.utils.TaskHelperResult,
        selectedScheduleIds: Set<String>,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val subtasksModels = result.subtasks.mapIndexed { idx, title ->
                Subtask(id = "ai_sub_${now}_$idx", title = title, isCompleted = false)
            }
            val dueMillis = now + (7L * 24L * 60L * 60L * 1000L)
            repository.insertTask(
                TaskItem(
                    title = result.goalTitle,
                    description = result.strategySummary,
                    priority = result.priority,
                    category = result.category,
                    dueDateTime = dueMillis,
                    subtasksJson = TaskUtils.serializeSubtasks(subtasksModels),
                    status = "TODO"
                )
            )

            result.scheduleBlocks.filter { it.id in selectedScheduleIds }.forEach { block ->
                val cal = java.util.Calendar.getInstance().apply {
                    timeInMillis = now
                    add(java.util.Calendar.DAY_OF_YEAR, block.dayOffset)
                    set(java.util.Calendar.HOUR_OF_DAY, block.startHour.coerceIn(5, 22))
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                val startMs = cal.timeInMillis
                val endMs = startMs + (block.durationMinutes.coerceIn(15, 240) * 60_000L)
                repository.insertEvent(
                    ScheduleEvent(
                        title = block.title,
                        description = block.notes,
                        startTime = startMs,
                        endTime = endMs,
                        location = "",
                        category = result.category,
                        reminderEnabled = true,
                        reminderOffsetMinutes = 15
                    )
                )
            }
            _taskHelperState.value = com.example.utils.AiUiState.Idle
            onComplete()
        }
    }
}

class FinanceViewModelFactory(private val repository: TransactionRepository, private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FinanceViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FinanceViewModel(repository, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

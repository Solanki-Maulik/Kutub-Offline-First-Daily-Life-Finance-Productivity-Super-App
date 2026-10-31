package com.example.viewmodel

import kotlinx.coroutines.flow.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.Budget
import com.example.data.Debt
import com.example.data.Goal
import com.example.data.Transaction
import com.example.data.TransactionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.async

import com.example.data.Account
import com.example.data.MoneyNote
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FinanceViewModel(private val repository: TransactionRepository, private val context: Context) : ViewModel() {
    private val prefs = context.getSharedPreferences("rupee_story_prefs", Context.MODE_PRIVATE)

    private val _privacyMode = MutableStateFlow(prefs.getBoolean("privacy_mode", false))
    val privacyMode: StateFlow<Boolean> = _privacyMode.asStateFlow()

    private val _appLockEnabled = MutableStateFlow(prefs.getBoolean("app_lock", false))
    val appLockEnabled: StateFlow<Boolean> = _appLockEnabled.asStateFlow()

    fun togglePrivacyMode() {
        val newState = !_privacyMode.value
        _privacyMode.value = newState
        prefs.edit().putBoolean("privacy_mode", newState).apply()
    }

    fun restoreBackup(data: com.example.data.BackupData) {
        viewModelScope.launch {
            data.accounts.forEach { repository.insertAccount(it) }
            data.budgets.forEach { repository.insertBudget(it) }
            data.debts.forEach { repository.insertDebt(it) }
            data.goals.forEach { repository.insertGoal(it) }
            data.moneyNotes.forEach { repository.insertNote(it) }
            data.transactions.forEach { repository.insert(it) }
        }
    }

    fun toggleAppLock(enabled: Boolean) {
        _appLockEnabled.value = enabled
        prefs.edit().putBoolean("app_lock", enabled).apply()
    }

    val transactions: StateFlow<List<Transaction>> = repository.allTransactions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val accounts: StateFlow<List<Account>> = repository.allAccounts.map { if (it.isEmpty()) { val defaults = listOf(Account("Cash", 0.0), Account("Bank", 0.0)); defaults.forEach { acc -> addAccount(acc.name, acc.balance) }; defaults } else it }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = listOf(Account("Cash"), Account("Bank"), Account("Credit Card")) // default fallback
        )

    val moneyNotes: StateFlow<List<MoneyNote>> = repository.allNotes
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
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

    suspend fun getTransactionById(id: Int): Transaction? {
        return viewModelScope.async { repository.getTransactionById(id) }.await()
    }

    fun addTransaction(amount: Double, isIncome: Boolean, category: String, account: String, note: String, timestamp: Long) {
        viewModelScope.launch {
            repository.insert(
                Transaction(
                    amount = amount,
                    isIncome = isIncome,
                    category = category,
                    account = account,
                    note = note,
                    timestamp = timestamp
                )
            )
        }
    }

    fun updateTransaction(id: Int, amount: Double, isIncome: Boolean, category: String, account: String, note: String, timestamp: Long) {
        viewModelScope.launch {
            repository.update(
                Transaction(
                    id = id,
                    amount = amount,
                    isIncome = isIncome,
                    category = category,
                    account = account,
                    note = note,
                    timestamp = timestamp
                )
            )
        }
    }

    fun deleteTransaction(id: Int) {
        viewModelScope.launch {
            repository.deleteById(id)
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
                    note = "Transfer from $fromAccount",
                    timestamp = timestamp
                )
            )
        }
    }

    fun saveBudget(category: String, limitAmount: Double) {
        viewModelScope.launch {
            repository.insertBudget(Budget(category, limitAmount))
        }
    }

    fun addGoal(name: String, targetAmount: Double) {
        viewModelScope.launch {
            repository.insertGoal(Goal(name = name, targetAmount = targetAmount))
        }
    }

    fun addSavedAmountToGoal(goal: Goal, amountToAdd: Double) {
        viewModelScope.launch {
            repository.updateGoal(goal.copy(savedAmount = goal.savedAmount + amountToAdd))
        }
    }

    fun deleteGoal(id: Int) {
        viewModelScope.launch {
            repository.deleteGoalById(id)
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

    fun addAccount(name: String, initialBalance: Double = 0.0) {
        viewModelScope.launch {
            repository.insertAccount(Account(name, initialBalance))
        }
    }

    fun deleteAccount(name: String) {
        viewModelScope.launch {
            repository.deleteAccountByName(name)
        }
    }

    fun addNote(title: String, content: String, timestamp: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            repository.insertNote(MoneyNote(title = title, content = content, timestamp = timestamp))
        }
    }

    fun deleteNote(id: Int) {
        viewModelScope.launch {
            repository.deleteNoteById(id)
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

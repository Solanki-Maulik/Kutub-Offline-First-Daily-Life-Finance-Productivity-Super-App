package com.example.data

import kotlinx.coroutines.flow.Flow

class TransactionRepository(
    private val transactionDao: TransactionDao,
    private val budgetDao: BudgetDao,
    private val goalDao: GoalDao,
    private val debtDao: DebtDao,
    private val accountDao: AccountDao,
    private val moneyNoteDao: MoneyNoteDao
) {
    val allTransactions: Flow<List<Transaction>> = transactionDao.getAllTransactions()
    val allBudgets: Flow<List<Budget>> = budgetDao.getAllBudgets()
    val allGoals: Flow<List<Goal>> = goalDao.getAllGoals()
    val allDebts: Flow<List<Debt>> = debtDao.getAllDebts()
    val allAccounts: Flow<List<Account>> = accountDao.getAllAccounts()
    val allNotes: Flow<List<MoneyNote>> = moneyNoteDao.getAllNotes()

    suspend fun getTransactionById(id: Int): Transaction? {
        return transactionDao.getTransactionById(id)
    }

    suspend fun insert(transaction: Transaction) {
        transactionDao.insert(transaction)
    }

    suspend fun update(transaction: Transaction) {
        transactionDao.update(transaction)
    }

    suspend fun deleteById(id: Int) {
        transactionDao.deleteById(id)
    }

    suspend fun insertBudget(budget: Budget) {
        budgetDao.insertBudget(budget)
    }

    suspend fun insertGoal(goal: Goal) {
        goalDao.insertGoal(goal)
    }

    suspend fun updateGoal(goal: Goal) {
        goalDao.updateGoal(goal)
    }

    suspend fun deleteGoalById(id: Int) {
        goalDao.deleteGoalById(id)
    }

    suspend fun insertDebt(debt: Debt) {
        debtDao.insertDebt(debt)
    }

    suspend fun updateDebt(debt: Debt) {
        debtDao.updateDebt(debt)
    }

    suspend fun deleteDebtById(id: Int) {
        debtDao.deleteDebtById(id)
    }

    suspend fun insertAccount(account: Account) {
        accountDao.insertAccount(account)
    }

    suspend fun deleteAccountByName(name: String) {
        accountDao.deleteAccountByName(name)
    }

    suspend fun insertNote(note: MoneyNote) {
        moneyNoteDao.insertNote(note)
    }

    suspend fun deleteNoteById(id: Int) {
        moneyNoteDao.deleteNoteById(id)
    }
}

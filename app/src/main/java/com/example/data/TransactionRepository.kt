package com.example.data

import kotlinx.coroutines.flow.Flow

class TransactionRepository(
    private val transactionDao: TransactionDao,
    private val budgetDao: BudgetDao,
    private val goalDao: GoalDao,
    private val debtDao: DebtDao,
    private val accountDao: AccountDao,
    private val moneyNoteDao: MoneyNoteDao,
    private val categoryDao: CategoryDao,
    private val udhaarDao: UdhaarDao,
    private val recurringDao: RecurringDao,
    private val billDao: BillDao,
    private val taskDao: TaskDao,
    private val scheduleDao: ScheduleDao,
    private val splitDao: SplitDao,
    private val habitDao: HabitDao,
    private val investmentDao: InvestmentDao,
    private val documentVaultDao: DocumentVaultDao
) {
    val allTransactions: Flow<List<Transaction>> = transactionDao.getAllTransactions()
    val allBudgets: Flow<List<Budget>> = budgetDao.getAllBudgets()
    val allGoals: Flow<List<Goal>> = goalDao.getAllGoals()
    val allGoalContributions: Flow<List<GoalContribution>> = goalDao.getAllContributions()
    val allDebts: Flow<List<Debt>> = debtDao.getAllDebts()
    val allAccounts: Flow<List<Account>> = accountDao.getAllAccounts()
    val allNotes: Flow<List<MoneyNote>> = moneyNoteDao.getAllNotes()
    val activeNotes: Flow<List<MoneyNote>> = moneyNoteDao.getActiveNotes()
    val archivedNotes: Flow<List<MoneyNote>> = moneyNoteDao.getArchivedNotes()
    val allCategories: Flow<List<Category>> = categoryDao.getAllCategories()
    val allPeople: Flow<List<Person>> = udhaarDao.getAllPeople()
    val allUdhaarEntries: Flow<List<UdhaarEntry>> = udhaarDao.getAllEntries()
    val allUdhaarRepayments: Flow<List<UdhaarRepayment>> = udhaarDao.getAllRepayments()
    val allRecurring: Flow<List<RecurringTransaction>> = recurringDao.getAllRecurring()
    val activeRecurring: Flow<List<RecurringTransaction>> = recurringDao.getActiveRecurring()
    val allBills: Flow<List<BillReminder>> = billDao.getAllBills()
    val pendingBills: Flow<List<BillReminder>> = billDao.getPendingBills()
    val allTasks: Flow<List<TaskItem>> = taskDao.getAllTasks()
    val activeTasks: Flow<List<TaskItem>> = taskDao.getActiveTasks()
    val completedTasks: Flow<List<TaskItem>> = taskDao.getCompletedTasks()
    val taskStreak: Flow<TaskStreak?> = taskDao.getStreak()
    val allEvents: Flow<List<ScheduleEvent>> = scheduleDao.getAllEvents()
    val allSplitGroups: Flow<List<SplitGroup>> = splitDao.getAllGroups()
    val allSplitExpenses: Flow<List<SplitExpense>> = splitDao.getAllSplitExpenses()
    val allSplitSettlements: Flow<List<SplitSettlement>> = splitDao.getAllSettlements()
    val activeHabits: Flow<List<Habit>> = habitDao.getActiveHabits()
    val allHabits: Flow<List<Habit>> = habitDao.getAllHabits()
    val allHabitLogs: Flow<List<HabitLog>> = habitDao.getAllHabitLogs()
    val allHoldings: Flow<List<InvestmentHolding>> = investmentDao.getAllHoldings()
    val allLiabilities: Flow<List<LiabilityItem>> = investmentDao.getAllLiabilities()
    val allVaultDocuments: Flow<List<VaultDocument>> = documentVaultDao.getAllDocuments()

    suspend fun getTransactionById(id: Int): Transaction? {
        return transactionDao.getTransactionById(id)
    }

    suspend fun insert(transaction: Transaction): Long {
        return transactionDao.insert(transaction)
    }

    suspend fun update(transaction: Transaction) {
        transactionDao.update(transaction)
    }

    suspend fun deleteById(id: Int) {
        transactionDao.deleteById(id)
    }

    suspend fun reassignAccountForTransactions(oldAccount: String, newAccount: String) {
        transactionDao.reassignAccountForTransactions(oldAccount, newAccount)
    }

    suspend fun deleteTransactionsForAccount(accountName: String) {
        transactionDao.deleteTransactionsForAccount(accountName)
    }

    suspend fun insertBudget(budget: Budget) {
        budgetDao.insertBudget(budget)
    }

    suspend fun deleteBudget(category: String) {
        budgetDao.deleteBudget(category)
    }

    // Goals & Contributions
    suspend fun insertGoal(goal: Goal): Long {
        return goalDao.insertGoal(goal)
    }

    suspend fun updateGoal(goal: Goal) {
        goalDao.updateGoal(goal)
    }

    suspend fun deleteGoalById(id: Int) {
        goalDao.deleteGoalById(id)
    }

    fun getGoalById(id: Int): Flow<Goal?> = goalDao.getGoalById(id)
    suspend fun getGoalByIdSync(id: Int): Goal? = goalDao.getGoalByIdSync(id)

    fun getContributionsForGoal(goalId: Int): Flow<List<GoalContribution>> =
        goalDao.getContributionsForGoal(goalId)

    suspend fun getContributionsForGoalSync(goalId: Int): List<GoalContribution> =
        goalDao.getContributionsForGoalSync(goalId)

    suspend fun insertGoalContribution(contribution: GoalContribution): Long =
        goalDao.insertContribution(contribution)

    suspend fun deleteGoalContributionById(id: Long) =
        goalDao.deleteContributionById(id)

    // Debts
    suspend fun insertDebt(debt: Debt) {
        debtDao.insertDebt(debt)
    }

    suspend fun updateDebt(debt: Debt) {
        debtDao.updateDebt(debt)
    }

    suspend fun deleteDebtById(id: Int) {
        debtDao.deleteDebtById(id)
    }

    // Accounts
    suspend fun insertAccount(account: Account) {
        accountDao.insertAccount(account)
    }

    suspend fun deleteAccountByName(name: String) {
        accountDao.deleteAccountByName(name)
    }

    // Notes
    suspend fun getNoteById(id: Long): MoneyNote? {
        return moneyNoteDao.getNoteById(id)
    }

    suspend fun insertNote(note: MoneyNote): Long {
        return moneyNoteDao.insertNote(note)
    }

    suspend fun updateNote(note: MoneyNote) {
        moneyNoteDao.updateNote(note)
    }

    suspend fun deleteNoteById(id: Long) {
        moneyNoteDao.deleteNoteById(id)
    }

    suspend fun updateNotePinStatus(id: Long, isPinned: Boolean) {
        moneyNoteDao.updatePinStatus(id, isPinned)
    }

    suspend fun updateNoteArchiveStatus(id: Long, isArchived: Boolean) {
        moneyNoteDao.updateArchiveStatus(id, isArchived)
    }

    // Categories
    suspend fun insertCategory(category: Category) {
        categoryDao.insertCategory(category)
    }

    suspend fun updateCategory(category: Category) {
        categoryDao.updateCategory(category)
    }

    suspend fun deleteCategoryById(id: Int) {
        categoryDao.deleteCategoryById(id)
    }

    // Udhaar / People
    fun getPersonById(personId: Long): Flow<Person?> = udhaarDao.getPersonById(personId)
    suspend fun getPersonByIdSync(personId: Long): Person? = udhaarDao.getPersonByIdSync(personId)
    suspend fun insertPerson(person: Person): Long = udhaarDao.insertPerson(person)
    suspend fun updatePerson(person: Person) = udhaarDao.updatePerson(person)
    suspend fun deletePersonById(personId: Long) = udhaarDao.deletePersonById(personId)

    fun getEntriesForPerson(personId: Long): Flow<List<UdhaarEntry>> = udhaarDao.getEntriesForPerson(personId)
    suspend fun getEntriesForPersonSync(personId: Long): List<UdhaarEntry> = udhaarDao.getEntriesForPersonSync(personId)
    suspend fun getUdhaarEntryById(id: Long): UdhaarEntry? = udhaarDao.getEntryById(id)
    suspend fun insertUdhaarEntry(entry: UdhaarEntry): Long = udhaarDao.insertEntry(entry)
    suspend fun updateUdhaarEntry(entry: UdhaarEntry) = udhaarDao.updateEntry(entry)
    suspend fun deleteUdhaarEntryById(id: Long) = udhaarDao.deleteEntryById(id)

    fun getRepaymentsForEntry(entryId: Long): Flow<List<UdhaarRepayment>> = udhaarDao.getRepaymentsForEntry(entryId)
    suspend fun getRepaymentsForEntrySync(entryId: Long): List<UdhaarRepayment> = udhaarDao.getRepaymentsForEntrySync(entryId)
    fun getRepaymentsForPerson(personId: Long): Flow<List<UdhaarRepayment>> = udhaarDao.getRepaymentsForPerson(personId)
    suspend fun insertUdhaarRepayment(repayment: UdhaarRepayment): Long = udhaarDao.insertRepayment(repayment)
    suspend fun deleteUdhaarRepaymentById(id: Long) = udhaarDao.deleteRepaymentById(id)

    // Recurring Transactions
    suspend fun getRecurringById(id: Long): RecurringTransaction? = recurringDao.getRecurringById(id)
    suspend fun getActiveRecurringSync(): List<RecurringTransaction> = recurringDao.getActiveRecurringSync()
    suspend fun insertRecurring(recurring: RecurringTransaction): Long = recurringDao.insertRecurring(recurring)
    suspend fun updateRecurring(recurring: RecurringTransaction) = recurringDao.updateRecurring(recurring)
    suspend fun deleteRecurringById(id: Long) = recurringDao.deleteRecurringById(id)

    // Bills
    suspend fun getBillById(id: Long): BillReminder? = billDao.getBillById(id)
    suspend fun getPendingBillsSync(): List<BillReminder> = billDao.getPendingBillsSync()
    suspend fun insertBill(bill: BillReminder): Long = billDao.insertBill(bill)
    suspend fun updateBill(bill: BillReminder) = billDao.updateBill(bill)
    suspend fun deleteBillById(id: Long) = billDao.deleteBillById(id)

    // Tasks & Streaks
    suspend fun getTaskByIdSync(id: Long): TaskItem? = taskDao.getTaskByIdSync(id)
    suspend fun insertTask(task: TaskItem): Long = taskDao.insertTask(task)
    suspend fun updateTask(task: TaskItem) = taskDao.updateTask(task)
    suspend fun deleteTaskById(id: Long) = taskDao.deleteTaskById(id)

    suspend fun getStreakSync(): TaskStreak? = taskDao.getStreakSync()
    suspend fun insertOrUpdateStreak(streak: TaskStreak) = taskDao.insertOrUpdateStreak(streak)

    // Schedule Events
    fun getEventsInRange(start: Long, end: Long): Flow<List<ScheduleEvent>> = scheduleDao.getEventsInRange(start, end)
    suspend fun getEventById(id: Long): ScheduleEvent? = scheduleDao.getEventById(id)
    suspend fun insertEvent(event: ScheduleEvent): Long = scheduleDao.insertEvent(event)
    suspend fun updateEvent(event: ScheduleEvent) = scheduleDao.updateEvent(event)
    suspend fun deleteEventById(id: Long) = scheduleDao.deleteEventById(id)

    // Split Bills
    fun getSplitGroupById(id: Long): Flow<SplitGroup?> = splitDao.getGroupById(id)
    suspend fun getSplitGroupByIdSync(id: Long): SplitGroup? = splitDao.getGroupByIdSync(id)
    suspend fun insertSplitGroup(group: SplitGroup): Long = splitDao.insertGroup(group)
    suspend fun updateSplitGroup(group: SplitGroup) = splitDao.updateGroup(group)
    suspend fun deleteSplitGroupById(id: Long) {
        splitDao.deleteExpensesForGroup(id)
        splitDao.deleteSettlementsForGroup(id)
        splitDao.deleteGroupById(id)
    }

    fun getSplitExpensesForGroup(groupId: Long): Flow<List<SplitExpense>> = splitDao.getExpensesForGroup(groupId)
    suspend fun getSplitExpensesForGroupSync(groupId: Long): List<SplitExpense> = splitDao.getExpensesForGroupSync(groupId)
    suspend fun getSplitExpenseById(id: Long): SplitExpense? = splitDao.getSplitExpenseById(id)
    suspend fun insertSplitExpense(expense: SplitExpense): Long = splitDao.insertSplitExpense(expense)
    suspend fun updateSplitExpense(expense: SplitExpense) = splitDao.updateSplitExpense(expense)
    suspend fun deleteSplitExpenseById(id: Long) = splitDao.deleteSplitExpenseById(id)

    fun getSplitSettlementsForGroup(groupId: Long): Flow<List<SplitSettlement>> = splitDao.getSettlementsForGroup(groupId)
    suspend fun getSplitSettlementsForGroupSync(groupId: Long): List<SplitSettlement> = splitDao.getSettlementsForGroupSync(groupId)
    suspend fun insertSplitSettlement(settlement: SplitSettlement): Long = splitDao.insertSettlement(settlement)
    suspend fun deleteSplitSettlementById(id: Long) = splitDao.deleteSettlementById(id)

    // Habits
    suspend fun getHabitById(id: Long): Habit? = habitDao.getHabitById(id)
    suspend fun insertHabit(habit: Habit): Long = habitDao.insertHabit(habit)
    suspend fun updateHabit(habit: Habit) = habitDao.updateHabit(habit)
    suspend fun deleteHabitById(id: Long) {
        habitDao.deleteLogsForHabit(id)
        habitDao.deleteHabitById(id)
    }

    fun getLogsForHabit(habitId: Long): Flow<List<HabitLog>> = habitDao.getLogsForHabit(habitId)
    suspend fun getLogForHabitAndDate(habitId: Long, dateIso: String): HabitLog? =
        habitDao.getLogForHabitAndDate(habitId, dateIso)
    suspend fun insertOrUpdateHabitLog(log: HabitLog): Long = habitDao.insertOrUpdateLog(log)
    suspend fun deleteHabitLogForDate(habitId: Long, dateIso: String) =
        habitDao.deleteLogForDate(habitId, dateIso)

    // Investments & Liabilities
    suspend fun getHoldingById(id: Long): InvestmentHolding? = investmentDao.getHoldingById(id)
    suspend fun insertHolding(holding: InvestmentHolding): Long = investmentDao.insertHolding(holding)
    suspend fun updateHolding(holding: InvestmentHolding) = investmentDao.updateHolding(holding)
    suspend fun deleteHoldingById(id: Long) = investmentDao.deleteHoldingById(id)

    suspend fun getLiabilityById(id: Long): LiabilityItem? = investmentDao.getLiabilityById(id)
    suspend fun insertLiability(liability: LiabilityItem): Long = investmentDao.insertLiability(liability)
    suspend fun updateLiability(liability: LiabilityItem) = investmentDao.updateLiability(liability)
    suspend fun deleteLiabilityById(id: Long) = investmentDao.deleteLiabilityById(id)

    // Document Vault
    suspend fun getVaultDocumentById(id: Long): VaultDocument? = documentVaultDao.getDocumentById(id)
    suspend fun insertVaultDocument(doc: VaultDocument): Long = documentVaultDao.insertDocument(doc)
    suspend fun updateVaultDocument(doc: VaultDocument) = documentVaultDao.updateDocument(doc)
    suspend fun deleteVaultDocumentById(id: Long) = documentVaultDao.deleteDocumentById(id)
}

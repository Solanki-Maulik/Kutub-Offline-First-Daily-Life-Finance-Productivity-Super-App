package com.example

import com.example.data.*
import com.example.utils.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NewModulesSystemTest {

    @Test
    fun notesModule_checklistsTagsAndFullTextSearch_workCorrectly() {
        val checklists = listOf(
            NoteChecklistItem(id = "1", text = "Pay broadband bill", isChecked = true),
            NoteChecklistItem(id = "2", text = "Call CA for tax filing", isChecked = false)
        )
        val checklistsJson = NoteUtils.serializeChecklist(checklists)
        val tagsJson = NoteUtils.serializeTags(listOf("finance", "tax"))

        val note = MoneyNote(
            id = 10L,
            title = "March Checklist",
            content = "**Important** quarterly review",
            isPinned = true,
            colorHex = "#FFF9C4",
            tagsJson = tagsJson,
            checklistsJson = checklistsJson,
            attachedType = "TASK",
            attachedId = 5L,
            attachedTitle = "Submit ITR"
        )

        val parsedChecklists = NoteUtils.parseChecklist(note.checklistsJson)
        assertEquals(2, parsedChecklists.size)
        assertTrue(parsedChecklists[0].isChecked)

        // Full-text search matches title, markdown body, checklist item text, tags, and attached title
        assertTrue(NoteUtils.matchesQuery(note, "broadband"))
        assertTrue(NoteUtils.matchesQuery(note, "tax"))
        assertTrue(NoteUtils.matchesQuery(note, "Submit ITR"))
        assertFalse(NoteUtils.matchesQuery(note, "groceries"))
    }

    @Test
    fun splitBillsModule_splitModesSimplifyDebtsAndZeroDoubleCounting_workCorrectly() {
        val members = listOf("Me", "Rahul", "Priya")

        // 1. Equal split of ₹900 among 3 members -> ₹300 each
        val equalSplits = SplitUtils.calculateEqualSplits(900.0, members)
        assertEquals(3, equalSplits.size)
        assertEquals(300.0, equalSplits.first { it.memberName == "Me" }.amount, 0.01)

        // 2. Shares split of ₹1000 with shares 2:1:1 -> 500, 250, 250
        val shareSplits = SplitUtils.calculateShareSplits(
            1000.0,
            mapOf("Me" to 2, "Rahul" to 1, "Priya" to 1),
            members
        )
        assertEquals(500.0, shareSplits.first { it.memberName == "Me" }.amount, 0.01)
        assertEquals(250.0, shareSplits.first { it.memberName == "Rahul" }.amount, 0.01)

        // 3. Simplify Debts check
        val group = SplitGroup(
            id = 1L,
            name = "Goa Trip",
            type = "Trip",
            membersJson = SplitUtils.serializeMembers(members)
        )
        val expense1 = SplitExpense(
            id = 1L,
            groupId = 1L,
            title = "Hotel Booking",
            totalAmount = 900.0,
            paidBy = "Me",
            splitType = "EQUALLY",
            splitsJson = SplitUtils.serializeSplits(equalSplits),
            myShareAmount = 300.0
        )
        val summary = SplitUtils.calculateGroupSummary(group, listOf(expense1), emptyList())
        // Me paid 900, my share 300 -> net +600 owed to Me (300 from Rahul, 300 from Priya)
        assertEquals(600.0, summary.myNetBalance, 0.01)
        assertEquals(2, summary.simplifiedDebts.size)

        // 4. Verify Split Bills never double-count in expense reports
        val now = System.currentTimeMillis()
        val txsWhenIPaid = listOf(
            // My share (counts in expense reports)
            Transaction(
                id = 1,
                amount = 300.0,
                isIncome = false,
                category = "Travel",
                subcategory = "[Goa Trip] Hotel Booking (My Share)",
                account = "Bank Account",
                timestamp = now
            ),
            // Others' share (recorded as Udhaar receivable -> deducts from Bank Account, excluded from expense reports)
            Transaction(
                id = 2,
                amount = 600.0,
                isIncome = false,
                category = "Udhaar",
                subcategory = "[Goa Trip] Hotel Booking (Owed to Me)",
                account = "Bank Account",
                timestamp = now
            )
        )

        val bankAcc = Account(name = "Bank Account", balance = 5000.0, type = "BANK")
        // Full ₹900 left the bank account: 5000 - 900 = 4100
        assertEquals(4100.0, AccountUtils.calculateAccountBalance(bankAcc, txsWhenIPaid), 0.01)

        // Report summary only counts ₹300 (my share), NOT ₹900!
        val reportSummary = ReportUtils.calculateSummary(txsWhenIPaid, now - 86400000L, now + 86400000L)
        assertEquals(300.0, reportSummary.totalExpense, 0.01)
    }

    @Test
    fun habitsModule_restDayPreservesStreakAndMoneyLinkedHabitsAutoEvaluate() {
        val todayIso = HabitUtils.getTodayIso()
        val yesterdayIso = HabitUtils.getIsoDaysAgo(1)
        val twoDaysAgoIso = HabitUtils.getIsoDaysAgo(2)

        val habit = Habit(
            id = 1L,
            name = "Morning Run",
            frequencyType = "DAILY",
            targetCount = 1,
            createdAt = System.currentTimeMillis() - 10L * 86400000L
        )

        // Completed today, SKIPPED (rest day) yesterday, Completed 2 days ago -> streak should be 2!
        val logs = listOf(
            HabitLog(habitId = 1L, dateIso = todayIso, currentCount = 1, status = "COMPLETED"),
            HabitLog(habitId = 1L, dateIso = yesterdayIso, currentCount = 0, status = "SKIPPED"),
            HabitLog(habitId = 1L, dateIso = twoDaysAgoIso, currentCount = 1, status = "COMPLETED")
        )

        val stats = HabitUtils.computeHabitStats(
            habit = habit,
            selectedDateIso = todayIso,
            logsForHabit = logs,
            transactions = emptyList(),
            goalContributions = emptyList()
        )
        assertEquals(2, stats.currentStreak)
        assertEquals(2, stats.longestStreak)

        // Money-linked habit: "NO_SPEND_DAY" with no expenses today -> automatically satisfied!
        val noSpendHabit = Habit(
            id = 2L,
            name = "No-Spend Day",
            moneyRuleType = "NO_SPEND_DAY"
        )
        val (satisfied, _) = HabitUtils.evaluateMoneyRule(
            habit = noSpendHabit,
            dateIso = todayIso,
            transactions = emptyList(),
            goalContributions = emptyList()
        )
        assertTrue(satisfied)
    }

    @Test
    fun investmentsModule_netWorthReturnsAndNonExpenseRule_workCorrectly() {
        val now = System.currentTimeMillis()
        val oneYearAgo = now - (365L * 24L * 3600L * 1000L)

        val holding = InvestmentHolding(
            id = 1L,
            name = "Nifty 50 Index Fund",
            assetType = "Mutual Fund/SIP",
            units = 100.0,
            investedAmount = 100000.0,
            currentValue = 115000.0,
            buyDate = oneYearAgo,
            isSipEnabled = true,
            sipMonthlyAmount = 5000.0,
            sipLinkedAccount = "Bank Account"
        )

        val perf = InvestmentUtils.calculateHoldingPerformance(holding, now)
        assertEquals(15000.0, perf.profitLoss, 0.01)
        assertEquals(15.0, perf.simpleReturnPercent, 0.01)
        assertEquals(15.0, perf.annualizedReturnXirrPercent, 0.5)

        val liability = LiabilityItem(
            id = 1L,
            name = "Car Loan",
            type = "LOAN_EMI",
            principalAmount = 300000.0,
            interestRate = 9.0,
            emiAmount = 10000.0,
            remainingBalance = 80000.0
        )

        // SIP transaction has category = "Investment"
        val sipTx = Transaction(
            id = 10,
            amount = 5000.0,
            isIncome = false,
            category = "Investment",
            subcategory = "SIP: Nifty 50 Index Fund",
            account = "Bank Account",
            timestamp = now
        )

        val bankAcc = Account(name = "Bank Account", balance = 50000.0, type = "BANK")
        val liquidBal = AccountUtils.calculateAccountBalance(bankAcc, listOf(sipTx))
        // 50,000 - 5,000 SIP = 45,000 liquid balance
        assertEquals(45000.0, liquidBal, 0.01)

        // Expense report MUST NOT include the ₹5,000 SIP investment
        val report = ReportUtils.calculateSummary(listOf(sipTx), now - 86400000L, now + 86400000L)
        assertEquals(0.0, report.totalExpense, 0.01)

        // Net Worth = Liquid (45,000) + Holdings (1,15,000) - Liabilities (80,000) = 80,000
        val nw = InvestmentUtils.calculateNetWorthOverview(
            accounts = listOf(bankAcc),
            transactions = listOf(sipTx),
            holdings = listOf(holding),
            liabilities = listOf(liability),
            nowMillis = now
        )
        assertEquals(160000.0, nw.totalAssets, 0.01)
        assertEquals(80000.0, nw.totalLiabilities, 0.01)
        assertEquals(80000.0, nw.netWorth, 0.01)
    }

    @Test
    fun documentVaultModule_warrantyExpiryAndStorageQuotaWarning_workCorrectly() {
        val purchaseTime = 1700000000000L
        val expiryAfter12Months = DocumentVaultUtils.calculateExpiryFromWarrantyMonths(purchaseTime, 12)
        assertNotNull(expiryAfter12Months)
        assertTrue(expiryAfter12Months!! > purchaseTime)

        // Simulate 40 MB used out of 50 MB limit -> 80% usage -> triggers warning threshold (>= 75%)
        val fortyMb = 40L * 1024L * 1024L
        val docs = listOf(
            VaultDocument(
                id = 1L,
                title = "Washing Machine Warranty",
                category = "Warranty",
                issueDate = purchaseTime,
                expiryDate = expiryAfter12Months,
                warrantyMonths = 12,
                mimeType = "image/jpeg",
                fileSizeBytes = fortyMb,
                isSensitive = false
            )
        )
        val summary = DocumentVaultUtils.calculateStorageSummary(docs)
        assertEquals(1, summary.totalDocuments)
        assertEquals(fortyMb, summary.totalBytesUsed)
        assertTrue(summary.isStorageWarning)
    }

    @Test
    fun ecosystemHub_globalSearchAndUnifiedTodayRespectModuleToggles() {
        val now = System.currentTimeMillis()
        val task = TaskItem(id = 1L, title = "Renew Car Insurance", status = "TODO", dueDateTime = now)
        val note = MoneyNote(id = 2L, title = "Insurance Comparison", content = "Compare HDFC vs ICICI")
        val doc = VaultDocument(
            id = 3L,
            title = "Car Insurance Policy",
            category = "Insurance",
            issueDate = now - 30L * 86400000L,
            expiryDate = now + 5L * 86400000L // Expiring in 5 days
        )
        val bill = BillReminder(
            id = 4L,
            title = "Insurance Premium Bill",
            amount = 12000.0,
            category = "Bills",
            dueDate = now + 2L * 86400000L,
            isPaid = false
        )

        val allEnabled = setOf("ai_assistant", "tasks", "notes", "habits", "split_bills", "investments", "document_vault", "udhaar", "reports")

        // 1. Global search with all modules enabled finds Task, Note, and Vault Document
        val fullSearchResults = EcosystemHubUtils.searchAllModules(
            query = "insurance",
            enabledModules = allEnabled,
            transactions = emptyList(),
            tasks = listOf(task),
            events = emptyList(),
            notes = listOf(note),
            habits = emptyList(),
            splitGroups = emptyList(),
            splitExpenses = emptyList(),
            holdings = emptyList(),
            liabilities = emptyList(),
            vaultDocuments = listOf(doc),
            people = emptyList(),
            udhaarEntries = emptyList()
        )
        assertEquals(3, fullSearchResults.size)

        // 2. If user toggles OFF "document_vault" and "notes", Global Search hides them while keeping "tasks" working
        val onlyTasksEnabled = setOf("tasks")
        val filteredResults = EcosystemHubUtils.searchAllModules(
            query = "insurance",
            enabledModules = onlyTasksEnabled,
            transactions = emptyList(),
            tasks = listOf(task),
            events = emptyList(),
            notes = listOf(note),
            habits = emptyList(),
            splitGroups = emptyList(),
            splitExpenses = emptyList(),
            holdings = emptyList(),
            liabilities = emptyList(),
            vaultDocuments = listOf(doc),
            people = emptyList(),
            udhaarEntries = emptyList()
        )
        assertEquals(1, filteredResults.size)
        assertEquals("tasks", filteredResults.first().moduleKey)

        // 3. Unified Today View aggregates Tasks + Bills Due + Expiring Vault Docs
        val todayFull = EcosystemHubUtils.buildUnifiedTodayOverview(
            enabledModules = allEnabled,
            tasks = listOf(task),
            habits = emptyList(),
            habitLogs = emptyList(),
            transactions = emptyList(),
            goalContributions = emptyList(),
            bills = listOf(bill),
            vaultDocuments = listOf(doc),
            holdings = emptyList(),
            nowMillis = now
        )
        assertEquals(1, todayFull.tasksDueToday.size)
        assertEquals(1, todayFull.billsDueSoon.size)
        assertEquals(1, todayFull.expiringDocuments.size)
        assertEquals(3, todayFull.totalActionableCount)
    }

    @Test
    fun aiAssistant_smartEntryAndMinimalSummaryValidation_workCorrectly() {
        val now = System.currentTimeMillis()
        val accounts = listOf("Cash in Hand", "Bank Account", "UPI Wallet")
        val categories = listOf("Food", "Transport", "Shopping", "Bills", "Udhaar", "Other")

        // 1. Validate Smart Entry structured JSON for "chai 20 cash, petrol 300 bank, got 5000 from Raj"
        val mockSmartJson = """
            {
              "summaryNote": "Parsed 2 expenses and 1 Udhaar receipt",
              "entries": [
                {
                  "entryKind": "EXPENSE",
                  "amount": 20.0,
                  "account": "cash",
                  "category": "Food",
                  "date": "01/11/2026",
                  "note": "Chai"
                },
                {
                  "entryKind": "EXPENSE",
                  "amount": 300.0,
                  "account": "bank",
                  "category": "Transport",
                  "date": "01/11/2026",
                  "note": "Petrol"
                },
                {
                  "entryKind": "UDHAAR_GOT",
                  "amount": 5000.0,
                  "account": "Bank Account",
                  "category": "Udhaar",
                  "personName": "Raj",
                  "date": "01/11/2026",
                  "note": "Received from Raj"
                }
              ]
            }
        """.trimIndent()

        val smartResult = AiSchemasAndValidators.parseAndValidateSmartEntry(
            jsonString = mockSmartJson,
            availableAccounts = accounts,
            availableCategories = categories,
            nowMillis = now
        )
        assertEquals(3, smartResult.entries.size)
        assertEquals("EXPENSE", smartResult.entries[0].entryKind)
        assertEquals(20.0, smartResult.entries[0].amount, 0.01)
        assertEquals("Cash in Hand", smartResult.entries[0].account)
        assertEquals("Bank Account", smartResult.entries[1].account)
        assertEquals("UDHAAR_GOT", smartResult.entries[2].entryKind)
        assertEquals("Raj", smartResult.entries[2].personName)
        assertEquals(5000.0, smartResult.entries[2].amount, 0.01)

        // 2. Verify Minimum-Data Computed Financial Summary excludes Investment/Udhaar from expenses
        val summaryJson = AiDataSummaryBuilder.buildComputedFinancialSummary(
            accounts = listOf(Account(name = "Bank Account", balance = 10000.0, type = "BANK")),
            transactions = listOf(
                Transaction(amount = 450.0, isIncome = false, category = "Food", account = "Bank Account", timestamp = now),
                Transaction(amount = 5000.0, isIncome = false, category = "Investment", account = "Bank Account", timestamp = now)
            ),
            budgets = listOf(Budget(category = "Food", limitAmount = 2000.0)),
            goals = emptyList(),
            bills = emptyList(),
            overallToReceive = 5000.0,
            overallToPay = 0.0,
            nowMillis = now
        )
        val parsedSummary = org.json.JSONObject(summaryJson)
        val thisMonthObj = parsedSummary.getJSONObject("thisMonthSummary")
        assertEquals(450L, thisMonthObj.getLong("totalExpense"))
        assertTrue(parsedSummary.getJSONArray("budgetsStatus").getString(0).contains("450"))

        // 3. Verify Task Helper structured JSON validation
        val mockTaskJson = """
            {
              "goalTitle": "File Income Tax Return",
              "strategySummary": "Collect Form 16 and investment proofs before filing.",
              "priority": "HIGH",
              "category": "Finance",
              "subtasks": ["Download Form 16", "Verify AIS statement", "Submit ITR on portal"],
              "scheduleBlocks": [
                {
                  "title": "Download Form 16 & AIS",
                  "dayOffset": 0,
                  "startHour": 18,
                  "durationMinutes": 45,
                  "notes": "Check salary & TDS"
                }
              ]
            }
        """.trimIndent()
        val taskPlan = AiSchemasAndValidators.parseAndValidateTaskHelper(mockTaskJson)
        assertEquals("File Income Tax Return", taskPlan.goalTitle)
        assertEquals(3, taskPlan.subtasks.size)
        assertEquals(1, taskPlan.scheduleBlocks.size)
    }

    @Test
    fun backupRestoreValidationAndPrivacyFormatting_workCorrectly() {
        val now = System.currentTimeMillis()
        val backup = BackupData(
            schemaVersion = 2,
            exportedAt = now,
            transactions = listOf(
                Transaction(id = 1, amount = 1200.0, isIncome = false, category = "Food", account = "Bank Account", timestamp = now)
            ),
            accounts = listOf(
                Account(name = "Bank Account", balance = 25000.0, type = "BANK")
            ),
            categories = listOf(
                Category(name = "Food", iconName = "Fastfood")
            ),
            tasks = listOf(
                TaskItem(id = 1L, title = "Review Monthly Budget", status = "TODO")
            ),
            userSettings = mapOf(
                "pref_currency_code" to "USD",
                "pref_theme_mode" to "DARK",
                "pref_week_start_day" to "SUNDAY"
            )
        )

        val json = com.example.ui.BackupHelper.serializeBackupToJson(backup)
        assertTrue(json.isNotBlank())

        val validResult = com.example.ui.BackupHelper.parseAndValidateBackupJson(json)
        assertTrue(validResult.isValid)
        val preview = checkNotNull(validResult.preview)
        assertEquals(2, preview.schemaVersion)
        assertEquals(1, preview.transactionsCount)
        assertEquals(1, preview.accountsCount)
        assertEquals(1, preview.categoriesCount)
        assertEquals(1, preview.tasksAndEventsCount)
        assertEquals("USD", validResult.parsedData?.userSettings?.get("pref_currency_code"))

        val invalidResult = com.example.ui.BackupHelper.parseAndValidateBackupJson("{not valid json")
        assertFalse(invalidResult.isValid)
        assertTrue(invalidResult.errors.isNotEmpty())

        // Verify FormatUtils currency and hideBalances masking
        try {
            FormatUtils.setCurrency("USD")
            FormatUtils.hideBalances = false
            val formattedUsd = FormatUtils.formatInr(1500.0)
            assertTrue(formattedUsd.startsWith("$"))

            FormatUtils.hideBalances = true
            assertEquals("$••••", FormatUtils.formatInr(1500.0))
        } finally {
            FormatUtils.setCurrency("INR")
            FormatUtils.hideBalances = false
        }
    }

    @Test
    fun fullAppReview_edgeCasesReconciliationAnd5000TxPagination_workCorrectly() {
        // 1. Edge cases: Zero, negative-zero, NaN/Infinity, and huge amounts (> ₹2.14 Crore Int overflow threshold)
        FormatUtils.setCurrency("INR")
        FormatUtils.hideBalances = false
        assertEquals("₹0", FormatUtils.formatInr(0.0))
        assertEquals("₹0", FormatUtils.formatInr(-0.0001))
        assertEquals("₹0", FormatUtils.formatInr(Double.NaN))
        assertEquals("₹0", FormatUtils.formatInr(Double.POSITIVE_INFINITY))

        val twentyFiveCrore = 250_000_000.0
        assertEquals(twentyFiveCrore, FormatUtils.round2(twentyFiveCrore), 0.01)

        val hugeHolding = InvestmentHolding(
            id = 99L,
            name = "Commercial Property",
            assetType = "Property",
            investedAmount = 200_000_000.0,
            currentValue = twentyFiveCrore,
            buyDate = System.currentTimeMillis() - 400L * 86_400_000L
        )
        val hugePerf = InvestmentUtils.calculateHoldingPerformance(hugeHolding)
        assertEquals(50_000_000.0, hugePerf.profitLoss, 0.01)
        assertEquals(25.0, hugePerf.simpleReturnPercent, 0.01)

        val emptySplits = SplitUtils.calculateEqualSplits(0.0, listOf("Me", "A"))
        assertTrue(emptySplits.isEmpty())
        val hugeSplits = SplitUtils.calculateEqualSplits(30_000_000.0, listOf("Me", "A", "B"))
        assertEquals(30_000_000.0, hugeSplits.sumOf { it.amount }, 0.01)

        // 2. Empty data resilience across modules
        assertEquals(0.0, AccountUtils.calculateTotalBalance(emptyList(), emptyList()), 0.001)
        val emptyReconcile = AccountUtils.reconcileBalances(emptyList(), emptyList())
        assertTrue(emptyReconcile.isReconciled)

        // 3. Midnight boundaries (00:00:00.000 vs 23:59:59.999) & Month-End dates (Jan 31 -> Feb 28/29 -> Mar 31)
        val calTodayMidnight = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.JANUARY, 31, 0, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val calTodayEnd = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.JANUARY, 31, 23, 59, 59)
            set(java.util.Calendar.MILLISECOND, 999)
        }
        // Bill due at 00:00:00.000 today checked at 23:59:59.999 today MUST still be DueToday, not Overdue
        val dueStatusAtMidnight = FormatUtils.calculateDueStatus(calTodayMidnight.timeInMillis, calTodayEnd.timeInMillis)
        assertTrue("Expected DueToday at 23:59:59.999, got $dueStatusAtMidnight", dueStatusAtMidnight is DueStatus.DueToday)

        // Month-end recurring advancement: Jan 31, 2026 -> Feb 28, 2026 -> Mar 31, 2026
        val febEndMillis = FormatUtils.advanceRecurringDate(calTodayMidnight.timeInMillis, "MONTHLY", anchorDayOfMonth = 31)
        val febCal = java.util.Calendar.getInstance().apply { timeInMillis = febEndMillis }
        assertEquals(java.util.Calendar.FEBRUARY, febCal.get(java.util.Calendar.MONTH))
        assertEquals(28, febCal.get(java.util.Calendar.DAY_OF_MONTH))

        val marEndMillis = FormatUtils.advanceRecurringDate(febEndMillis, "MONTHLY", anchorDayOfMonth = 31)
        val marCal = java.util.Calendar.getInstance().apply { timeInMillis = marEndMillis }
        assertEquals(java.util.Calendar.MARCH, marCal.get(java.util.Calendar.MONTH))
        assertEquals(31, marCal.get(java.util.Calendar.DAY_OF_MONTH))

        // Custom report date range with midnight customEnd includes afternoon transactions on that same day
        val (_, normalizedEnd) = ReportUtils.getDateRangeBounds(
            ReportDateRange.CUSTOM,
            calTodayMidnight.timeInMillis,
            calTodayMidnight.timeInMillis
        )
        assertTrue(normalizedEnd >= calTodayEnd.timeInMillis)

        // 4. Multi-account reconciliation & deleting an account with transactions
        val accounts = listOf(
            Account(name = "Cash in Hand", balance = 5000.0, type = "CASH"),
            Account(name = "Bank Account", balance = 50000.0, type = "BANK"),
            Account(name = "UPI Wallet", balance = 2000.0, type = "UPI"),
            Account(name = "Card Account", balance = 0.0, type = "CREDIT_CARD")
        )

        // 5. Generate 5,500 transactions and verify reconciliation + virtual pagination speed
        val baseTime = calTodayMidnight.timeInMillis
        val largeTransactions = ArrayList<Transaction>(5500)
        val accNames = accounts.map { it.name }
        val cats = listOf("Food", "Transport", "Shopping", "Bills", "Salary")

        for (i in 1..5500) {
            val accName = accNames[i % accNames.size]
            val isInc = (i % 10 == 0)
            largeTransactions.add(
                Transaction(
                    id = i,
                    amount = (i % 500) + 10.0,
                    isIncome = isInc,
                    category = if (isInc) "Salary" else cats[i % 4],
                    account = accName,
                    note = "Tx #$i",
                    timestamp = baseTime - (i / 50) * 86_400_000L - (i % 1000) * 1000L
                )
            )
        }

        val reconcileBefore = AccountUtils.reconcileBalances(accounts, largeTransactions)
        assertTrue(reconcileBefore.isReconciled)
        assertEquals(reconcileBefore.sumOfAccountBalances, reconcileBefore.reportedTotalBalance, 0.01)
        assertEquals(reconcileBefore.sumByAccountTypes, reconcileBefore.reportedTotalBalance, 0.01)

        // Simulate deleting "UPI Wallet" (which has 1,375 transactions) and reassigning its transactions
        val remainingAccounts = accounts.filterNot { it.name == "UPI Wallet" }
        val reassignedTxs = AccountUtils.reassignOrphanedTransactions(
            accounts = remainingAccounts,
            transactions = largeTransactions,
            fallbackAccountName = "Bank Account"
        )
        val reconcileAfterDelete = AccountUtils.reconcileBalances(remainingAccounts, reassignedTxs)
        assertTrue(reconcileAfterDelete.isReconciled)
        assertTrue(reconcileAfterDelete.orphanedTransactionIds.isEmpty())
        assertEquals(
            remainingAccounts.sumOf { AccountUtils.calculateAccountBalance(it, reassignedTxs) },
            AccountUtils.calculateTotalBalance(remainingAccounts, reassignedTxs),
            0.01
        )

        // Verify virtual pagination on 5,500 transactions
        val filtered5500 = com.example.ui.screens.TransactionPaginationUtils.filterAndSortTransactions(
            transactions = reassignedTxs
        )
        assertEquals(5500, filtered5500.size)

        val page1VirtualItems = com.example.ui.screens.TransactionPaginationUtils.buildPaginatedVirtualItems(
            filteredTransactions = filtered5500,
            visibleCount = com.example.ui.screens.TransactionPaginationUtils.DEFAULT_PAGE_SIZE,
            nowMillis = baseTime
        )
        val page1TxRows = page1VirtualItems.filterIsInstance<com.example.ui.screens.HistoryVirtualItem.TransactionRow>()
        val page1Headers = page1VirtualItems.filterIsInstance<com.example.ui.screens.HistoryVirtualItem.DayHeader>()
        assertEquals(100, page1TxRows.size)
        assertTrue(page1Headers.isNotEmpty())
    }

    @Test
    fun fullAppQaAudit_allModulesBalancesBackupWipeRestoreYearEndAndSecurity_workCorrectly() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val now = System.currentTimeMillis()
        val lastYear = now - 400L * 86_400_000L
        val yearEndCutoff = now - 200L * 86_400_000L

        // 1. BALANCES & NON-EXPENSE EXCLUSION QA
        val accounts = listOf(
            Account(name = "Cash in Hand", balance = 10_000.0, type = "CASH"),
            Account(name = "Bank Account", balance = 100_000.0, type = "BANK"),
            Account(name = "UPI Wallet", balance = 5_000.0, type = "UPI")
        )

        // Include historical (lastYear) and current-year transactions across all categories:
        // - Standard Income & Expense
        // - Transfer (outflow + inflow)
        // - Udhaar (lent + borrowed)
        // - Split Bill where I paid (my share = Expense, others' share = Udhaar)
        // - Split Bill where Friend paid (my share = Expense on Bank Account + balancing Udhaar inflow on Bank Account)
        // - Investment SIP (category = "Investment")
        val qaTransactions = listOf(
            // Prior year transactions (to be archived in Year-End Close)
            Transaction(id = 1, amount = 50_000.0, isIncome = true, category = "Salary", account = "Bank Account", timestamp = lastYear),
            Transaction(id = 2, amount = 12_000.0, isIncome = false, category = "Rent", account = "Bank Account", timestamp = lastYear + 1000L),
            Transaction(id = 3, amount = 3_000.0, isIncome = false, category = "Transfer", account = "Bank Account", timestamp = lastYear + 2000L),
            Transaction(id = 4, amount = 3_000.0, isIncome = true, category = "Transfer", account = "Cash in Hand", timestamp = lastYear + 2000L),

            // Current year transactions
            Transaction(id = 5, amount = 80_000.0, isIncome = true, category = "Salary", account = "Bank Account", timestamp = now - 86_400_000L),
            Transaction(id = 6, amount = 2_500.0, isIncome = false, category = "Food", account = "UPI Wallet", timestamp = now - 70_000_000L),
            // Transfer ₹5,000 from Bank to UPI
            Transaction(id = 7, amount = 5_000.0, isIncome = false, category = "Transfer", account = "Bank Account", timestamp = now - 60_000_000L),
            Transaction(id = 8, amount = 5_000.0, isIncome = true, category = "Transfer", account = "UPI Wallet", timestamp = now - 60_000_000L),
            // Udhaar: Lent ₹4,000 to Raj from Bank
            Transaction(id = 9, amount = 4_000.0, isIncome = false, category = "Udhaar", account = "Bank Account", timestamp = now - 50_000_000L),
            // Split Bill #1: I paid ₹3,000 Dinner via Bank Account (My Share = ₹1,000 Food, Others' Share = ₹2,000 Udhaar)
            Transaction(id = 10, amount = 1_000.0, isIncome = false, category = "Food", account = "Bank Account", timestamp = now - 40_000_000L),
            Transaction(id = 11, amount = 2_000.0, isIncome = false, category = "Udhaar", account = "Bank Account", timestamp = now - 40_000_000L),
            // Split Bill #2: Friend paid ₹1,800 Cab (My Share = ₹600 Transport on Bank Account + balancing ₹600 Udhaar inflow on Bank Account)
            Transaction(id = 12, amount = 600.0, isIncome = false, category = "Transport", account = "Bank Account", timestamp = now - 30_000_000L),
            Transaction(id = 13, amount = 600.0, isIncome = true, category = "Udhaar", account = "Bank Account", timestamp = now - 30_000_000L),
            // Investment SIP: ₹10,000 into Nifty 50 Index Fund from Bank Account
            Transaction(id = 14, amount = 10_000.0, isIncome = false, category = "Investment", account = "Bank Account", timestamp = now - 20_000_000L)
        )

        // Verify reconciliation: sum of all account balances == total balance, 0 orphaned transactions
        val reconciliation = AccountUtils.reconcileBalances(accounts, qaTransactions)
        assertTrue(reconciliation.isReconciled)
        assertTrue(reconciliation.orphanedTransactionIds.isEmpty())
        assertEquals(reconciliation.sumOfAccountBalances, reconciliation.reportedTotalBalance, 0.01)

        // Verify Reports: Transfers, Udhaar, Other people's split shares, and Investments NEVER appear as income/expense
        val currentYearTxs = qaTransactions.filter { it.timestamp > yearEndCutoff }
        val reportSummary = ReportUtils.calculateSummary(currentYearTxs)
        // Current year true income = 80,000 (Salary)
        assertEquals(80_000.0, reportSummary.totalIncome, 0.01)
        // Current year true expense = 2,500 (Food) + 1,000 (Split #1 My Share) + 600 (Split #2 My Share) = 4,100
        assertEquals(4_100.0, reportSummary.totalExpense, 0.01)

        // Verify HistoryScreen "Income" and "Expenses" filters also exclude Transfers, Udhaar, and Investments
        val historyIncomeOnly = com.example.ui.screens.TransactionPaginationUtils.filterAndSortTransactions(
            transactions = currentYearTxs,
            filterType = "Income",
            nowMillis = now
        )
        val historyExpensesOnly = com.example.ui.screens.TransactionPaginationUtils.filterAndSortTransactions(
            transactions = currentYearTxs,
            filterType = "Expenses",
            nowMillis = now
        )
        assertEquals(80_000.0, historyIncomeOnly.sumOf { it.amount }, 0.01)
        assertEquals(4_100.0, historyExpensesOnly.sumOf { it.amount }, 0.01)

        // 2. YEAR-END CLOSE QA: Confirm opening balances and current balances stay 100% accurate after archiving
        val yearEndResult = AccountUtils.calculateYearEndClose(
            accounts = accounts,
            transactions = qaTransactions,
            cutoffTimestamp = yearEndCutoff
        )
        assertEquals(4, yearEndResult.archivedCount)
        assertEquals(10, yearEndResult.remainingTransactions.size)
        assertTrue(yearEndResult.balancesMatch)
        assertEquals(yearEndResult.totalBalanceBefore, yearEndResult.totalBalanceAfter, 0.01)

        // Verify each individual account's balance after Year-End Close matches its balance before Year-End Close
        for (i in accounts.indices) {
            val balBefore = AccountUtils.calculateAccountBalance(accounts[i], qaTransactions)
            val balAfter = AccountUtils.calculateAccountBalance(yearEndResult.updatedAccounts[i], yearEndResult.remainingTransactions)
            assertEquals(balBefore, balAfter, 0.01)
        }

        // 3. BACKUP -> WIPE -> RESTORE QA (Including Local Vault Files & Habits)
        val vaultDir = java.io.File(context.filesDir, "vault_docs").apply { mkdirs() }
        val sampleVaultFile = java.io.File(vaultDir, "qa_warranty_invoice.pdf")
        val sampleFileBytes = "PDF-1.4 QA Warranty Invoice Content 1234567890".toByteArray(Charsets.UTF_8)
        sampleVaultFile.writeBytes(sampleFileBytes)
        assertTrue(sampleVaultFile.exists())

        val vaultDoc = VaultDocument(
            id = 10L,
            title = "MacBook Pro Warranty",
            category = "Warranty",
            issueDate = now - 30L * 86_400_000L,
            expiryDate = now + 335L * 86_400_000L,
            warrantyMonths = 12,
            linkedTransactionId = 6L,
            localFilePath = sampleVaultFile.absolutePath,
            mimeType = "application/pdf",
            fileSizeBytes = sampleVaultFile.length(),
            isSensitive = true
        )
        val habitActive = Habit(id = 1L, name = "Drink 8 Glasses Water", targetCount = 8, isArchived = false)
        val habitArchived = Habit(id = 2L, name = "No Spend Challenge", moneyRuleType = "NO_SPEND_DAY", isArchived = true)
        val habitLog1 = HabitLog(id = 1L, habitId = 1L, dateIso = "2026-10-31", currentCount = 8, status = "COMPLETED")

        val encodedVaultPayloads = DocumentVaultUtils.encodeVaultFilesToBase64(listOf(vaultDoc))
        assertTrue(encodedVaultPayloads.containsKey(sampleVaultFile.absolutePath))

        val fullBackup = com.example.data.BackupData(
            accounts = accounts,
            transactions = qaTransactions,
            habits = listOf(habitActive, habitArchived),
            habitLogs = listOf(habitLog1),
            vaultDocuments = listOf(vaultDoc),
            vaultFilePayloadsBase64 = encodedVaultPayloads,
            splitGroups = listOf(SplitGroup(id = 1L, name = "Goa Trip", type = "Trip", membersJson = "[\"Me\",\"Raj\"]")),
            investmentHoldings = listOf(
                InvestmentHolding(
                    id = 1L,
                    name = "Nifty 50 Index",
                    assetType = "Mutual Fund/SIP",
                    units = 100.0,
                    investedAmount = 10_000.0,
                    currentValue = 11_500.0,
                    buyDate = lastYear
                )
            ),
            userSettings = mapOf(
                "currency_code" to "INR",
                "theme_mode" to "Dark",
                "default_account" to "Bank Account"
            )
        )

        val backupJson = com.example.ui.BackupHelper.serializeBackupToJson(fullBackup)
        // Security check: confirm no API keys or secrets exist in exported backup JSON
        assertFalse(backupJson.contains("AIza"))
        assertFalse(backupJson.contains("gemini_api_key", ignoreCase = true))

        // Simulate full WIPE: delete the local vault file from disk
        DocumentVaultUtils.deleteLocalFileIfExists(sampleVaultFile.absolutePath)
        assertFalse(sampleVaultFile.exists())

        // Validate and restore from JSON
        val validation = com.example.ui.BackupHelper.parseAndValidateBackupJson(backupJson)
        assertTrue(validation.isValid)
        val restoredData = validation.parsedData!!
        assertEquals(2, restoredData.habits.size)
        assertEquals(1, restoredData.habitLogs.size)
        assertEquals(1, restoredData.vaultDocuments.size)

        val restoredVaultDocs = DocumentVaultUtils.restoreVaultFilesFromBase64(
            context = context,
            documents = restoredData.vaultDocuments,
            payloadsBase64 = restoredData.vaultFilePayloadsBase64
        )
        val restoredFile = java.io.File(restoredVaultDocs.first().localFilePath!!)
        assertTrue(restoredFile.exists())
        assertEquals(String(sampleFileBytes, Charsets.UTF_8), String(restoredFile.readBytes(), Charsets.UTF_8))

        // 4. SECURITY & HIDE-BALANCES QA
        FormatUtils.hideBalances = true
        val masked = FormatUtils.formatInr(125_000.0)
        assertTrue(masked.contains("••••"))
        FormatUtils.hideBalances = false
        val unmasked = FormatUtils.formatInr(125_000.0)
        assertTrue(unmasked.contains("1,25,000"))

        // Clean up test vault file
        DocumentVaultUtils.deleteLocalFileIfExists(restoredFile.absolutePath)
    }
}

package com.example.utils

import com.example.data.*
import java.util.Calendar

data class GlobalSearchResultItem(
    val key: String,
    val moduleKey: String,
    val moduleLabel: String,
    val title: String,
    val subtitle: String,
    val badgeText: String,
    val targetRoute: String
)

data class UnifiedTodayOverview(
    val tasksDueToday: List<TaskItem>,
    val habitsForToday: List<HabitComputedStats>,
    val billsDueSoon: List<BillReminder>,
    val expiringDocuments: List<VaultDocument>,
    val maturingFds: List<HoldingPerformance>,
    val totalActionableCount: Int
)

object EcosystemHubUtils {

    data class ModuleDefinition(
        val key: String,
        val title: String,
        val subtitle: String,
        val route: String
    )

    val OPTIONAL_MODULES = listOf(
        ModuleDefinition(
            key = "ai_assistant",
            title = "AI Assistant (Gemini)",
            subtitle = "Smart entry, receipt scan, ask my data, monthly insights, notes & task helpers",
            route = "ai_assistant"
        ),
        ModuleDefinition(
            key = "tasks",
            title = "Tasks & Schedule",
            subtitle = "Daily to-dos, subtasks, calendar agenda & money-linked tasks",
            route = "tasks"
        ),
        ModuleDefinition(
            key = "notes",
            title = "Notes & Checklists",
            subtitle = "Rich markdown notes, checklists, color labels & attachments",
            route = "notes"
        ),
        ModuleDefinition(
            key = "habits",
            title = "Habits & Consistency",
            subtitle = "Streaks, 35-day heatmaps, rest days & money-linked habits",
            route = "habits"
        ),
        ModuleDefinition(
            key = "split_bills",
            title = "Split Bills (Splitwise)",
            subtitle = "Group expenses, simplify debts, settle up & WhatsApp/PDF export",
            route = "split_bills"
        ),
        ModuleDefinition(
            key = "investments",
            title = "Investments & Net Worth",
            subtitle = "Stocks, Mutual Funds/SIP, FD/RD, Gold, PPF, Loans & XIRR",
            route = "investments"
        ),
        ModuleDefinition(
            key = "document_vault",
            title = "Document Vault",
            subtitle = "Local warranties, bills, IDs, PIN lock & 30/7/1 day expiry alerts",
            route = "document_vault"
        ),
        ModuleDefinition(
            key = "udhaar",
            title = "People & Udhaar (Khata)",
            subtitle = "Track money lent, borrowed, due dates & repayments",
            route = "udhaar"
        ),
        ModuleDefinition(
            key = "reports",
            title = "Reports & Analytics",
            subtitle = "Charts, monthly comparisons & PDF/Excel/CSV export",
            route = "analytics"
        )
    )

    /**
     * Performs a fault-isolated search across all enabled modules.
     * Each module is wrapped in `runCatching` so an error in one module can never break search in others.
     */
    fun searchAllModules(
        query: String,
        enabledModules: Set<String>,
        moduleFilter: String = "ALL",
        transactions: List<Transaction>,
        tasks: List<TaskItem>,
        events: List<ScheduleEvent>,
        notes: List<MoneyNote>,
        habits: List<Habit>,
        splitGroups: List<SplitGroup>,
        splitExpenses: List<SplitExpense>,
        holdings: List<InvestmentHolding>,
        liabilities: List<LiabilityItem>,
        vaultDocuments: List<VaultDocument>,
        people: List<Person>,
        udhaarEntries: List<UdhaarEntry>
    ): List<GlobalSearchResultItem> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()

        val results = mutableListOf<GlobalSearchResultItem>()

        // 1. Money / Transactions (Always core)
        if (moduleFilter == "ALL" || moduleFilter == "money") {
            runCatching {
                transactions.filter { tx ->
                    tx.category.lowercase().contains(q) ||
                        tx.subcategory.lowercase().contains(q) ||
                        tx.note.lowercase().contains(q) ||
                        tx.account.lowercase().contains(q) ||
                        tx.tags.lowercase().contains(q) ||
                        tx.amount.toInt().toString().contains(q)
                }.take(15).forEach { tx ->
                    val label = tx.subcategory.ifBlank { tx.category }
                    results.add(
                        GlobalSearchResultItem(
                            key = "tx_${tx.id}",
                            moduleKey = "money",
                            moduleLabel = "Money",
                            title = "$label (${FormatUtils.formatInr(tx.amount)})",
                            subtitle = "${tx.account} • ${FormatUtils.formatDate(tx.timestamp)}${if (tx.note.isNotBlank()) " • ${tx.note}" else ""}",
                            badgeText = if (tx.isIncome) "INCOME" else tx.category.uppercase(),
                            targetRoute = "money"
                        )
                    )
                }
            }
        }

        // 2. Tasks & Schedule
        if ("tasks" in enabledModules && (moduleFilter == "ALL" || moduleFilter == "tasks")) {
            runCatching {
                tasks.filter { t ->
                    t.title.lowercase().contains(q) ||
                        t.description.lowercase().contains(q) ||
                        t.category.lowercase().contains(q)
                }.take(10).forEach { t ->
                    results.add(
                        GlobalSearchResultItem(
                            key = "task_${t.id}",
                            moduleKey = "tasks",
                            moduleLabel = "Tasks",
                            title = t.title,
                            subtitle = "${t.category} • Priority: ${t.priority} • Status: ${t.status}",
                            badgeText = t.status,
                            targetRoute = "tasks"
                        )
                    )
                }
                events.filter { ev ->
                    ev.title.lowercase().contains(q) ||
                        ev.location.lowercase().contains(q) ||
                        ev.description.lowercase().contains(q)
                }.take(8).forEach { ev ->
                    results.add(
                        GlobalSearchResultItem(
                            key = "event_${ev.id}",
                            moduleKey = "tasks",
                            moduleLabel = "Schedule",
                            title = ev.title,
                            subtitle = "${FormatUtils.formatDate(ev.startTime)}${if (ev.location.isNotBlank()) " @ ${ev.location}" else ""}",
                            badgeText = "EVENT",
                            targetRoute = "schedule"
                        )
                    )
                }
            }
        }

        // 3. Notes & Checklists
        if ("notes" in enabledModules && (moduleFilter == "ALL" || moduleFilter == "notes")) {
            runCatching {
                notes.filter { NoteUtils.matchesQuery(it, q) }.take(12).forEach { note ->
                    results.add(
                        GlobalSearchResultItem(
                            key = "note_${note.id}",
                            moduleKey = "notes",
                            moduleLabel = "Notes",
                            title = note.title.ifBlank { "Untitled Note" },
                            subtitle = note.content.replace("\n", " ").take(80),
                            badgeText = if (note.isPinned) "PINNED" else "NOTE",
                            targetRoute = "notes"
                        )
                    )
                }
            }
        }

        // 4. Habits
        if ("habits" in enabledModules && (moduleFilter == "ALL" || moduleFilter == "habits")) {
            runCatching {
                habits.filter { h ->
                    h.name.lowercase().contains(q) ||
                        h.unitLabel.lowercase().contains(q) ||
                        h.moneyRuleType.lowercase().contains(q)
                }.take(10).forEach { h ->
                    results.add(
                        GlobalSearchResultItem(
                            key = "habit_${h.id}",
                            moduleKey = "habits",
                            moduleLabel = "Habits",
                            title = h.name,
                            subtitle = "Target: ${h.targetCount} ${h.unitLabel} (${h.frequencyType})",
                            badgeText = "HABIT",
                            targetRoute = "habits"
                        )
                    )
                }
            }
        }

        // 5. Split Bills
        if ("split_bills" in enabledModules && (moduleFilter == "ALL" || moduleFilter == "split_bills")) {
            runCatching {
                splitGroups.filter { g ->
                    g.name.lowercase().contains(q) ||
                        g.type.lowercase().contains(q) ||
                        g.membersJson.lowercase().contains(q)
                }.take(8).forEach { g ->
                    val members = SplitUtils.parseMembers(g.membersJson)
                    results.add(
                        GlobalSearchResultItem(
                            key = "split_group_${g.id}",
                            moduleKey = "split_bills",
                            moduleLabel = "Split Bills",
                            title = g.name,
                            subtitle = "${g.type} • Members: ${members.joinToString(", ")}",
                            badgeText = "GROUP",
                            targetRoute = "split_bills"
                        )
                    )
                }
                splitExpenses.filter { exp ->
                    exp.title.lowercase().contains(q) || exp.paidBy.lowercase().contains(q)
                }.take(8).forEach { exp ->
                    results.add(
                        GlobalSearchResultItem(
                            key = "split_exp_${exp.id}",
                            moduleKey = "split_bills",
                            moduleLabel = "Split Bills",
                            title = "${exp.title} (${FormatUtils.formatInr(exp.totalAmount)})",
                            subtitle = "Paid by ${exp.paidBy} • My share: ${FormatUtils.formatInr(exp.myShareAmount)}",
                            badgeText = exp.splitType,
                            targetRoute = "split_bills"
                        )
                    )
                }
            }
        }

        // 6. Investments & Liabilities
        if ("investments" in enabledModules && (moduleFilter == "ALL" || moduleFilter == "investments")) {
            runCatching {
                holdings.filter { h ->
                    h.name.lowercase().contains(q) ||
                        h.assetType.lowercase().contains(q) ||
                        h.notes.lowercase().contains(q)
                }.take(10).forEach { h ->
                    results.add(
                        GlobalSearchResultItem(
                            key = "holding_${h.id}",
                            moduleKey = "investments",
                            moduleLabel = "Investments",
                            title = "${h.name} (${FormatUtils.formatInr(h.currentValue)})",
                            subtitle = "${h.assetType} • Invested: ${FormatUtils.formatInr(h.investedAmount)}",
                            badgeText = h.assetType.uppercase(),
                            targetRoute = "investments"
                        )
                    )
                }
                liabilities.filter { l ->
                    l.name.lowercase().contains(q) || l.type.lowercase().contains(q)
                }.take(6).forEach { l ->
                    results.add(
                        GlobalSearchResultItem(
                            key = "liability_${l.id}",
                            moduleKey = "investments",
                            moduleLabel = "Investments",
                            title = "${l.name} (Remaining: ${FormatUtils.formatInr(l.remainingBalance)})",
                            subtitle = "EMI: ${FormatUtils.formatInr(l.emiAmount)}/mo • Rate: ${l.interestRate}%",
                            badgeText = "LIABILITY",
                            targetRoute = "investments"
                        )
                    )
                }
            }
        }

        // 7. Document Vault
        if ("document_vault" in enabledModules && (moduleFilter == "ALL" || moduleFilter == "document_vault")) {
            runCatching {
                vaultDocuments.filter { doc ->
                    doc.title.lowercase().contains(q) ||
                        doc.category.lowercase().contains(q) ||
                        doc.tagsCsv.lowercase().contains(q) ||
                        (!doc.isSensitive && doc.notes.lowercase().contains(q))
                }.take(10).forEach { doc ->
                    results.add(
                        GlobalSearchResultItem(
                            key = "vault_${doc.id}",
                            moduleKey = "document_vault",
                            moduleLabel = "Doc Vault",
                            title = if (doc.isSensitive) "${doc.title} (PIN Protected)" else doc.title,
                            subtitle = "${doc.category} • Issued: ${FormatUtils.formatDate(doc.issueDate)}",
                            badgeText = if (doc.isSensitive) "PIN LOCKED" else doc.category.uppercase(),
                            targetRoute = "document_vault"
                        )
                    )
                }
            }
        }

        // 8. People & Udhaar
        if ("udhaar" in enabledModules && (moduleFilter == "ALL" || moduleFilter == "udhaar")) {
            runCatching {
                people.filter { p ->
                    p.name.lowercase().contains(q) || p.phone.lowercase().contains(q)
                }.take(8).forEach { p ->
                    results.add(
                        GlobalSearchResultItem(
                            key = "person_${p.id}",
                            moduleKey = "udhaar",
                            moduleLabel = "Udhaar",
                            title = p.name,
                            subtitle = p.phone.ifBlank { "Udhaar Contact" },
                            badgeText = "PERSON",
                            targetRoute = "person/${p.id}"
                        )
                    )
                }
                udhaarEntries.filter { u ->
                    u.note.lowercase().contains(q)
                }.take(6).forEach { u ->
                    results.add(
                        GlobalSearchResultItem(
                            key = "udhaar_${u.id}",
                            moduleKey = "udhaar",
                            moduleLabel = "Udhaar",
                            title = "${u.note.ifBlank { "Udhaar" }} (${FormatUtils.formatInr(u.remainingAmount)})",
                            subtitle = if (u.type == "GAVE") "To Receive" else "To Pay",
                            badgeText = u.type,
                            targetRoute = "udhaar"
                        )
                    )
                }
            }
        }

        return results
    }

    /**
     * Builds the Unified "Today" View across Tasks, Habits, Bills Due, and Expiring Documents/FDs.
     * Every section is isolated with `runCatching` and gated by `enabledModules`.
     */
    fun buildUnifiedTodayOverview(
        enabledModules: Set<String>,
        tasks: List<TaskItem>,
        habits: List<Habit>,
        habitLogs: List<HabitLog>,
        transactions: List<Transaction>,
        goalContributions: List<GoalContribution>,
        bills: List<BillReminder>,
        vaultDocuments: List<VaultDocument>,
        holdings: List<InvestmentHolding>,
        nowMillis: Long = System.currentTimeMillis()
    ): UnifiedTodayOverview {
        val calEndOfToday = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis

        val todayIso = HabitUtils.getIsoFromMillis(nowMillis)

        // 1. Tasks due today or overdue (not DONE)
        val tasksDueToday = if ("tasks" in enabledModules) {
            runCatching {
                tasks.filter { t ->
                    t.status != "DONE" && (t.dueDateTime == null || t.dueDateTime <= calEndOfToday)
                }.sortedBy { it.dueDateTime ?: Long.MAX_VALUE }
            }.getOrElse { emptyList() }
        } else emptyList()

        // 2. Habits scheduled for today
        val habitsForToday = if ("habits" in enabledModules) {
            runCatching {
                habits.mapNotNull { h ->
                    if (HabitUtils.isScheduledOnDate(h, todayIso)) {
                        val logsForH = habitLogs.filter { it.habitId == h.id }
                        HabitUtils.computeHabitStats(
                            habit = h,
                            selectedDateIso = todayIso,
                            logsForHabit = logsForH,
                            transactions = transactions,
                            goalContributions = goalContributions
                        )
                    } else null
                }
            }.getOrElse { emptyList() }
        } else emptyList()

        // 3. Bills due today, overdue, or due within next 7 days
        val sevenDaysLater = nowMillis + 7L * 24L * 3600L * 1000L
        val billsDueSoon = runCatching {
            bills.filter { b ->
                !b.isPaid && b.dueDate <= sevenDaysLater
            }.sortedBy { it.dueDate }
        }.getOrElse { emptyList() }

        // 4. Expiring Vault Documents (expired or expiring within 30 days)
        val expiringDocuments = if ("document_vault" in enabledModules) {
            runCatching {
                vaultDocuments.filter { doc ->
                    val d = DocumentVaultUtils.daysUntilExpiry(doc.expiryDate, nowMillis)
                    d != null && d <= 30L
                }.sortedBy { it.expiryDate ?: Long.MAX_VALUE }
            }.getOrElse { emptyList() }
        } else emptyList()

        // 5. Maturing FDs (<= 30 days)
        val maturingFds = if ("investments" in enabledModules) {
            runCatching {
                holdings
                    .filter { it.assetType == "FD/RD" }
                    .map { InvestmentUtils.calculateHoldingPerformance(it, nowMillis) }
                    .filter { it.isMaturityAlert }
            }.getOrElse { emptyList() }
        } else emptyList()

        val pendingHabitsCount = habitsForToday.count { it.selectedDayStatus != "COMPLETED" && it.selectedDayStatus != "SKIPPED" }
        val totalActionable = tasksDueToday.size + pendingHabitsCount + billsDueSoon.size + expiringDocuments.size + maturingFds.size

        return UnifiedTodayOverview(
            tasksDueToday = tasksDueToday,
            habitsForToday = habitsForToday,
            billsDueSoon = billsDueSoon,
            expiringDocuments = expiringDocuments,
            maturingFds = maturingFds,
            totalActionableCount = totalActionable
        )
    }
}

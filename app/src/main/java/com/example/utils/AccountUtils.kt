package com.example.utils

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.data.Account
import com.example.data.AccountType
import com.example.data.Transaction
import java.util.Locale
import kotlin.math.abs

/**
 * Precomputed O(N) snapshot of all account balances, per-account transaction counts,
 * and reconciled totals for fast rendering with 5,000+ transactions.
 */
data class AccountBalanceSnapshot(
    val balancesByAccountName: Map<String, Double>,
    val txCountsByAccountName: Map<String, Int>,
    val totalBalance: Double,
    val cashBalance: Double,
    val bankBalance: Double,
    val upiBalance: Double,
    val creditCardBalance: Double,
    val orphanedTransactionCount: Int,
    val orphanedNetAmount: Double
) {
    fun getBalance(accountName: String): Double =
        balancesByAccountName[accountName.trim().lowercase(Locale.US)] ?: 0.0

    fun getTransactionCount(accountName: String): Int =
        txCountsByAccountName[accountName.trim().lowercase(Locale.US)] ?: 0
}

/**
 * Audit result verifying that individual account balances reconcile with total balance.
 */
data class BalanceReconciliationResult(
    val isReconciled: Boolean,
    val sumOfAccountBalances: Double,
    val reportedTotalBalance: Double,
    val sumByAccountTypes: Double,
    val discrepancyAmount: Double,
    val orphanedTransactionIds: List<Int>
)

/**
 * Result of a Year-End Close / Archive operation up to [cutoffTimestamp].
 * Guarantees that every account's current balance and total balance remain identical
 * after rolling archived transaction net flows into each account's opening balance.
 */
data class YearEndCloseResult(
    val cutoffTimestamp: Long,
    val updatedAccounts: List<Account>,
    val archivedTransactions: List<Transaction>,
    val remainingTransactions: List<Transaction>,
    val archivedCount: Int,
    val totalBalanceBefore: Double,
    val totalBalanceAfter: Double,
    val balancesMatch: Boolean
)

object AccountUtils {

    /**
     * Single source of truth for an individual account's balance:
     * Current balance = opening balance + income - expense (including transfers & adjustments).
     */
    fun calculateAccountBalance(account: Account, transactions: List<Transaction>): Double {
        val targetKey = account.name.trim().lowercase(Locale.US)
        var netFlow = 0.0
        for (tx in transactions) {
            if (!tx.amount.isFinite()) continue
            if (tx.account.trim().lowercase(Locale.US) == targetKey) {
                if (tx.isIncome) {
                    netFlow += tx.amount
                } else {
                    netFlow -= tx.amount
                }
            }
        }
        val opening = if (account.balance.isFinite()) account.balance else 0.0
        return FormatUtils.round2(opening + netFlow)
    }

    /**
     * Computes all account balances and type totals in a single O(N) pass over [transactions].
     * Guarantees: totalBalance == sum of all individual account balances.
     */
    fun buildAccountBalanceSnapshot(
        accounts: List<Account>,
        transactions: List<Transaction>
    ): AccountBalanceSnapshot {
        val netFlowByAcc = HashMap<String, Double>(accounts.size * 2 + 4)
        val countByAcc = HashMap<String, Int>(accounts.size * 2 + 4)
        val knownKeys = accounts.mapTo(HashSet()) { it.name.trim().lowercase(Locale.US) }

        var orphanedCount = 0
        var orphanedNet = 0.0

        for (tx in transactions) {
            if (!tx.amount.isFinite()) continue
            val key = tx.account.trim().lowercase(Locale.US)
            val signed = if (tx.isIncome) tx.amount else -tx.amount
            if (knownKeys.contains(key)) {
                netFlowByAcc[key] = (netFlowByAcc[key] ?: 0.0) + signed
                countByAcc[key] = (countByAcc[key] ?: 0) + 1
            } else {
                orphanedCount++
                orphanedNet += signed
            }
        }

        val balancesMap = HashMap<String, Double>(accounts.size * 2 + 2)
        var total = 0.0
        var cash = 0.0
        var bank = 0.0
        var upi = 0.0
        var creditCard = 0.0

        for (acc in accounts) {
            val key = acc.name.trim().lowercase(Locale.US)
            val opening = if (acc.balance.isFinite()) acc.balance else 0.0
            val bal = FormatUtils.round2(opening + (netFlowByAcc[key] ?: 0.0))
            balancesMap[key] = bal
            total = FormatUtils.round2(total + bal)

            when (AccountType.fromId(acc.type)) {
                AccountType.CASH -> cash = FormatUtils.round2(cash + bal)
                AccountType.BANK -> bank = FormatUtils.round2(bank + bal)
                AccountType.UPI -> upi = FormatUtils.round2(upi + bal)
                AccountType.CREDIT_CARD -> creditCard = FormatUtils.round2(creditCard + bal)
            }
        }

        return AccountBalanceSnapshot(
            balancesByAccountName = balancesMap,
            txCountsByAccountName = countByAcc,
            totalBalance = total,
            cashBalance = cash,
            bankBalance = bank,
            upiBalance = upi,
            creditCardBalance = creditCard,
            orphanedTransactionCount = orphanedCount,
            orphanedNetAmount = FormatUtils.round2(orphanedNet)
        )
    }

    /**
     * Total balance across all active accounts.
     * Invariant: calculateTotalBalance(accounts, transactions) == sum of calculateAccountBalance(acc, transactions).
     */
    fun calculateTotalBalance(accounts: List<Account>, transactions: List<Transaction>): Double {
        if (accounts.isEmpty()) return 0.0
        return buildAccountBalanceSnapshot(accounts, transactions).totalBalance
    }

    fun calculateCashBalance(accounts: List<Account>, transactions: List<Transaction>): Double {
        if (accounts.isEmpty()) return 0.0
        return buildAccountBalanceSnapshot(accounts, transactions).cashBalance
    }

    fun calculateBankBalance(accounts: List<Account>, transactions: List<Transaction>): Double {
        if (accounts.isEmpty()) return 0.0
        return buildAccountBalanceSnapshot(accounts, transactions).bankBalance
    }

    fun calculateUpiBalance(accounts: List<Account>, transactions: List<Transaction>): Double {
        if (accounts.isEmpty()) return 0.0
        return buildAccountBalanceSnapshot(accounts, transactions).upiBalance
    }

    fun calculateCreditCardBalance(accounts: List<Account>, transactions: List<Transaction>): Double {
        if (accounts.isEmpty()) return 0.0
        return buildAccountBalanceSnapshot(accounts, transactions).creditCardBalance
    }

    /**
     * Verifies mathematical reconciliation across all accounts and account types.
     */
    fun reconcileBalances(
        accounts: List<Account>,
        transactions: List<Transaction>
    ): BalanceReconciliationResult {
        val snapshot = buildAccountBalanceSnapshot(accounts, transactions)
        val sumIndividual = FormatUtils.round2(
            accounts.sumOf { calculateAccountBalance(it, transactions) }
        )
        val sumTypes = FormatUtils.round2(
            snapshot.cashBalance + snapshot.bankBalance + snapshot.upiBalance + snapshot.creditCardBalance
        )
        val discrepancy = FormatUtils.round2(abs(sumIndividual - snapshot.totalBalance) + abs(sumTypes - snapshot.totalBalance))
        val knownKeys = accounts.mapTo(HashSet()) { it.name.trim().lowercase(Locale.US) }
        val orphanedIds = transactions
            .filter { !knownKeys.contains(it.account.trim().lowercase(Locale.US)) }
            .map { it.id }

        return BalanceReconciliationResult(
            isReconciled = discrepancy < 0.01 && orphanedIds.isEmpty(),
            sumOfAccountBalances = sumIndividual,
            reportedTotalBalance = snapshot.totalBalance,
            sumByAccountTypes = sumTypes,
            discrepancyAmount = discrepancy,
            orphanedTransactionIds = orphanedIds
        )
    }

    /**
     * Reassigns any transactions referencing a deleted/missing account to [fallbackAccountName].
     */
    fun reassignOrphanedTransactions(
        accounts: List<Account>,
        transactions: List<Transaction>,
        fallbackAccountName: String = accounts.firstOrNull()?.name ?: "Cash in Hand"
    ): List<Transaction> {
        val knownKeys = accounts.mapTo(HashSet()) { it.name.trim().lowercase(Locale.US) }
        return transactions.map { tx ->
            if (knownKeys.contains(tx.account.trim().lowercase(Locale.US))) {
                tx
            } else {
                tx.copy(account = fallbackAccountName)
            }
        }
    }

    /**
     * Rolls all transactions with `timestamp <= cutoffTimestamp` into each account's
     * opening balance (`account.balance`) and returns the updated accounts along with
     * the archived and remaining transactions.
     * Invariant: every account's current balance and totalBalance before and after are identical.
     */
    fun calculateYearEndClose(
        accounts: List<Account>,
        transactions: List<Transaction>,
        cutoffTimestamp: Long
    ): YearEndCloseResult {
        val totalBefore = calculateTotalBalance(accounts, transactions)
        val archived = ArrayList<Transaction>()
        val remaining = ArrayList<Transaction>()
        val archivedNetByAccount = HashMap<String, Double>(accounts.size * 2 + 4)

        for (tx in transactions) {
            if (tx.timestamp <= cutoffTimestamp) {
                archived.add(tx)
                if (tx.amount.isFinite()) {
                    val key = tx.account.trim().lowercase(Locale.US)
                    val signed = if (tx.isIncome) tx.amount else -tx.amount
                    archivedNetByAccount[key] = (archivedNetByAccount[key] ?: 0.0) + signed
                }
            } else {
                remaining.add(tx)
            }
        }

        val updatedAccounts = accounts.map { acc ->
            val key = acc.name.trim().lowercase(Locale.US)
            val opening = if (acc.balance.isFinite()) acc.balance else 0.0
            val archivedNet = archivedNetByAccount[key] ?: 0.0
            acc.copy(balance = FormatUtils.round2(opening + archivedNet))
        }

        val totalAfter = calculateTotalBalance(updatedAccounts, remaining)
        val perAccountMatches = accounts.indices.all { idx ->
            val beforeBal = calculateAccountBalance(accounts[idx], transactions)
            val afterBal = calculateAccountBalance(updatedAccounts[idx], remaining)
            abs(beforeBal - afterBal) < 0.01
        }

        return YearEndCloseResult(
            cutoffTimestamp = cutoffTimestamp,
            updatedAccounts = updatedAccounts,
            archivedTransactions = archived,
            remainingTransactions = remaining,
            archivedCount = archived.size,
            totalBalanceBefore = totalBefore,
            totalBalanceAfter = totalAfter,
            balancesMatch = perAccountMatches && abs(totalBefore - totalAfter) < 0.01
        )
    }

    val availableColors = listOf(
        "#2E7D32" to "Forest Green",
        "#1565C0" to "Royal Blue",
        "#7B1FA2" to "Deep Purple",
        "#E65100" to "Vibrant Orange",
        "#00897B" to "Teal",
        "#C2185B" to "Pink / Berry",
        "#D32F2F" to "Ruby Red",
        "#455A64" to "Slate Grey"
    )

    val availableIcons = listOf(
        "AccountBalance" to Icons.Default.AccountBalance,
        "Payments" to Icons.Default.Payments,
        "QrCode" to Icons.Default.QrCode,
        "CreditCard" to Icons.Default.CreditCard,
        "AccountBalanceWallet" to Icons.Default.AccountBalanceWallet,
        "Savings" to Icons.Default.Savings
    )

    fun getIconForName(iconName: String, accountType: AccountType = AccountType.BANK): ImageVector {
        return when (iconName) {
            "AccountBalance" -> Icons.Default.AccountBalance
            "Payments" -> Icons.Default.Payments
            "QrCode" -> Icons.Default.QrCode
            "CreditCard" -> Icons.Default.CreditCard
            "AccountBalanceWallet" -> Icons.Default.AccountBalanceWallet
            "Savings" -> Icons.Default.Savings
            else -> when (accountType) {
                AccountType.CASH -> Icons.Default.Payments
                AccountType.BANK -> Icons.Default.AccountBalance
                AccountType.UPI -> Icons.Default.QrCode
                AccountType.CREDIT_CARD -> Icons.Default.CreditCard
            }
        }
    }

    fun parseColor(hex: String, fallback: Color = Color(0xFF1565C0)): Color {
        return try {
            val cleanHex = hex.removePrefix("#")
            val colorLong = cleanHex.toLong(16)
            if (cleanHex.length == 6) {
                Color(0xFF000000 or colorLong)
            } else {
                Color(colorLong)
            }
        } catch (_: Exception) {
            fallback
        }
    }
}

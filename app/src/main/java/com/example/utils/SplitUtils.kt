package com.example.utils

import com.example.data.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlin.math.abs
import kotlin.math.roundToInt

object SplitUtils {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    private val stringListType = Types.newParameterizedType(List::class.java, String::class.java)
    private val stringListAdapter = moshi.adapter<List<String>>(stringListType)

    private val splitListType = Types.newParameterizedType(List::class.java, MemberSplit::class.java)
    private val splitListAdapter = moshi.adapter<List<MemberSplit>>(splitListType)

    fun parseMembers(json: String?): List<String> {
        if (json.isNullOrBlank()) return listOf("Me")
        return try {
            val list = stringListAdapter.fromJson(json) ?: listOf("Me")
            if (list.none { it.equals("Me", ignoreCase = true) }) {
                listOf("Me") + list
            } else {
                list
            }
        } catch (e: Exception) {
            listOf("Me")
        }
    }

    fun serializeMembers(members: List<String>): String {
        val cleaned = members.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        val withMe = if (cleaned.none { it.equals("Me", ignoreCase = true) }) {
            listOf("Me") + cleaned
        } else {
            cleaned
        }
        return try {
            stringListAdapter.toJson(withMe)
        } catch (e: Exception) {
            "[\"Me\"]"
        }
    }

    fun parseSplits(json: String?): List<MemberSplit> {
        if (json.isNullOrBlank() || json == "[]") return emptyList()
        return try {
            splitListAdapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun serializeSplits(splits: List<MemberSplit>): String {
        return try {
            splitListAdapter.toJson(splits)
        } catch (e: Exception) {
            "[]"
        }
    }

    /**
     * Rounds to 2 decimal places safely without Int overflow on large group expenses.
     */
    fun round2(value: Double): Double {
        return FormatUtils.round2(value)
    }

    fun calculateEqualSplits(totalAmount: Double, members: List<String>): List<MemberSplit> {
        if (members.isEmpty() || totalAmount <= 0.0) return emptyList()
        val count = members.size
        val baseShare = ((totalAmount * 100.0).toLong() / count) / 100.0
        val remainder = round2(totalAmount - (baseShare * count))
        val pct = round2(100.0 / count)

        return members.mapIndexed { index, member ->
            val share = if (index == 0) round2(baseShare + remainder) else baseShare
            MemberSplit(
                memberName = member,
                amount = share,
                percentage = pct,
                shares = 1
            )
        }
    }

    fun calculateExactSplits(exactAmounts: Map<String, Double>, members: List<String>, totalAmount: Double): List<MemberSplit> {
        return members.map { member ->
            val amt = round2(exactAmounts[member] ?: 0.0)
            val pct = if (totalAmount > 0) round2((amt / totalAmount) * 100.0) else 0.0
            MemberSplit(
                memberName = member,
                amount = amt,
                percentage = pct,
                shares = 1
            )
        }
    }

    fun calculatePercentageSplits(
        totalAmount: Double,
        percentages: Map<String, Double>,
        members: List<String>
    ): List<MemberSplit> {
        if (members.isEmpty() || totalAmount <= 0.0) return emptyList()
        val rawSplits = members.map { member ->
            val pct = percentages[member] ?: 0.0
            val amt = round2(totalAmount * (pct / 100.0))
            MemberSplit(memberName = member, amount = amt, percentage = pct, shares = 1)
        }.toMutableList()

        val sumAllocated = rawSplits.sumOf { it.amount }
        val diff = round2(totalAmount - sumAllocated)
        if (abs(diff) <= 0.05 && rawSplits.isNotEmpty()) {
            val firstNonZeroIdx = rawSplits.indexOfFirst { it.percentage > 0 }.takeIf { it >= 0 } ?: 0
            val first = rawSplits[firstNonZeroIdx]
            rawSplits[firstNonZeroIdx] = first.copy(amount = round2(first.amount + diff))
        }
        return rawSplits
    }

    fun calculateShareSplits(
        totalAmount: Double,
        sharesMap: Map<String, Int>,
        members: List<String>
    ): List<MemberSplit> {
        if (members.isEmpty() || totalAmount <= 0.0) return emptyList()
        val totalShares = members.sumOf { (sharesMap[it] ?: 0).coerceAtLeast(0) }
        if (totalShares <= 0) return calculateEqualSplits(totalAmount, members)

        val rawSplits = members.map { member ->
            val sh = (sharesMap[member] ?: 0).coerceAtLeast(0)
            val amt = round2(totalAmount * (sh.toDouble() / totalShares.toDouble()))
            val pct = round2((sh.toDouble() / totalShares.toDouble()) * 100.0)
            MemberSplit(memberName = member, amount = amt, percentage = pct, shares = sh)
        }.toMutableList()

        val sumAllocated = rawSplits.sumOf { it.amount }
        val diff = round2(totalAmount - sumAllocated)
        if (abs(diff) <= 0.05 && rawSplits.isNotEmpty()) {
            val firstActiveIdx = rawSplits.indexOfFirst { it.shares > 0 }.takeIf { it >= 0 } ?: 0
            val first = rawSplits[firstActiveIdx]
            rawSplits[firstActiveIdx] = first.copy(amount = round2(first.amount + diff))
        }
        return rawSplits
    }

    /**
     * Simplifies debts across group members to minimize the number of payments required.
     */
    fun simplifyDebts(balances: List<MemberBalance>): List<SimplifiedDebt> {
        data class Node(val name: String, var amount: Double)

        val debtors = balances
            .filter { it.netBalance < -0.01 }
            .map { Node(it.memberName, round2(-it.netBalance)) }
            .sortedByDescending { it.amount }
            .toMutableList()

        val creditors = balances
            .filter { it.netBalance > 0.01 }
            .map { Node(it.memberName, round2(it.netBalance)) }
            .sortedByDescending { it.amount }
            .toMutableList()

        val result = mutableListOf<SimplifiedDebt>()
        var i = 0
        var j = 0

        while (i < debtors.size && j < creditors.size) {
            val debtor = debtors[i]
            val creditor = creditors[j]
            val settleAmt = round2(minOf(debtor.amount, creditor.amount))

            if (settleAmt > 0.01) {
                result.add(
                    SimplifiedDebt(
                        fromMember = debtor.name,
                        toMember = creditor.name,
                        amount = settleAmt
                    )
                )
            }

            debtor.amount = round2(debtor.amount - settleAmt)
            creditor.amount = round2(creditor.amount - settleAmt)

            if (debtor.amount <= 0.01) i++
            if (creditor.amount <= 0.01) j++
        }

        return result
    }

    fun calculateGroupSummary(
        group: SplitGroup,
        expenses: List<SplitExpense>,
        settlements: List<SplitSettlement>
    ): SplitGroupSummary {
        val members = parseMembers(group.membersJson)
        val paidMap = mutableMapOf<String, Double>()
        val shareMap = mutableMapOf<String, Double>()
        val settledOutMap = mutableMapOf<String, Double>()
        val settledInMap = mutableMapOf<String, Double>()

        members.forEach { m ->
            paidMap[m] = 0.0
            shareMap[m] = 0.0
            settledOutMap[m] = 0.0
            settledInMap[m] = 0.0
        }

        expenses.forEach { exp ->
            paidMap[exp.paidBy] = (paidMap[exp.paidBy] ?: 0.0) + exp.totalAmount
            val splits = parseSplits(exp.splitsJson)
            splits.forEach { sp ->
                shareMap[sp.memberName] = (shareMap[sp.memberName] ?: 0.0) + sp.amount
            }
        }

        settlements.forEach { st ->
            settledOutMap[st.fromMember] = (settledOutMap[st.fromMember] ?: 0.0) + st.amount
            settledInMap[st.toMember] = (settledInMap[st.toMember] ?: 0.0) + st.amount
        }

        val allKnownMembers = (members + paidMap.keys + shareMap.keys).distinct()
        val memberBalances = allKnownMembers.map { m ->
            val paid = round2(paidMap[m] ?: 0.0)
            val share = round2(shareMap[m] ?: 0.0)
            val settledOut = round2(settledOutMap[m] ?: 0.0)
            val settledIn = round2(settledInMap[m] ?: 0.0)
            // Net balance: (paid - share) + settledOut - settledIn
            val net = round2((paid - share) + settledOut - settledIn)
            MemberBalance(
                memberName = m,
                totalPaid = paid,
                totalShare = share,
                netBalance = net
            )
        }

        val simplified = simplifyDebts(memberBalances)
        val myBalance = memberBalances.firstOrNull { it.memberName.equals("Me", ignoreCase = true) }

        return SplitGroupSummary(
            group = group,
            members = allKnownMembers,
            totalGroupSpend = round2(expenses.sumOf { it.totalAmount }),
            myTotalPaid = myBalance?.totalPaid ?: 0.0,
            myTotalShare = myBalance?.totalShare ?: 0.0,
            myNetBalance = myBalance?.netBalance ?: 0.0,
            memberBalances = memberBalances,
            simplifiedDebts = simplified,
            expensesCount = expenses.size,
            settlementsCount = settlements.size
        )
    }

    fun buildWhatsAppGroupSummary(
        summary: SplitGroupSummary,
        expenses: List<SplitExpense>,
        settlements: List<SplitSettlement>
    ): String {
        val sb = StringBuilder()
        sb.append("*MoneyMate - Split Bills Summary*\n")
        sb.append("Group: *${summary.group.name}* (${summary.group.type})\n")
        sb.append("Date: ${FormatUtils.formatDate(System.currentTimeMillis())}\n")
        sb.append("Total Group Spend: *${FormatUtils.formatInr(summary.totalGroupSpend)}*\n")
        sb.append("--------------------------------\n")
        sb.append("*Member Balances:*\n")
        summary.memberBalances.forEach { mb ->
            val status = when {
                mb.netBalance > 0.01 -> "gets back ${FormatUtils.formatInr(mb.netBalance)}"
                mb.netBalance < -0.01 -> "owes ${FormatUtils.formatInr(-mb.netBalance)}"
                else -> "settled up"
            }
            sb.append("• ${mb.memberName}: $status (Paid ${FormatUtils.formatInr(mb.totalPaid)}, Share ${FormatUtils.formatInr(mb.totalShare)})\n")
        }
        sb.append("--------------------------------\n")
        sb.append("*Simplified Debts (Who Pays Whom):*\n")
        if (summary.simplifiedDebts.isEmpty()) {
            sb.append("All balances are settled up! 🎉\n")
        } else {
            summary.simplifiedDebts.forEach { debt ->
                sb.append("👉 *${debt.fromMember}* pays *${debt.toMember}*: *${FormatUtils.formatInr(debt.amount)}*\n")
            }
        }
        if (expenses.isNotEmpty()) {
            sb.append("--------------------------------\n")
            sb.append("*Recent Shared Expenses:*\n")
            expenses.take(10).forEach { exp ->
                sb.append("• ${FormatUtils.formatDate(exp.timestamp)}: ${exp.title} - ${FormatUtils.formatInr(exp.totalAmount)} (Paid by ${exp.paidBy})\n")
            }
        }
        return sb.toString().trim()
    }
}

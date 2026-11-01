package com.example.utils

import com.example.data.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToInt

object InvestmentUtils {

    val ASSET_TYPES = listOf(
        "Stocks",
        "Mutual Fund/SIP",
        "FD/RD",
        "Gold",
        "PPF/EPF",
        "Crypto",
        "Property",
        "Other"
    )

    val LIABILITY_TYPES = listOf(
        "LOAN_EMI" to "Loan / EMI",
        "CREDIT_CARD" to "Credit Card Due",
        "OTHER" to "Other Liability"
    )

    fun getColorForAssetType(assetType: String): String {
        return when (assetType) {
            "Stocks" -> "#1565C0"          // Royal Blue
            "Mutual Fund/SIP" -> "#2E7D32" // Emerald Green
            "FD/RD" -> "#00897B"           // Teal
            "Gold" -> "#F9A825"            // Gold
            "PPF/EPF" -> "#6A1B9A"         // Deep Purple
            "Crypto" -> "#E65100"          // Vibrant Orange
            "Property" -> "#4E342E"        // Warm Brown
            "Cash & Bank" -> "#0277BD"     // Sky Blue
            else -> "#546E7A"              // Slate
        }
    }

    /**
     * Rounds to 2 decimal places safely for huge holdings (> ₹2.14 Crore) without Int overflow.
     */
    fun round2(v: Double): Double = FormatUtils.round2(v)

    /**
     * Calculates Simple Return % and Annualized Return / XIRR % for a holding.
     */
    fun calculateHoldingPerformance(
        holding: InvestmentHolding,
        nowMillis: Long = System.currentTimeMillis()
    ): HoldingPerformance {
        val invested = holding.investedAmount
        val current = holding.currentValue
        val profitLoss = round2(current - invested)
        val simpleReturnPct = if (invested > 0.0) {
            round2(((current - invested) / invested) * 100.0)
        } else 0.0

        val daysHeld = maxOf(1, ((nowMillis - holding.buyDate) / (24L * 60 * 60 * 1000)).toInt())

        // Annualized return / XIRR calculation
        val xirrPct = if (invested > 0.0 && current > 0.0) {
            if (daysHeld < 30) {
                // For holdings held less than 30 days, show simple return to avoid extreme extrapolation
                simpleReturnPct
            } else {
                val years = daysHeld / 365.25
                val cagr = ((current / invested).pow(1.0 / years) - 1.0) * 100.0
                round2(cagr.coerceIn(-99.9, 999.9))
            }
        } else 0.0

        val daysToMaturity = holding.maturityDate?.let { mat ->
            ((mat - nowMillis) / (24L * 60 * 60 * 1000))
        }
        val isMaturityAlert = daysToMaturity != null && daysToMaturity <= 30L

        return HoldingPerformance(
            holding = holding,
            profitLoss = profitLoss,
            simpleReturnPercent = simpleReturnPct,
            annualizedReturnXirrPercent = xirrPct,
            daysHeld = daysHeld,
            isMaturityAlert = isMaturityAlert,
            daysToMaturity = daysToMaturity
        )
    }

    /**
     * Estimates Indian FD Maturity Amount using quarterly compounding: A = P * (1 + r/400)^(4 * years)
     */
    fun estimateFdMaturity(principal: Double, annualRatePercent: Double, durationDays: Int): Double {
        if (principal <= 0.0 || annualRatePercent <= 0.0 || durationDays <= 0) return principal
        val years = durationDays / 365.0
        val quarters = 4.0 * years
        val maturity = principal * (1.0 + (annualRatePercent / 400.0)).pow(quarters)
        return round2(maturity)
    }

    /**
     * Standard reducing-balance Loan EMI formula: E = P * r * (1+r)^n / ((1+r)^n - 1)
     */
    fun calculateMonthlyEmi(principal: Double, annualRatePercent: Double, tenureMonths: Int): Double {
        if (principal <= 0.0 || tenureMonths <= 0) return 0.0
        if (annualRatePercent <= 0.0) return round2(principal / tenureMonths)
        val r = (annualRatePercent / 12.0) / 100.0
        val factor = (1.0 + r).pow(tenureMonths.toDouble())
        val emi = (principal * r * factor) / (factor - 1.0)
        return round2(emi)
    }

    fun calculateNetWorthOverview(
        accounts: List<Account>,
        transactions: List<Transaction>,
        holdings: List<InvestmentHolding>,
        liabilities: List<LiabilityItem>,
        udhaarToPay: Double = 0.0,
        nowMillis: Long = System.currentTimeMillis()
    ): NetWorthOverview {
        val liquidBalance = AccountUtils.calculateTotalBalance(accounts, transactions)
        val holdingsPerf = holdings.map { calculateHoldingPerformance(it, nowMillis) }

        val totalInvested = round2(holdings.sumOf { it.investedAmount })
        val totalInvestmentsValue = round2(holdings.sumOf { it.currentValue })
        val totalProfitLoss = round2(totalInvestmentsValue - totalInvested)
        val totalReturnPct = if (totalInvested > 0.0) {
            round2((totalProfitLoss / totalInvested) * 100.0)
        } else 0.0

        val weightedXirr = if (totalInvested > 0.0) {
            round2(
                holdingsPerf.sumOf { it.annualizedReturnXirrPercent * it.holding.investedAmount } / totalInvested
            )
        } else 0.0

        val positiveLiquid = liquidBalance.coerceAtLeast(0.0)
        val negativeLiquidLiability = if (liquidBalance < 0.0) -liquidBalance else 0.0

        val totalAssets = round2(positiveLiquid + totalInvestmentsValue)
        val totalLiabilities = round2(liabilities.sumOf { it.remainingBalance } + udhaarToPay + negativeLiquidLiability)
        val netWorth = round2(totalAssets - totalLiabilities)

        // Asset Allocation Slices
        val allocationMap = mutableMapOf<String, Double>()
        if (positiveLiquid > 0.0) {
            allocationMap["Cash & Bank"] = positiveLiquid
        }
        holdings.forEach { h ->
            if (h.currentValue > 0.0) {
                allocationMap[h.assetType] = (allocationMap[h.assetType] ?: 0.0) + h.currentValue
            }
        }

        val allocationSlices = if (totalAssets > 0.0) {
            allocationMap.entries
                .sortedByDescending { it.value }
                .map { (type, amt) ->
                    AssetAllocationSlice(
                        assetType = type,
                        amount = round2(amt),
                        percentage = round2((amt / totalAssets) * 100.0),
                        colorHex = getColorForAssetType(type)
                    )
                }
        } else emptyList()

        // 6-Month Net Worth Trend
        val monthFormat = SimpleDateFormat("MMM", Locale.getDefault())
        val trendPoints = mutableListOf<MonthlyNetWorthPoint>()
        for (i in 5 downTo 0) {
            val calEnd = Calendar.getInstance().apply {
                timeInMillis = nowMillis
                add(Calendar.MONTH, -i)
                val maxDay = getActualMaximum(Calendar.DAY_OF_MONTH)
                set(Calendar.DAY_OF_MONTH, maxDay)
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
            }
            val cutoff = calEnd.timeInMillis
            val txsUpToMonth = transactions.filter { it.timestamp <= cutoff }
            val liquidAtMonth = AccountUtils.calculateTotalBalance(accounts, txsUpToMonth).coerceAtLeast(0.0)
            val holdingsAtMonth = holdings.filter { it.buyDate <= cutoff }.sumOf {
                if (i == 0) it.currentValue else it.investedAmount + ((it.currentValue - it.investedAmount) * ((6 - i) / 6.0))
            }
            val assetsAtMonth = round2(liquidAtMonth + holdingsAtMonth)
            val liabAtMonth = round2(
                liabilities.filter { it.startDate <= cutoff }.sumOf { it.remainingBalance } +
                        (if (i == 0) udhaarToPay else 0.0)
            )
            trendPoints.add(
                MonthlyNetWorthPoint(
                    monthLabel = monthFormat.format(calEnd.time),
                    assets = assetsAtMonth,
                    liabilities = liabAtMonth,
                    netWorth = round2(assetsAtMonth - liabAtMonth)
                )
            )
        }

        return NetWorthOverview(
            liquidCashBankBalance = round2(liquidBalance),
            totalInvestmentsCurrentValue = totalInvestmentsValue,
            totalInvestedCost = totalInvested,
            totalPortfolioProfitLoss = totalProfitLoss,
            totalPortfolioReturnPercent = totalReturnPct,
            totalPortfolioXirrPercent = weightedXirr,
            totalAssets = totalAssets,
            totalLiabilities = totalLiabilities,
            netWorth = netWorth,
            holdingsPerformance = holdingsPerf,
            assetAllocation = allocationSlices,
            monthlyTrend = trendPoints
        )
    }
}

package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
@Entity(tableName = "investment_holdings")
data class InvestmentHolding(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    // "Stocks", "Mutual Fund/SIP", "FD/RD", "Gold", "PPF/EPF", "Crypto", "Property", "Other"
    val assetType: String = "Mutual Fund/SIP",
    val units: Double = 1.0,
    val investedAmount: Double = 0.0,
    val currentValue: Double = 0.0,
    val buyDate: Long = System.currentTimeMillis(),
    // SIP Tracker fields
    val isSipEnabled: Boolean = false,
    val sipMonthlyAmount: Double = 0.0,
    val sipDayOfMonth: Int = 5,
    val sipLinkedAccount: String = "Bank Account",
    // FD/RD Tracker fields
    val interestRate: Double = 0.0,
    val maturityDate: Long? = null,
    val maturityAmount: Double? = null,
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "liability_items")
data class LiabilityItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    // "LOAN_EMI", "CREDIT_CARD", "OTHER"
    val type: String = "LOAN_EMI",
    val principalAmount: Double = 0.0,
    val interestRate: Double = 0.0,
    val emiAmount: Double = 0.0,
    val remainingBalance: Double = 0.0,
    val startDate: Long = System.currentTimeMillis(),
    val payoffDate: Long? = null,
    val linkedAccount: String = "Bank Account",
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

data class HoldingPerformance(
    val holding: InvestmentHolding,
    val profitLoss: Double,
    val simpleReturnPercent: Double,
    val annualizedReturnXirrPercent: Double,
    val daysHeld: Int,
    val isMaturityAlert: Boolean,
    val daysToMaturity: Long?
)

data class AssetAllocationSlice(
    val assetType: String,
    val amount: Double,
    val percentage: Double,
    val colorHex: String
)

data class MonthlyNetWorthPoint(
    val monthLabel: String,
    val assets: Double,
    val liabilities: Double,
    val netWorth: Double
)

data class NetWorthOverview(
    val liquidCashBankBalance: Double,
    val totalInvestmentsCurrentValue: Double,
    val totalInvestedCost: Double,
    val totalPortfolioProfitLoss: Double,
    val totalPortfolioReturnPercent: Double,
    val totalPortfolioXirrPercent: Double,
    val totalAssets: Double,
    val totalLiabilities: Double,
    val netWorth: Double,
    val holdingsPerformance: List<HoldingPerformance>,
    val assetAllocation: List<AssetAllocationSlice>,
    val monthlyTrend: List<MonthlyNetWorthPoint>
)

package com.example

import com.example.data.Account
import com.example.data.AccountType
import com.example.data.Transaction
import com.example.utils.AccountUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountUtilsTest {

    @Test
    fun testAccountBalanceCalculationWithOpeningBalance() {
        val account = Account(
            name = "SBI Bank",
            balance = 10000.0,
            type = "BANK"
        )
        val transactions = listOf(
            Transaction(id = 1, amount = 5000.0, isIncome = true, category = "Salary", account = "SBI Bank", timestamp = 1000L),
            Transaction(id = 2, amount = 2000.0, isIncome = false, category = "Food", account = "SBI Bank", timestamp = 2000L)
        )

        val balance = AccountUtils.calculateAccountBalance(account, transactions)
        // 10000 (opening) + 5000 (income) - 2000 (expense) = 13000
        assertEquals(13000.0, balance, 0.001)
    }

    @Test
    fun testTransferBetweenAccountsDoesNotAffectNetWorth() {
        val bank = Account(name = "Bank Account", balance = 25000.0, type = "BANK")
        val cash = Account(name = "Cash in Hand", balance = 2000.0, type = "CASH")
        val accounts = listOf(bank, cash)

        // Transfer 5000 from Bank to Cash
        val transactions = listOf(
            Transaction(id = 1, amount = 5000.0, isIncome = false, category = "Transfer", account = "Bank Account", timestamp = 1000L),
            Transaction(id = 2, amount = 5000.0, isIncome = true, category = "Transfer", account = "Cash in Hand", timestamp = 1000L)
        )

        val bankBalance = AccountUtils.calculateAccountBalance(bank, transactions)
        val cashBalance = AccountUtils.calculateAccountBalance(cash, transactions)
        val totalBalance = AccountUtils.calculateTotalBalance(accounts, transactions)

        // Bank: 25000 - 5000 = 20000
        assertEquals(20000.0, bankBalance, 0.001)
        // Cash: 2000 + 5000 = 7000
        assertEquals(7000.0, cashBalance, 0.001)
        // Total: 27000 (unchanged)
        assertEquals(27000.0, totalBalance, 0.001)
    }

    @Test
    fun testSegregationOfCashAndBankBalances() {
        val cash1 = Account(name = "Pocket Cash", balance = 1500.0, type = "CASH")
        val cash2 = Account(name = "Emergency Cash", balance = 3500.0, type = "CASH")
        val bank1 = Account(name = "HDFC Bank", balance = 50000.0, type = "BANK")
        val bank2 = Account(name = "ICICI Bank", balance = 25000.0, type = "BANK")
        val upi = Account(name = "Paytm Wallet", balance = 1200.0, type = "UPI")
        val accounts = listOf(cash1, cash2, bank1, bank2, upi)

        val transactions = listOf(
            Transaction(id = 1, amount = 500.0, isIncome = false, category = "Food", account = "Pocket Cash", timestamp = 1000L),
            Transaction(id = 2, amount = 10000.0, isIncome = true, category = "Freelance", account = "HDFC Bank", timestamp = 2000L)
        )

        val totalCash = AccountUtils.calculateCashBalance(accounts, transactions)
        val totalBank = AccountUtils.calculateBankBalance(accounts, transactions)
        val totalUpi = AccountUtils.calculateUpiBalance(accounts, transactions)

        // Cash: (1500 - 500) + 3500 = 4500
        assertEquals(4500.0, totalCash, 0.001)
        // Bank: (50000 + 10000) + 25000 = 85000
        assertEquals(85000.0, totalBank, 0.001)
        // UPI: 1200
        assertEquals(1200.0, totalUpi, 0.001)
    }

    @Test
    fun testBalanceAdjustment() {
        val account = Account(name = "Cash in Hand", balance = 1000.0, type = "CASH")
        // Target is 1500 -> Diff is +500 adjustment
        val transactions = listOf(
            Transaction(id = 1, amount = 500.0, isIncome = true, category = "Adjustment", account = "Cash in Hand", note = "Found cash in wallet", timestamp = 1000L)
        )
        val balance = AccountUtils.calculateAccountBalance(account, transactions)
        assertEquals(1500.0, balance, 0.001)
    }

    @Test
    fun testAccountTypeInferenceFallback() {
        assertEquals(AccountType.CASH, AccountType.fromId("CASH"))
        assertEquals(AccountType.CASH, AccountType.fromId("Cash in Hand"))
        assertEquals(AccountType.BANK, AccountType.fromId("BANK"))
        assertEquals(AccountType.BANK, AccountType.fromId("Main Bank"))
        assertEquals(AccountType.UPI, AccountType.fromId("UPI"))
        assertEquals(AccountType.UPI, AccountType.fromId("Online / UPI"))
        assertEquals(AccountType.CREDIT_CARD, AccountType.fromId("CREDIT_CARD"))
        assertEquals(AccountType.CREDIT_CARD, AccountType.fromId("Credit Card"))
    }
}

package com.example

import com.example.data.Account
import com.example.data.Transaction
import com.example.utils.AccountUtils
import com.example.utils.CategoryUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionSystemTest {

    @Test
    fun testDeletingTransactionRestoresBalanceCorrectly() {
        val account = Account(name = "SBI Bank", balance = 10000.0, type = "BANK")
        val tx1 = Transaction(id = 1, amount = 2000.0, isIncome = false, category = "Food", account = "SBI Bank", timestamp = 1000L)
        val tx2 = Transaction(id = 2, amount = 1500.0, isIncome = false, category = "Shopping", account = "SBI Bank", timestamp = 2000L)

        val beforeDelete = listOf(tx1, tx2)
        assertEquals(6500.0, AccountUtils.calculateAccountBalance(account, beforeDelete), 0.001)

        // Deleting tx2 restores the ₹1500 back to the account
        val afterDelete = beforeDelete.filter { it.id != 2 }
        assertEquals(8000.0, AccountUtils.calculateAccountBalance(account, afterDelete), 0.001)

        // Restoring tx2 (Undo action) puts it back to ₹6500
        val afterUndo = afterDelete + tx2
        assertEquals(6500.0, AccountUtils.calculateAccountBalance(account, afterUndo), 0.001)
    }

    @Test
    fun testDuplicateTransaction() {
        val account = Account(name = "Cash in Hand", balance = 5000.0, type = "CASH")
        val original = Transaction(
            id = 1,
            amount = 150.0,
            isIncome = false,
            category = "Chai/Snacks",
            subcategory = "Tea & Samosa",
            account = "Cash in Hand",
            paymentMethod = "Cash",
            tags = "Snacks, Office",
            timestamp = 1000L
        )

        val listWithOriginal = listOf(original)
        assertEquals(4850.0, AccountUtils.calculateAccountBalance(account, listWithOriginal), 0.001)

        // Duplicating creates a new copy with id = 2
        val duplicate = original.copy(id = 2, timestamp = 2000L)
        val listWithDuplicate = listWithOriginal + duplicate
        assertEquals(4700.0, AccountUtils.calculateAccountBalance(account, listWithDuplicate), 0.001)
    }

    @Test
    fun testIndianDefaultCategoriesPresence() {
        val defaults = CategoryUtils.defaultCategories
        val names = defaults.map { it.name }

        assertTrue(names.contains("Food"))
        assertTrue(names.contains("Chai/Snacks"))
        assertTrue(names.contains("Travel/Petrol"))
        assertTrue(names.contains("Rent"))
        assertTrue(names.contains("Bills"))
        assertTrue(names.contains("Recharge"))
        assertTrue(names.contains("Shopping"))
        assertTrue(names.contains("Medical"))
        assertTrue(names.contains("Education"))
        assertTrue(names.contains("Salary"))
        assertTrue(names.contains("Business"))
        assertTrue(names.contains("Gift"))
        assertTrue(names.contains("Other"))

        // Check icon and color resolution
        val foodCat = defaults.first { it.name == "Food" }
        assertNotNull(CategoryUtils.getIcon(foodCat.iconName))
        assertNotNull(CategoryUtils.parseColor(foodCat.colorHex))
    }
}

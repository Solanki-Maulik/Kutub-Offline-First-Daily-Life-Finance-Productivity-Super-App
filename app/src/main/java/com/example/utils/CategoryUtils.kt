package com.example.utils

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.data.Category

object CategoryUtils {
    val defaultCategories = listOf(
        // Expense categories for India
        Category(name = "Food", isIncome = false, colorHex = "#FF5722", iconName = "Restaurant", isDefault = true),
        Category(name = "Chai/Snacks", isIncome = false, colorHex = "#795548", iconName = "Coffee", isDefault = true),
        Category(name = "Travel/Petrol", isIncome = false, colorHex = "#2196F3", iconName = "DirectionsCar", isDefault = true),
        Category(name = "Rent", isIncome = false, colorHex = "#E91E63", iconName = "Home", isDefault = true),
        Category(name = "Bills", isIncome = false, colorHex = "#9C27B0", iconName = "Receipt", isDefault = true),
        Category(name = "Recharge", isIncome = false, colorHex = "#00BCD4", iconName = "PhoneAndroid", isDefault = true),
        Category(name = "Shopping", isIncome = false, colorHex = "#E65100", iconName = "ShoppingBag", isDefault = true),
        Category(name = "Medical", isIncome = false, colorHex = "#F44336", iconName = "LocalHospital", isDefault = true),
        Category(name = "Education", isIncome = false, colorHex = "#3F51B5", iconName = "School", isDefault = true),
        Category(name = "Other", isIncome = false, colorHex = "#607D8B", iconName = "MoreHoriz", isDefault = true),

        // Income categories for India
        Category(name = "Salary", isIncome = true, colorHex = "#4CAF50", iconName = "Payments", isDefault = true),
        Category(name = "Business", isIncome = true, colorHex = "#009688", iconName = "Store", isDefault = true),
        Category(name = "Gift", isIncome = true, colorHex = "#FF9800", iconName = "CardGiftcard", isDefault = true),
        Category(name = "Other", isIncome = true, colorHex = "#607D8B", iconName = "MoreHoriz", isDefault = true)
    )

    fun getIcon(iconName: String): ImageVector {
        return when (iconName) {
            "Restaurant" -> Icons.Default.Restaurant
            "Coffee" -> Icons.Default.Coffee
            "DirectionsCar" -> Icons.Default.DirectionsCar
            "Home" -> Icons.Default.Home
            "Receipt" -> Icons.Default.Receipt
            "PhoneAndroid" -> Icons.Default.PhoneAndroid
            "ShoppingBag" -> Icons.Default.ShoppingBag
            "LocalHospital" -> Icons.Default.LocalHospital
            "School" -> Icons.Default.School
            "Payments" -> Icons.Default.Payments
            "Store" -> Icons.Default.Store
            "CardGiftcard" -> Icons.Default.CardGiftcard
            "Fastfood" -> Icons.Default.Fastfood
            "Flight" -> Icons.Default.Flight
            "Movie" -> Icons.Default.Movie
            "Pets" -> Icons.Default.Pets
            "Work" -> Icons.Default.Work
            "Savings" -> Icons.Default.Savings
            "AccountBalance" -> Icons.Default.AccountBalance
            "FitnessCenter" -> Icons.Default.FitnessCenter
            "Fitness" -> Icons.Default.FitnessCenter
            "AutoAwesome" -> Icons.Default.AutoAwesome
            else -> Icons.Default.Category
        }
    }

    fun parseColor(colorHex: String): Color {
        return try {
            Color(android.graphics.Color.parseColor(colorHex))
        } catch (e: Exception) {
            Color(0xFF2E7D32)
        }
    }

    val availableIcons = listOf(
        "Restaurant", "Coffee", "DirectionsCar", "Home", "Receipt", "PhoneAndroid",
        "ShoppingBag", "LocalHospital", "School", "Payments", "Store", "CardGiftcard",
        "Fastfood", "Flight", "Movie", "Pets", "Work", "Savings", "FitnessCenter"
    )

    val availableColors = listOf(
        "#FF5722", "#795548", "#2196F3", "#E91E63", "#9C27B0", "#00BCD4",
        "#E65100", "#F44336", "#3F51B5", "#4CAF50", "#009688", "#FF9800",
        "#607D8B", "#673AB7", "#00ACC1", "#D81B60", "#43A047", "#FB8C00"
    )

    val paymentMethods = listOf(
        "Cash",
        "UPI",
        "Net Banking",
        "Debit Card",
        "Credit Card",
        "Cheque",
        "Other"
    )
}

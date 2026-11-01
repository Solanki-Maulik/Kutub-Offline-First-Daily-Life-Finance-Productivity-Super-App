package com.example.ui.screens

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.example.viewmodel.FinanceViewModel

/**
 * Backward-compatible entry point for Debts & Loans screen,
 * powered by the comprehensive People & Udhaar (Khata) ledger system.
 */
@Composable
fun DebtsScreen(viewModel: FinanceViewModel, navController: NavController) {
    UdhaarOverviewScreen(viewModel = viewModel, navController = navController)
}

package com.example.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.screens.AddExpenseScreen
import com.example.ui.screens.BudgetsScreen
import com.example.ui.screens.GoalsScreen
import com.example.ui.screens.DebtsScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SettingsScreen

import com.example.viewmodel.FinanceViewModel

import com.example.ui.screens.AccountsScreen
import com.example.ui.screens.NotesScreen

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object History : Screen("history", "History", Icons.Default.ReceiptLong)
    object Add : Screen("add", "Add", Icons.Default.Add)
    object Budgets : Screen("budgets", "Budgets", Icons.Default.PieChart)
    object Settings : Screen("settings", "More", Icons.Default.Menu)
    object Analytics : Screen("analytics", "Analytics", Icons.Default.PieChart)

}

val bottomNavItems = listOf(
    Screen.Home,
    Screen.History,
    Screen.Add,
    Screen.Budgets,
    Screen.Settings
)

@Composable
fun MainScreen(viewModel: FinanceViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomNav = currentRoute in bottomNavItems.map { it.route } || currentRoute?.startsWith("add") == true

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomNav) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 0.dp,
                    modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    bottomNavItems.forEach { screen ->
                        if (screen.route == Screen.Add.route) {
                            NavigationBarItem(
                                selected = false,
                                onClick = { navController.navigate(screen.route) },
                                icon = { Icon(screen.icon, contentDescription = screen.title) },
                                label = { Text(screen.title) },
                                colors = NavigationBarItemDefaults.colors(
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        } else {
                            NavigationBarItem(
                                selected = currentRoute == screen.route,
                                onClick = {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.startDestinationId) { saveState = false }
                                        launchSingleTop = true
                                        restoreState = false
                                    }
                                },
                                icon = { Icon(screen.icon, contentDescription = screen.title) },
                                label = { Text(screen.title) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) { HomeScreen(viewModel, navController) }
            composable(Screen.History.route) { HistoryScreen(viewModel, navController) }
            composable(
                route = "${Screen.Add.route}?id={id}",
                arguments = listOf(navArgument("id") { type = NavType.IntType; defaultValue = -1 })
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getInt("id") ?: -1
                AddExpenseScreen(viewModel = viewModel, transactionId = id, onBack = { navController.popBackStack() })
            }
            composable(Screen.Add.route) { AddExpenseScreen(viewModel = viewModel, transactionId = -1, onBack = { navController.popBackStack() }) }
            composable(Screen.Budgets.route) { BudgetsScreen(viewModel, navController) }
            composable(Screen.Analytics.route) {
                com.example.ui.screens.AnalyticsScreen(viewModel = viewModel, navController = navController)
            }

            composable(Screen.Settings.route) { SettingsScreen(navController, viewModel) }
            composable("profile") { ProfileScreen(onBack = { navController.popBackStack() }) }
            composable("goals") { GoalsScreen(viewModel, navController) }
            composable("debts") { DebtsScreen(viewModel, navController) }
            composable("accounts") { AccountsScreen(viewModel, navController) }
            composable("notes") { NotesScreen(viewModel, navController) }
        }
    }
}

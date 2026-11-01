package com.example.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.screens.*
import com.example.viewmodel.FinanceViewModel

sealed class Screen(val route: String, val title: String, val icon: ImageVector, val moduleKey: String? = null) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object Money : Screen("money", "Money", Icons.Default.AccountBalanceWallet)
    object AiAssistant : Screen("ai_assistant", "AI", Icons.Default.AutoAwesome, moduleKey = "ai_assistant")
    object Tasks : Screen("tasks", "Tasks", Icons.Default.Checklist, moduleKey = "tasks")
    object Notes : Screen("notes", "Notes", Icons.Default.EditNote, moduleKey = "notes")
    object Settings : Screen("settings", "More", Icons.Default.Apps)

    // Secondary routes preserved for backward compatibility & direct links
    object History : Screen("history", "History", Icons.Default.ReceiptLong)
    object Add : Screen("add", "Add", Icons.Default.Add)
    object Budgets : Screen("budgets", "Budgets", Icons.Default.PieChart)
    object Analytics : Screen("analytics", "Reports", Icons.Default.PieChart)
}

val primaryHubTabs = listOf(
    Screen.Home,
    Screen.Money,
    Screen.AiAssistant,
    Screen.Tasks,
    Screen.Notes,
    Screen.Settings
)

@Composable
fun MainScreen(viewModel: FinanceViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val enabledModules by viewModel.enabledModules.collectAsStateWithLifecycle()

    // Filter primary navigation items dynamically based on Module On/Off toggles in Settings
    val visibleNavItems = remember(enabledModules) {
        primaryHubTabs.filter { screen ->
            screen.moduleKey == null || screen.moduleKey in enabledModules
        }
    }

    val topLevelRoutes = remember(visibleNavItems) {
        visibleNavItems.map { it.route }.toSet() + setOf("history", "budgets")
    }
    val showPrimaryNavigation = currentRoute in topLevelRoutes

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 600.dp

        if (isWideScreen) {
            // Adaptive Sidebar (NavigationRail) for tablets / landscape / desktop
            Row(modifier = Modifier.fillMaxSize()) {
                if (showPrimaryNavigation) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        header = {
                            FloatingActionButton(
                                onClick = { navController.navigate(Screen.Add.route) },
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier
                                    .padding(vertical = 8.dp)
                                    .testTag("sidebar_quick_add_fab")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add Transaction")
                            }
                        }
                    ) {
                        Spacer(Modifier.height(8.dp))
                        visibleNavItems.forEach { screen ->
                            val isSelected = currentRoute == screen.route ||
                                (screen == Screen.Money && currentRoute == Screen.History.route)
                            NavigationRailItem(
                                selected = isSelected,
                                onClick = { navigateToHubTab(navController, currentRoute, screen.route) },
                                icon = { Icon(screen.icon, contentDescription = screen.title) },
                                label = {
                                    Text(
                                        screen.title,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                },
                                modifier = Modifier.testTag("nav_rail_${screen.route}")
                            )
                        }
                    }
                }

                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    modifier = Modifier.weight(1f)
                ) { innerPadding ->
                    EcosystemNavHost(
                        navController = navController,
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        } else {
            // Standard Bottom Navigation Bar for handheld devices
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    if (showPrimaryNavigation) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            tonalElevation = 2.dp,
                            modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            visibleNavItems.forEach { screen ->
                                val isSelected = currentRoute == screen.route ||
                                    (screen == Screen.Money && currentRoute == Screen.History.route)
                                NavigationBarItem(
                                    selected = isSelected,
                                    onClick = { navigateToHubTab(navController, currentRoute, screen.route) },
                                    icon = {
                                        Icon(
                                            screen.icon,
                                            contentDescription = screen.title,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = screen.title,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    alwaysShowLabel = true,
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.testTag("bottom_nav_${screen.route}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                EcosystemNavHost(
                    navController = navController,
                    viewModel = viewModel,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}

private fun navigateToHubTab(navController: NavHostController, currentRoute: String?, targetRoute: String) {
    if (currentRoute == targetRoute) return
    navController.navigate(targetRoute) {
        popUpTo(navController.graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun EcosystemNavHost(
    navController: NavHostController,
    viewModel: FinanceViewModel,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier,
        enterTransition = { fadeIn(animationSpec = tween(150)) },
        exitTransition = { fadeOut(animationSpec = tween(120)) },
        popEnterTransition = { fadeIn(animationSpec = tween(150)) },
        popExitTransition = { fadeOut(animationSpec = tween(120)) }
    ) {
        // Primary Ecosystem Hub Tabs
        composable(Screen.Home.route) { HomeScreen(viewModel, navController) }
        composable(Screen.Money.route) { MoneyHubScreen(viewModel, navController) }
        composable(
            route = "${Screen.AiAssistant.route}?tab={tab}",
            arguments = listOf(navArgument("tab") { type = NavType.IntType; defaultValue = 0 })
        ) { backStackEntry ->
            val tab = backStackEntry.arguments?.getInt("tab") ?: 0
            AiAssistantScreen(viewModel = viewModel, navController = navController, initialTab = tab)
        }
        composable(Screen.AiAssistant.route) {
            AiAssistantScreen(viewModel = viewModel, navController = navController, initialTab = 0)
        }
        composable(Screen.Tasks.route) { TasksScheduleScreen(viewModel, navController, initialTab = 0) }
        composable(Screen.Notes.route) { NotesScreen(viewModel, navController) }
        composable(Screen.Settings.route) { SettingsScreen(navController, viewModel) }

        // Unified Today Hub
        composable("today_hub") { UnifiedTodayScreen(viewModel, navController) }

        // Money & Secondary Routes
        composable(Screen.History.route) { HistoryScreen(viewModel, navController) }
        composable(
            route = "${Screen.Add.route}?id={id}",
            arguments = listOf(navArgument("id") { type = NavType.IntType; defaultValue = -1 })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getInt("id") ?: -1
            AddExpenseScreen(viewModel = viewModel, transactionId = id, onBack = { navController.popBackStack() })
        }
        composable(Screen.Add.route) {
            AddExpenseScreen(viewModel = viewModel, transactionId = -1, onBack = { navController.popBackStack() })
        }
        composable(Screen.Budgets.route) { BudgetsScreen(viewModel, navController, initialTab = 0) }
        composable(Screen.Analytics.route) {
            AnalyticsScreen(viewModel = viewModel, navController = navController)
        }
        composable("profile") { ProfileScreen(onBack = { navController.popBackStack() }) }
        composable("goals") { BudgetsScreen(viewModel, navController, initialTab = 1) }
        composable("recurring") { BudgetsScreen(viewModel, navController, initialTab = 2) }
        composable("bills") { BudgetsScreen(viewModel, navController, initialTab = 3) }
        composable("debts") { DebtsScreen(viewModel, navController) }
        composable("udhaar") { UdhaarOverviewScreen(viewModel, navController) }
        composable(
            route = "person/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = 0L })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getLong("id") ?: 0L
            PersonDetailScreen(personId = id, viewModel = viewModel, navController = navController)
        }
        composable("accounts") { AccountsScreen(viewModel, navController) }
        composable("schedule") { TasksScheduleScreen(viewModel, navController, initialTab = 1) }
        composable("calendar") { TasksScheduleScreen(viewModel, navController, initialTab = 2) }
        composable("split_bills") { SplitBillsScreen(viewModel, navController) }
        composable("habits") { HabitsScreen(viewModel, navController) }
        composable("investments") { InvestmentsScreen(viewModel, navController) }
        composable("document_vault") { DocumentVaultScreen(viewModel, navController) }
    }
}

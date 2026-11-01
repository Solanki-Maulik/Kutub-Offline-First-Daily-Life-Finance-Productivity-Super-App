package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.viewmodel.FinanceViewModel

enum class PlanningTab(val title: String, val icon: ImageVector) {
    BUDGETS("Budgets", Icons.Default.PieChart),
    GOALS("Savings Goals", Icons.Default.Savings),
    RECURRING("Recurring", Icons.Default.Repeat),
    BILLS("Bills", Icons.Default.Receipt)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(
    viewModel: FinanceViewModel,
    navController: NavController,
    initialTab: Int = 0
) {
    var selectedTabIndex by remember(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 3)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (selectedTabIndex) {
                            0 -> "Budgets & Limits"
                            1 -> "Savings Goals"
                            2 -> "Recurring Payments"
                            3 -> "Bill Reminders"
                            else -> "Planning Hub"
                        },
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.testTag("budgets_back_button")
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                edgePadding = 16.dp,
                modifier = Modifier.fillMaxWidth().testTag("planning_tabs_row")
            ) {
                PlanningTab.values().forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(tab.title, fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal) },
                        icon = { Icon(tab.icon, contentDescription = tab.title, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.testTag("planning_tab_${tab.name.lowercase()}")
                    )
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (selectedTabIndex) {
                    0 -> BudgetsTabContent(viewModel = viewModel, onNavigateToAddExpense = { navController.navigate("add") })
                    1 -> GoalsTabContent(viewModel = viewModel)
                    2 -> RecurringTabContent(viewModel = viewModel)
                    3 -> BillsTabContent(viewModel = viewModel)
                }
            }
        }
    }
}

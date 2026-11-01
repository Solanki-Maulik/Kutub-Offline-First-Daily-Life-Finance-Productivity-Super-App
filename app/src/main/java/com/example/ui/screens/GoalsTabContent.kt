package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.example.data.Goal
import com.example.data.GoalContribution
import com.example.utils.FormatUtils
import com.example.viewmodel.FinanceViewModel
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GoalsTabContent(
    viewModel: FinanceViewModel
) {
    val goals by viewModel.goals.collectAsStateWithLifecycle()
    val allContributions by viewModel.allGoalContributions.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()

    var showAddGoalDialog by remember { mutableStateOf(false) }
    var newGoalName by remember { mutableStateOf("") }
    var newGoalTarget by remember { mutableStateOf("") }
    var newGoalCategory by remember { mutableStateOf("Savings") }
    var newGoalNote by remember { mutableStateOf("") }
    var newGoalDeadlineDays by remember { mutableStateOf(180) } // default 6 months
    var newGoalCustomDeadline by remember { mutableStateOf("") }

    // Contribution dialog
    var targetGoalForContribution by remember { mutableStateOf<Goal?>(null) }
    var contributionAmountInput by remember { mutableStateOf("") }
    var contributionNoteInput by remember { mutableStateOf("") }
    var contributionAccount by remember { mutableStateOf("Bank Account") }

    // History dialog
    var selectedGoalForHistory by remember { mutableStateOf<Goal?>(null) }

    // Edit goal dialog
    var goalToEdit by remember { mutableStateOf<Goal?>(null) }
    var editGoalName by remember { mutableStateOf("") }
    var editGoalTarget by remember { mutableStateOf("") }

    val totalTarget = remember(goals) { goals.sumOf { it.targetAmount } }
    val totalSaved = remember(goals) { goals.sumOf { it.savedAmount } }
    val overallSavingsProgress = if (totalTarget > 0) (totalSaved / totalTarget).toFloat() else 0f

    // Add Goal Dialog
    if (showAddGoalDialog) {
        AlertDialog(
            onDismissRequest = { showAddGoalDialog = false },
            title = { Text("New Savings Goal") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newGoalName,
                        onValueChange = { newGoalName = it },
                        label = { Text("Goal Name (e.g. Emergency Fund)") },
                        modifier = Modifier.fillMaxWidth().testTag("goal_name_input"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newGoalTarget,
                        onValueChange = { newGoalTarget = it },
                        label = { Text("Target Amount (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("goal_target_input"),
                        singleLine = true
                    )

                    Text("Deadline Preset", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(30 to "1 Mo", 90 to "3 Mo", 180 to "6 Mo", 365 to "1 Yr").forEach { (days, label) ->
                            FilterChip(
                                selected = newGoalDeadlineDays == days && newGoalCustomDeadline.isBlank(),
                                onClick = {
                                    newGoalDeadlineDays = days
                                    newGoalCustomDeadline = ""
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag("deadline_preset_$label")
                            )
                        }
                    }

                    OutlinedTextField(
                        value = newGoalCustomDeadline,
                        onValueChange = { newGoalCustomDeadline = it },
                        label = { Text("Or Custom Deadline (DD/MM/YYYY)") },
                        placeholder = { Text("31/12/2026") },
                        modifier = Modifier.fillMaxWidth().testTag("goal_deadline_input"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = newGoalNote,
                        onValueChange = { newGoalNote = it },
                        label = { Text("Note / Purpose (Optional)") },
                        modifier = Modifier.fillMaxWidth().testTag("goal_note_input"),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = newGoalTarget.toDoubleOrNull()
                        if (newGoalName.isNotBlank() && target != null && target > 0) {
                            val deadlineMillis = if (newGoalCustomDeadline.isNotBlank()) {
                                FormatUtils.parseDate(newGoalCustomDeadline) ?: (System.currentTimeMillis() + newGoalDeadlineDays * 86400000L)
                            } else {
                                System.currentTimeMillis() + (newGoalDeadlineDays * 86400000L)
                            }
                            viewModel.addGoal(
                                name = newGoalName.trim(),
                                targetAmount = target,
                                targetDateMillis = deadlineMillis,
                                category = newGoalCategory,
                                note = newGoalNote.trim()
                            )
                            showAddGoalDialog = false
                            newGoalName = ""
                            newGoalTarget = ""
                            newGoalNote = ""
                            newGoalCustomDeadline = ""
                        }
                    },
                    modifier = Modifier.testTag("save_goal_button")
                ) {
                    Text("Create Goal")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddGoalDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Add Contribution Dialog
    if (targetGoalForContribution != null) {
        val goal = targetGoalForContribution!!
        val accNames = accounts.map { it.name }.ifEmpty { listOf("Bank Account", "Cash in Hand", "UPI Wallet") }
        if (contributionAccount !in accNames) {
            contributionAccount = accNames.first()
        }

        AlertDialog(
            onDismissRequest = { targetGoalForContribution = null },
            title = { Text("Add Money to ${goal.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Current progress: ${FormatUtils.formatInr(goal.savedAmount)} / ${FormatUtils.formatInr(goal.targetAmount)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = contributionAmountInput,
                        onValueChange = { contributionAmountInput = it },
                        label = { Text("Amount to Add (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("contribution_amount_input"),
                        singleLine = true
                    )

                    Text("Funded From Account", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        accNames.forEach { acc ->
                            FilterChip(
                                selected = contributionAccount == acc,
                                onClick = { contributionAccount = acc },
                                label = { Text(acc) },
                                modifier = Modifier.testTag("contrib_account_chip_$acc")
                            )
                        }
                    }

                    OutlinedTextField(
                        value = contributionNoteInput,
                        onValueChange = { contributionNoteInput = it },
                        label = { Text("Note (e.g. Monthly Savings, Bonus)") },
                        modifier = Modifier.fillMaxWidth().testTag("contribution_note_input"),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = contributionAmountInput.toDoubleOrNull()
                        if (amount != null && amount > 0) {
                            viewModel.addSavedAmountToGoal(
                                goal = goal,
                                amountToAdd = amount,
                                note = contributionNoteInput.trim(),
                                account = contributionAccount
                            )
                            targetGoalForContribution = null
                            contributionAmountInput = ""
                            contributionNoteInput = ""
                        }
                    },
                    modifier = Modifier.testTag("confirm_contribution_button")
                ) {
                    Text("Add Money")
                }
            },
            dismissButton = {
                TextButton(onClick = { targetGoalForContribution = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Contribution History Dialog
    if (selectedGoalForHistory != null) {
        val goal = selectedGoalForHistory!!
        val goalContribs = allContributions.filter { it.goalId == goal.id }.sortedByDescending { it.date }

        AlertDialog(
            onDismissRequest = { selectedGoalForHistory = null },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${goal.name} History", fontWeight = FontWeight.Bold)
                    IconButton(onClick = { selectedGoalForHistory = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    Text(
                        "Total Saved: ${FormatUtils.formatInr(goal.savedAmount)} of ${FormatUtils.formatInr(goal.targetAmount)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))

                    if (goalContribs.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No contributions recorded yet.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(goalContribs, key = { it.id }) { c ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().testTag("contribution_item_${c.id}"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                "+${FormatUtils.formatInr(c.amount)}",
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF2E7D32),
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            Text(
                                                FormatUtils.formatDate(c.date) + if (!c.account.isNullOrBlank()) " via ${c.account}" else "",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (c.note.isNotBlank()) {
                                                Text(
                                                    c.note,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                        IconButton(
                                            onClick = {
                                                viewModel.deleteGoalContribution(c)
                                            },
                                            modifier = Modifier.testTag("delete_contribution_${c.id}")
                                        ) {
                                            Icon(
                                                Icons.Default.DeleteOutline,
                                                contentDescription = "Delete Deposit",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        targetGoalForContribution = goal
                        selectedGoalForHistory = null
                    },
                    modifier = Modifier.testTag("add_money_from_history_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Money")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedGoalForHistory = null }) { Text("Close") }
            }
        )
    }

    // Edit Goal Dialog
    if (goalToEdit != null) {
        val goal = goalToEdit!!
        AlertDialog(
            onDismissRequest = { goalToEdit = null },
            title = { Text("Edit Goal") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editGoalName,
                        onValueChange = { editGoalName = it },
                        label = { Text("Goal Name") },
                        modifier = Modifier.fillMaxWidth().testTag("edit_goal_name_input"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editGoalTarget,
                        onValueChange = { editGoalTarget = it },
                        label = { Text("Target Amount (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("edit_goal_target_input"),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = editGoalTarget.toDoubleOrNull()
                        if (editGoalName.isNotBlank() && target != null && target > 0) {
                            viewModel.updateGoal(
                                goal.copy(
                                    name = editGoalName.trim(),
                                    targetAmount = target
                                )
                            )
                            goalToEdit = null
                        }
                    },
                    modifier = Modifier.testTag("save_edit_goal_button")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            viewModel.deleteGoal(goal.id)
                            goalToEdit = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.testTag("delete_goal_button")
                    ) {
                        Text("Delete")
                    }
                    TextButton(onClick = { goalToEdit = null }) { Text("Cancel") }
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp)
    ) {
        // Overall Savings Summary Hero Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("overall_goals_summary_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Total Savings Goals",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${goals.size} Active Goals",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilledTonalButton(
                            onClick = { showAddGoalDialog = true },
                            modifier = Modifier.testTag("add_goal_header_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("New Goal")
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text(
                                "Total Saved",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                FormatUtils.formatInr(totalSaved),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "Target",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                FormatUtils.formatInr(totalTarget),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    LinearProgressIndicator(
                        progress = { overallSavingsProgress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.tertiary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${(overallSavingsProgress * 100).toInt()}% Total Progress • ${FormatUtils.formatInr(maxOf(0.0, totalTarget - totalSaved))} Remaining",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Goals List
        if (goals.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Savings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "No Savings Goals Yet",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Plan for a new gadget, car downpayment, or emergency fund.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { showAddGoalDialog = true },
                            modifier = Modifier.testTag("create_first_goal_button")
                        ) {
                            Text("Create First Goal")
                        }
                    }
                }
            }
        } else {
            items(goals, key = { it.id }) { goal ->
                val progress = if (goal.targetAmount > 0) (goal.savedAmount / goal.targetAmount).toFloat() else 0f
                val isCompleted = progress >= 1.0f
                val contribCount = allContributions.count { it.goalId == goal.id }

                val deadlineText = remember(goal.targetDateMillis) {
                    if (goal.targetDateMillis > 0) {
                        val dueCal = Calendar.getInstance().apply { timeInMillis = goal.targetDateMillis }
                        val nowCal = Calendar.getInstance()
                        val diffDays = ((dueCal.timeInMillis - nowCal.timeInMillis) / 86400000L).toInt()
                        when {
                            diffDays < 0 -> "Target expired on ${FormatUtils.formatDate(goal.targetDateMillis)}"
                            diffDays == 0 -> "Target due today!"
                            diffDays == 1 -> "1 day left (Due tomorrow)"
                            else -> "Target: ${FormatUtils.formatDate(goal.targetDateMillis)} ($diffDays days left)"
                        }
                    } else "No deadline"
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("goal_card_${goal.id}"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, if (isCompleted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(if (isCompleted) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.tertiaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        if (isCompleted) Icons.Default.CheckCircle else Icons.Default.Savings,
                                        contentDescription = null,
                                        tint = if (isCompleted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        goal.name,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        deadlineText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (goal.targetDateMillis > 0 && goal.targetDateMillis < System.currentTimeMillis() && !isCompleted) {
                                            MaterialTheme.colorScheme.error
                                        } else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    goalToEdit = goal
                                    editGoalName = goal.name
                                    editGoalTarget = goal.targetAmount.toInt().toString()
                                },
                                modifier = Modifier.testTag("edit_goal_${goal.id}")
                            ) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Goal Options")
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Text(
                                "${FormatUtils.formatInr(goal.savedAmount)} / ${FormatUtils.formatInr(goal.targetAmount)}",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isCompleted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "${(progress * 100).toInt()}% Achieved",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isCompleted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.tertiary
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape),
                            color = if (isCompleted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.tertiary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )

                        Spacer(Modifier.height(14.dp))

                        // Action Buttons: "+ Add Money" and "History (N deposits)"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { selectedGoalForHistory = goal },
                                modifier = Modifier.testTag("view_history_goal_${goal.id}")
                            ) {
                                Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (contribCount > 0) "$contribCount Deposits" else "Log History")
                            }

                            Button(
                                onClick = {
                                    targetGoalForContribution = goal
                                    contributionAmountInput = ""
                                    contributionNoteInput = ""
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("add_money_goal_${goal.id}")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add Money")
                            }
                        }
                    }
                }
            }
        }
    }
}

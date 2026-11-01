package com.example.ui.components

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ScheduleEvent
import com.example.data.TaskItem
import com.example.utils.FormatUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DailyTasksScheduleCard(
    onAddTaskClick: () -> Unit,
    onViewAllClick: () -> Unit = {},
    onAddEventClick: (() -> Unit)? = null,
    liveTasks: List<TaskItem>? = null,
    liveEvents: List<ScheduleEvent>? = null,
    currentStreak: Int = 0,
    onToggleLiveTask: ((TaskItem) -> Unit)? = null,
    onQuickAddStarterTask: ((title: String, repeatFrequency: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("daily_tasks_prefs", Context.MODE_PRIVATE) }

    // Fallback static tasks only used when liveTasks is null
    val fallbackTasks = remember {
        listOf(
            Pair("1", "Log daily chai, snacks & travel"),
            Pair("2", "Check bank balance & match UPI spends"),
            Pair("3", "Follow up on pending Udhaar / Khata")
        )
    }

    var fallbackDoneMap by remember {
        mutableStateOf(
            mapOf(
                "1" to prefs.getBoolean("task_done_1", false),
                "2" to prefs.getBoolean("task_done_2", false),
                "3" to prefs.getBoolean("task_done_3", false)
            )
        )
    }

    // Prioritize today/active tasks first so newly added tasks are visible immediately
    val sortedLiveTasks = remember(liveTasks) {
        liveTasks?.sortedWith(
            compareBy<TaskItem> { it.status == "DONE" }
                .thenBy { it.dueDateTime ?: Long.MAX_VALUE }
        )
    }
    val displayTasks = sortedLiveTasks?.take(5)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("daily_tasks_schedule_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onViewAllClick() }
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Checklist,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Today's Tasks & Schedule",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val total = if (liveTasks != null) liveTasks.size else fallbackTasks.size
                        val done = if (liveTasks != null) liveTasks.count { it.status == "DONE" } else fallbackDoneMap.values.count { it }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "$done of $total completed",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (currentStreak > 0) Color(0xFFFFF3E0) else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.testTag("home_task_day_streak_badge")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        Icons.Default.LocalFireDepartment,
                                        contentDescription = null,
                                        tint = if (currentStreak > 0) Color(0xFFE65100) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        "$currentStreak Day Streak",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (currentStreak > 0) Color(0xFFE65100) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Uniform Action Buttons Row (fits cleanly on all phone widths)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = onAddTaskClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .testTag("add_task_header_button"),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.AddTask, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Task", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                }

                if (onAddEventClick != null) {
                    OutlinedButton(
                        onClick = onAddEventClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .testTag("add_event_header_button"),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Schedule", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }

                TextButton(
                    onClick = onViewAllClick,
                    modifier = Modifier
                        .height(38.dp)
                        .testTag("view_all_tasks_button"),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text("View All", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                    Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(Modifier.height(12.dp))

            // Tasks List
            if (displayTasks != null && displayTasks.isNotEmpty()) {
                val timeFmt = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    displayTasks.forEach { task ->
                        val isDone = task.status == "DONE"
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isDone) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleLiveTask?.invoke(task) }
                                .testTag("live_task_item_${task.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                        contentDescription = if (isDone) "Completed" else "Mark Complete",
                                        tint = if (isDone) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            task.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None,
                                            color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (task.dueDateTime != null) {
                                                Text(
                                                    "🕒 ${timeFmt.format(Date(task.dueDateTime))}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (task.repeatFrequency != "NONE") {
                                                Text(
                                                    "🔁 ${task.repeatFrequency.lowercase().replaceFirstChar { it.uppercase() }}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                            if (task.isLinkedToMoney && task.linkedAmount != null) {
                                                Text(
                                                    "💰 ${FormatUtils.formatInr(task.linkedAmount)}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color(0xFFE65100),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                ) {
                                    Text(
                                        task.priority,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (liveTasks != null) {
                // Real empty state with 1-click starter tasks so adding & completing works immediately
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "No tasks yet for today. Tap a quick preset below or 'Add Task' to start your Day Streak:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (onQuickAddStarterTask != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AssistChip(
                                    onClick = { onQuickAddStarterTask("Log daily expenses", "DAILY") },
                                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    label = { Text("Log daily expenses (Daily)") },
                                    modifier = Modifier.testTag("starter_task_daily_expenses")
                                )
                                AssistChip(
                                    onClick = { onQuickAddStarterTask("Check bank & UPI balance", "DAILY") },
                                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    label = { Text("Check bank & UPI (Daily)") },
                                    modifier = Modifier.testTag("starter_task_daily_bank")
                                )
                                AssistChip(
                                    onClick = { onQuickAddStarterTask("Review weekly budget", "WEEKLY") },
                                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    label = { Text("Weekly budget review (Weekly)") },
                                    modifier = Modifier.testTag("starter_task_weekly_budget")
                                )
                            }
                        }
                    }
                }
            } else {
                // Legacy fallback only when liveTasks is null
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    fallbackTasks.forEach { (id, title) ->
                        val isDone = fallbackDoneMap[id] ?: false
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isDone) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val newDone = !isDone
                                    prefs.edit().putBoolean("task_done_$id", newDone).apply()
                                    fallbackDoneMap = fallbackDoneMap + (id to newDone)
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = if (isDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (isDone) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None,
                                    color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // Events Section
            val eventsToShow = liveEvents?.sortedBy { it.startTime }?.take(3)
            if (eventsToShow != null && eventsToShow.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("Scheduled Events", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val timeFormat = SimpleDateFormat("dd MMM • hh:mm a", Locale.getDefault())
                    eventsToShow.forEach { evt ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                            Text(evt.title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(timeFormat.format(Date(evt.startTime)), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

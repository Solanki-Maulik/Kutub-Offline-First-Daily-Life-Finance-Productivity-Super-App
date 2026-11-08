package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.ScheduleEvent
import com.example.data.TaskItem
import com.example.utils.FormatUtils
import com.example.utils.TaskUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun TaskCalendarView(
    tasks: List<TaskItem>,
    events: List<ScheduleEvent>,
    onTaskStatusToggle: (TaskItem, String) -> Unit,
    onSubtaskToggle: (TaskItem, String) -> Unit,
    onDeleteTask: (TaskItem) -> Unit,
    onDeleteEvent: (ScheduleEvent) -> Unit,
    onAddTaskForDate: (Long) -> Unit,
    onAddEventForDate: (Long) -> Unit,
    onEditTask: (TaskItem) -> Unit = {},
    onEditEvent: (ScheduleEvent) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var calendarMonth by remember {
        mutableStateOf(Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) })
    }
    var selectedDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val monthYearFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    val dayFormat = remember { SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault()) }

    // Calculate calendar days
    val (daysInMonth, firstDayOfWeek, monthCal) = remember(calendarMonth) {
        val cal = calendarMonth.clone() as Calendar
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val firstDay = cal.get(Calendar.DAY_OF_WEEK) - 1 // 0-indexed for Sunday
        Triple(maxDays, firstDay, cal)
    }

    // Filter tasks and events for the selected date
    val selectedDayTasks = remember(tasks, selectedDateMillis) {
        tasks.filter { it.dueDateTime != null && TaskUtils.isSameDay(it.dueDateTime, selectedDateMillis) }
    }
    val selectedDayEvents = remember(events, selectedDateMillis) {
        events.filter { TaskUtils.isSameDay(it.startTime, selectedDateMillis) }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)
    ) {
        // 1. Month Header & Navigation
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("calendar_month_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                val cal = calendarMonth.clone() as Calendar
                                cal.add(Calendar.MONTH, -1)
                                calendarMonth = cal
                            },
                            modifier = Modifier.testTag("calendar_prev_month")
                        ) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Month")
                        }

                        Text(
                            text = monthYearFormat.format(calendarMonth.time),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )

                        IconButton(
                            onClick = {
                                val cal = calendarMonth.clone() as Calendar
                                cal.add(Calendar.MONTH, 1)
                                calendarMonth = cal
                            },
                            modifier = Modifier.testTag("calendar_next_month")
                        ) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Next Month")
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Days of week header
                    Row(modifier = Modifier.fillMaxWidth()) {
                        listOf("S", "M", "T", "W", "T", "F", "S").forEach { d ->
                            Text(
                                text = d,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Month days grid (up to 6 rows)
                    val totalCells = ((firstDayOfWeek + daysInMonth + 6) / 7) * 7
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (row in 0 until (totalCells / 7)) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                for (col in 0..6) {
                                    val cellIndex = row * 7 + col
                                    val dayNum = cellIndex - firstDayOfWeek + 1

                                    if (dayNum in 1..daysInMonth) {
                                        val dayCal = (calendarMonth.clone() as Calendar).apply {
                                            set(Calendar.DAY_OF_MONTH, dayNum)
                                        }
                                        val cellMillis = dayCal.timeInMillis
                                        val isSelected = TaskUtils.isSameDay(cellMillis, selectedDateMillis)
                                        val isToday = TaskUtils.isToday(cellMillis)

                                        val hasTasks = tasks.any { it.dueDateTime != null && TaskUtils.isSameDay(it.dueDateTime, cellMillis) }
                                        val hasEvents = events.any { TaskUtils.isSameDay(it.startTime, cellMillis) }

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    if (isSelected) MaterialTheme.colorScheme.primary
                                                    else if (isToday) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                                    else Color.Transparent
                                                )
                                                .clickable { selectedDateMillis = cellMillis }
                                                .testTag("calendar_day_$dayNum"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Text(
                                                    text = dayNum.toString(),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                    else if (isToday) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.onSurface
                                                )
                                                // Activity indicator dots
                                                if (hasTasks || hasEvents) {
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                                        modifier = Modifier.padding(top = 2.dp)
                                                    ) {
                                                        if (hasTasks) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(4.dp)
                                                                    .clip(CircleShape)
                                                                    .background(if (isSelected) Color.White else Color(0xFFF57C00))
                                                            )
                                                        }
                                                        if (hasEvents) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(4.dp)
                                                                    .clip(CircleShape)
                                                                    .background(if (isSelected) Color.White else MaterialTheme.colorScheme.primary)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Day Agenda Header
        item {
            val dateTitle = dayFormat.format(selectedDateMillis)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Day Agenda",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = dateTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalButton(
                        onClick = { onAddTaskForDate(selectedDateMillis) },
                        modifier = Modifier.testTag("agenda_add_task_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("Task")
                    }
                    Button(
                        onClick = { onAddEventForDate(selectedDateMillis) },
                        modifier = Modifier.testTag("agenda_add_event_button")
                    ) {
                        Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("Event")
                    }
                }
            }
        }

        // 3. Day Events Section
        if (selectedDayEvents.isNotEmpty()) {
            item {
                Text(
                    "Scheduled Events (${selectedDayEvents.size})",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            items(selectedDayEvents, key = { "evt_${it.id}" }) { event ->
                ScheduleEventCard(
                    event = event,
                    onDelete = { onDeleteEvent(event) },
                    onEdit = { onEditEvent(event) }
                )
            }
        }

        // 4. Day Tasks Section
        if (selectedDayTasks.isNotEmpty()) {
            item {
                Text(
                    "Tasks for this Day (${selectedDayTasks.size})",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE65100)
                )
            }
            items(selectedDayTasks, key = { "task_${it.id}" }) { task ->
                TaskCard(
                    task = task,
                    onStatusToggle = { nextStatus -> onTaskStatusToggle(task, nextStatus) },
                    onSubtaskToggle = { subtaskId -> onSubtaskToggle(task, subtaskId) },
                    onDelete = { onDeleteTask(task) },
                    onEdit = { onEditTask(task) }
                )
            }
        }

        // Empty state for selected date
        if (selectedDayEvents.isEmpty() && selectedDayTasks.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.EventAvailable,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Nothing scheduled for this day",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Use the buttons above to add a task or schedule an event.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

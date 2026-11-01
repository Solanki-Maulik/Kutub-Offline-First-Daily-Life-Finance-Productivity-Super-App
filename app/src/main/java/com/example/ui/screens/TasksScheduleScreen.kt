package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.data.ScheduleEvent
import com.example.data.TaskItem
import com.example.utils.FormatUtils
import com.example.utils.TaskUtils
import com.example.viewmodel.FinanceViewModel
import java.util.Calendar

enum class TaskViewMode(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    TODAY("Today", Icons.Default.Today),
    UPCOMING("Upcoming", Icons.Default.Upcoming),
    CALENDAR("Calendar", Icons.Default.CalendarMonth),
    COMPLETED("Completed", Icons.Default.CheckCircle)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScheduleScreen(
    viewModel: FinanceViewModel,
    navController: NavController,
    initialTab: Int = 0
) {
    val allTasks by viewModel.allTasks.collectAsStateWithLifecycle()
    val allEvents by viewModel.allScheduleEvents.collectAsStateWithLifecycle()
    val taskStats by viewModel.taskStats.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()

    var selectedView by remember(initialTab) {
        mutableStateOf(TaskViewMode.values().getOrElse(initialTab) { TaskViewMode.TODAY })
    }

    var showAddTaskDialog by remember { mutableStateOf(false) }
    var showAddEventDialog by remember { mutableStateOf(false) }

    // Date prefill for calendar
    var prefilledDateMillis by remember { mutableStateOf<Long?>(null) }

    val activeTasks = remember(allTasks) { allTasks.filter { it.status != "DONE" } }
    val completedTasks = remember(allTasks) { allTasks.filter { it.status == "DONE" }.sortedByDescending { it.completedAt ?: 0L } }

    // Categorized Tasks for Today & Upcoming
    val todayTasks = remember(activeTasks) {
        activeTasks.filter { it.dueDateTime != null && TaskUtils.isToday(it.dueDateTime) }
    }
    val overdueTasks = remember(activeTasks) {
        activeTasks.filter { it.dueDateTime != null && TaskUtils.isOverdue(it.dueDateTime) }
    }
    val noDateTasks = remember(activeTasks) {
        activeTasks.filter { it.dueDateTime == null || it.dueDateTime <= 0 }
    }

    val todayEvents = remember(allEvents) {
        allEvents.filter { TaskUtils.isToday(it.startTime) }.sortedBy { it.startTime }
    }

    val upcomingEvents = remember(allEvents) {
        allEvents.filter { it.startTime > System.currentTimeMillis() && !TaskUtils.isToday(it.startTime) }.sortedBy { it.startTime }
    }

    // Add Task Dialog
    if (showAddTaskDialog) {
        AddTaskDialog(
            onDismiss = {
                showAddTaskDialog = false
                prefilledDateMillis = null
            },
            accounts = accounts,
            categories = categories,
            initialDateMillis = prefilledDateMillis,
            onSaveTask = { title, desc, prio, cat, due, repeat, subtasks, isMoney, amt, acc, lCat, reminder ->
                viewModel.addTask(
                    title = title,
                    description = desc,
                    priority = prio,
                    category = cat,
                    dueDateTime = due ?: prefilledDateMillis,
                    repeatFrequency = repeat,
                    subtasks = subtasks,
                    isLinkedToMoney = isMoney,
                    linkedAmount = amt,
                    linkedAccount = acc,
                    linkedCategory = lCat,
                    reminderEnabled = reminder
                )
            }
        )
    }

    // Add Event Dialog
    if (showAddEventDialog) {
        AddScheduleEventDialog(
            onDismiss = {
                showAddEventDialog = false
                prefilledDateMillis = null
            },
            initialDateMillis = prefilledDateMillis,
            onSaveEvent = { title, desc, start, end, loc, cat, rem, off ->
                viewModel.addScheduleEvent(
                    title = title,
                    description = desc,
                    startTime = start,
                    endTime = end,
                    location = loc,
                    category = cat,
                    reminderEnabled = rem,
                    reminderOffsetMinutes = off
                )
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tasks & Schedule", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.testTag("tasks_back_button")
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { navController.navigate("ai_assistant?tab=5") },
                        modifier = Modifier.testTag("topbar_ai_task_helper_button")
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = "AI Task Helper (Goal to Subtasks & Schedule)",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = { showAddEventDialog = true },
                        modifier = Modifier.testTag("topbar_add_event_button")
                    ) {
                        Icon(Icons.Default.Event, contentDescription = "Add Event")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddTaskDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("fab_add_task")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Task")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // View Selector Tabs (Today, Upcoming, Calendar, Completed)
            TabRow(
                selectedTabIndex = selectedView.ordinal,
                modifier = Modifier.fillMaxWidth().testTag("task_view_tabs")
            ) {
                TaskViewMode.values().forEach { mode ->
                    Tab(
                        selected = selectedView == mode,
                        onClick = { selectedView = mode },
                        text = { Text(mode.title, fontWeight = if (selectedView == mode) FontWeight.Bold else FontWeight.Normal) },
                        icon = { Icon(mode.icon, contentDescription = mode.title, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.testTag("tab_${mode.name.lowercase()}")
                    )
                }
            }

            when (selectedView) {
                TaskViewMode.TODAY -> {
                    TodayView(
                        taskStats = taskStats,
                        overdueTasks = overdueTasks,
                        todayTasks = todayTasks,
                        todayEvents = todayEvents,
                        noDateTasks = noDateTasks,
                        onTaskStatusToggle = { task, newStatus -> viewModel.toggleTaskStatus(task, newStatus) },
                        onSubtaskToggle = { task, stId -> viewModel.toggleSubtask(task, stId) },
                        onDeleteTask = { task -> viewModel.deleteTask(task.id) },
                        onDeleteEvent = { event -> viewModel.deleteScheduleEvent(event.id) },
                        onQuickAddTask = { showAddTaskDialog = true }
                    )
                }
                TaskViewMode.UPCOMING -> {
                    UpcomingView(
                        activeTasks = activeTasks.filter { !TaskUtils.isToday(it.dueDateTime) && !TaskUtils.isOverdue(it.dueDateTime) },
                        upcomingEvents = upcomingEvents,
                        onTaskStatusToggle = { task, newStatus -> viewModel.toggleTaskStatus(task, newStatus) },
                        onSubtaskToggle = { task, stId -> viewModel.toggleSubtask(task, stId) },
                        onDeleteTask = { task -> viewModel.deleteTask(task.id) },
                        onDeleteEvent = { event -> viewModel.deleteScheduleEvent(event.id) }
                    )
                }
                TaskViewMode.CALENDAR -> {
                    TaskCalendarView(
                        tasks = allTasks,
                        events = allEvents,
                        onTaskStatusToggle = { task, newStatus -> viewModel.toggleTaskStatus(task, newStatus) },
                        onSubtaskToggle = { task, stId -> viewModel.toggleSubtask(task, stId) },
                        onDeleteTask = { task -> viewModel.deleteTask(task.id) },
                        onDeleteEvent = { event -> viewModel.deleteScheduleEvent(event.id) },
                        onAddTaskForDate = { dateMillis ->
                            prefilledDateMillis = dateMillis
                            showAddTaskDialog = true
                        },
                        onAddEventForDate = { dateMillis ->
                            prefilledDateMillis = dateMillis
                            showAddEventDialog = true
                        }
                    )
                }
                TaskViewMode.COMPLETED -> {
                    CompletedView(
                        completedTasks = completedTasks,
                        taskStats = taskStats,
                        onTaskStatusToggle = { task, newStatus -> viewModel.toggleTaskStatus(task, newStatus) },
                        onDeleteTask = { task -> viewModel.deleteTask(task.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TodayView(
    taskStats: com.example.data.TaskStats,
    overdueTasks: List<TaskItem>,
    todayTasks: List<TaskItem>,
    todayEvents: List<ScheduleEvent>,
    noDateTasks: List<TaskItem>,
    onTaskStatusToggle: (TaskItem, String) -> Unit,
    onSubtaskToggle: (TaskItem, String) -> Unit,
    onDeleteTask: (TaskItem) -> Unit,
    onDeleteEvent: (ScheduleEvent) -> Unit,
    onQuickAddTask: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 96.dp)
    ) {
        // 1. Streak and Daily Completion Stats Hero Banner
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("streak_hero_card"),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                border = BorderStroke(1.5.dp, Color(0xFFFFB74D))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🔥", style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "${taskStats.currentStreak} Day Streak",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE65100)
                            )
                        }
                        Text(
                            "Best streak: ${taskStats.maxStreak} days • ${taskStats.completedTodayCount} tasks completed today",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { (taskStats.completionRatePercent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                            color = Color(0xFFF57C00),
                            trackColor = Color(0xFFFFE0B2)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFFE0B2)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "${taskStats.completionRatePercent}%",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE65100)
                            )
                            Text("Done", style = MaterialTheme.typography.labelSmall, color = Color(0xFFE65100))
                        }
                    }
                }
            }
        }

        // 2. Overdue Tasks Alert (if any)
        if (overdueTasks.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Overdue Tasks (${overdueTasks.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            items(overdueTasks, key = { "overdue_${it.id}" }) { task ->
                TaskCard(
                    task = task,
                    onStatusToggle = { nextStatus -> onTaskStatusToggle(task, nextStatus) },
                    onSubtaskToggle = { stId -> onSubtaskToggle(task, stId) },
                    onDelete = { onDeleteTask(task) }
                )
            }
        }

        // 3. Today's Events (if any)
        if (todayEvents.isNotEmpty()) {
            item {
                Text(
                    "Today's Schedule (${todayEvents.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            items(todayEvents, key = { "evt_${it.id}" }) { event ->
                ScheduleEventCard(
                    event = event,
                    onDelete = { onDeleteEvent(event) }
                )
            }
        }

        // 4. Today's Tasks
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Today's Tasks (${todayTasks.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onQuickAddTask, modifier = Modifier.testTag("quick_add_today_task")) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("Add Task")
                }
            }
        }

        if (todayTasks.isEmpty() && overdueTasks.isEmpty() && todayEvents.isEmpty()) {
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
                            Icons.Default.CheckCircleOutline,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("All Clear for Today!", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "You have no tasks or events pending today. Plan ahead or take a break!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onQuickAddTask, modifier = Modifier.testTag("create_today_task_button")) {
                            Text("Create Task")
                        }
                    }
                }
            }
        } else {
            items(todayTasks, key = { "today_${it.id}" }) { task ->
                TaskCard(
                    task = task,
                    onStatusToggle = { nextStatus -> onTaskStatusToggle(task, nextStatus) },
                    onSubtaskToggle = { stId -> onSubtaskToggle(task, stId) },
                    onDelete = { onDeleteTask(task) }
                )
            }
        }

        // 5. Tasks without Due Date
        if (noDateTasks.isNotEmpty()) {
            item {
                Text(
                    "Unscheduled / Anytime (${noDateTasks.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(noDateTasks, key = { "nodate_${it.id}" }) { task ->
                TaskCard(
                    task = task,
                    onStatusToggle = { nextStatus -> onTaskStatusToggle(task, nextStatus) },
                    onSubtaskToggle = { stId -> onSubtaskToggle(task, stId) },
                    onDelete = { onDeleteTask(task) }
                )
            }
        }
    }
}

@Composable
private fun UpcomingView(
    activeTasks: List<TaskItem>,
    upcomingEvents: List<ScheduleEvent>,
    onTaskStatusToggle: (TaskItem, String) -> Unit,
    onSubtaskToggle: (TaskItem, String) -> Unit,
    onDeleteTask: (TaskItem) -> Unit,
    onDeleteEvent: (ScheduleEvent) -> Unit
) {
    val tomorrowCal = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, 1)
    }
    val tomorrowTasks = remember(activeTasks) {
        activeTasks.filter { it.dueDateTime != null && TaskUtils.isSameDay(it.dueDateTime, tomorrowCal.timeInMillis) }
    }
    val laterTasks = remember(activeTasks) {
        activeTasks.filter { it.dueDateTime != null && !TaskUtils.isSameDay(it.dueDateTime, tomorrowCal.timeInMillis) && it.dueDateTime > tomorrowCal.timeInMillis }
            .sortedBy { it.dueDateTime }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 96.dp)
    ) {
        // Upcoming Events
        if (upcomingEvents.isNotEmpty()) {
            item {
                Text(
                    "Upcoming Schedule Events (${upcomingEvents.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            items(upcomingEvents, key = { "up_evt_${it.id}" }) { event ->
                ScheduleEventCard(event = event, onDelete = { onDeleteEvent(event) })
            }
        }

        // Tomorrow's Tasks
        if (tomorrowTasks.isNotEmpty()) {
            item {
                Text(
                    "Tomorrow (${tomorrowTasks.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            items(tomorrowTasks, key = { "tmr_${it.id}" }) { task ->
                TaskCard(
                    task = task,
                    onStatusToggle = { nextStatus -> onTaskStatusToggle(task, nextStatus) },
                    onSubtaskToggle = { stId -> onSubtaskToggle(task, stId) },
                    onDelete = { onDeleteTask(task) }
                )
            }
        }

        // Later Tasks
        if (laterTasks.isNotEmpty()) {
            item {
                Text(
                    "Later / This Week (${laterTasks.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            items(laterTasks, key = { "later_${it.id}" }) { task ->
                TaskCard(
                    task = task,
                    onStatusToggle = { nextStatus -> onTaskStatusToggle(task, nextStatus) },
                    onSubtaskToggle = { stId -> onSubtaskToggle(task, stId) },
                    onDelete = { onDeleteTask(task) }
                )
            }
        }

        if (upcomingEvents.isEmpty() && tomorrowTasks.isEmpty() && laterTasks.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    Text("No upcoming tasks or events scheduled.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun CompletedView(
    completedTasks: List<TaskItem>,
    taskStats: com.example.data.TaskStats,
    onTaskStatusToggle: (TaskItem, String) -> Unit,
    onDeleteTask: (TaskItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 96.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("completed_summary_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Total Completed", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${taskStats.completedTasks} Tasks", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Current Streak", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("🔥 ${taskStats.currentStreak} Days", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                    }
                }
            }
        }

        if (completedTasks.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    Text("No completed tasks yet. Finish a task to build your streak!", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(completedTasks, key = { "done_${it.id}" }) { task ->
                TaskCard(
                    task = task,
                    onStatusToggle = { nextStatus -> onTaskStatusToggle(task, nextStatus) },
                    onSubtaskToggle = {},
                    onDelete = { onDeleteTask(task) }
                )
            }
        }
    }
}

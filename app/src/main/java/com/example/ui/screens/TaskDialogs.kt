package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.Account
import com.example.data.Category
import com.example.data.ScheduleEvent
import com.example.data.Subtask
import com.example.data.TaskItem
import com.example.ui.NotificationHelper
import com.example.ui.components.AutoSizeSingleLineText
import com.example.utils.TaskUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddTaskDialog(
    onDismiss: () -> Unit,
    accounts: List<Account>,
    categories: List<Category>,
    initialDateMillis: Long? = null,
    existingTask: TaskItem? = null,
    onSaveTask: (
        title: String,
        description: String,
        priority: String,
        category: String,
        dueDateTime: Long?,
        repeatFrequency: String,
        subtasks: List<Subtask>,
        isLinkedToMoney: Boolean,
        linkedAmount: Double?,
        linkedAccount: String?,
        linkedCategory: String?,
        reminderEnabled: Boolean
    ) -> Unit
) {
    val context = LocalContext.current
    var title by remember(existingTask) { mutableStateOf(existingTask?.title ?: "") }
    var description by remember(existingTask) { mutableStateOf(existingTask?.description ?: "") }
    var priority by remember(existingTask) { mutableStateOf(existingTask?.priority ?: "MEDIUM") }
    var category by remember(existingTask) { mutableStateOf(existingTask?.category ?: "Personal") }
    var repeatFrequency by remember(existingTask) { mutableStateOf(existingTask?.repeatFrequency ?: "NONE") }

    // Calendar & Clock state (defaults to existingTask dueDateTime or Today at 6:00 PM)
    val initialCal = remember(initialDateMillis, existingTask) {
        Calendar.getInstance().apply {
            val baseMillis = existingTask?.dueDateTime ?: initialDateMillis
            if (baseMillis != null && baseMillis > 0L) {
                timeInMillis = baseMillis
            }
        }
    }
    var selectedDateMillis by remember(existingTask) { mutableLongStateOf(initialCal.timeInMillis) }
    var selectedHour by remember(existingTask) {
        mutableIntStateOf(if (existingTask?.dueDateTime != null) initialCal.get(Calendar.HOUR_OF_DAY) else 18)
    }
    var selectedMinute by remember(existingTask) {
        mutableIntStateOf(if (existingTask?.dueDateTime != null) initialCal.get(Calendar.MINUTE) else 0)
    }

    var showDatePickerDialog by remember { mutableStateOf(false) }
    var showTimePickerDialog by remember { mutableStateOf(false) }

    // Subtasks
    var subtasks by remember(existingTask) {
        mutableStateOf(
            if (existingTask != null) TaskUtils.parseSubtasks(existingTask.subtasksJson) else emptyList()
        )
    }
    var newSubtaskText by remember { mutableStateOf("") }

    // Money link
    var isLinkedToMoney by remember(existingTask) { mutableStateOf(existingTask?.isLinkedToMoney ?: false) }
    var linkedAmountStr by remember(existingTask) {
        mutableStateOf(
            existingTask?.linkedAmount?.let { amt ->
                if (amt % 1.0 == 0.0) amt.toLong().toString() else amt.toString()
            } ?: ""
        )
    }
    var linkedAccount by remember(existingTask, accounts) {
        mutableStateOf(existingTask?.linkedAccount ?: accounts.firstOrNull()?.name ?: "Bank Account")
    }
    var linkedCategory by remember(existingTask) { mutableStateOf(existingTask?.linkedCategory ?: "Bills") }

    // Reminder & Notification Permission
    var reminderEnabled by remember(existingTask) { mutableStateOf(existingTask?.reminderEnabled ?: false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        reminderEnabled = isGranted
    }

    val priorities = listOf("LOW", "MEDIUM", "HIGH", "URGENT")
    val taskCategories = listOf("Personal", "Work", "Finance", "Bills", "Health", "Shopping", "Home")
    val repeatOptions = listOf(
        "NONE" to "One-Time",
        "DAILY" to "Daily",
        "WEEKLY" to "Weekly",
        "MONTHLY" to "Monthly"
    )

    val dateDisplayFmt = remember { SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault()) }
    val formattedDateLabel = remember(selectedDateMillis) { dateDisplayFmt.format(Date(selectedDateMillis)) }
    val formattedTimeLabel = remember(selectedHour, selectedMinute) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, selectedHour)
            set(Calendar.MINUTE, selectedMinute)
        }
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(cal.time)
    }

    fun buildCombinedDateTimeMillis(): Long {
        val dateCal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, dateCal.get(Calendar.YEAR))
            set(Calendar.MONTH, dateCal.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, dateCal.get(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, selectedHour)
            set(Calendar.MINUTE, selectedMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun submitTask() {
        val finalTitle = title.trim().ifBlank { "Daily Task" }
        val finalDateTime = buildCombinedDateTimeMillis()
        val linkedAmt = linkedAmountStr.toDoubleOrNull()
        onSaveTask(
            finalTitle,
            description.trim(),
            priority,
            category,
            finalDateTime,
            repeatFrequency,
            subtasks,
            isLinkedToMoney,
            if (isLinkedToMoney) linkedAmt else null,
            if (isLinkedToMoney) linkedAccount else null,
            if (isLinkedToMoney) linkedCategory else null,
            reminderEnabled
        )
        onDismiss()
    }

    // Material 3 Calendar DatePicker Dialog
    if (showDatePickerDialog) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { picked ->
                            selectedDateMillis = picked
                        }
                        showDatePickerDialog = false
                    },
                    modifier = Modifier.testTag("confirm_task_calendar_date")
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Select Date")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerDialog = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Material 3 Clock TimePicker Dialog
    if (showTimePickerDialog) {
        val timePickerState = rememberTimePickerState(
            initialHour = selectedHour,
            initialMinute = selectedMinute,
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { showTimePickerDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.AccessTime, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Select Task Time", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedHour = timePickerState.hour
                        selectedMinute = timePickerState.minute
                        showTimePickerDialog = false
                    },
                    modifier = Modifier.testTag("confirm_task_clock_time")
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Select Time")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePickerDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("task_dialog_back_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Spacer(Modifier.width(4.dp))
                    AutoSizeSingleLineText(
                        text = if (existingTask != null) "Edit Task" else "New Task",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { submitTask() },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("task_dialog_top_save_check")
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Save Task", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (existingTask != null) "Update" else "Save", fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Task Title *") },
                        placeholder = { Text("e.g. Pay electricity bill, Buy groceries") },
                        modifier = Modifier.fillMaxWidth().testTag("task_title_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Calendar & Clock Interactive Pickers
                item {
                    Text(
                        "Date & Time",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Calendar Picker Card
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .weight(1.25f)
                                .clickable { showDatePickerDialog = true }
                                .testTag("task_due_date_input")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.CalendarMonth,
                                    contentDescription = "Open Calendar",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column {
                                    Text(
                                        "Date",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                    Text(
                                        formattedDateLabel,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // Clock Picker Card
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .weight(0.95f)
                                .clickable { showTimePickerDialog = true }
                                .testTag("task_due_time_input")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.AccessTime,
                                    contentDescription = "Open Clock",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column {
                                    Text(
                                        "Time",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                    Text(
                                        formattedTimeLabel,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    // Quick 1-Tap Date & Time Presets
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AssistChip(
                            onClick = { selectedDateMillis = System.currentTimeMillis() },
                            label = { Text("Today") }
                        )
                        AssistChip(
                            onClick = {
                                selectedDateMillis = Calendar.getInstance().apply {
                                    add(Calendar.DAY_OF_YEAR, 1)
                                }.timeInMillis
                            },
                            label = { Text("Tomorrow") }
                        )
                        AssistChip(
                            onClick = {
                                selectedDateMillis = Calendar.getInstance().apply {
                                    add(Calendar.DAY_OF_YEAR, 7)
                                }.timeInMillis
                            },
                            label = { Text("Next Week") }
                        )
                        AssistChip(
                            onClick = { selectedHour = 9; selectedMinute = 0 },
                            label = { Text("9:00 AM") }
                        )
                        AssistChip(
                            onClick = { selectedHour = 14; selectedMinute = 0 },
                            label = { Text("2:00 PM") }
                        )
                        AssistChip(
                            onClick = { selectedHour = 18; selectedMinute = 0 },
                            label = { Text("6:00 PM") }
                        )
                        AssistChip(
                            onClick = { selectedHour = 21; selectedMinute = 0 },
                            label = { Text("9:00 PM") }
                        )
                    }
                }

                // Repeat Frequency (One-Time, Daily, Weekly, Monthly)
                item {
                    Text(
                        "Repeat Schedule (Daily & Weekly Auto-Repeat)",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        repeatOptions.forEach { (freq, label) ->
                            FilterChip(
                                selected = repeatFrequency == freq,
                                onClick = { repeatFrequency = freq },
                                leadingIcon = if (repeatFrequency == freq) {
                                    { Icon(Icons.Default.EventRepeat, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null,
                                label = { Text(label, fontWeight = if (repeatFrequency == freq) FontWeight.Bold else FontWeight.Normal) },
                                modifier = Modifier.testTag("repeat_chip_$freq")
                            )
                        }
                    }
                    if (repeatFrequency != "NONE") {
                        Text(
                            when (repeatFrequency) {
                                "DAILY" -> "✓ Repeats every day. Completing today's task automatically schedules tomorrow's task and builds your Day Streak!"
                                "WEEKLY" -> "✓ Repeats every week on the same day. Completing it automatically schedules next week's task!"
                                else -> "✓ Repeats every month on the same date."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Priority
                item {
                    Text("Priority", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        priorities.forEach { p ->
                            FilterChip(
                                selected = priority == p,
                                onClick = { priority = p },
                                label = { Text(p) },
                                modifier = Modifier.testTag("priority_chip_$p")
                            )
                        }
                    }
                }

                // Category
                item {
                    Text("Category", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        taskCategories.forEach { c ->
                            FilterChip(
                                selected = category == c,
                                onClick = { category = c },
                                label = { Text(c) },
                                modifier = Modifier.testTag("category_chip_$c")
                            )
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description / Notes (Optional)") },
                        modifier = Modifier.fillMaxWidth().testTag("task_desc_input"),
                        maxLines = 2
                    )
                }

                // Subtasks Section
                item {
                    Text("Subtasks (${subtasks.size})", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newSubtaskText,
                            onValueChange = { newSubtaskText = it },
                            placeholder = { Text("Add subtask item...") },
                            modifier = Modifier.weight(1f).testTag("new_subtask_input"),
                            singleLine = true
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                if (newSubtaskText.isNotBlank()) {
                                    subtasks = subtasks + Subtask(title = newSubtaskText.trim())
                                    newSubtaskText = ""
                                }
                            },
                            modifier = Modifier.testTag("add_subtask_button")
                        ) {
                            Icon(Icons.Default.AddCircle, contentDescription = "Add Subtask", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                if (subtasks.isNotEmpty()) {
                    itemsIndexed(subtasks) { index, st ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("• ${st.title}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            IconButton(
                                onClick = { subtasks = subtasks.filterIndexed { i, _ -> i != index } },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                // Link to Money
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Link Task to Money", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                                    Text("Creates an expense when completed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = isLinkedToMoney,
                                    onCheckedChange = { isLinkedToMoney = it },
                                    modifier = Modifier.testTag("link_money_switch")
                                )
                            }

                            if (isLinkedToMoney) {
                                Spacer(Modifier.height(10.dp))
                                OutlinedTextField(
                                    value = linkedAmountStr,
                                    onValueChange = { linkedAmountStr = it },
                                    label = { Text("Expense Amount (₹)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth().testTag("linked_amount_input"),
                                    singleLine = true
                                )
                                Spacer(Modifier.height(8.dp))
                                Text("Payment Account", style = MaterialTheme.typography.labelSmall)
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    accounts.map { it.name }.forEach { acc ->
                                        FilterChip(
                                            selected = linkedAccount == acc,
                                            onClick = { linkedAccount = acc },
                                            label = { Text(acc) },
                                            modifier = Modifier.testTag("linked_acc_$acc")
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Reminder switch
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Set Reminder Notification", style = MaterialTheme.typography.bodyMedium)
                        }
                        Switch(
                            checked = reminderEnabled,
                            onCheckedChange = { checked ->
                                if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    !NotificationHelper.hasNotificationPermission(context)
                                ) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    reminderEnabled = checked
                                }
                            },
                            modifier = Modifier.testTag("task_reminder_switch")
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { submitTask() },
                modifier = Modifier.testTag("confirm_create_task_button")
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (existingTask != null) "Update Task" else "Save Task", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Back")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddScheduleEventDialog(
    onDismiss: () -> Unit,
    initialDateMillis: Long? = null,
    existingEvent: ScheduleEvent? = null,
    onSaveEvent: (
        title: String,
        description: String,
        startTime: Long,
        endTime: Long,
        location: String,
        category: String,
        reminderEnabled: Boolean,
        reminderOffsetMinutes: Int
    ) -> Unit
) {
    val context = LocalContext.current
    var title by remember(existingEvent) { mutableStateOf(existingEvent?.title ?: "") }
    var description by remember(existingEvent) { mutableStateOf(existingEvent?.description ?: "") }
    var location by remember(existingEvent) { mutableStateOf(existingEvent?.location ?: "") }
    var category by remember(existingEvent) { mutableStateOf(existingEvent?.category ?: "Meeting") }
    var repeatSchedule by remember { mutableStateOf("NONE") } // NONE, DAILY, WEEKLY
    var reminderEnabled by remember(existingEvent) { mutableStateOf(existingEvent?.reminderEnabled ?: true) }
    var reminderOffsetMinutes by remember(existingEvent) { mutableIntStateOf(existingEvent?.reminderOffsetMinutes ?: 15) }

    val initialCal = remember(initialDateMillis, existingEvent) {
        Calendar.getInstance().apply {
            val baseMillis = existingEvent?.startTime ?: initialDateMillis
            if (baseMillis != null && baseMillis > 0L) {
                timeInMillis = baseMillis
            }
        }
    }
    val initialEndCal = remember(existingEvent) {
        Calendar.getInstance().apply {
            if (existingEvent != null && existingEvent.endTime > 0L) {
                timeInMillis = existingEvent.endTime
            }
        }
    }
    var selectedDateMillis by remember(existingEvent) { mutableLongStateOf(initialCal.timeInMillis) }
    var startHour by remember(existingEvent) {
        mutableIntStateOf(if (existingEvent != null) initialCal.get(Calendar.HOUR_OF_DAY) else 10)
    }
    var startMinute by remember(existingEvent) {
        mutableIntStateOf(if (existingEvent != null) initialCal.get(Calendar.MINUTE) else 0)
    }
    var endHour by remember(existingEvent) {
        mutableIntStateOf(if (existingEvent != null) initialEndCal.get(Calendar.HOUR_OF_DAY) else 11)
    }
    var endMinute by remember(existingEvent) {
        mutableIntStateOf(if (existingEvent != null) initialEndCal.get(Calendar.MINUTE) else 0)
    }

    var showDatePickerDialog by remember { mutableStateOf(false) }
    var showStartTimePickerDialog by remember { mutableStateOf(false) }
    var showEndTimePickerDialog by remember { mutableStateOf(false) }

    val dateDisplayFmt = remember { SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault()) }
    val formattedDateLabel = remember(selectedDateMillis) { dateDisplayFmt.format(Date(selectedDateMillis)) }
    val timeFmt = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }

    val formattedStartTime = remember(startHour, startMinute) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, startHour)
            set(Calendar.MINUTE, startMinute)
        }
        timeFmt.format(cal.time)
    }

    val formattedEndTime = remember(endHour, endMinute) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, endHour)
            set(Calendar.MINUTE, endMinute)
        }
        timeFmt.format(cal.time)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        reminderEnabled = isGranted
    }

    fun submitEvent() {
        val finalTitle = title.trim().ifBlank { "Scheduled Event" }
        val baseCal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }

        val occurrences = if (existingEvent != null) 1 else when (repeatSchedule) {
            "DAILY" -> 7   // Creates daily schedule for the next 7 days
            "WEEKLY" -> 4  // Creates weekly schedule for the next 4 weeks
            else -> 1
        }

        for (i in 0 until occurrences) {
            val dayOffset = when (repeatSchedule) {
                "DAILY" -> i
                "WEEKLY" -> i * 7
                else -> 0
            }
            val startCal = Calendar.getInstance().apply {
                set(Calendar.YEAR, baseCal.get(Calendar.YEAR))
                set(Calendar.MONTH, baseCal.get(Calendar.MONTH))
                set(Calendar.DAY_OF_MONTH, baseCal.get(Calendar.DAY_OF_MONTH))
                add(Calendar.DAY_OF_YEAR, dayOffset)
                set(Calendar.HOUR_OF_DAY, startHour)
                set(Calendar.MINUTE, startMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val endCal = Calendar.getInstance().apply {
                timeInMillis = startCal.timeInMillis
                set(Calendar.HOUR_OF_DAY, endHour)
                set(Calendar.MINUTE, endMinute)
                if (timeInMillis <= startCal.timeInMillis) {
                    add(Calendar.HOUR_OF_DAY, 1)
                }
            }
            onSaveEvent(
                finalTitle,
                description.trim(),
                startCal.timeInMillis,
                endCal.timeInMillis,
                location.trim(),
                category,
                reminderEnabled && i == 0,
                reminderOffsetMinutes
            )
        }
        onDismiss()
    }

    // Calendar Date Picker Dialog
    if (showDatePickerDialog) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selectedDateMillis = it }
                        showDatePickerDialog = false
                    },
                    modifier = Modifier.testTag("confirm_event_calendar_date")
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Select Date")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerDialog = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Start Clock Time Picker Dialog
    if (showStartTimePickerDialog) {
        val state = rememberTimePickerState(initialHour = startHour, initialMinute = startMinute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { showStartTimePickerDialog = false },
            title = { Text("Select Start Time", fontWeight = FontWeight.Bold) },
            text = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = state)
                }
            },
            confirmButton = {
                Button(onClick = {
                    startHour = state.hour
                    startMinute = state.minute
                    if (endHour < startHour || (endHour == startHour && endMinute <= startMinute)) {
                        endHour = (startHour + 1).coerceAtMost(23)
                        endMinute = startMinute
                    }
                    showStartTimePickerDialog = false
                }) {
                    Text("Select Start Time")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartTimePickerDialog = false }) { Text("Cancel") }
            }
        )
    }

    // End Clock Time Picker Dialog
    if (showEndTimePickerDialog) {
        val state = rememberTimePickerState(initialHour = endHour, initialMinute = endMinute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { showEndTimePickerDialog = false },
            title = { Text("Select End Time", fontWeight = FontWeight.Bold) },
            text = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = state)
                }
            },
            confirmButton = {
                Button(onClick = {
                    endHour = state.hour
                    endMinute = state.minute
                    showEndTimePickerDialog = false
                }) {
                    Text("Select End Time")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndTimePickerDialog = false }) { Text("Cancel") }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("event_dialog_back_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Spacer(Modifier.width(4.dp))
                    AutoSizeSingleLineText(
                        text = if (existingEvent != null) "Edit Event" else "New Event",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { submitEvent() },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("event_dialog_top_save_check")
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Save Event", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (existingEvent != null) "Update" else "Save", fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Event Title *") },
                    placeholder = { Text("e.g. Doctor appointment, Team sync") },
                    modifier = Modifier.fillMaxWidth().testTag("event_title_input"),
                    singleLine = true
                )

                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Location (Optional)") },
                    placeholder = { Text("e.g. City Hospital, Office Room 3") },
                    modifier = Modifier.fillMaxWidth().testTag("event_location_input"),
                    singleLine = true
                )

                // Calendar Date Selector Card
                Text(
                    "Event Date & Time (Tap Calendar & Clock)",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePickerDialog = true }
                        .testTag("event_date_input")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = "Pick Date", tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text("Event Date (Tap Calendar)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(formattedDateLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text("Change", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }

                // Quick Date Presets
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AssistChip(
                        onClick = { selectedDateMillis = System.currentTimeMillis() },
                        label = { Text("Today") }
                    )
                    AssistChip(
                        onClick = {
                            selectedDateMillis = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
                        },
                        label = { Text("Tomorrow") }
                    )
                    AssistChip(
                        onClick = {
                            selectedDateMillis = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 7) }.timeInMillis
                        },
                        label = { Text("Next Week") }
                    )
                }

                // Start & End Clock Picker Cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showStartTimePickerDialog = true }
                            .testTag("event_start_time_input")
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.AccessTime, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Column {
                                Text("Start Clock", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(formattedStartTime, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showEndTimePickerDialog = true }
                            .testTag("event_end_time_input")
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Column {
                                Text("End Clock", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(formattedEndTime, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Repeat Schedule (One-Time, Daily, Weekly)
                Text("Repeat Schedule", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "NONE" to "One-Time",
                        "DAILY" to "Daily (7 Days)",
                        "WEEKLY" to "Weekly (4 Weeks)"
                    ).forEach { (freq, label) ->
                        FilterChip(
                            selected = repeatSchedule == freq,
                            onClick = { repeatSchedule = freq },
                            label = { Text(label) },
                            modifier = Modifier.testTag("event_repeat_chip_$freq")
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("15 Min Reminder", style = MaterialTheme.typography.bodyMedium)
                    }
                    Switch(
                        checked = reminderEnabled,
                        onCheckedChange = { checked ->
                            if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                !NotificationHelper.hasNotificationPermission(context)
                            ) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                reminderEnabled = checked
                            }
                        },
                        modifier = Modifier.testTag("event_reminder_switch")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { submitEvent() },
                modifier = Modifier.testTag("save_event_button")
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (existingEvent != null) "Update Event" else "Save Event", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Back")
            }
        }
    )
}

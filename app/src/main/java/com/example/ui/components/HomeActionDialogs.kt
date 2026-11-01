package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

@Composable
fun QuickNoteDialog(
    onDismiss: () -> Unit,
    onSave: (title: String, content: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    fun submitNote() {
        val resolvedTitle = title.trim().ifBlank {
            content.trim().lineSequence().firstOrNull()?.take(36) ?: ""
        }
        if (resolvedTitle.isBlank()) {
            error = true
        } else {
            onSave(resolvedTitle, content.trim())
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Add Note", fontWeight = FontWeight.Bold)
                Button(
                    onClick = { submitNote() },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("quick_note_top_save_check")
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Save Note", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Jot down financial reminders, PIN hints, promises, or ideas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        if (it.isNotBlank()) error = false
                    },
                    label = { Text("Note Title *") },
                    placeholder = { Text("e.g. Next month budget rule") },
                    leadingIcon = { Icon(Icons.Default.EditNote, contentDescription = null) },
                    singleLine = true,
                    isError = error,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quick_note_title_input")
                )

                OutlinedTextField(
                    value = content,
                    onValueChange = {
                        content = it
                        if (it.isNotBlank()) error = false
                    },
                    label = { Text("Content (Optional)") },
                    placeholder = { Text("e.g. Save ₹5000 before Diwali shopping...") },
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quick_note_content_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { submitNote() },
                modifier = Modifier.testTag("save_quick_note_button")
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Save Note", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_quick_note_button")
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun QuickTaskDialog(
    onDismiss: () -> Unit,
    onSave: (title: String, timeLabel: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var timeLabel by remember { mutableStateOf("Today") }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text("Add Daily Task", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Add an item to today's money and life checklist.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        if (it.isNotBlank()) error = false
                    },
                    label = { Text("Task Description *") },
                    placeholder = { Text("e.g. Pay maintenance fee") },
                    leadingIcon = { Icon(Icons.Default.Assignment, contentDescription = null) },
                    singleLine = true,
                    isError = error,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quick_task_title_input")
                )

                OutlinedTextField(
                    value = timeLabel,
                    onValueChange = { timeLabel = it },
                    label = { Text("Time / Target (Optional)") },
                    placeholder = { Text("e.g. 5:00 PM, Today, Tomorrow") },
                    leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quick_task_time_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank()) {
                        error = true
                    } else {
                        onSave(title.trim(), timeLabel.trim())
                    }
                },
                modifier = Modifier.testTag("save_quick_task_button")
            ) {
                Text("Add Task")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_quick_task_button")
            ) {
                Text("Cancel")
            }
        }
    )
}

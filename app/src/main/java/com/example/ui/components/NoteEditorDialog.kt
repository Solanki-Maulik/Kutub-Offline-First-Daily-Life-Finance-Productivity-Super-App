package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.*
import com.example.utils.FormatUtils
import com.example.utils.NoteUtils
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NoteEditorDialog(
    initialNote: MoneyNote?,
    transactions: List<Transaction>,
    people: List<Person>,
    tasks: List<TaskItem>,
    onAutoSave: (MoneyNote, (Long) -> Unit) -> Unit,
    onDelete: ((MoneyNote) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val isDark = isSystemInDarkTheme()

    var currentId by remember { mutableStateOf(initialNote?.id ?: 0L) }
    var title by remember { mutableStateOf(initialNote?.title ?: "") }
    var content by remember { mutableStateOf(initialNote?.content ?: "") }
    var isPinned by remember { mutableStateOf(initialNote?.isPinned ?: false) }
    var isArchived by remember { mutableStateOf(initialNote?.isArchived ?: false) }
    var colorHex by remember { mutableStateOf(initialNote?.colorHex ?: "#DEFAULT") }
    var tags by remember { mutableStateOf(NoteUtils.parseTags(initialNote?.tagsJson)) }
    var newTagInput by remember { mutableStateOf("") }
    var checklists by remember { mutableStateOf(NoteUtils.parseChecklist(initialNote?.checklistsJson)) }
    var newChecklistInput by remember { mutableStateOf("") }

    var attachedType by remember { mutableStateOf(initialNote?.attachedType) }
    var attachedId by remember { mutableStateOf(initialNote?.attachedId) }
    var attachedTitle by remember { mutableStateOf(initialNote?.attachedTitle) }

    var showMarkdownPreview by remember { mutableStateOf(false) }
    var showAttachSelector by remember { mutableStateOf(false) }
    var saveStatus by remember { mutableStateOf(if (currentId > 0L) "Saved" else "Ready") }
    var hasUserEdited by remember { mutableStateOf(false) }

    fun buildCurrentNote(): MoneyNote {
        return MoneyNote(
            id = currentId,
            title = title.trim(),
            content = content,
            isPinned = isPinned,
            isArchived = isArchived,
            colorHex = colorHex,
            tagsJson = NoteUtils.serializeTags(tags),
            checklistsJson = NoteUtils.serializeChecklist(checklists),
            attachedType = attachedType,
            attachedId = attachedId,
            attachedTitle = attachedTitle,
            timestamp = initialNote?.timestamp ?: System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }

    // Auto-save while typing with 350ms debounce
    LaunchedEffect(
        title,
        content,
        isPinned,
        isArchived,
        colorHex,
        tags,
        checklists,
        attachedType,
        attachedId
    ) {
        if (!hasUserEdited && currentId == 0L && title.isBlank() && content.isBlank() && checklists.isEmpty()) {
            return@LaunchedEffect
        }
        if (title.isBlank() && content.isBlank() && checklists.isEmpty()) {
            return@LaunchedEffect
        }
        saveStatus = "Auto-saving..."
        delay(350)
        onAutoSave(buildCurrentNote()) { savedId ->
            currentId = savedId
            saveStatus = "Auto-saved ✓"
        }
    }

    val bgColor = if (colorHex == "#DEFAULT") {
        MaterialTheme.colorScheme.surface
    } else {
        NoteUtils.getNoteColor(colorHex, isDark)
    }

    Dialog(
        onDismissRequest = {
            if (title.isNotBlank() || content.isNotBlank() || checklists.isNotEmpty()) {
                onAutoSave(buildCurrentNote()) {}
            }
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(8.dp),
            shape = RoundedCornerShape(24.dp),
            color = bgColor,
            tonalElevation = 4.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Bar with prominent top-right Checkmark (Save) button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                if (title.isNotBlank() || content.isNotBlank() || checklists.isNotEmpty()) {
                                    onAutoSave(buildCurrentNote()) {}
                                }
                                onDismiss()
                            },
                            modifier = Modifier.testTag("note_editor_close")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = saveStatus,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        // Pin toggle
                        IconButton(
                            onClick = {
                                hasUserEdited = true
                                isPinned = !isPinned
                            },
                            modifier = Modifier.testTag("note_editor_pin")
                        ) {
                            Icon(
                                Icons.Default.PushPin,
                                contentDescription = "Pin Note",
                                tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Archive toggle
                        IconButton(
                            onClick = {
                                hasUserEdited = true
                                isArchived = !isArchived
                            },
                            modifier = Modifier.testTag("note_editor_archive")
                        ) {
                            Icon(
                                if (isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                                contentDescription = "Archive Note",
                                tint = if (isArchived) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Delete button if editing existing note
                        if (initialNote != null && onDelete != null) {
                            IconButton(
                                onClick = { onDelete(initialNote) },
                                modifier = Modifier.testTag("note_editor_delete")
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete Note",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        // Prominent Top-Right Checkmark ("Right Sign") Save Button
                        Button(
                            onClick = {
                                val noteToSave = buildCurrentNote()
                                if (title.isNotBlank() || content.isNotBlank() || checklists.isNotEmpty()) {
                                    onAutoSave(noteToSave) {}
                                } else {
                                    onAutoSave(noteToSave.copy(title = "Quick Note")) {}
                                }
                                onDismiss()
                            },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .height(40.dp)
                                .testTag("note_editor_top_save_check")
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Save Note",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Save", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { Spacer(Modifier.height(4.dp)) }

                    // Color Label Picker
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Color:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            NoteUtils.COLOR_OPTIONS.forEach { option ->
                                val selected = colorHex.equals(option.hex, ignoreCase = true)
                                val circleColor = NoteUtils.getNoteColor(option.hex, isDark)
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(circleColor)
                                        .border(
                                            width = if (selected) 2.5.dp else 1.dp,
                                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            hasUserEdited = true
                                            colorHex = option.hex
                                        }
                                        .testTag("note_color_${option.label.lowercase()}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (selected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = option.label,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Title Input
                    item {
                        OutlinedTextField(
                            value = title,
                            onValueChange = {
                                hasUserEdited = true
                                title = it
                            },
                            placeholder = { Text("Note Title...", fontWeight = FontWeight.Bold) },
                            textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("note_title_input")
                        )
                    }

                    // Rich Text / Markdown Toolbar
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AssistChip(
                                onClick = {
                                    hasUserEdited = true
                                    content += if (content.isEmpty()) "**Bold**" else " **Bold**"
                                },
                                label = { Text("B", fontWeight = FontWeight.Bold) }
                            )
                            AssistChip(
                                onClick = {
                                    hasUserEdited = true
                                    content += if (content.isEmpty()) "*Italic*" else " *Italic*"
                                },
                                label = { Text("I", fontStyle = FontStyle.Italic) }
                            )
                            AssistChip(
                                onClick = {
                                    hasUserEdited = true
                                    content += (if (content.isEmpty() || content.endsWith("\n")) "" else "\n") + "# Heading "
                                },
                                label = { Text("H1", fontWeight = FontWeight.SemiBold) }
                            )
                            AssistChip(
                                onClick = {
                                    hasUserEdited = true
                                    content += (if (content.isEmpty() || content.endsWith("\n")) "" else "\n") + "- "
                                },
                                label = { Text("• Bullet") }
                            )
                            AssistChip(
                                onClick = {
                                    hasUserEdited = true
                                    content += (if (content.isEmpty() || content.endsWith("\n")) "" else "\n") + "> "
                                },
                                label = { Text("“ Quote") }
                            )
                            FilterChip(
                                selected = showMarkdownPreview,
                                onClick = { showMarkdownPreview = !showMarkdownPreview },
                                label = { Text(if (showMarkdownPreview) "Edit Text" else "Rich Preview") },
                                leadingIcon = {
                                    Icon(
                                        if (showMarkdownPreview) Icons.Default.Edit else Icons.Default.Visibility,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }

                    // Content Input or Rich Markdown Preview
                    item {
                        if (showMarkdownPreview) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 120.dp),
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Box(modifier = Modifier.padding(14.dp)) {
                                    if (content.isBlank()) {
                                        Text(
                                            "No markdown content yet. Tap 'Edit Text' to write.",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        RichMarkdownText(markdown = content)
                                    }
                                }
                            }
                        } else {
                            OutlinedTextField(
                                value = content,
                                onValueChange = {
                                    hasUserEdited = true
                                    content = it
                                },
                                placeholder = { Text("Write note... Supports **bold**, *italic*, # headings, - bullets") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 140.dp)
                                    .testTag("note_content_input"),
                                maxLines = 12
                            )
                        }
                    }

                    // Checklist Section
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    "Checklist Items (${checklists.count { it.isChecked }}/${checklists.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(8.dp))
                                checklists.forEach { item ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = item.isChecked,
                                            onCheckedChange = { checked ->
                                                hasUserEdited = true
                                                checklists = checklists.map {
                                                    if (it.id == item.id) it.copy(isChecked = checked) else it
                                                }
                                            }
                                        )
                                        Text(
                                            text = item.text,
                                            style = MaterialTheme.typography.bodyMedium,
                                            textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = {
                                                hasUserEdited = true
                                                checklists = checklists.filterNot { it.id == item.id }
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Remove checklist item",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = newChecklistInput,
                                        onValueChange = { newChecklistInput = it },
                                        placeholder = { Text("Add checklist item...") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("note_checklist_input")
                                    )
                                    FilledTonalIconButton(
                                        onClick = {
                                            if (newChecklistInput.isNotBlank()) {
                                                hasUserEdited = true
                                                checklists = checklists + NoteChecklistItem(text = newChecklistInput.trim())
                                                newChecklistInput = ""
                                            }
                                        },
                                        modifier = Modifier.testTag("note_add_checklist_btn")
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Add Checklist Item")
                                    }
                                }
                            }
                        }
                    }

                    // Tags Section
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Tags", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            if (tags.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    tags.forEach { tag ->
                                        InputChip(
                                            selected = true,
                                            onClick = {
                                                hasUserEdited = true
                                                tags = tags - tag
                                            },
                                            label = { Text("#$tag") },
                                            trailingIcon = {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "Remove tag",
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = newTagInput,
                                    onValueChange = { newTagInput = it },
                                    placeholder = { Text("Add tag (e.g. tax, receipt, idea)") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("note_tag_input")
                                )
                                FilledTonalButton(
                                    onClick = {
                                        val clean = newTagInput.trim().removePrefix("#")
                                        if (clean.isNotBlank() && !tags.contains(clean)) {
                                            hasUserEdited = true
                                            tags = tags + clean
                                            newTagInput = ""
                                        }
                                    },
                                    modifier = Modifier.testTag("note_add_tag_btn")
                                ) {
                                    Text("Add Tag")
                                }
                            }
                        }
                    }

                    // Attach to Transaction, Person, or Task
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            Icons.Default.AttachFile,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        AutoSizeSingleLineText(
                                            text = "Attach Transaction, Person, or Task",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            minFontSizeSp = 10f
                                        )
                                    }
                                    if (attachedType != null) {
                                        Spacer(Modifier.width(6.dp))
                                        FilledTonalButton(
                                            onClick = {
                                                hasUserEdited = true
                                                attachedType = null
                                                attachedId = null
                                                attachedTitle = null
                                                showAttachSelector = false
                                            },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Text(
                                                "Clear",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }

                                if (attachedType != null && attachedTitle != null) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                when (attachedType) {
                                                    "TRANSACTION" -> Icons.Default.Receipt
                                                    "PERSON" -> Icons.Default.Person
                                                    else -> Icons.Default.TaskAlt
                                                },
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            AutoSizeSingleLineText(
                                                text = "$attachedType: $attachedTitle",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            if (attachedType == "TRANSACTION" && showAttachSelector) {
                                                showAttachSelector = false
                                            } else {
                                                attachedType = "TRANSACTION"
                                                showAttachSelector = true
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                    ) {
                                        AutoSizeSingleLineText(
                                            text = "Transaction",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            minFontSizeSp = 9f
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            if (attachedType == "PERSON" && showAttachSelector) {
                                                showAttachSelector = false
                                            } else {
                                                attachedType = "PERSON"
                                                showAttachSelector = true
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                    ) {
                                        AutoSizeSingleLineText(
                                            text = "Person",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            minFontSizeSp = 9f
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            if (attachedType == "TASK" && showAttachSelector) {
                                                showAttachSelector = false
                                            } else {
                                                attachedType = "TASK"
                                                showAttachSelector = true
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                    ) {
                                        AutoSizeSingleLineText(
                                            text = "Task",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            minFontSizeSp = 9f
                                        )
                                    }
                                }

                                if (showAttachSelector) {
                                    when (attachedType) {
                                        "TRANSACTION" -> {
                                            if (transactions.isEmpty()) {
                                                Text("No transactions available to attach.", style = MaterialTheme.typography.bodySmall)
                                            } else {
                                                transactions.take(6).forEach { tx ->
                                                    val label = "${tx.category} (${FormatUtils.formatInr(tx.amount)}) - ${FormatUtils.formatDate(tx.timestamp)}"
                                                    Surface(
                                                        shape = RoundedCornerShape(10.dp),
                                                        color = MaterialTheme.colorScheme.surface,
                                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                hasUserEdited = true
                                                                attachedId = tx.id.toLong()
                                                                attachedTitle = label
                                                                showAttachSelector = false
                                                            }
                                                    ) {
                                                        Text(
                                                            text = label,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = FontWeight.Medium,
                                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        "PERSON" -> {
                                            if (people.isEmpty()) {
                                                Text("No people in Udhaar ledger yet.", style = MaterialTheme.typography.bodySmall)
                                            } else {
                                                people.take(8).forEach { p ->
                                                    Surface(
                                                        shape = RoundedCornerShape(10.dp),
                                                        color = MaterialTheme.colorScheme.surface,
                                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                hasUserEdited = true
                                                                attachedId = p.id
                                                                attachedTitle = p.name
                                                                showAttachSelector = false
                                                            }
                                                    ) {
                                                        Text(
                                                            text = p.name,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = FontWeight.Medium,
                                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        "TASK" -> {
                                            if (tasks.isEmpty()) {
                                                Text("No tasks created yet.", style = MaterialTheme.typography.bodySmall)
                                            } else {
                                                tasks.take(8).forEach { t ->
                                                    Surface(
                                                        shape = RoundedCornerShape(10.dp),
                                                        color = MaterialTheme.colorScheme.surface,
                                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                hasUserEdited = true
                                                                attachedId = t.id
                                                                attachedTitle = t.title
                                                                showAttachSelector = false
                                                            }
                                                    ) {
                                                        Text(
                                                            text = t.title,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = FontWeight.Medium,
                                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
fun RichMarkdownText(markdown: String, modifier: Modifier = Modifier) {
    val lines = markdown.split("\n")
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEach { rawLine ->
            val trimmed = rawLine.trimStart()
            when {
                trimmed.startsWith("# ") -> {
                    Text(
                        text = parseInlineMarkdown(trimmed.removePrefix("# ")),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                trimmed.startsWith("## ") -> {
                    Text(
                        text = parseInlineMarkdown(trimmed.removePrefix("## ")),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("•", fontWeight = FontWeight.Bold)
                        Text(
                            text = parseInlineMarkdown(trimmed.substring(2)),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                trimmed.startsWith("> ") -> {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = parseInlineMarkdown(trimmed.removePrefix("> ")),
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
                else -> {
                    Text(
                        text = parseInlineMarkdown(rawLine),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

private fun parseInlineMarkdown(text: String) = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        if (text.startsWith("**", i)) {
            val end = text.indexOf("**", i + 2)
            if (end != -1) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(text.substring(i + 2, end))
                }
                i = end + 2
                continue
            }
        }
        if (text.startsWith("*", i)) {
            val end = text.indexOf("*", i + 1)
            if (end != -1) {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(text.substring(i + 1, end))
                }
                i = end + 1
                continue
            }
        }
        if (text.startsWith("`", i)) {
            val end = text.indexOf("`", i + 1)
            if (end != -1) {
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)) {
                    append(text.substring(i + 1, end))
                }
                i = end + 1
                continue
            }
        }
        append(text[i])
        i++
    }
}

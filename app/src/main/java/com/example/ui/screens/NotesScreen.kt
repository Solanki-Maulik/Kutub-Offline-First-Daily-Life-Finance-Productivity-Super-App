package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.data.MoneyNote
import com.example.ui.components.NoteEditorDialog
import com.example.ui.components.RichMarkdownText
import com.example.utils.FormatUtils
import com.example.utils.NoteUtils
import com.example.viewmodel.FinanceViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NotesScreen(viewModel: FinanceViewModel, navController: NavController) {
    val activeNotes by viewModel.activeNotes.collectAsStateWithLifecycle()
    val archivedNotes by viewModel.archivedNotes.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val people by viewModel.people.collectAsStateWithLifecycle()
    val tasks by viewModel.allTasks.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Notes, 1: Archived
    var selectedTagFilter by remember { mutableStateOf<String?>(null) }
    var isGridView by remember { mutableStateOf(true) }

    var editingNote by remember { mutableStateOf<MoneyNote?>(null) }
    var showNoteEditor by remember { mutableStateOf(false) }
    var notePendingDelete by remember { mutableStateOf<MoneyNote?>(null) }

    val baseList = if (selectedTab == 0) activeNotes else archivedNotes

    val allAvailableTags = remember(baseList) {
        baseList.flatMap { NoteUtils.parseTags(it.tagsJson) }.distinct().sorted()
    }

    val filteredNotes = remember(baseList, searchQuery, selectedTagFilter) {
        baseList.filter { note ->
            val matchesSearch = NoteUtils.matchesQuery(note, searchQuery)
            val matchesTag = selectedTagFilter == null ||
                    NoteUtils.parseTags(note.tagsJson).any { it.equals(selectedTagFilter, ignoreCase = true) }
            matchesSearch && matchesTag
        }.sortedWith(
            compareByDescending<MoneyNote> { it.isPinned }
                .thenByDescending { it.updatedAt }
                .thenByDescending { it.timestamp }
        )
    }

    val pinnedNotes = remember(filteredNotes) { filteredNotes.filter { it.isPinned } }
    val unpinnedNotes = remember(filteredNotes) { filteredNotes.filter { !it.isPinned } }

    if (showNoteEditor) {
        NoteEditorDialog(
            initialNote = editingNote,
            transactions = transactions,
            people = people,
            tasks = tasks,
            onAutoSave = { updatedNote, onSaved ->
                viewModel.saveOrUpdateNote(updatedNote, onSaved)
            },
            onDelete = { noteToDelete ->
                showNoteEditor = false
                notePendingDelete = noteToDelete
            },
            onDismiss = {
                showNoteEditor = false
                editingNote = null
            }
        )
    }

    if (notePendingDelete != null) {
        AlertDialog(
            onDismissRequest = { notePendingDelete = null },
            title = { Text("Delete Note?") },
            text = {
                Text("Are you sure you want to permanently delete \"${notePendingDelete?.title?.ifBlank { "Untitled Note" }}\"?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        notePendingDelete?.let { viewModel.deleteNote(it.id) }
                        notePendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_note_btn")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { notePendingDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {
                    Column {
                        Text("Notes & Checklists", fontWeight = FontWeight.Bold)
                        Text(
                            "${activeNotes.size} active • ${archivedNotes.size} archived",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.testTag("notes_back_btn")
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { navController.navigate("ai_assistant?tab=4") },
                        modifier = Modifier.testTag("notes_ai_helper_btn")
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = "AI Notes Helper (Summarize, Grammar, Tasks)",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = { isGridView = !isGridView },
                        modifier = Modifier.testTag("notes_toggle_view_btn")
                    ) {
                        Icon(
                            if (isGridView) Icons.Default.ViewAgenda else Icons.Default.GridView,
                            contentDescription = if (isGridView) "Switch to List View" else "Switch to Grid View"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    editingNote = null
                    showNoteEditor = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New Note") },
                modifier = Modifier.testTag("notes_fab_add")
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search notes, tags, checklists, attachments...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("notes_search_input")
            )

            Spacer(Modifier.height(10.dp))

            // Active / Archived Tabs + Tag filters
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    label = { Text("Active (${activeNotes.size})") },
                    leadingIcon = { Icon(Icons.Default.StickyNote2, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("notes_tab_active")
                )
                FilterChip(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    label = { Text("Archived (${archivedNotes.size})") },
                    leadingIcon = { Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("notes_tab_archived")
                )

                allAvailableTags.forEach { tag ->
                    val isTagSelected = selectedTagFilter.equals(tag, ignoreCase = true)
                    FilterChip(
                        selected = isTagSelected,
                        onClick = { selectedTagFilter = if (isTagSelected) null else tag },
                        label = { Text("#$tag") }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            if (filteredNotes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.Notes,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                        Text(
                            if (selectedTab == 1) "No archived notes" else "No notes yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Capture rich financial notes, checklists, tax ideas, or attach notes to transactions, people, and tasks.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (isGridView) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 90.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (pinnedNotes.isNotEmpty()) {
                        item(span = { GridItemSpan(2) }) {
                            Text(
                                "PINNED",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        items(pinnedNotes, key = { it.id }) { note ->
                            NoteCardItem(
                                note = note,
                                onClick = {
                                    editingNote = note
                                    showNoteEditor = true
                                },
                                onTogglePin = { viewModel.toggleNotePin(note) },
                                onToggleArchive = { viewModel.toggleNoteArchive(note) },
                                onToggleChecklist = { itemId -> viewModel.toggleNoteChecklistItem(note, itemId) },
                                onDelete = { notePendingDelete = note }
                            )
                        }
                    }

                    if (unpinnedNotes.isNotEmpty()) {
                        if (pinnedNotes.isNotEmpty()) {
                            item(span = { GridItemSpan(2) }) {
                                Text(
                                    "RECENT",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }
                        }
                        items(unpinnedNotes, key = { it.id }) { note ->
                            NoteCardItem(
                                note = note,
                                onClick = {
                                    editingNote = note
                                    showNoteEditor = true
                                },
                                onTogglePin = { viewModel.toggleNotePin(note) },
                                onToggleArchive = { viewModel.toggleNoteArchive(note) },
                                onToggleChecklist = { itemId -> viewModel.toggleNoteChecklistItem(note, itemId) },
                                onDelete = { notePendingDelete = note }
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 90.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (pinnedNotes.isNotEmpty()) {
                        item {
                            Text(
                                "PINNED",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        items(pinnedNotes, key = { it.id }) { note ->
                            NoteCardItem(
                                note = note,
                                onClick = {
                                    editingNote = note
                                    showNoteEditor = true
                                },
                                onTogglePin = { viewModel.toggleNotePin(note) },
                                onToggleArchive = { viewModel.toggleNoteArchive(note) },
                                onToggleChecklist = { itemId -> viewModel.toggleNoteChecklistItem(note, itemId) },
                                onDelete = { notePendingDelete = note }
                            )
                        }
                    }

                    if (unpinnedNotes.isNotEmpty()) {
                        if (pinnedNotes.isNotEmpty()) {
                            item {
                                Text(
                                    "RECENT",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }
                        }
                        items(unpinnedNotes, key = { it.id }) { note ->
                            NoteCardItem(
                                note = note,
                                onClick = {
                                    editingNote = note
                                    showNoteEditor = true
                                },
                                onTogglePin = { viewModel.toggleNotePin(note) },
                                onToggleArchive = { viewModel.toggleNoteArchive(note) },
                                onToggleChecklist = { itemId -> viewModel.toggleNoteChecklistItem(note, itemId) },
                                onDelete = { notePendingDelete = note }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoteCardItem(
    note: MoneyNote,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleArchive: () -> Unit,
    onToggleChecklist: (String) -> Unit,
    onDelete: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val cardBg = if (note.colorHex == "#DEFAULT") {
        MaterialTheme.colorScheme.surface
    } else {
        NoteUtils.getNoteColor(note.colorHex, isDark)
    }

    val tags = remember(note.tagsJson) { NoteUtils.parseTags(note.tagsJson) }
    val checklists = remember(note.checklistsJson) { NoteUtils.parseChecklist(note.checklistsJson) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("note_card_${note.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(
            width = if (note.isPinned) 1.5.dp else 1.dp,
            color = if (note.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header row: Title + Pin
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = note.title.ifBlank { "Untitled" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onTogglePin,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.PushPin,
                        contentDescription = "Pin",
                        modifier = Modifier.size(16.dp),
                        tint = if (note.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }

            // Markdown / Rich Text Content
            if (note.content.isNotBlank()) {
                RichMarkdownText(
                    markdown = note.content.take(240),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Checklist preview
            if (checklists.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    checklists.take(4).forEach { item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleChecklist(item.id) }
                        ) {
                            Icon(
                                if (item.isChecked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (item.isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = item.text,
                                style = MaterialTheme.typography.bodySmall,
                                textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (checklists.size > 4) {
                        Text(
                            "+${checklists.size - 4} more items",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Attachment badge
            if (!note.attachedType.isNullOrBlank() && !note.attachedTitle.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.AttachFile,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "${note.attachedType}: ${note.attachedTitle}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Tags
            if (tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    tags.forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = "#$tag",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Footer: Date + Archive/Delete actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = FormatUtils.formatDate(note.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onToggleArchive,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            if (note.isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                            contentDescription = "Archive",
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}

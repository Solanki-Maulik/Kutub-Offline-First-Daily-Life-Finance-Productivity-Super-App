package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.data.Account
import com.example.data.Category
import com.example.data.MoneyNote
import com.example.data.Transaction
import com.example.utils.*
import com.example.viewmodel.FinanceViewModel

enum class AiAssistantTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    SMART_ENTRY("Smart Entry", Icons.Default.Bolt),
    RECEIPT_SCAN("Receipt Scan", Icons.Default.DocumentScanner),
    ASK_DATA("Ask My Data", Icons.Default.QuestionAnswer),
    INSIGHTS("Monthly Insights", Icons.Default.Insights),
    NOTES_HELPER("Notes Helper", Icons.Default.EditNote),
    TASK_HELPER("Task Helper", Icons.Default.TaskAlt)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantScreen(
    viewModel: FinanceViewModel,
    navController: NavController,
    initialTab: Int = 0
) {
    var selectedTab by remember(initialTab) {
        mutableStateOf(AiAssistantTab.entries.getOrElse(initialTab) { AiAssistantTab.SMART_ENTRY })
    }
    var showPrivacyPayloadDialog by remember { mutableStateOf(false) }

    if (showPrivacyPayloadDialog) {
        val previewJson = remember { viewModel.getComputedFinancialSummaryPreview() }
        AlertDialog(
            onDismissRequest = { showPrivacyPayloadDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.PrivacyTip, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Minimum-Data Privacy Summary", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "MoneyMate never sends your full database or personal files to Gemini. Only compact pre-computed summaries or the single text you type are shared:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                    ) {
                        LazyColumn(contentPadding = PaddingValues(12.dp)) {
                            item {
                                Text(
                                    text = previewJson,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyPayloadDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.testTag("ai_assistant_back_btn")
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                "Gemini AI Assistant",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "Fast AI Data Entry & Financial Helpers",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showPrivacyPayloadDialog = true },
                        modifier = Modifier.testTag("ai_privacy_payload_btn")
                    ) {
                        Icon(Icons.Default.Shield, contentDescription = "View Minimum Data Sent to AI")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)
        ) {
            // Feature Selector Strip
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AiAssistantTab.entries.forEach { tab ->
                        val isSelected = selectedTab == tab
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedTab = tab },
                            label = {
                                Text(
                                    tab.title,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            leadingIcon = {
                                Icon(tab.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            modifier = Modifier.testTag("ai_tab_${tab.name.lowercase()}")
                        )
                    }
                }
            }

            // Active AI Feature Content
            item {
                when (selectedTab) {
                    AiAssistantTab.SMART_ENTRY -> SmartEntryHelperSection(viewModel = viewModel)
                    AiAssistantTab.RECEIPT_SCAN -> ReceiptScanHelperSection(viewModel = viewModel)
                    AiAssistantTab.ASK_DATA -> AskMyDataHelperSection(
                        viewModel = viewModel,
                        onShowSummaryPayload = { showPrivacyPayloadDialog = true }
                    )
                    AiAssistantTab.INSIGHTS -> MonthlyInsightsHelperSection(viewModel = viewModel)
                    AiAssistantTab.NOTES_HELPER -> NotesAiHelperSection(viewModel = viewModel)
                    AiAssistantTab.TASK_HELPER -> TaskAiHelperSection(viewModel = viewModel)
                }
            }
        }
    }
}

// ============================================================================
// Shared Components: "AI can make mistakes" Banner & Error State Card
// ============================================================================

@Composable
fun AiMistakesNoticeBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("ai_mistakes_notice_banner"),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = "AI Disclaimer",
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = AI_MISTAKES_DISCLAIMER,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun AiErrorStateCard(
    errorState: AiUiState.Error,
    onRetry: () -> Unit,
    onDismiss: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ai_error_state_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = when {
                        errorState.isOffline -> Icons.Default.WifiOff
                        errorState.isQuotaExceeded -> Icons.Default.HourglassBottom
                        else -> Icons.Default.ErrorOutline
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = when {
                        errorState.isOffline -> "Offline — Local Data Safe"
                        errorState.isQuotaExceeded -> "AI Quota / Rate Limit Reached"
                        else -> "AI Request Could Not Complete"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Text(
                text = errorState.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("ai_retry_btn")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Try Again")
                }
                if (onDismiss != null) {
                    TextButton(onClick = onDismiss) {
                        Text("Dismiss", color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }
    }
}

// ============================================================================
// 1. SMART ENTRY: Natural Language -> Structured JSON + Confirmation Screen
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartEntryHelperSection(
    viewModel: FinanceViewModel,
    onSavedCallback: ((Int) -> Unit)? = null
) {
    val smartState by viewModel.smartEntryState.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()

    var inputText by remember {
        mutableStateOf("chai 20 cash, petrol 300 bank, got 5000 from Raj")
    }
    var editableEntries by remember { mutableStateOf<List<SmartParsedEntry>>(emptyList()) }
    var savedBannerCount by remember { mutableStateOf<Int?>(null) }
    var savedBannerMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(smartState) {
        val current = smartState
        if (current is AiUiState.Success) {
            editableEntries = current.data.entries
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Bolt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("AI Smart Data Entry", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Add Expenses, Income, Transfers, Udhaar, Tasks, Schedule, Habits & Notes in plain words.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Quick Sample Prompts covering all modules
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val examples = listOf(
                    "chai 20 cash, petrol 300 bank, got 5000 from Raj",
                    "task pay electricity bill, habit morning walk",
                    "schedule client meeting 4pm, note tax receipt saved",
                    "salary 50000 bank, transfer 5000 bank to cash"
                )
                examples.forEach { sample ->
                    AssistChip(
                        onClick = {
                            inputText = sample
                            savedBannerCount = null
                            savedBannerMessage = null
                        },
                        label = { Text(sample, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                }
            }

            OutlinedTextField(
                value = inputText,
                onValueChange = {
                    inputText = it
                    savedBannerCount = null
                    savedBannerMessage = null
                },
                label = { Text("Type expense, income, udhaar, task, habit, schedule or note") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("smart_entry_input"),
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(14.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        savedBannerCount = null
                        savedBannerMessage = null
                        viewModel.quickSaveSmartEntryWithAi(inputText) { count, msg ->
                            savedBannerCount = count
                            savedBannerMessage = msg
                            if (count > 0) {
                                onSavedCallback?.invoke(count)
                            }
                        }
                    },
                    enabled = smartState !is AiUiState.Loading && inputText.isNotBlank(),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("smart_entry_quick_save_btn"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (smartState is AiUiState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Saving...", maxLines = 1)
                    } else {
                        Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("AI Save Now", fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }

                OutlinedButton(
                    onClick = {
                        savedBannerCount = null
                        savedBannerMessage = null
                        viewModel.parseSmartEntryWithAi(inputText)
                    },
                    enabled = smartState !is AiUiState.Loading && inputText.isNotBlank(),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("smart_entry_parse_btn"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Review & Edit", fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }

            if (savedBannerCount != null || savedBannerMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            savedBannerMessage ?: "Confirmed and saved ${savedBannerCount ?: 0} item(s)!",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            when (val st = smartState) {
                is AiUiState.Error -> {
                    AiErrorStateCard(
                        errorState = st,
                        onRetry = { viewModel.parseSmartEntryWithAi(inputText) },
                        onDismiss = { viewModel.resetSmartEntryState() }
                    )
                }
                is AiUiState.Success -> {
                    // Confirmation Screen Before Saving
                    AiMistakesNoticeBanner()

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("smart_entry_confirmation_box")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Confirm Parsed Entries (${editableEntries.count { it.selected }})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        "Review or edit fields below before saving.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = { viewModel.resetSmartEntryState() }) {
                                    Text("Cancel")
                                }
                            }

                            editableEntries.forEachIndexed { index, item ->
                                SmartEntryConfirmationCard(
                                    entry = item,
                                    accounts = accounts,
                                    categories = categories,
                                    onUpdate = { updated ->
                                        editableEntries = editableEntries.toMutableList().also { it[index] = updated }
                                    }
                                )
                            }

                            val selectedCount = editableEntries.count { it.isReadyToSave }
                            Button(
                                onClick = {
                                    viewModel.confirmAndSaveSmartEntries(editableEntries) { count ->
                                        savedBannerCount = count
                                        savedBannerMessage = "Confirmed and saved $count item(s)!"
                                        onSavedCallback?.invoke(count)
                                    }
                                },
                                enabled = selectedCount > 0,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("smart_entry_confirm_save_btn"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Confirm & Save $selectedCount Entr${if (selectedCount == 1) "y" else "ies"}", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                else -> {}
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmartEntryConfirmationCard(
    entry: SmartParsedEntry,
    accounts: List<Account>,
    categories: List<Category>,
    onUpdate: (SmartParsedEntry) -> Unit
) {
    var amountText by remember(entry.id, entry.amount) {
        mutableStateOf(if (entry.amount % 1.0 == 0.0) entry.amount.toLong().toString() else entry.amount.toString())
    }
    val kindTypes = listOf("EXPENSE", "INCOME", "UDHAAR_GOT", "UDHAAR_GAVE", "TRANSFER", "TASK", "SCHEDULE", "HABIT", "NOTE")

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.dp,
            if (entry.selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Checkbox(
                        checked = entry.selected,
                        onCheckedChange = { onUpdate(entry.copy(selected = it)) }
                    )
                    Text(
                        text = entry.note.ifBlank { entry.category },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Badge(
                    containerColor = when (entry.entryKind) {
                        "INCOME", "UDHAAR_GOT" -> MaterialTheme.colorScheme.primaryContainer
                        "TASK", "SCHEDULE", "HABIT", "NOTE" -> MaterialTheme.colorScheme.tertiaryContainer
                        else -> MaterialTheme.colorScheme.secondaryContainer
                    }
                ) {
                    Text(
                        text = entry.entryKind.replace("_", " "),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            if (entry.validationWarning != null) {
                Text(
                    text = entry.validationWarning,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // Type selector chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                kindTypes.forEach { k ->
                    FilterChip(
                        selected = entry.entryKind == k,
                        onClick = { onUpdate(entry.copy(entryKind = k)) },
                        label = { Text(k.replace("_", " "), style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            OutlinedTextField(
                value = entry.note,
                onValueChange = { onUpdate(entry.copy(note = it)) },
                label = { Text(if (entry.requiresAmount) "Description / Note" else "Title / Details") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (entry.requiresAmount) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { newVal ->
                            amountText = newVal
                            val parsed = newVal.toDoubleOrNull() ?: 0.0
                            onUpdate(entry.copy(amount = parsed))
                        },
                        label = { Text("Amount (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = entry.category,
                        onValueChange = { onUpdate(entry.copy(category = it)) },
                        label = { Text("Category") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = entry.account,
                        onValueChange = { onUpdate(entry.copy(account = it)) },
                        label = { Text("Account") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = entry.dateString,
                        onValueChange = {
                            val (_, ts) = AiDataSummaryBuilder.parseDdMmYyyyOrToday(it, entry.timestamp)
                            onUpdate(entry.copy(dateString = it, timestamp = ts))
                        },
                        label = { Text("Date (DD/MM/YYYY)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = entry.category,
                        onValueChange = { onUpdate(entry.copy(category = it)) },
                        label = { Text("Category") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = entry.dateString,
                        onValueChange = {
                            val (_, ts) = AiDataSummaryBuilder.parseDdMmYyyyOrToday(it, entry.timestamp)
                            onUpdate(entry.copy(dateString = it, timestamp = ts))
                        },
                        label = { Text("Date (DD/MM/YYYY)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (entry.entryKind in setOf("UDHAAR_GOT", "UDHAAR_GAVE") || entry.personName.isNotBlank()) {
                OutlinedTextField(
                    value = entry.personName,
                    onValueChange = { onUpdate(entry.copy(personName = it)) },
                    label = { Text("Person Name (for People & Udhaar Ledger)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Quick account chips from user's actual accounts
            if (entry.requiresAmount && accounts.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Account:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    accounts.forEach { acc ->
                        SuggestionChip(
                            onClick = { onUpdate(entry.copy(account = acc.name)) },
                            label = { Text(acc.name, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// 2. RECEIPT / BILL SCAN: Upload Photo -> Extract Amount, Date, Shop, Category
// ============================================================================

@Composable
fun ReceiptScanHelperSection(
    viewModel: FinanceViewModel,
    onPrefillExpense: ((ReceiptScanResult, String?) -> Unit)? = null
) {
    val receiptState by viewModel.receiptScanState.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()

    var selectedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var editAmount by remember { mutableStateOf("") }
    var editShop by remember { mutableStateOf("") }
    var editCategory by remember { mutableStateOf("Food") }
    var editDate by remember { mutableStateOf(AiDataSummaryBuilder.todayDateDdMmYyyy()) }
    var editAccount by remember { mutableStateOf(accounts.firstOrNull()?.name ?: "UPI Wallet") }
    var editPaymentMethod by remember { mutableStateOf("UPI") }
    var editItemsSummary by remember { mutableStateOf("") }
    var receiptSavedBanner by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedPhotoUri = uri
            receiptSavedBanner = false
            viewModel.scanReceiptWithAi(uri)
        }
    }

    LaunchedEffect(receiptState) {
        val current = receiptState
        if (current is AiUiState.Success) {
            val res = current.data
            editAmount = if (res.amount % 1.0 == 0.0) res.amount.toLong().toString() else res.amount.toString()
            editShop = res.shopName
            editCategory = res.category
            editDate = res.dateString
            editPaymentMethod = res.paymentMethod
            editItemsSummary = res.itemsSummary
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.DocumentScanner,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Receipt & Bill Scanner", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Upload a bill photo to automatically extract amount, date, merchant & category.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                enabled = receiptState !is AiUiState.Loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("receipt_scan_pick_photo_btn"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (selectedPhotoUri == null) "Select Receipt / Bill Photo" else "Choose Another Receipt Photo",
                    fontWeight = FontWeight.Bold
                )
            }

            if (selectedPhotoUri != null) {
                AsyncImage(
                    model = selectedPhotoUri,
                    contentDescription = "Selected Receipt Preview",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
            }

            if (receiptState is AiUiState.Loading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Extracting bill details with Gemini Vision...")
                }
            }

            if (receiptSavedBanner) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            "Receipt expense saved to your ledger!",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            when (val st = receiptState) {
                is AiUiState.Error -> {
                    AiErrorStateCard(
                        errorState = st,
                        onRetry = { selectedPhotoUri?.let { viewModel.scanReceiptWithAi(it) } },
                        onDismiss = { viewModel.resetReceiptScanState() }
                    )
                }
                is AiUiState.Success -> {
                    AiMistakesNoticeBanner()

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                "Confirm Extracted Receipt Details",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold
                            )
                            if (st.data.confidenceNote.isNotBlank()) {
                                Text(
                                    text = st.data.confidenceNote,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = editAmount,
                                    onValueChange = { editAmount = it },
                                    label = { Text("Total Amount (₹)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = editDate,
                                    onValueChange = { editDate = it },
                                    label = { Text("Date (DD/MM/YYYY)") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = editShop,
                                    onValueChange = { editShop = it },
                                    label = { Text("Shop / Merchant") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = editCategory,
                                    onValueChange = { editCategory = it },
                                    label = { Text("Category") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            OutlinedTextField(
                                value = editItemsSummary,
                                onValueChange = { editItemsSummary = it },
                                label = { Text("Items / Notes") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (accounts.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Account:", style = MaterialTheme.typography.labelSmall)
                                    accounts.forEach { acc ->
                                        FilterChip(
                                            selected = editAccount == acc.name,
                                            onClick = { editAccount = acc.name },
                                            label = { Text(acc.name, style = MaterialTheme.typography.labelSmall) }
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (onPrefillExpense != null) {
                                    OutlinedButton(
                                        onClick = {
                                            val (_, ts) = AiDataSummaryBuilder.parseDdMmYyyyOrToday(editDate)
                                            onPrefillExpense(
                                                st.data.copy(
                                                    amount = editAmount.toDoubleOrNull() ?: st.data.amount,
                                                    dateString = editDate,
                                                    timestamp = ts,
                                                    shopName = editShop,
                                                    category = editCategory,
                                                    paymentMethod = editPaymentMethod,
                                                    itemsSummary = editItemsSummary
                                                ),
                                                selectedPhotoUri?.toString()
                                            )
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Fill Form")
                                    }
                                }
                                Button(
                                    onClick = {
                                        val finalAmt = editAmount.toDoubleOrNull() ?: 0.0
                                        if (finalAmt > 0.0) {
                                            val (_, ts) = AiDataSummaryBuilder.parseDdMmYyyyOrToday(editDate)
                                            val noteStr = buildString {
                                                append(editShop.ifBlank { "Receipt" })
                                                if (editItemsSummary.isNotBlank()) {
                                                    append(" - ")
                                                    append(editItemsSummary)
                                                }
                                            }
                                            viewModel.restoreTransaction(
                                                Transaction(
                                                    amount = finalAmt,
                                                    isIncome = false,
                                                    category = editCategory.ifBlank { "Shopping" },
                                                    account = editAccount.ifBlank { accounts.firstOrNull()?.name ?: "Cash" },
                                                    paymentMethod = editPaymentMethod,
                                                    note = noteStr,
                                                    photoUri = selectedPhotoUri?.toString(),
                                                    timestamp = ts
                                                )
                                            )
                                            receiptSavedBanner = true
                                            viewModel.resetReceiptScanState()
                                        }
                                    },
                                    enabled = (editAmount.toDoubleOrNull() ?: 0.0) > 0.0,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("receipt_scan_confirm_save_btn")
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Confirm & Save Expense", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                else -> {}
            }
        }
    }
}

// ============================================================================
// 3. ASK MY DATA: Answers strictly using computed summaries, not guesses
// ============================================================================

@Composable
fun AskMyDataHelperSection(
    viewModel: FinanceViewModel,
    onShowSummaryPayload: () -> Unit
) {
    val askState by viewModel.askMyDataState.collectAsStateWithLifecycle()
    var questionText by remember { mutableStateOf("How much did I spend on food last month?") }

    val quickQuestions = listOf(
        "How much did I spend on food last month?",
        "Compare this month vs last month spending",
        "What are my top expense categories this month?",
        "How much Udhaar money is owed to me?",
        "Which budgets are close to their limit?"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.QuestionAnswer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Column {
                        Text("Ask My Data", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Answers using pre-computed summaries only — never guesses or sends your full DB.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                TextButton(onClick = onShowSummaryPayload) {
                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Summary Sent", style = MaterialTheme.typography.labelSmall)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickQuestions.forEach { q ->
                    AssistChip(
                        onClick = {
                            questionText = q
                            viewModel.askMyDataWithAi(q)
                        },
                        label = { Text(q) }
                    )
                }
            }

            OutlinedTextField(
                value = questionText,
                onValueChange = { questionText = it },
                label = { Text("Ask a question about your spending, budgets, or balances") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ask_my_data_input"),
                shape = RoundedCornerShape(14.dp)
            )

            Button(
                onClick = { viewModel.askMyDataWithAi(questionText) },
                enabled = askState !is AiUiState.Loading && questionText.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ask_my_data_submit_btn"),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (askState is AiUiState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Checking Computed Summaries...")
                } else {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Ask Gemini", fontWeight = FontWeight.Bold)
                }
            }

            when (val st = askState) {
                is AiUiState.Error -> {
                    AiErrorStateCard(
                        errorState = st,
                        onRetry = { viewModel.askMyDataWithAi(questionText) }
                    )
                }
                is AiUiState.Success -> {
                    AiMistakesNoticeBanner()

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ask_my_data_answer_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = st.data.answer,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )

                            if (st.data.figuresCited.isNotEmpty()) {
                                HorizontalDivider()
                                Text(
                                    "Computed Figures Cited:",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                st.data.figuresCited.forEach { fig ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Verified,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(fig, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }

                            if (st.data.followUpSuggestions.isNotEmpty()) {
                                HorizontalDivider()
                                Text(
                                    "Follow-up Questions:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    st.data.followUpSuggestions.forEach { followUp ->
                                        SuggestionChip(
                                            onClick = {
                                                questionText = followUp
                                                viewModel.askMyDataWithAi(followUp)
                                            },
                                            label = { Text(followUp, style = MaterialTheme.typography.labelSmall) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {}
            }
        }
    }
}

// ============================================================================
// 4. MONTHLY INSIGHTS: Patterns, Unusual Expenses, Saving Tips, Budget Warnings
// ============================================================================

@Composable
fun MonthlyInsightsHelperSection(viewModel: FinanceViewModel) {
    val insightsState by viewModel.monthlyInsightsState.collectAsStateWithLifecycle()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Insights,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("AI Monthly Insights", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Spending patterns, unusual expenses, saving tips & budget warnings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = { viewModel.generateMonthlyInsightsWithAi() },
                enabled = insightsState !is AiUiState.Loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("generate_monthly_insights_btn"),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (insightsState is AiUiState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Generating Monthly Insights...")
                } else {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Analyze This Month's Finances", fontWeight = FontWeight.Bold)
                }
            }

            when (val st = insightsState) {
                is AiUiState.Error -> {
                    AiErrorStateCard(
                        errorState = st,
                        onRetry = { viewModel.generateMonthlyInsightsWithAi() }
                    )
                }
                is AiUiState.Success -> {
                    val data = st.data
                    AiMistakesNoticeBanner()

                    // Headline + Score Card
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(54.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        "${data.healthScore}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Monthly Financial Health Score",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = data.headlineSummary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    InsightCategoryBox(
                        title = "Spending Patterns",
                        icon = Icons.Default.TrendingUp,
                        items = data.spendingPatterns
                    )
                    InsightCategoryBox(
                        title = "Unusual Expenses & Spikes",
                        icon = Icons.Default.WarningAmber,
                        items = data.unusualExpenses
                    )
                    InsightCategoryBox(
                        title = "Actionable Saving Tips",
                        icon = Icons.Default.Lightbulb,
                        items = data.savingTips
                    )
                    InsightCategoryBox(
                        title = "Budget & Bill Warnings",
                        icon = Icons.Default.NotificationImportant,
                        items = data.budgetWarnings
                    )
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun InsightCategoryBox(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    items: List<String>
) {
    if (items.isEmpty()) return
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            items.forEach { bullet ->
                Row(
                    modifier = Modifier.padding(start = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("•", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(bullet, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

// ============================================================================
// 5. NOTES HELPER: Summarize, Fix Grammar, Turn a Note into Tasks
// ============================================================================

@Composable
fun NotesAiHelperSection(
    viewModel: FinanceViewModel,
    initialTitle: String = "",
    initialContent: String = "",
    onApplyTitleAndContent: ((String, String) -> Unit)? = null
) {
    val noteHelperState by viewModel.noteHelperState.collectAsStateWithLifecycle()
    val activeNotes by viewModel.activeNotes.collectAsStateWithLifecycle()

    var noteTitle by remember(initialTitle) { mutableStateOf(initialTitle) }
    var noteContent by remember(initialContent) {
        mutableStateOf(
            initialContent.ifBlank {
                "Meeting notes: Need to pay broadband bill 999 by tomorrow, renew car insurance next week, and buy vegetables on the way home."
            }
        )
    }
    var editableTasks by remember { mutableStateOf<List<AiSuggestedTask>>(emptyList()) }
    var createdTasksCount by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(noteHelperState) {
        val current = noteHelperState
        if (current is AiUiState.Success) {
            editableTasks = current.data.extractedTasks
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.EditNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("AI Notes Helper", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Summarize notes, fix grammar & turn action items into Tasks.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Pick from existing notes if available
            if (activeNotes.isNotEmpty() && onApplyTitleAndContent == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Load Note:", style = MaterialTheme.typography.labelSmall)
                    activeNotes.take(6).forEach { existingNote ->
                        AssistChip(
                            onClick = {
                                noteTitle = existingNote.title
                                noteContent = existingNote.content
                            },
                            label = {
                                Text(
                                    existingNote.title.ifBlank { existingNote.content.take(20) },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = noteTitle,
                onValueChange = { noteTitle = it },
                label = { Text("Note Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = noteContent,
                onValueChange = { noteContent = it },
                label = { Text("Note Content") },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("note_helper_content_input"),
                shape = RoundedCornerShape(12.dp)
            )

            Button(
                onClick = {
                    createdTasksCount = null
                    viewModel.analyzeNoteWithAi(noteTitle, noteContent)
                },
                enabled = noteHelperState !is AiUiState.Loading && (noteTitle.isNotBlank() || noteContent.isNotBlank()),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("note_helper_analyze_btn"),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (noteHelperState is AiUiState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Summarizing & Extracting Tasks...")
                } else {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Summarize, Fix Grammar & Extract Tasks", fontWeight = FontWeight.Bold)
                }
            }

            if (createdTasksCount != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            "Added $createdTasksCount task(s) to your Tasks module!",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            when (val st = noteHelperState) {
                is AiUiState.Error -> {
                    AiErrorStateCard(
                        errorState = st,
                        onRetry = { viewModel.analyzeNoteWithAi(noteTitle, noteContent) },
                        onDismiss = { viewModel.resetNoteHelperState() }
                    )
                }
                is AiUiState.Success -> {
                    val res = st.data

                    // 1. Bullet Summary
                    InsightCategoryBox(
                        title = "Concise Summary",
                        icon = Icons.Default.Summarize,
                        items = res.summaryBullets
                    )

                    // 2. Grammar-Fixed Version
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
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
                                Text("Polished & Grammar-Fixed Text", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                TextButton(
                                    onClick = {
                                        noteTitle = res.grammarFixedTitle.ifBlank { noteTitle }
                                        noteContent = res.grammarFixedContent.ifBlank { noteContent }
                                        onApplyTitleAndContent?.invoke(noteTitle, noteContent)
                                    }
                                ) {
                                    Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (onApplyTitleAndContent != null) "Apply to Note" else "Use Polished Text")
                                }
                            }
                            if (res.grammarFixedTitle.isNotBlank()) {
                                Text(res.grammarFixedTitle, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            }
                            Text(res.grammarFixedContent, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    // 3. Turn Note into Tasks
                    if (editableTasks.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "Extracted Actionable Tasks (${editableTasks.count { it.selected }})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                editableTasks.forEachIndexed { idx, task ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Checkbox(
                                            checked = task.selected,
                                            onCheckedChange = { checked ->
                                                editableTasks = editableTasks.toMutableList().also {
                                                    it[idx] = task.copy(selected = checked)
                                                }
                                            }
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(task.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                            Text(
                                                "${task.priority} • ${task.category} • Due in ${task.dueDaysFromNow}d",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                Button(
                                    onClick = {
                                        viewModel.saveAiExtractedTasks(editableTasks) { count ->
                                            createdTasksCount = count
                                        }
                                    },
                                    enabled = editableTasks.any { it.selected },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("note_helper_create_tasks_btn")
                                ) {
                                    Icon(Icons.Default.AddTask, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Create Selected Tasks")
                                }
                            }
                        }
                    }
                }
                else -> {}
            }
        }
    }
}

// ============================================================================
// 6. TASK HELPER: Break a Goal into Subtasks and a Schedule
// ============================================================================

@Composable
fun TaskAiHelperSection(
    viewModel: FinanceViewModel,
    onSavedCallback: (() -> Unit)? = null
) {
    val taskHelperState by viewModel.taskHelperState.collectAsStateWithLifecycle()
    var goalInput by remember {
        mutableStateOf("File my income tax return and organize tax-saving receipts by this weekend")
    }
    var selectedScheduleIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var planSavedBanner by remember { mutableStateOf(false) }

    LaunchedEffect(taskHelperState) {
        val current = taskHelperState
        if (current is AiUiState.Success) {
            selectedScheduleIds = current.data.scheduleBlocks.map { it.id }.toSet()
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.TaskAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("AI Task & Schedule Planner", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Break any goal into step-by-step subtasks and calendar time blocks.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val sampleGoals = listOf(
                    "File income tax return by this weekend",
                    "Plan a 4-day family trip under ₹20,000",
                    "Monthly home deep-clean and bill audit"
                )
                sampleGoals.forEach { sample ->
                    AssistChip(
                        onClick = { goalInput = sample },
                        label = { Text(sample) }
                    )
                }
            }

            OutlinedTextField(
                value = goalInput,
                onValueChange = {
                    goalInput = it
                    planSavedBanner = false
                },
                label = { Text("Enter your goal or project") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("task_helper_goal_input"),
                shape = RoundedCornerShape(14.dp)
            )

            Button(
                onClick = {
                    planSavedBanner = false
                    viewModel.breakGoalWithAi(goalInput)
                },
                enabled = taskHelperState !is AiUiState.Loading && goalInput.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("task_helper_generate_btn"),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (taskHelperState is AiUiState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Building Subtasks & Schedule...")
                } else {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Break Goal into Subtasks & Schedule", fontWeight = FontWeight.Bold)
                }
            }

            if (planSavedBanner) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            "Saved task with subtasks and scheduled calendar events!",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            when (val st = taskHelperState) {
                is AiUiState.Error -> {
                    AiErrorStateCard(
                        errorState = st,
                        onRetry = { viewModel.breakGoalWithAi(goalInput) },
                        onDismiss = { viewModel.resetTaskHelperState() }
                    )
                }
                is AiUiState.Success -> {
                    val res = st.data
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(res.goalTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                                Badge {
                                    Text("${res.priority} • ${res.category}", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                            if (res.strategySummary.isNotBlank()) {
                                Text(
                                    res.strategySummary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            HorizontalDivider()
                            Text("Subtasks (${res.subtasks.size}):", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            res.subtasks.forEachIndexed { idx, sub ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${idx + 1}.", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Text(sub, style = MaterialTheme.typography.bodySmall)
                                }
                            }

                            if (res.scheduleBlocks.isNotEmpty()) {
                                HorizontalDivider()
                                Text(
                                    "Suggested Schedule Blocks (${selectedScheduleIds.size} selected):",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                res.scheduleBlocks.forEach { block ->
                                    val isChecked = block.id in selectedScheduleIds
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = { checked ->
                                                selectedScheduleIds = if (checked) {
                                                    selectedScheduleIds + block.id
                                                } else {
                                                    selectedScheduleIds - block.id
                                                }
                                            }
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(block.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                            val dayLabel = when (block.dayOffset) {
                                                0 -> "Today"
                                                1 -> "Tomorrow"
                                                else -> "In ${block.dayOffset} days"
                                            }
                                            Text(
                                                "$dayLabel • ${block.startHour}:00 (${block.durationMinutes} mins) — ${block.notes}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            Button(
                                onClick = {
                                    viewModel.saveAiGoalPlan(res, selectedScheduleIds) {
                                        planSavedBanner = true
                                        onSavedCallback?.invoke()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("task_helper_save_plan_btn"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.EventAvailable, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Save Task + Subtasks & Schedule", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                else -> {}
            }
        }
    }
}

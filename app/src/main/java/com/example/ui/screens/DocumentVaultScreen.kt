package com.example.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.data.Transaction
import com.example.data.VaultDocument
import com.example.data.VaultStorageSummary
import com.example.utils.DocumentVaultUtils
import com.example.utils.FormatUtils
import com.example.viewmodel.FinanceViewModel
import kotlinx.coroutines.launch
import java.io.File

private fun parseTagsCsv(csv: String): List<String> =
    csv.split(",").map { it.trim() }.filter { it.isNotEmpty() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentVaultScreen(
    viewModel: FinanceViewModel,
    navController: NavController
) {
    val vaultDocuments by viewModel.vaultDocuments.collectAsStateWithLifecycle()
    val storageSummary by viewModel.vaultStorageSummary.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var selectedFilterMode by remember { mutableStateOf("ALL") } // ALL, EXPIRING_SOON, WARRANTIES, SENSITIVE
    var selectedTag by remember { mutableStateOf<String?>(null) }

    var isSensitiveUnlocked by remember { mutableStateOf(false) }
    var showPinPromptForUnlock by remember { mutableStateOf(false) }
    var pendingDocAfterUnlock by remember { mutableStateOf<VaultDocument?>(null) }
    var showSetPinDialog by remember { mutableStateOf(false) }

    var showAddOrEditDialog by remember { mutableStateOf(false) }
    var editingDocument by remember { mutableStateOf<VaultDocument?>(null) }
    var viewingDocument by remember { mutableStateOf<VaultDocument?>(null) }

    val allTags = remember(vaultDocuments) {
        vaultDocuments
            .flatMap { parseTagsCsv(it.tagsCsv) }
            .distinct()
            .sorted()
    }

    val filteredDocs = remember(vaultDocuments, searchQuery, selectedCategory, selectedFilterMode, selectedTag) {
        vaultDocuments.filter { doc ->
            val matchesCategory = selectedCategory == "All" || doc.category.equals(selectedCategory, ignoreCase = true)
            val matchesTag = selectedTag == null || parseTagsCsv(doc.tagsCsv).any { it.equals(selectedTag, ignoreCase = true) }
            val daysToExp = DocumentVaultUtils.daysUntilExpiry(doc.expiryDate)
            val matchesMode = when (selectedFilterMode) {
                "EXPIRING_SOON" -> daysToExp != null && daysToExp <= 30L
                "WARRANTIES" -> doc.category == "Warranty" || doc.warrantyMonths > 0
                "SENSITIVE" -> doc.isSensitive
                else -> true
            }
            val q = searchQuery.trim().lowercase()
            val matchesQuery = q.isEmpty() ||
                doc.title.lowercase().contains(q) ||
                doc.category.lowercase().contains(q) ||
                doc.notes.lowercase().contains(q) ||
                doc.tagsCsv.lowercase().contains(q) ||
                (doc.linkedTransactionSummary?.lowercase()?.contains(q) == true)

            matchesCategory && matchesTag && matchesMode && matchesQuery
        }
    }

    if (showPinPromptForUnlock) {
        VaultPinDialog(
            title = "Enter App PIN",
            subtitle = "Sensitive documents (IDs, certificates, confidential records) are protected by your Vault PIN (default: 1234).",
            onDismiss = {
                showPinPromptForUnlock = false
                pendingDocAfterUnlock = null
            },
            onVerify = { pin ->
                if (viewModel.verifyVaultPin(pin)) {
                    isSensitiveUnlocked = true
                    showPinPromptForUnlock = false
                    pendingDocAfterUnlock?.let { viewingDocument = it }
                    pendingDocAfterUnlock = null
                    true
                } else {
                    false
                }
            }
        )
    }

    if (showSetPinDialog) {
        ChangeVaultPinDialog(
            currentPin = viewModel.getVaultPin(),
            onDismiss = { showSetPinDialog = false },
            onSaveNewPin = { newPin ->
                viewModel.setVaultPin(newPin)
                showSetPinDialog = false
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Updated Document Vault PIN")
                }
            }
        )
    }

    if (showAddOrEditDialog || editingDocument != null) {
        AddOrEditVaultDocumentDialog(
            initialDoc = editingDocument,
            transactions = transactions.filter { !it.isIncome && it.category != "Transfer" && it.category != "Udhaar" },
            onDismiss = {
                showAddOrEditDialog = false
                editingDocument = null
            },
            onSave = { doc ->
                viewModel.addOrUpdateVaultDocument(doc)
                showAddOrEditDialog = false
                editingDocument = null
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Saved \"${doc.title}\" locally to Document Vault")
                }
            }
        )
    }

    viewingDocument?.let { doc ->
        VaultDocumentDetailDialog(
            doc = doc,
            onDismiss = { viewingDocument = null },
            onEdit = {
                viewingDocument = null
                editingDocument = doc
            },
            onDelete = {
                viewModel.deleteVaultDocument(doc)
                viewingDocument = null
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Deleted \"${doc.title}\" and freed local file storage")
                }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text("Document Vault", fontWeight = FontWeight.Bold)
                        Text(
                            "100% Local On-Device Storage • Never Uploaded",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (isSensitiveUnlocked) {
                                isSensitiveUnlocked = false
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Sensitive documents locked")
                                }
                            } else {
                                showPinPromptForUnlock = true
                            }
                        },
                        modifier = Modifier.testTag("vault_lock_toggle_btn")
                    ) {
                        Icon(
                            if (isSensitiveUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                            contentDescription = "Toggle Vault PIN Lock",
                            tint = if (isSensitiveUnlocked) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { showSetPinDialog = true }) {
                        Icon(Icons.Default.Password, contentDescription = "Configure Vault PIN")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    editingDocument = null
                    showAddOrEditDialog = true
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add Document") },
                modifier = Modifier.testTag("document_vault_fab")
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
            item {
                VaultStorageCard(summary = storageSummary)
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search bills, warranties, IDs, tags, or linked purchase...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vault_search_bar")
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "ALL" to "All (${vaultDocuments.size})",
                            "EXPIRING_SOON" to "Expiring / Expired",
                            "WARRANTIES" to "Warranty Tracker",
                            "SENSITIVE" to "PIN Protected"
                        ).forEach { (mode, label) ->
                            FilterChip(
                                selected = selectedFilterMode == mode,
                                onClick = { selectedFilterMode = mode },
                                label = { Text(label) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        (listOf("All") + DocumentVaultUtils.DOCUMENT_CATEGORIES).forEach { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat },
                                label = { Text(cat) }
                            )
                        }
                    }

                    if (allTags.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            allTags.forEach { tag ->
                                AssistChip(
                                    onClick = {
                                        selectedTag = if (selectedTag == tag) null else tag
                                    },
                                    label = { Text("#$tag") },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = if (selectedTag == tag) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                    )
                                )
                            }
                        }
                    }
                }
            }

            if (filteredDocs.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Default.FolderSpecial,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(42.dp)
                            )
                            Text("No documents in vault yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "Store appliance warranties, invoices, IDs, insurance policies & certificates locally with 30/7/1 day expiry alerts.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(onClick = { showAddOrEditDialog = true }) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Add First Document")
                            }
                        }
                    }
                }
            } else {
                items(filteredDocs, key = { it.id }) { doc ->
                    VaultDocumentCard(
                        doc = doc,
                        isSensitiveUnlocked = isSensitiveUnlocked,
                        onClick = {
                            if (doc.isSensitive && !isSensitiveUnlocked) {
                                pendingDocAfterUnlock = doc
                                showPinPromptForUnlock = true
                            } else {
                                viewingDocument = doc
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun VaultStorageCard(summary: VaultStorageSummary) {
    val barColor = when {
        summary.usagePercent >= 100f -> Color(0xFFC62828)
        summary.isStorageWarning -> Color(0xFFEF6C00)
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("vault_storage_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.SdStorage, contentDescription = null, tint = barColor, modifier = Modifier.size(20.dp))
                    Text("Local Vault Storage", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                Text(
                    "${DocumentVaultUtils.formatFileSize(summary.totalBytesUsed)} / ${DocumentVaultUtils.formatFileSize(summary.maxRecommendedBytes)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = barColor
                )
            }

            LinearProgressIndicator(
                progress = { (summary.usagePercent / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = barColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            if (summary.isStorageWarning) {
                Text(
                    "Warning: Local vault storage is above 75% capacity. Consider removing old high-res PDFs.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFC62828),
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                "Images are auto-compressed (JPEG 75%). Included in app Backup/Restore (increases backup size by ${DocumentVaultUtils.formatFileSize(summary.totalBytesUsed)}). Never uploaded to any cloud.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun VaultDocumentCard(
    doc: VaultDocument,
    isSensitiveUnlocked: Boolean,
    onClick: () -> Unit
) {
    val locked = doc.isSensitive && !isSensitiveUnlocked
    val daysLeft = DocumentVaultUtils.daysUntilExpiry(doc.expiryDate)
    val isExpired = daysLeft != null && daysLeft < 0L
    val isExpiringSoon = daysLeft != null && daysLeft in 0L..30L

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("vault_doc_card_${doc.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            when {
                isExpired -> Color(0xFFC62828)
                isExpiringSoon -> Color(0xFFEF6C00)
                else -> MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            doc.category,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    if (doc.isSensitive) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF3E5F5)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    if (locked) Icons.Default.Lock else Icons.Default.LockOpen,
                                    contentDescription = null,
                                    tint = Color(0xFF6A1B9A),
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    if (locked) "PIN Locked" else "Unlocked",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF6A1B9A),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                if (daysLeft != null) {
                    val statusColor = when {
                        isExpired -> Color(0xFFC62828)
                        isExpiringSoon -> Color(0xFFE65100)
                        else -> Color(0xFF2E7D32)
                    }
                    val statusText = when {
                        isExpired -> "Expired ${-daysLeft}d ago"
                        daysLeft == 0L -> "Expires Today!"
                        isExpiringSoon -> "Expires in ${daysLeft}d"
                        else -> "Valid (${daysLeft}d left)"
                    }
                    Text(
                        statusText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
            }

            Text(
                if (locked) "•••••••••••• (${doc.category} Protected)" else doc.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (!locked) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Issued/Bought: ${FormatUtils.formatDate(doc.issueDate)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    doc.expiryDate?.let {
                        Text(
                            "Expires: ${FormatUtils.formatDate(it)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (!doc.linkedTransactionSummary.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(
                            "Linked Purchase: ${doc.linkedTransactionSummary}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                val tags = parseTagsCsv(doc.tagsCsv)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (tags.isNotEmpty()) {
                        Text(
                            tags.joinToString(" ") { "#$it" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }

                    if (!doc.mimeType.isNullOrBlank() && doc.fileSizeBytes > 0L) {
                        val typeLabel = if (doc.mimeType.contains("pdf")) "PDF" else "IMAGE"
                        Text(
                            "$typeLabel • ${DocumentVaultUtils.formatFileSize(doc.fileSizeBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Text(
                    "Tap and enter your 4-digit App PIN to view details & attachment.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddOrEditVaultDocumentDialog(
    initialDoc: VaultDocument?,
    transactions: List<Transaction>,
    onDismiss: () -> Unit,
    onSave: (VaultDocument) -> Unit
) {
    val context = LocalContext.current

    var title by remember { mutableStateOf(initialDoc?.title ?: "") }
    var category by remember { mutableStateOf(initialDoc?.category ?: "Warranty") }
    var warrantyMonthsText by remember { mutableStateOf((initialDoc?.warrantyMonths?.takeIf { it > 0 } ?: 12).toString()) }
    var hasExpiry by remember { mutableStateOf(initialDoc?.expiryDate != null || category == "Warranty" || category == "Insurance") }
    var notes by remember { mutableStateOf(initialDoc?.notes ?: "") }
    var tagsText by remember { mutableStateOf(initialDoc?.tagsCsv ?: "") }
    var isSensitive by remember { mutableStateOf(initialDoc?.isSensitive ?: (category == "ID")) }
    var reminderEnabled by remember { mutableStateOf(initialDoc?.reminderEnabled ?: true) }

    var selectedTransaction by remember {
        mutableStateOf(transactions.find { it.id.toLong() == initialDoc?.linkedTransactionId })
    }
    var showTxDropdown by remember { mutableStateOf(false) }

    var localFilePath by remember { mutableStateOf(initialDoc?.localFilePath) }
    var mimeType by remember { mutableStateOf(initialDoc?.mimeType) }
    var fileSizeBytes by remember { mutableStateOf(initialDoc?.fileSizeBytes ?: 0L) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val saved = DocumentVaultUtils.saveAttachmentLocally(context, uri, explicitMimeType = "image/jpeg")
            if (saved != null) {
                localFilePath = saved.first
                mimeType = saved.second
                fileSizeBytes = saved.third
            }
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val saved = DocumentVaultUtils.saveAttachmentLocally(context, uri, explicitMimeType = "application/pdf")
            if (saved != null) {
                localFilePath = saved.first
                mimeType = saved.second
                fileSizeBytes = saved.third
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialDoc == null) "Add Vault Document" else "Edit Document", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Category", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DocumentVaultUtils.DOCUMENT_CATEGORIES.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = {
                                category = cat
                                if (cat == "ID") isSensitive = true
                                if (cat == "Warranty" || cat == "Insurance") hasExpiry = true
                            },
                            label = { Text(cat) }
                        )
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Document Title (e.g., MacBook Warranty, Car Insurance)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vault_doc_title_input")
                )

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { showTxDropdown = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        val txLabel = selectedTransaction?.let { tx ->
                            val label = tx.subcategory.ifBlank { tx.category }
                            "Linked Purchase: $label (${FormatUtils.formatInr(tx.amount)})"
                        } ?: "Link to Purchase Transaction (Optional)"
                        Text(txLabel)
                    }
                    DropdownMenu(
                        expanded = showTxDropdown,
                        onDismissRequest = { showTxDropdown = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("None") },
                            onClick = {
                                selectedTransaction = null
                                showTxDropdown = false
                            }
                        )
                        transactions.take(15).forEach { tx ->
                            val label = tx.subcategory.ifBlank { tx.category }
                            DropdownMenuItem(
                                text = { Text("$label • ${FormatUtils.formatInr(tx.amount)} (${FormatUtils.formatDate(tx.timestamp)})") },
                                onClick = {
                                    selectedTransaction = tx
                                    if (title.isBlank()) title = "$label Warranty"
                                    showTxDropdown = false
                                }
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = hasExpiry, onCheckedChange = { hasExpiry = it })
                    Text("Track Warranty / Expiry Period", style = MaterialTheme.typography.bodyMedium)
                }

                if (hasExpiry) {
                    OutlinedTextField(
                        value = warrantyMonthsText,
                        onValueChange = { warrantyMonthsText = it },
                        label = { Text("Warranty / Validity Period (Months)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Text("Attach Local File (Stored On-Device Only)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            imagePickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Image (Compressed)")
                    }

                    FilledTonalButton(
                        onClick = {
                            pdfPickerLauncher.launch(arrayOf("application/pdf"))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Attach PDF")
                    }
                }

                if (localFilePath != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFE8F5E9),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Attached ${mimeType ?: "File"} (${DocumentVaultUtils.formatFileSize(fileSizeBytes)})",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF2E7D32),
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(
                                onClick = {
                                    DocumentVaultUtils.deleteLocalFileIfExists(localFilePath)
                                    localFilePath = null
                                    mimeType = null
                                    fileSizeBytes = 0L
                                }
                            ) {
                                Text("Remove")
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = tagsText,
                    onValueChange = { tagsText = it },
                    label = { Text("Tags (comma separated, e.g., electronics, tax, car)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Serial No / Policy No / Notes") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = isSensitive, onCheckedChange = { isSensitive = it })
                    Spacer(Modifier.width(8.dp))
                    Text("Hide behind App PIN (Sensitive ID/Doc)", style = MaterialTheme.typography.bodySmall)
                }

                if (hasExpiry) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = reminderEnabled, onCheckedChange = { reminderEnabled = it })
                        Spacer(Modifier.width(8.dp))
                        Text("Expiry Reminders (30, 7 & 1 days before)", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val purchaseTs = selectedTransaction?.timestamp ?: initialDoc?.issueDate ?: System.currentTimeMillis()
                    val months = warrantyMonthsText.toIntOrNull() ?: 12
                    val computedExpiry = if (hasExpiry) {
                        DocumentVaultUtils.calculateExpiryFromWarrantyMonths(purchaseTs, months)
                    } else null

                    val linkedSummary = selectedTransaction?.let { tx ->
                        val label = tx.subcategory.ifBlank { tx.category }
                        "$label (${FormatUtils.formatInr(tx.amount)})"
                    } ?: initialDoc?.linkedTransactionSummary

                    val doc = VaultDocument(
                        id = initialDoc?.id ?: 0L,
                        title = title.trim().ifBlank { "$category Document" },
                        category = category,
                        issueDate = purchaseTs,
                        expiryDate = computedExpiry,
                        warrantyMonths = if (hasExpiry) months else 0,
                        linkedTransactionId = selectedTransaction?.id?.toLong() ?: initialDoc?.linkedTransactionId,
                        linkedTransactionSummary = linkedSummary,
                        localFilePath = localFilePath,
                        mimeType = mimeType,
                        fileSizeBytes = fileSizeBytes,
                        isSensitive = isSensitive,
                        reminderEnabled = reminderEnabled,
                        tagsCsv = tagsText.trim(),
                        notes = notes.trim()
                    )
                    onSave(doc)
                },
                enabled = title.isNotBlank(),
                modifier = Modifier.testTag("save_vault_doc_btn")
            ) {
                Text("Save Document")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun VaultDocumentDetailDialog(
    doc: VaultDocument,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isImage = doc.mimeType?.contains("image", ignoreCase = true) == true
    val isPdf = doc.mimeType?.contains("pdf", ignoreCase = true) == true
    val bitmap = remember(doc.localFilePath, isImage) {
        if (isImage && !doc.localFilePath.isNullOrBlank()) {
            val f = File(doc.localFilePath)
            if (f.exists()) BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() else null
        } else null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(doc.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (doc.isSensitive) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Category: ${doc.category}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text("Issue / Purchase Date: ${FormatUtils.formatDate(doc.issueDate)}", style = MaterialTheme.typography.bodySmall)
                doc.expiryDate?.let { exp ->
                    val d = DocumentVaultUtils.daysUntilExpiry(exp) ?: 0L
                    Text(
                        "Expiry Date: ${FormatUtils.formatDate(exp)} (${if (d >= 0L) "$d days left" else "Expired ${-d} days ago"})",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (doc.warrantyMonths > 0) {
                    Text("Warranty Period: ${doc.warrantyMonths} months", style = MaterialTheme.typography.bodySmall)
                }
                if (!doc.linkedTransactionSummary.isNullOrBlank()) {
                    Text(
                        "Linked Purchase: ${doc.linkedTransactionSummary}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (doc.notes.isNotBlank()) {
                    Text("Notes: ${doc.notes}", style = MaterialTheme.typography.bodyMedium)
                }
                if (doc.tagsCsv.isNotBlank()) {
                    Text("Tags: ${doc.tagsCsv}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }

                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = doc.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else if (isPdf && !doc.localFilePath.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color(0xFFC62828))
                            Column {
                                Text("Local PDF Attached", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                Text(
                                    "Size: ${DocumentVaultUtils.formatFileSize(doc.fileSizeBytes)}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text("Delete")
                }
                FilledTonalButton(onClick = onEdit) {
                    Text("Edit")
                }
                Button(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}

@Composable
private fun VaultPinDialog(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    onVerify: (String) -> Boolean
) {
    var pinInput by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = {
                        pinInput = it
                        showError = false
                    },
                    label = { Text("4-Digit PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    isError = showError,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vault_pin_input")
                )
                if (showError) {
                    Text(
                        "Incorrect PIN. Default PIN is 1234.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!onVerify(pinInput)) {
                        showError = true
                    }
                },
                modifier = Modifier.testTag("vault_pin_unlock_btn")
            ) {
                Text("Unlock")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ChangeVaultPinDialog(
    currentPin: String,
    onDismiss: () -> Unit,
    onSaveNewPin: (String) -> Unit
) {
    var oldPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Vault PIN", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = oldPin,
                    onValueChange = { oldPin = it; errorMsg = null },
                    label = { Text("Current PIN (Default: 1234)") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { newPin = it; errorMsg = null },
                    label = { Text("New PIN (4+ digits)") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                errorMsg?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (oldPin != currentPin) {
                        errorMsg = "Current PIN does not match."
                    } else if (newPin.length < 4) {
                        errorMsg = "New PIN must be at least 4 digits."
                    } else {
                        onSaveNewPin(newPin)
                    }
                }
            ) {
                Text("Update PIN")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

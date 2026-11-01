package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.data.BackupValidationResult
import com.example.ui.BiometricHelper
import com.example.utils.FormatUtils

@Composable
fun AppLockScreen(
    expectedPin: String,
    biometricEnabled: Boolean,
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val biometricSupported = remember(context) { BiometricHelper.isBiometricAvailable(context) }

    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(biometricEnabled, biometricSupported, activity) {
        if (biometricEnabled && biometricSupported && activity != null) {
            BiometricHelper.showBiometricPrompt(
                activity = activity,
                onSuccess = { onUnlocked() },
                onError = { /* Stay on PIN keypad */ }
            )
        }
    }

    fun onDigitPressed(digit: String) {
        if (enteredPin.length < 6) {
            val next = enteredPin + digit
            enteredPin = next
            errorMessage = null
            if (next == expectedPin) {
                onUnlocked()
            } else if (next.length == 6 && next != expectedPin) {
                errorMessage = "Incorrect PIN. Try again."
                enteredPin = ""
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("app_lock_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "App Locked",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "RupeeStory Locked",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Enter your ${expectedPin.length.coerceIn(4, 6)}-digit PIN to unlock",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(28.dp))

                // PIN Dots Indicator (supports 4 to 6 digits)
                val targetLen = expectedPin.length.coerceIn(4, 6)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until targetLen) {
                        val isFilled = i < enteredPin.length
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isFilled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    width = 1.5.dp,
                                    color = if (isFilled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                        )
                    }
                }

                if (errorMessage != null) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Numeric Keypad
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.widthIn(max = 320.dp)
            ) {
                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9")
                )
                rows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        row.forEach { digit ->
                            PinKeypadButton(
                                text = digit,
                                onClick = { onDigitPressed(digit) },
                                modifier = Modifier.testTag("pin_key_$digit")
                            )
                        }
                    }
                }

                // Bottom row: Biometric / Clear, 0, Backspace
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (biometricEnabled && biometricSupported && activity != null) {
                        IconButton(
                            onClick = {
                                BiometricHelper.showBiometricPrompt(
                                    activity = activity,
                                    onSuccess = { onUnlocked() },
                                    onError = {}
                                )
                            },
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .testTag("pin_biometric_btn")
                        ) {
                            Icon(
                                Icons.Default.Fingerprint,
                                contentDescription = "Unlock with Biometrics",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    } else {
                        TextButton(
                            onClick = {
                                enteredPin = ""
                                errorMessage = null
                            },
                            modifier = Modifier.size(68.dp)
                        ) {
                            Text("Clear", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    PinKeypadButton(
                        text = "0",
                        onClick = { onDigitPressed("0") },
                        modifier = Modifier.testTag("pin_key_0")
                    )

                    IconButton(
                        onClick = {
                            if (enteredPin.isNotEmpty()) {
                                enteredPin = enteredPin.dropLast(1)
                                errorMessage = null
                            }
                        },
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .testTag("pin_backspace_btn")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Backspace,
                            contentDescription = "Backspace",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (enteredPin.length in 4..6) {
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = {
                            if (enteredPin == expectedPin) {
                                onUnlocked()
                            } else {
                                errorMessage = "Incorrect PIN. Try again."
                                enteredPin = ""
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pin_submit_btn"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Unlock App", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Text(
                text = "Protected by RupeeStory On-Device App Lock",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PinKeypadButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
            .size(68.dp)
            .clip(CircleShape)
            .clickable { onClick() }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun ConfigureAppPinDialog(
    currentPin: String,
    requireCurrentPinVerification: Boolean,
    title: String = "Set 4–6 Digit App PIN",
    onDismiss: () -> Unit,
    onPinSaved: (String) -> Unit
) {
    var oldPinInput by remember { mutableStateOf("") }
    var newPinInput by remember { mutableStateOf("") }
    var confirmPinInput by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Pin, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Choose a 4 to 6 digit numeric PIN. This PIN protects both App Lock and sensitive Document Vault records.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (requireCurrentPinVerification) {
                    OutlinedTextField(
                        value = oldPinInput,
                        onValueChange = { oldPinInput = it.filter { c -> c.isDigit() }.take(6); errorText = null },
                        label = { Text("Current PIN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_old_pin_input")
                    )
                }
                OutlinedTextField(
                    value = newPinInput,
                    onValueChange = { newPinInput = it.filter { c -> c.isDigit() }.take(6); errorText = null },
                    label = { Text("New 4–6 Digit PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_new_pin_input")
                )
                OutlinedTextField(
                    value = confirmPinInput,
                    onValueChange = { confirmPinInput = it.filter { c -> c.isDigit() }.take(6); errorText = null },
                    label = { Text("Confirm New PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_confirm_pin_input")
                )
                if (errorText != null) {
                    Text(
                        text = errorText!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        requireCurrentPinVerification && oldPinInput != currentPin -> {
                            errorText = "Current PIN is incorrect."
                        }
                        newPinInput.length !in 4..6 -> {
                            errorText = "New PIN must be between 4 and 6 digits."
                        }
                        newPinInput != confirmPinInput -> {
                            errorText = "New PIN and confirmation do not match."
                        }
                        else -> {
                            onPinSaved(newPinInput)
                        }
                    }
                },
                modifier = Modifier.testTag("dialog_save_pin_btn")
            ) {
                Text("Save PIN", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun BackupRestorePreviewDialog(
    validationResult: BackupValidationResult,
    sourceLabel: String = "Local JSON File",
    onDismiss: () -> Unit,
    onConfirmRestore: (clearExistingFirst: Boolean) -> Unit
) {
    var clearExistingFirst by remember { mutableStateOf(false) }
    val preview = validationResult.preview

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (validationResult.isValid) Icons.Default.Verified else Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = if (validationResult.isValid) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                if (validationResult.isValid) "Backup Preview & Validation"
                else "Backup Validation Failed",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (validationResult.isValid) Color(0xFF2E7D32).copy(alpha = 0.12f)
                    else MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (validationResult.isValid) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (validationResult.isValid) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (validationResult.isValid) "Schema Validated ($sourceLabel)"
                                else "Cannot restore from this backup",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            if (preview != null) {
                                Text(
                                    text = "Exported: ${FormatUtils.formatDateTime(preview.exportedAt)} • Schema v${preview.schemaVersion}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                if (validationResult.errors.isNotEmpty()) {
                    validationResult.errors.forEach { err ->
                        Text(
                            text = "• $err",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (preview != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "Data Summary (${preview.totalRecordsCount} total records)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text("Date Range: ${preview.dateRangeLabel}", style = MaterialTheme.typography.bodySmall)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            PreviewRowItem("Transactions", preview.transactionsCount)
                            PreviewRowItem("Accounts & Categories", preview.accountsCount + preview.categoriesCount)
                            PreviewRowItem("Budgets & Savings Goals", preview.budgetsCount + preview.goalsCount)
                            PreviewRowItem("People & Udhaar Entries", preview.debtsAndUdhaarCount)
                            PreviewRowItem("Bills & Recurring Rules", preview.recurringAndBillsCount)
                            PreviewRowItem("Tasks & Calendar Events", preview.tasksAndEventsCount)
                            PreviewRowItem("Notes & Checklists", preview.notesCount)
                            PreviewRowItem("Split Bills & Settlements", preview.splitGroupsAndExpensesCount)
                            PreviewRowItem("Habits & Streak Logs", preview.habitsAndLogsCount)
                            PreviewRowItem("Investments & Liabilities", preview.investmentsAndLiabilitiesCount)
                            PreviewRowItem("Document Vault Records", preview.vaultDocumentsCount)
                            if (preview.includesSettings) {
                                Text(
                                    "✓ Includes App Settings & Module Preferences",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF2E7D32),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                if (validationResult.warnings.isNotEmpty()) {
                    Text(
                        "Validation Notices:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    validationResult.warnings.forEach { warn ->
                        Text(
                            text = "• $warn",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (validationResult.isValid) {
                    HorizontalDivider()
                    Text(
                        "Restore Mode",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { clearExistingFirst = false }
                    ) {
                        RadioButton(selected = !clearExistingFirst, onClick = { clearExistingFirst = false })
                        Column {
                            Text("Merge with current data (Recommended)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("Adds/updates records without deleting existing items", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { clearExistingFirst = true }
                    ) {
                        RadioButton(selected = clearExistingFirst, onClick = { clearExistingFirst = true })
                        Column {
                            Text("Clean restore (Overwrite all existing data)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("Wipes current local data before restoring backup", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (validationResult.isValid && validationResult.parsedData != null) {
                Button(
                    onClick = { onConfirmRestore(clearExistingFirst) },
                    modifier = Modifier.testTag("confirm_restore_backup_btn")
                ) {
                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Restore Now", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun PreviewRowItem(label: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(
            count.toString(),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun DoubleConfirmResetDataDialog(
    onDismiss: () -> Unit,
    onExportBackupFirst: () -> Unit,
    onConfirmFinalReset: () -> Unit
) {
    var step by remember { mutableStateOf(1) }
    var confirmationText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.DeleteForever,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                if (step == 1) "Reset All Data? (Step 1 of 2)"
                else "Final Confirmation (Step 2 of 2)",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
        },
        text = {
            if (step == 1) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "This will permanently delete all your local records across all modules:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "• All Transactions, Accounts, Budgets & Savings Goals\n" +
                            "• People & Udhaar Ledger, Split Bills & Settlements\n" +
                            "• Tasks, Calendar Schedule, Habits & Streaks\n" +
                            "• Notes, Investments, Loans & Document Vault files",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = onExportBackupFirst,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Export JSON Backup First")
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "This action cannot be undone. Type RESET below to permanently erase all data and restore factory defaults.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = confirmationText,
                        onValueChange = { confirmationText = it },
                        label = { Text("Type RESET to confirm") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reset_data_confirm_input")
                    )
                }
            }
        },
        confirmButton = {
            if (step == 1) {
                Button(
                    onClick = { step = 2 },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("reset_step1_continue_btn")
                ) {
                    Text("Continue to Step 2", fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = onConfirmFinalReset,
                    enabled = confirmationText.trim().equals("RESET", ignoreCase = true),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("reset_step2_confirm_btn")
                ) {
                    Text("Erase All Data", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

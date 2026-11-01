package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.*
import com.example.utils.FormatUtils
import com.example.utils.SplitUtils
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateSplitGroupDialog(
    existingPeople: List<Person>,
    onDismiss: () -> Unit,
    onCreate: (name: String, type: String, members: List<String>, linkToUdhaar: Boolean) -> Unit
) {
    var groupName by remember { mutableStateOf("") }
    var groupType by remember { mutableStateOf("Trip") }
    var members by remember { mutableStateOf(listOf("Me")) }
    var newMemberInput by remember { mutableStateOf("") }
    var linkToUdhaar by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val groupTypes = listOf("Trip", "Flatmates", "Dinner", "Event", "Other")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Create Split Group", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = groupName,
                    onValueChange = {
                        groupName = it
                        errorMsg = null
                    },
                    label = { Text("Group Name (e.g. Goa Trip, Flat 402)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("split_group_name_input")
                )

                Text("Group Type", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    groupTypes.forEach { type ->
                        FilterChip(
                            selected = groupType == type,
                            onClick = { groupType = type },
                            label = { Text(type) }
                        )
                    }
                }

                Text(
                    "Members (${members.size})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    members.forEach { member ->
                        val isMe = member.equals("Me", ignoreCase = true)
                        InputChip(
                            selected = true,
                            onClick = {
                                if (!isMe) members = members - member
                            },
                            label = { Text(if (isMe) "Me (You)" else member) },
                            trailingIcon = if (!isMe) {
                                {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove $member",
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            } else null
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newMemberInput,
                        onValueChange = {
                            newMemberInput = it
                            errorMsg = null
                        },
                        placeholder = { Text("Add member name...") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("split_member_name_input")
                    )
                    FilledTonalIconButton(
                        onClick = {
                            val clean = newMemberInput.trim()
                            if (clean.isNotBlank() && members.none { it.equals(clean, ignoreCase = true) }) {
                                members = members + clean
                                newMemberInput = ""
                            }
                        },
                        modifier = Modifier.testTag("split_add_member_btn")
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = "Add Member")
                    }
                }

                // Quick add from People & Udhaar
                val unaddedPeople = existingPeople.filter { p ->
                    members.none { it.equals(p.name, ignoreCase = true) }
                }
                if (unaddedPeople.isNotEmpty()) {
                    Text(
                        "Quick add from People & Udhaar:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        unaddedPeople.take(8).forEach { person ->
                            AssistChip(
                                onClick = { members = members + person.name },
                                label = { Text("+ ${person.name}") }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Link to People & Udhaar",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Sync members with your Khata ledger",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = linkToUdhaar,
                        onCheckedChange = { linkToUdhaar = it },
                        modifier = Modifier.testTag("split_link_udhaar_switch")
                    )
                }

                if (errorMsg != null) {
                    Text(
                        text = errorMsg!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val autoAddedMembers = if (newMemberInput.isNotBlank() && members.none { it.equals(newMemberInput.trim(), ignoreCase = true) }) {
                        members + newMemberInput.trim()
                    } else {
                        members
                    }
                    when {
                        groupName.isBlank() -> errorMsg = "Please enter a group name."
                        autoAddedMembers.size < 2 -> errorMsg = "Add at least 1 other member besides yourself."
                        else -> onCreate(groupName.trim(), groupType, autoAddedMembers, linkToUdhaar)
                    }
                },
                modifier = Modifier.testTag("split_create_group_confirm_btn")
            ) {
                Text("Create Group")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSplitExpenseDialog(
    group: SplitGroup,
    members: List<String>,
    accounts: List<Account>,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        totalAmount: Double,
        category: String,
        paidBy: String,
        paymentAccount: String,
        splitType: String,
        splits: List<MemberSplit>,
        note: String
    ) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var amountInput by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Food") }
    var paidBy by remember { mutableStateOf("Me") }
    var paymentAccount by remember {
        mutableStateOf(
            accounts.firstOrNull { it.name.contains("UPI", ignoreCase = true) }?.name
                ?: accounts.firstOrNull()?.name
                ?: "UPI Wallet"
        )
    }
    var splitType by remember { mutableStateOf("EQUALLY") } // EQUALLY, EXACT, PERCENTAGE, SHARES
    var note by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // Per-mode state
    var includedEqualMembers by remember { mutableStateOf(members.toSet()) }
    var exactInputs by remember { mutableStateOf(members.associateWith { "" }) }
    var percentInputs by remember {
        val eqPct = SplitUtils.round2(100.0 / members.size.coerceAtLeast(1))
        mutableStateOf(members.associateWith { eqPct.toString() })
    }
    var shareInputs by remember { mutableStateOf(members.associateWith { 1 }) }

    val totalAmount = amountInput.toDoubleOrNull() ?: 0.0

    val computedSplits = remember(
        totalAmount,
        splitType,
        includedEqualMembers,
        exactInputs,
        percentInputs,
        shareInputs,
        members
    ) {
        when (splitType) {
            "EQUALLY" -> {
                val activeList = members.filter { includedEqualMembers.contains(it) }
                val activeSplits = SplitUtils.calculateEqualSplits(totalAmount, activeList).associateBy { it.memberName }
                members.map { m ->
                    activeSplits[m] ?: MemberSplit(memberName = m, amount = 0.0, percentage = 0.0, shares = 0)
                }
            }
            "EXACT" -> {
                val map = exactInputs.mapValues { it.value.toDoubleOrNull() ?: 0.0 }
                SplitUtils.calculateExactSplits(map, members, totalAmount)
            }
            "PERCENTAGE" -> {
                val map = percentInputs.mapValues { it.value.toDoubleOrNull() ?: 0.0 }
                SplitUtils.calculatePercentageSplits(totalAmount, map, members)
            }
            "SHARES" -> {
                SplitUtils.calculateShareSplits(totalAmount, shareInputs, members)
            }
            else -> SplitUtils.calculateEqualSplits(totalAmount, members)
        }
    }

    val myComputedShare = computedSplits.firstOrNull { it.memberName.equals("Me", ignoreCase = true) }?.amount ?: 0.0
    val expenseCategories = remember(categories) {
        val list = categories.filter { !it.isIncome }.map { it.name }
        if (list.isEmpty()) listOf("Food", "Travel/Petrol", "Rent", "Bills", "Shopping", "Other") else list
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Add Shared Expense",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Group: ${group.name}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider()

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item { Spacer(Modifier.height(4.dp)) }

                    // Description & Amount
                    item {
                        OutlinedTextField(
                            value = title,
                            onValueChange = {
                                title = it
                                errorMsg = null
                            },
                            label = { Text("What was this expense for? (e.g. Dinner, Hotel)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("split_expense_title_input")
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = amountInput,
                            onValueChange = {
                                amountInput = it
                                errorMsg = null
                            },
                            label = { Text("Total Bill Amount (₹)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("split_expense_amount_input")
                        )
                    }

                    // Category selector
                    item {
                        Text("Category", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            expenseCategories.forEach { cat ->
                                FilterChip(
                                    selected = selectedCategory == cat,
                                    onClick = { selectedCategory = cat },
                                    label = { Text(cat) }
                                )
                            }
                        }
                    }

                    // Who Paid
                    item {
                        Text("Paid By", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            members.forEach { member ->
                                FilterChip(
                                    selected = paidBy == member,
                                    onClick = { paidBy = member },
                                    label = { Text(if (member == "Me") "Me (You)" else member) },
                                    leadingIcon = if (paidBy == member) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null,
                                    modifier = Modifier.testTag("split_paid_by_$member")
                                )
                            }
                        }
                    }

                    // Payment Account if "Me" paid
                    if (paidBy.equals("Me", ignoreCase = true)) {
                        item {
                            Text(
                                "Paid From Account",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                accounts.forEach { acc ->
                                    FilterChip(
                                        selected = paymentAccount == acc.name,
                                        onClick = { paymentAccount = acc.name },
                                        label = { Text(acc.name) }
                                    )
                                }
                            }
                        }
                    }

                    // Smart Accounting Notice Card
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.Default.VerifiedUser,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                val infoText = if (paidBy.equals("Me", ignoreCase = true)) {
                                    "You paid ${FormatUtils.formatInr(totalAmount)}: Only your share (${FormatUtils.formatInr(myComputedShare)}) counts as an expense in reports; ${FormatUtils.formatInr((totalAmount - myComputedShare).coerceAtLeast(0.0))} is tracked as owed to you."
                                } else {
                                    "$paidBy paid ${FormatUtils.formatInr(totalAmount)}: Only your share (${FormatUtils.formatInr(myComputedShare)}) counts as your expense in reports. Account balance deducts only when you Settle Up."
                                }
                                Text(
                                    text = infoText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    // How to Split Mode Selector
                    item {
                        Text(
                            "How to Split",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "EQUALLY" to "Equally (=)",
                                "EXACT" to "Exact (₹)",
                                "PERCENTAGE" to "Percentage (%)",
                                "SHARES" to "Shares (x)"
                            ).forEach { (modeId, label) ->
                                FilterChip(
                                    selected = splitType == modeId,
                                    onClick = {
                                        splitType = modeId
                                        errorMsg = null
                                    },
                                    label = { Text(label) },
                                    modifier = Modifier.testTag("split_mode_$modeId")
                                )
                            }
                        }
                    }

                    // Split Breakdown Editor per Mode
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                when (splitType) {
                                    "EQUALLY" -> {
                                        Text(
                                            "Split equally among selected members:",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                        computedSplits.forEach { sp ->
                                            val checked = includedEqualMembers.contains(sp.memberName)
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Checkbox(
                                                        checked = checked,
                                                        onCheckedChange = { isChecked ->
                                                            if (isChecked) {
                                                                includedEqualMembers = includedEqualMembers + sp.memberName
                                                            } else if (includedEqualMembers.size > 1) {
                                                                includedEqualMembers = includedEqualMembers - sp.memberName
                                                            }
                                                        }
                                                    )
                                                    Text(
                                                        text = sp.memberName,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                                Text(
                                                    text = FormatUtils.formatInr(sp.amount),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }

                                    "EXACT" -> {
                                        val allocated = computedSplits.sumOf { it.amount }
                                        val remaining = SplitUtils.round2(totalAmount - allocated)
                                        Text(
                                            "Allocated: ${FormatUtils.formatInr(allocated)} / ${FormatUtils.formatInr(totalAmount)} (Remaining: ${FormatUtils.formatInr(remaining)})",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (abs(remaining) <= 0.05) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.Bold
                                        )
                                        members.forEach { m ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = m,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Medium,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                OutlinedTextField(
                                                    value = exactInputs[m] ?: "",
                                                    onValueChange = { v ->
                                                        exactInputs = exactInputs.toMutableMap().apply { put(m, v) }
                                                    },
                                                    label = { Text("₹ Amount") },
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                    singleLine = true,
                                                    modifier = Modifier
                                                        .width(140.dp)
                                                        .testTag("split_exact_$m")
                                                )
                                            }
                                        }
                                    }

                                    "PERCENTAGE" -> {
                                        val pctSum = SplitUtils.round2(
                                            percentInputs.values.sumOf { it.toDoubleOrNull() ?: 0.0 }
                                        )
                                        Text(
                                            "Total Percentage: $pctSum% / 100%",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (abs(pctSum - 100.0) <= 0.1) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.Bold
                                        )
                                        computedSplits.forEach { sp ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(sp.memberName, fontWeight = FontWeight.Medium)
                                                    Text(
                                                        FormatUtils.formatInr(sp.amount),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                                OutlinedTextField(
                                                    value = percentInputs[sp.memberName] ?: "",
                                                    onValueChange = { v ->
                                                        percentInputs = percentInputs.toMutableMap().apply { put(sp.memberName, v) }
                                                    },
                                                    label = { Text("%") },
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                    singleLine = true,
                                                    modifier = Modifier
                                                        .width(120.dp)
                                                        .testTag("split_pct_${sp.memberName}")
                                                )
                                            }
                                        }
                                    }

                                    "SHARES" -> {
                                        val totalShares = shareInputs.values.sum()
                                        Text(
                                            "Total Shares: $totalShares",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        computedSplits.forEach { sp ->
                                            val currentShares = shareInputs[sp.memberName] ?: 1
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(sp.memberName, fontWeight = FontWeight.Medium)
                                                    Text(
                                                        FormatUtils.formatInr(sp.amount),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    FilledTonalIconButton(
                                                        onClick = {
                                                            if (currentShares > 0) {
                                                                shareInputs = shareInputs.toMutableMap().apply {
                                                                    put(sp.memberName, currentShares - 1)
                                                                }
                                                            }
                                                        },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(Icons.Default.Remove, contentDescription = "Decrease Share", modifier = Modifier.size(16.dp))
                                                    }
                                                    Text(
                                                        "${currentShares}x",
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    FilledTonalIconButton(
                                                        onClick = {
                                                            shareInputs = shareInputs.toMutableMap().apply {
                                                                put(sp.memberName, currentShares + 1)
                                                            }
                                                        },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(Icons.Default.Add, contentDescription = "Increase Share", modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Optional Note
                    item {
                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it },
                            label = { Text("Optional Note") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (errorMsg != null) {
                        item {
                            Text(
                                text = errorMsg!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    item { Spacer(Modifier.height(16.dp)) }
                }

                // Bottom Save Bar
                Surface(tonalElevation = 8.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel")
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val sumSplits = computedSplits.sumOf { it.amount }
                                val pctSum = percentInputs.values.sumOf { it.toDoubleOrNull() ?: 0.0 }
                                when {
                                    title.isBlank() -> errorMsg = "Please enter an expense description."
                                    totalAmount <= 0.0 -> errorMsg = "Please enter a valid amount greater than ₹0."
                                    splitType == "EXACT" && abs(totalAmount - sumSplits) > 0.05 -> {
                                        errorMsg = "Exact amounts (${FormatUtils.formatInr(sumSplits)}) must equal total bill (${FormatUtils.formatInr(totalAmount)})."
                                    }
                                    splitType == "PERCENTAGE" && abs(pctSum - 100.0) > 0.1 -> {
                                        errorMsg = "Percentages must add up to 100% (currently ${SplitUtils.round2(pctSum)}%)."
                                    }
                                    else -> {
                                        onSave(
                                            title.trim(),
                                            totalAmount,
                                            selectedCategory,
                                            paidBy,
                                            paymentAccount,
                                            splitType,
                                            computedSplits,
                                            note.trim()
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.testTag("split_save_expense_btn")
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Save Shared Expense")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettleUpDialog(
    groupName: String,
    members: List<String>,
    accounts: List<Account>,
    initialFromMember: String? = null,
    initialToMember: String? = null,
    initialAmount: Double? = null,
    onDismiss: () -> Unit,
    onSettle: (fromMember: String, toMember: String, amount: Double, account: String, note: String) -> Unit
) {
    var fromMember by remember { mutableStateOf(initialFromMember ?: members.firstOrNull() ?: "Me") }
    var toMember by remember {
        mutableStateOf(
            initialToMember ?: members.firstOrNull { it != fromMember } ?: "Me"
        )
    }
    var amountInput by remember {
        mutableStateOf(if (initialAmount != null && initialAmount > 0) initialAmount.toString() else "")
    }
    var selectedAccount by remember {
        mutableStateOf(
            accounts.firstOrNull { it.name.contains("UPI", ignoreCase = true) }?.name
                ?: accounts.firstOrNull()?.name
                ?: "UPI Wallet"
        )
    }
    var note by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val involvesMe = fromMember.equals("Me", ignoreCase = true) || toMember.equals("Me", ignoreCase = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Handshake, contentDescription = null, tint = Color(0xFF2E7D32))
                Text("Settle Up Balance", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Group: $groupName",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Text("Who Paid (Payer):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    members.forEach { m ->
                        FilterChip(
                            selected = fromMember == m,
                            onClick = {
                                fromMember = m
                                if (toMember == m) {
                                    toMember = members.firstOrNull { it != m } ?: m
                                }
                            },
                            label = { Text(m) }
                        )
                    }
                }

                Text("Paid To (Receiver):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    members.filter { it != fromMember }.forEach { m ->
                        FilterChip(
                            selected = toMember == m,
                            onClick = { toMember = m },
                            label = { Text(m) }
                        )
                    }
                }

                OutlinedTextField(
                    value = amountInput,
                    onValueChange = {
                        amountInput = it
                        errorMsg = null
                    },
                    label = { Text("Settlement Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("split_settle_amount_input")
                )

                if (involvesMe) {
                    Text(
                        if (fromMember.equals("Me", ignoreCase = true)) "Deduct From My Account:" else "Deposit Into My Account:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        accounts.forEach { acc ->
                            FilterChip(
                                selected = selectedAccount == acc.name,
                                onClick = { selectedAccount = acc.name },
                                label = { Text(acc.name) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (e.g. GPay settlement)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMsg != null) {
                    Text(
                        text = errorMsg!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountInput.toDoubleOrNull() ?: 0.0
                    when {
                        fromMember == toMember -> errorMsg = "Payer and receiver must be different members."
                        amt <= 0.0 -> errorMsg = "Enter a valid settlement amount greater than ₹0."
                        else -> onSettle(fromMember, toMember, amt, selectedAccount, note)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                modifier = Modifier.testTag("split_confirm_settle_btn")
            ) {
                Text("Record Settlement")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

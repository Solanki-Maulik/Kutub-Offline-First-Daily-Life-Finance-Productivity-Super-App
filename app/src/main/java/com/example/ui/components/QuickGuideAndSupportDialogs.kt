package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.utils.FeedbackValidationOutcome
import com.example.utils.FormatUtils
import com.example.utils.SupportAndFeedbackUtils
import kotlinx.coroutines.launch

@Composable
fun HomeQuickGuideBanner(
    onOpenFullGuide: () -> Unit,
    onOpenBugReport: () -> Unit,
    onDismissBanner: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_quick_guide_banner"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.kutub_launcher_icon),
                        contentDescription = "Kutub Logo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        AutoSizeSingleLineText(
                            text = "Welcome to Kutub (કુટુંબ) — Quick Guide",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "100% Offline Finance, Fuel km/L, Meditation Timer & Daily Tasks",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(
                    onClick = onDismissBanner,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("dismiss_quick_guide_banner_btn")
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Dismiss Quick Guide",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onOpenFullGuide,
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .testTag("open_quick_guide_btn"),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.HelpOutline,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    AutoSizeSingleLineText(
                        text = "How to Use App",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = onOpenBugReport,
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .testTag("home_banner_report_bug_btn"),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(
                        Icons.Default.BugReport,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    AutoSizeSingleLineText(
                        text = "Report Bug / Support",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

private data class GuideStepInfo(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val badge: String
)

@Composable
fun AppQuickGuideDialog(
    onDismiss: () -> Unit,
    onOpenBugReport: () -> Unit
) {
    val context = LocalContext.current
    val guideSteps = remember {
        listOf(
            GuideStepInfo(
                icon = Icons.Default.AccountBalanceWallet,
                title = "1. Money Ledger & Udhaar (+ Button)",
                description = "Tap '+ Expense', '+ Income', or 'Transfer' to log Cash, Bank, or UPI transactions. Use the People & Udhaar tab to track money You Got (મળ્યા) or You Gave (આપ્યા) with 1-tap WhatsApp reminders.",
                badge = "Core Finance"
            ),
            GuideStepInfo(
                icon = Icons.Default.SelfImprovement,
                title = "2. Live Meditation Timer & Breathing Pacer",
                description = "Open Daily Toolkit -> Meditation & Timer for a live circular countdown ring (5m, 10m, 15m, 25m Pomodoro) with a 4-4-6 guided breathing pacer that automatically checks off your Meditation habit.",
                badge = "Personal Favorite"
            ),
            GuideStepInfo(
                icon = Icons.Default.LocalGasStation,
                title = "3. Vehicle Fuel Draft & Real Mileage (km/L)",
                description = "At the petrol pump, enter Fuel Paid (₹) & Rate (₹/L) and tap 'Save Draft'. Days or weeks later on your next refill, tap 'Edit Odo' on that entry to enter your new odometer reading and calculate exact km/L mileage & ₹/km cost.",
                badge = "Personal Favorite"
            ),
            GuideStepInfo(
                icon = Icons.Default.EventAvailable,
                title = "4. Tasks, Schedule & Daily Streaks",
                description = "Tap any task or habit on the Home screen to check it off and build your daily streak. Use interactive Calendar & Clock pickers when adding or editing tasks and schedules.",
                badge = "Productivity"
            ),
            GuideStepInfo(
                icon = Icons.Default.AutoAwesome,
                title = "5. Instant Smart Entry (Works 100% Offline)",
                description = "Type natural phrases like 'Petrol 250 cash', 'Got 500 from Rahul', or 'Task pay bill tomorrow' in the Smart Entry bar for instant <5ms local parsing and 1-tap saving.",
                badge = "Offline Smart NLP"
            )
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .widthIn(max = 540.dp)
                .heightIn(max = 680.dp)
                .padding(vertical = 12.dp)
                .testTag("app_quick_guide_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        Image(
                            painter = painterResource(id = R.drawable.kutub_launcher_icon),
                            contentDescription = "Kutub Logo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            AutoSizeSingleLineText(
                                text = "Kutub (કુટુંબ) — Quick Start Guide",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "100% Offline-First • MIT Open Source by ${SupportAndFeedbackUtils.DEVELOPER_NAME}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider()

                // Scrollable Steps
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    guideSteps.forEach { step ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            step.icon,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        AutoSizeSingleLineText(
                                            text = step.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = step.badge,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = step.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Developer & Portfolio Card inside Guide
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Created by ${SupportAndFeedbackUtils.DEVELOPER_NAME} (${SupportAndFeedbackUtils.LICENSE_NAME})",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Portfolio: ${SupportAndFeedbackUtils.PORTFOLIO_URL}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(SupportAndFeedbackUtils.PORTFOLIO_URL))
                                        )
                                    }
                                }
                            )
                        }
                    }
                }

                HorizontalDivider()

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onOpenBugReport()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("guide_open_bug_report_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        AutoSizeSingleLineText(
                            text = "Report Bug",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("guide_got_it_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        AutoSizeSingleLineText(
                            text = "Got It!",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BugReportAndFeedbackDialog(
    onDismiss: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val sp = remember { SupportAndFeedbackUtils.prefs(context) }
    val coroutineScope = rememberCoroutineScope()

    var userName by remember { mutableStateOf("") }
    var contactOrDeviceNote by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("BUG_REPORT") }
    var messageText by remember { mutableStateOf("") }
    var honeypotText by remember { mutableStateOf("") } // Anti-bot hidden state
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var statusSuccessMessage by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var feedbackHistory by remember { mutableStateOf(SupportAndFeedbackUtils.loadFeedbackHistory(sp)) }

    val categories = remember {
        listOf(
            "BUG_REPORT" to "Bug Report",
            "UI_FIT_ISSUE" to "Screen / UI Fit",
            "FEATURE_REQUEST" to "Feature Idea",
            "GENERAL_FEEDBACK" to "Feedback"
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .widthIn(max = 540.dp)
                .heightIn(max = 720.dp)
                .imePadding()
                .padding(vertical = 10.dp)
                .testTag("bug_report_feedback_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Top Header with Back & Close
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
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("bug_dialog_back_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            AutoSizeSingleLineText(
                                text = "Report Bug & Contact Support",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Direct Google Sheet Sync • Anti-Spam Protected",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider()

                // Scrollable Body
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Developer Contact Info Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Developer: ${SupportAndFeedbackUtils.DEVELOPER_NAME}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = SupportAndFeedbackUtils.PORTFOLIO_URL,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(SupportAndFeedbackUtils.PORTFOLIO_URL))
                                            )
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier
                                        .height(34.dp)
                                        .testTag("open_portfolio_btn"),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Portfolio", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }

                            // Security & Rate Limit Notice
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.VerifiedUser,
                                    contentDescription = null,
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Anti-Spam Active: 2-min rate limit, max 5/day, formula-injection sanitization & offline queue.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Category Selection
                    Text(
                        text = "Report Type",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categories.forEach { (key, label) ->
                            FilterChip(
                                selected = selectedCategory == key,
                                onClick = {
                                    selectedCategory = key
                                    errorMessage = null
                                },
                                label = { Text(label, maxLines = 1) },
                                leadingIcon = if (selectedCategory == key) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null,
                                modifier = Modifier.testTag("feedback_cat_$key")
                            )
                        }
                    }

                    // 1. Name (Optional)
                    OutlinedTextField(
                        value = userName,
                        onValueChange = {
                            userName = it.take(60)
                            errorMessage = null
                        },
                        label = { Text("Your Name (Optional)") },
                        placeholder = { Text("e.g. Rahul Patel (or leave blank)") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("feedback_name_input"),
                        shape = RoundedCornerShape(14.dp)
                    )

                    // 2. Contact or Extra Info (Optional)
                    OutlinedTextField(
                        value = contactOrDeviceNote,
                        onValueChange = {
                            contactOrDeviceNote = it.take(100)
                            errorMessage = null
                        },
                        label = { Text("Email / Phone / Screen Info (Optional)") },
                        placeholder = { Text("So Maulik can reply, or mention screen/module") },
                        leadingIcon = { Icon(Icons.Default.AlternateEmail, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("feedback_contact_input"),
                        shape = RoundedCornerShape(14.dp)
                    )

                    // 3. Issue or Feedback Details (Required)
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = {
                            messageText = it.take(SupportAndFeedbackUtils.MAX_MESSAGE_LENGTH)
                            errorMessage = null
                            statusSuccessMessage = null
                        },
                        label = { Text("Issue, Bug Steps, or Feedback *") },
                        placeholder = { Text("Describe what happened or what feature you'd like to see (min 10 chars)...") },
                        minLines = 3,
                        maxLines = 5,
                        isError = errorMessage != null,
                        supportingText = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = errorMessage ?: "Auto-attaches Android version & device model for bug fixing",
                                    color = if (errorMessage != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${messageText.length}/${SupportAndFeedbackUtils.MAX_MESSAGE_LENGTH}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("feedback_message_input"),
                        shape = RoundedCornerShape(14.dp)
                    )

                    if (statusSuccessMessage != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFE8F5E9),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
                                Text(
                                    text = statusSuccessMessage!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF1B5E20),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Recent Submitted / Queued Reports
                    if (feedbackHistory.isNotEmpty()) {
                        Text(
                            text = "Recent Submissions (${feedbackHistory.size})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        feedbackHistory.take(3).forEach { item ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "[${item.category}] ${item.message}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${FormatUtils.formatDateTime(item.timestamp)} • ${if (item.status == "SENT_TO_SHEET") "Synced to Sheet" else "Saved Locally"}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()

                // Bottom Action Row (Never clips or wraps vertically)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            val subject = Uri.encode("[Kutub $selectedCategory] Feedback from ${userName.ifBlank { "User" }}")
                            val body = Uri.encode(
                                "Name: ${userName.ifBlank { "Anonymous" }}\n" +
                                    "Contact/Info: ${contactOrDeviceNote.ifBlank { "N/A" }}\n" +
                                    "Category: $selectedCategory\n\n" +
                                    "Issue/Feedback:\n$messageText\n"
                            )
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${SupportAndFeedbackUtils.SUPPORT_EMAIL}?subject=$subject&body=$body"))
                                )
                            }
                        },
                        modifier = Modifier
                            .weight(0.42f)
                            .height(46.dp)
                            .testTag("feedback_email_direct_btn"),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        AutoSizeSingleLineText(
                            text = "Email Direct",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Button(
                        onClick = {
                            if (isSubmitting) return@Button
                            when (
                                val validation = SupportAndFeedbackUtils.validateAndCheckRateLimit(
                                    sp = sp,
                                    rawName = userName,
                                    rawContactOrExtraInfo = contactOrDeviceNote,
                                    category = selectedCategory,
                                    rawMessage = messageText,
                                    honeypot = honeypotText
                                )
                            ) {
                                is FeedbackValidationOutcome.Blocked -> {
                                    errorMessage = validation.reason
                                }
                                is FeedbackValidationOutcome.Valid -> {
                                    isSubmitting = true
                                    errorMessage = null
                                    coroutineScope.launch {
                                        val res = SupportAndFeedbackUtils.submitFeedbackReport(
                                            context = context,
                                            sp = sp,
                                            item = validation.sanitizedItem,
                                            contentHash = validation.contentHash
                                        )
                                        isSubmitting = false
                                        statusSuccessMessage = res.userMessage
                                        messageText = ""
                                        feedbackHistory = SupportAndFeedbackUtils.loadFeedbackHistory(sp)
                                        onShowMessage(res.userMessage)
                                    }
                                }
                            }
                        },
                        enabled = !isSubmitting,
                        modifier = Modifier
                            .weight(0.58f)
                            .height(46.dp)
                            .testTag("submit_feedback_sheet_btn"),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        AutoSizeSingleLineText(
                            text = if (isSubmitting) "Submitting..." else "Submit Report",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DeveloperSupportAndFeedbackSection(
    onOpenQuickGuide: () -> Unit,
    onOpenBugReport: () -> Unit
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Developer, Support & Bug Report (Google Sheet Sync)",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("developer_support_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .clickable {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(SupportAndFeedbackUtils.PORTFOLIO_URL))
                                )
                            }
                        }
                        .testTag("developer_portfolio_item"),
                    leadingContent = {
                        Icon(Icons.Default.PersonPin, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    headlineContent = {
                        Text("Created by ${SupportAndFeedbackUtils.DEVELOPER_NAME}", fontWeight = FontWeight.Bold)
                    },
                    supportingContent = {
                        Text("Portfolio: ${SupportAndFeedbackUtils.PORTFOLIO_URL} • ${SupportAndFeedbackUtils.LICENSE_NAME}")
                    },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open Portfolio")
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                ListItem(
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .clickable { onOpenBugReport() }
                        .testTag("open_bug_report_settings_item"),
                    leadingContent = {
                        Icon(Icons.Default.BugReport, contentDescription = null, tint = Color(0xFFD84315))
                    },
                    headlineContent = {
                        Text("Report a Bug / Send Feedback", fontWeight = FontWeight.SemiBold)
                    },
                    supportingContent = {
                        Text("Syncs to Google Sheet with anti-spam rate limiting, or email ${SupportAndFeedbackUtils.SUPPORT_EMAIL}")
                    },
                    trailingContent = {
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                ListItem(
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .clickable { onOpenQuickGuide() }
                        .testTag("open_app_guide_settings_item"),
                    leadingContent = {
                        Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    headlineContent = {
                        Text("App Quick Start Guide", fontWeight = FontWeight.SemiBold)
                    },
                    supportingContent = {
                        Text("Learn how to use Money Ledger, Vehicle Fuel km/L, Meditation Timer & Offline Smart Entry")
                    },
                    trailingContent = {
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                )
            }
        }
    }
}

package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.BiometricHelper
import com.example.ui.NotificationHelper
import com.example.utils.FormatUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var name by remember { mutableStateOf(FormatUtils.resolveUserDisplayName(prefs)) }
    var email by remember { mutableStateOf(prefs.getString("user_email", "kutub@app.local") ?: "kutub@app.local") }
    var tzMode by remember { mutableStateOf(prefs.getString("greeting_tz_mode", "IST") ?: "IST") }
    var periodOverride by remember { mutableStateOf(prefs.getString("greeting_period_override", "AUTO") ?: "AUTO") }

    var isEditing by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(name) }
    var editEmail by remember { mutableStateOf(email) }

    var currentNowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentNowMillis = System.currentTimeMillis()
            delay(5_000L)
        }
    }

    val greetingSnapshot = remember(currentNowMillis, tzMode, periodOverride) {
        FormatUtils.computeGreetingTimeSnapshot(
            nowMillis = currentNowMillis,
            timeZoneMode = tzMode,
            overridePeriod = periodOverride
        )
    }

    var hasNotifPerm by remember { mutableStateOf(NotificationHelper.hasNotificationPermission(context)) }
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPerm = granted
        coroutineScope.launch {
            if (granted) {
                NotificationHelper.scheduleDailyReminder(context)
                snackbarHostState.showSnackbar("Notification permission granted!")
            } else {
                snackbarHostState.showSnackbar("Notification permission denied.")
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text("Profile, Greeting & Permissions", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. User Profile Card (કુતુબ)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_user_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        val initial = if (name.isNotBlank()) name.first().uppercase() else "ક"
                        Text(
                            initial,
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (isEditing) {
                        OutlinedTextField(
                            value = editName,
                            onValueChange = { editName = it },
                            label = { Text("Display Name (ગુજરાતી / English)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_name_input"),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AssistChip(
                                onClick = { editName = "કુતુબ" },
                                label = { Text("Set કુતુબ (Gujarati)") },
                                leadingIcon = { Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                            AssistChip(
                                onClick = { editName = "Kutub" },
                                label = { Text("Set Kutub (English)") }
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = editEmail,
                            onValueChange = { editEmail = it },
                            label = { Text("Email / Subtitle") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_email_input"),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    editName = name
                                    editEmail = email
                                    isEditing = false
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Cancel")
                            }
                            Button(
                                onClick = {
                                    val finalName = editName.trim().ifEmpty { FormatUtils.DEFAULT_USER_NAME_GUJARATI }
                                    name = finalName
                                    email = editEmail.trim()
                                    prefs.edit()
                                        .putString("user_name", name)
                                        .putString("user_email", email)
                                        .apply()
                                    isEditing = false
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Saved profile name: $name")
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("profile_save_btn")
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Save Profile")
                            }
                        }
                    } else {
                        Text(
                            name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            email,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    editName = name
                                    editEmail = email
                                    isEditing = true
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("profile_edit_btn")
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Edit Name")
                            }
                            if (name != "કુતુબ") {
                                OutlinedButton(
                                    onClick = {
                                        name = "કુતુબ"
                                        editName = "કુતુબ"
                                        prefs.edit().putString("user_name", "કુતુબ").apply()
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Name set to કુતુબ")
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Use કુતુબ")
                                }
                            }
                        }
                    }
                }
            }

            // 2. Top-Left Time & Greeting Configuration Card (Morning, Afternoon, Evening, Night)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_greeting_time_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Top-Left Greeting & Live Time Settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Live Top-Left Header Preview
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                "LIVE TOP-LEFT HEADER PREVIEW",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${greetingSnapshot.greetingEnglish}, $name",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${greetingSnapshot.formattedTime} ${greetingSnapshot.timeZoneLabel} • ${greetingSnapshot.greetingGujarati} • ${greetingSnapshot.formattedDate}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Text(
                        "Time Zone for Greeting & Clock",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = tzMode == "IST",
                            onClick = {
                                tzMode = "IST"
                                prefs.edit().putString("greeting_tz_mode", "IST").apply()
                            },
                            label = { Text("India Time (IST +5:30)") },
                            leadingIcon = if (tzMode == "IST") {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                        FilterChip(
                            selected = tzMode == "SYSTEM",
                            onClick = {
                                tzMode = "SYSTEM"
                                prefs.edit().putString("greeting_tz_mode", "SYSTEM").apply()
                            },
                            label = { Text("Device Timezone") },
                            leadingIcon = if (tzMode == "SYSTEM") {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }

                    Text(
                        "Greeting Mode (Auto by Time or Test Period)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "• Morning (5:00 AM – 11:59 AM): Good morning (સુપ્રભાત)\n" +
                        "• Afternoon (12:00 PM – 4:59 PM): Good afternoon (શુભ બપોર)\n" +
                        "• Evening (5:00 PM – 8:59 PM): Good evening (શુભ સાંજ)\n" +
                        "• Night (9:00 PM – 4:59 AM): Good night (શુભ રાત્રિ)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "AUTO" to "Auto (Live Clock)",
                            "MORNING" to "Morning (સુપ્રભાત)",
                            "AFTERNOON" to "Afternoon (શુભ બપોર)",
                            "EVENING" to "Evening (શુભ સાંજ)",
                            "NIGHT" to "Night (શુભ રાત્રિ)"
                        ).forEach { (key, label) ->
                            FilterChip(
                                selected = periodOverride == key,
                                onClick = {
                                    periodOverride = key
                                    prefs.edit().putString("greeting_period_override", key).apply()
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }

            // 3. App Permissions & Working Features Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_permissions_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "App Permissions & Why કુતુબ Uses Them",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "કુતુબ follows strict least-privilege privacy. It only uses the permissions below:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val biometricReady = remember(context) { BiometricHelper.isBiometricAvailable(context) }

                    PermissionStatusRow(
                        title = "1. Notifications (POST_NOTIFICATIONS)",
                        subtitle = "Runtime permission (Android 13+) for Daily Expense Reminders, Task & Habit Alarms, Bill Due Alerts, and Document Vault Expiry warnings.",
                        statusText = if (hasNotifPerm) "Granted" else "Tap below to allow",
                        isGranted = hasNotifPerm
                    )
                    HorizontalDivider()

                    PermissionStatusRow(
                        title = "2. Exact Alarms & Vibration (SCHEDULE_EXACT_ALARM, VIBRATE)",
                        subtitle = "Triggers scheduled task reminders, daily 8:00 PM expense log alerts, and habit reminders on time.",
                        statusText = "Active",
                        isGranted = true
                    )
                    HorizontalDivider()

                    PermissionStatusRow(
                        title = "3. Biometric Hardware (USE_BIOMETRIC)",
                        subtitle = "Optional Fingerprint or Face Unlock alongside your 4–6 digit PIN for App Lock and Document Vault.",
                        statusText = if (biometricReady) "Enrolled" else "PIN Fallback Active",
                        isGranted = true
                    )
                    HorizontalDivider()

                    PermissionStatusRow(
                        title = "4. Network & Internet (INTERNET, ACCESS_NETWORK_STATE)",
                        subtitle = "Used ONLY for Gemini AI Assistant (Smart Entry, Receipt Scan, Insights) and optional Firebase Cloud Sync. All 12 modules work 100% offline.",
                        statusText = "Active",
                        isGranted = true
                    )
                    HorizontalDivider()

                    PermissionStatusRow(
                        title = "5. Zero Storage/Camera Permissions (System Picker & SAF)",
                        subtitle = "Document Vault & Backup/Restore use Android's secure Photo Picker & Storage Access Framework—no invasive gallery or file storage permission is ever asked.",
                        statusText = "Privacy Safe",
                        isGranted = true
                    )

                    Spacer(Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (!hasNotifPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            Button(
                                onClick = {
                                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("request_notif_permission_btn")
                            ) {
                                Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Allow Notifications")
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                if (!NotificationHelper.hasNotificationPermission(context) &&
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                                ) {
                                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    NotificationHelper.sendTaskNotification(
                                        context = context,
                                        title = "કુતુબ • ${greetingSnapshot.greetingEnglish}!",
                                        message = "Your reminders and notifications are working properly (${greetingSnapshot.formattedTime})."
                                    )
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Test notification sent!")
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("send_test_notification_btn")
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Test Notification")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionStatusRow(
    title: String,
    subtitle: String,
    statusText: String,
    isGranted: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Info,
            contentDescription = null,
            tint = if (isGranted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = if (isGranted) Color(0xFF2E7D32).copy(alpha = 0.14f) else MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        statusText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isGranted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.ui.BiometricHelper
import com.example.ui.NotificationHelper
import com.example.viewmodel.FinanceViewModel
import androidx.compose.foundation.BorderStroke

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController, viewModel: FinanceViewModel) {
    val context = LocalContext.current
    val privacyMode by viewModel.privacyMode.collectAsStateWithLifecycle()
    val appLockEnabled by viewModel.appLockEnabled.collectAsStateWithLifecycle()

    val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
    var reminderEnabled by remember { mutableStateOf(prefs.getBoolean("daily_reminder", false)) }
    var themeMode by remember { mutableStateOf(prefs.getString("theme_mode", "System") ?: "System") }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text("More", fontWeight = FontWeight.Bold) }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.clickable { navController.navigate("analytics") },
                            headlineContent = { Text("Analytics & Reports", fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text("View spending insights") },
                            trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) }
                        )
                    }
                }
            }

            item {
                Text("Appearance", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.clickable {
                                themeMode = when (themeMode) {
                                    "System" -> "Light"
                                    "Light" -> "Dark"
                                    else -> "System"
                                }
                                prefs.edit().putString("theme_mode", themeMode).apply()
                            },
                            headlineContent = { Text("App Theme", fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text(themeMode) },
                            trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) }
                        )
                    }
                }
            }

            item {
                Text("Notifications", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                            headlineContent = { Text("Enable Daily Reminders", fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text("Get notified every evening at 8 PM") },
                            trailingContent = {
                                Switch(
                                    checked = reminderEnabled,
                                    onCheckedChange = { checked ->
                                        reminderEnabled = checked
                                        prefs.edit().putBoolean("daily_reminder", checked).apply()
                                        if (checked) NotificationHelper.scheduleDailyReminder(context)
                                    }
                                )
                            }
                        )
                    }
                }
            }

            item {
                Text("Privacy & Security", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                            headlineContent = { Text("Privacy Mode", fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text("Hide sensitive amounts") },
                            trailingContent = {
                                Switch(checked = privacyMode, onCheckedChange = { viewModel.togglePrivacyMode() })
                            }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                            headlineContent = { Text("Biometric App Lock", fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text("Require authentication to open app") },
                            trailingContent = {
                                Switch(
                                    checked = appLockEnabled,
                                    onCheckedChange = {
                                        if (it) {
                                            if (BiometricHelper.isBiometricAvailable(context)) {
                                                viewModel.toggleAppLock(true)
                                            }
                                        } else {
                                            viewModel.toggleAppLock(false)
                                        }
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

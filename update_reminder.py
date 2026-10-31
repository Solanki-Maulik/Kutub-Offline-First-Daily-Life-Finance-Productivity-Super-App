import re

with open("app/src/main/java/com/example/ui/screens/SettingsScreen.kt", "r") as f:
    content = f.read()

replacement = """
                        var reminderEnabled by remember { mutableStateOf(context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE).getBoolean("daily_reminder", false)) }
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                            headlineContent = { Text("Enable Daily Reminders", fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text("Get notified every evening at 8 PM") },
                            trailingContent = {
                                Switch(
                                    checked = reminderEnabled,
                                    onCheckedChange = { checked ->
                                        reminderEnabled = checked
                                        context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE).edit().putBoolean("daily_reminder", checked).apply()
                                        if (checked) {
                                            NotificationHelper.scheduleDailyReminder(context)
                                        }
                                    }
                                )
                            }
                        )"""

# Use regex to find the ListItem for Daily Reminders
pattern = re.compile(r'ListItem\([\s\S]*?Text\("Enable Daily Reminders"[\s\S]*?\)[\s\S]*?\)(?=\s*\n)', re.MULTILINE)
content = re.sub(pattern, replacement, content)

with open("app/src/main/java/com/example/ui/screens/SettingsScreen.kt", "w") as f:
    f.write(content)

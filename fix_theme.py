import re

with open("app/src/main/java/com/example/ui/screens/SettingsScreen.kt", "r") as f:
    content = f.read()

replacement = """
                        val themeMode = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE).getString("theme_mode", "System") ?: "System"
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.clickable {
                                val nextTheme = when (themeMode) {
                                    "System" -> "Light"
                                    "Light" -> "Dark"
                                    else -> "System"
                                }
                                context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE).edit().putString("theme_mode", nextTheme).apply()
                            },
                            headlineContent = { Text("App Theme", fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text(themeMode) },
                            trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) }
                        )"""

pattern = re.compile(r'ListItem\([\s\S]*?Text\("App Theme"[\s\S]*?\)[\s\S]*?\)(?=\s*\n)', re.MULTILINE)
content = re.sub(pattern, replacement, content)

with open("app/src/main/java/com/example/ui/screens/SettingsScreen.kt", "w") as f:
    f.write(content)

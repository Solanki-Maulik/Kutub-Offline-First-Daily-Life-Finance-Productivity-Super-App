sed -i 's/MyApplicationTheme(/MyApplicationTheme(darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(), /g' app/src/main/java/com/example/ui/theme/Theme.kt
sed -i 's/colorScheme = LightColorScheme,/colorScheme = if (darkTheme) androidx.compose.material3.darkColorScheme() else LightColorScheme,/g' app/src/main/java/com/example/ui/theme/Theme.kt

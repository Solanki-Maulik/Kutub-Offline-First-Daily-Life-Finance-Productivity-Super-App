sed -i 's/popUpTo(navController.graph.startDestinationId) { saveState = true }/popUpTo(navController.graph.startDestinationId) { saveState = false }/g' app/src/main/java/com/example/ui/MainScreen.kt
sed -i 's/restoreState = true/restoreState = false/g' app/src/main/java/com/example/ui/MainScreen.kt

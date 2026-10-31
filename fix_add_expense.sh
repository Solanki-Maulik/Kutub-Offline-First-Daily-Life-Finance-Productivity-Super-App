sed -i 's/Modifier.menuAnchor().fillMaxWidth()/Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()/g' app/src/main/java/com/example/ui/screens/AddExpenseScreen.kt
sed -i 's/columns = GridCells.Fixed(4),/columns = GridCells.Fixed(4),\n                    modifier = Modifier.heightIn(max = 200.dp),/g' app/src/main/java/com/example/ui/screens/AddExpenseScreen.kt

import re

with open("app/src/main/java/com/example/ui/screens/DebtsScreen.kt", "r") as f:
    content = f.read()

# We will inject a TabRow above the list, and calculate totals.
# First, let's find the main Scaffold's innerPadding column
top_part = """    var selectedTab by remember { mutableStateOf(0) }
    val iOwe = debts.filter { !it.isOwedToMe && !it.isSettled }
    val owedToMe = debts.filter { it.isOwedToMe && !it.isSettled }
    
    val totalIOwe = iOwe.sumOf { it.amount }
    val totalOwedToMe = owedToMe.sumOf { it.amount }

    Scaffold("""

content = content.replace("    Scaffold(", top_part)

column_part = """        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            TabRow(selectedTabIndex = selectedTab, containerColor = MaterialTheme.colorScheme.background) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Owed To Me (\\u20B9${totalOwedToMe.toInt()})", fontWeight = FontWeight.Bold) })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("I Owe (\\u20B9${totalIOwe.toInt()})", fontWeight = FontWeight.Bold) })
            }
            Spacer(Modifier.height(16.dp))
            val displayList = if (selectedTab == 0) owedToMe else iOwe
            if (displayList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No active debts here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(displayList) { debt ->
                        val color = if (debt.isOwedToMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error"""

# Replace the LazyColumn in the original code
pattern = re.compile(r'        LazyColumn\([\s\S]*?val color = if \(debt\.isOwedToMe\) MaterialTheme\.colorScheme\.primary else MaterialTheme\.colorScheme\.error', re.MULTILINE)
content = re.sub(pattern, column_part, content)

with open("app/src/main/java/com/example/ui/screens/DebtsScreen.kt", "w") as f:
    f.write(content)

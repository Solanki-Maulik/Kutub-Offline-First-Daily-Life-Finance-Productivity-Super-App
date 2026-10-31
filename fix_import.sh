sed -i '1d' app/src/main/java/com/example/viewmodel/FinanceViewModel.kt
sed -i 's/package com.example.viewmodel/package com.example.viewmodel\n\nimport kotlinx.coroutines.flow.map/g' app/src/main/java/com/example/viewmodel/FinanceViewModel.kt

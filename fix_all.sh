sed -i 's/viewModel.addTransaction(tx)/viewModel.addTransaction(tx.amount, tx.isIncome, tx.category, tx.account, tx.note, tx.timestamp)/g' app/src/main/java/com/example/ui/screens/AddExpenseScreen.kt
sed -i 's/viewModel.updateTransaction(tx)/viewModel.updateTransaction(tx.id, tx.amount, tx.isIncome, tx.category, tx.account, tx.note, tx.timestamp)/g' app/src/main/java/com/example/ui/screens/AddExpenseScreen.kt
sed -i 's/viewModel.addDebt(Debt(personName = newPersonName.trim(), amount = amount, isOwedToMe = newIsOwedToMe))/viewModel.addDebt(newPersonName.trim(), amount, newIsOwedToMe)/g' app/src/main/java/com/example/ui/screens/DebtsScreen.kt
sed -i 's/viewModel.settleDebt(debt.id)/viewModel.settleDebt(debt)/g' app/src/main/java/com/example/ui/screens/DebtsScreen.kt

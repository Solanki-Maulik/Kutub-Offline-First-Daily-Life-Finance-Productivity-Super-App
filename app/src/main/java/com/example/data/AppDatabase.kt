package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Transaction::class,
        Budget::class,
        Goal::class,
        GoalContribution::class,
        Debt::class,
        Account::class,
        MoneyNote::class,
        Category::class,
        Person::class,
        UdhaarEntry::class,
        UdhaarRepayment::class,
        RecurringTransaction::class,
        BillReminder::class,
        TaskItem::class,
        ScheduleEvent::class,
        TaskStreak::class,
        SplitGroup::class,
        SplitExpense::class,
        SplitSettlement::class,
        Habit::class,
        HabitLog::class,
        InvestmentHolding::class,
        LiabilityItem::class,
        VaultDocument::class
    ],
    version = 14,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun goalDao(): GoalDao
    abstract fun debtDao(): DebtDao
    abstract fun accountDao(): AccountDao
    abstract fun moneyNoteDao(): MoneyNoteDao
    abstract fun categoryDao(): CategoryDao
    abstract fun udhaarDao(): UdhaarDao
    abstract fun recurringDao(): RecurringDao
    abstract fun billDao(): BillDao
    abstract fun taskDao(): TaskDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun splitDao(): SplitDao
    abstract fun habitDao(): HabitDao
    abstract fun investmentDao(): InvestmentDao
    abstract fun documentVaultDao(): DocumentVaultDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "rupee_story_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

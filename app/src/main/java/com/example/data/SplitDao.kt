package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SplitDao {
    @Query("SELECT * FROM split_groups ORDER BY createdAt DESC")
    fun getAllGroups(): Flow<List<SplitGroup>>

    @Query("SELECT * FROM split_groups WHERE id = :id")
    fun getGroupById(id: Long): Flow<SplitGroup?>

    @Query("SELECT * FROM split_groups WHERE id = :id")
    suspend fun getGroupByIdSync(id: Long): SplitGroup?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: SplitGroup): Long

    @Update
    suspend fun updateGroup(group: SplitGroup)

    @Query("DELETE FROM split_groups WHERE id = :id")
    suspend fun deleteGroupById(id: Long)

    // Expenses
    @Query("SELECT * FROM split_expenses ORDER BY timestamp DESC")
    fun getAllSplitExpenses(): Flow<List<SplitExpense>>

    @Query("SELECT * FROM split_expenses WHERE groupId = :groupId ORDER BY timestamp DESC")
    fun getExpensesForGroup(groupId: Long): Flow<List<SplitExpense>>

    @Query("SELECT * FROM split_expenses WHERE groupId = :groupId ORDER BY timestamp DESC")
    suspend fun getExpensesForGroupSync(groupId: Long): List<SplitExpense>

    @Query("SELECT * FROM split_expenses WHERE id = :id")
    suspend fun getSplitExpenseById(id: Long): SplitExpense?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSplitExpense(expense: SplitExpense): Long

    @Update
    suspend fun updateSplitExpense(expense: SplitExpense)

    @Query("DELETE FROM split_expenses WHERE id = :id")
    suspend fun deleteSplitExpenseById(id: Long)

    @Query("DELETE FROM split_expenses WHERE groupId = :groupId")
    suspend fun deleteExpensesForGroup(groupId: Long)

    // Settlements
    @Query("SELECT * FROM split_settlements ORDER BY timestamp DESC")
    fun getAllSettlements(): Flow<List<SplitSettlement>>

    @Query("SELECT * FROM split_settlements WHERE groupId = :groupId ORDER BY timestamp DESC")
    fun getSettlementsForGroup(groupId: Long): Flow<List<SplitSettlement>>

    @Query("SELECT * FROM split_settlements WHERE groupId = :groupId ORDER BY timestamp DESC")
    suspend fun getSettlementsForGroupSync(groupId: Long): List<SplitSettlement>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettlement(settlement: SplitSettlement): Long

    @Query("DELETE FROM split_settlements WHERE id = :id")
    suspend fun deleteSettlementById(id: Long)

    @Query("DELETE FROM split_settlements WHERE groupId = :groupId")
    suspend fun deleteSettlementsForGroup(groupId: Long)
}

package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BillDao {
    @Query("SELECT * FROM bill_reminders ORDER BY dueDate ASC")
    fun getAllBills(): Flow<List<BillReminder>>

    @Query("SELECT * FROM bill_reminders WHERE isPaid = 0 ORDER BY dueDate ASC")
    fun getPendingBills(): Flow<List<BillReminder>>

    @Query("SELECT * FROM bill_reminders WHERE id = :id")
    suspend fun getBillById(id: Long): BillReminder?

    @Query("SELECT * FROM bill_reminders WHERE isPaid = 0")
    suspend fun getPendingBillsSync(): List<BillReminder>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBill(bill: BillReminder): Long

    @Update
    suspend fun updateBill(bill: BillReminder)

    @Query("DELETE FROM bill_reminders WHERE id = :id")
    suspend fun deleteBillById(id: Long)
}

package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransactionById(id: Int): Transaction?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: Transaction): Long

    @Update
    suspend fun update(transaction: Transaction)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("UPDATE transactions SET account = :newAccount WHERE LOWER(account) = LOWER(:oldAccount)")
    suspend fun reassignAccountForTransactions(oldAccount: String, newAccount: String)

    @Query("DELETE FROM transactions WHERE LOWER(account) = LOWER(:accountName)")
    suspend fun deleteTransactionsForAccount(accountName: String)
}

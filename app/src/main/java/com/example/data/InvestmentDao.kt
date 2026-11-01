package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface InvestmentDao {
    @Query("SELECT * FROM investment_holdings ORDER BY currentValue DESC, updatedAt DESC")
    fun getAllHoldings(): Flow<List<InvestmentHolding>>

    @Query("SELECT * FROM investment_holdings WHERE id = :id")
    suspend fun getHoldingById(id: Long): InvestmentHolding?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHolding(holding: InvestmentHolding): Long

    @Update
    suspend fun updateHolding(holding: InvestmentHolding)

    @Query("DELETE FROM investment_holdings WHERE id = :id")
    suspend fun deleteHoldingById(id: Long)

    // Liabilities
    @Query("SELECT * FROM liability_items ORDER BY remainingBalance DESC, updatedAt DESC")
    fun getAllLiabilities(): Flow<List<LiabilityItem>>

    @Query("SELECT * FROM liability_items WHERE id = :id")
    suspend fun getLiabilityById(id: Long): LiabilityItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLiability(liability: LiabilityItem): Long

    @Update
    suspend fun updateLiability(liability: LiabilityItem)

    @Query("DELETE FROM liability_items WHERE id = :id")
    suspend fun deleteLiabilityById(id: Long)
}

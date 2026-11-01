package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentVaultDao {
    @Query("SELECT * FROM vault_documents ORDER BY CASE WHEN expiryDate IS NULL THEN 1 ELSE 0 END, expiryDate ASC, createdAt DESC")
    fun getAllDocuments(): Flow<List<VaultDocument>>

    @Query("SELECT * FROM vault_documents WHERE id = :id")
    suspend fun getDocumentById(id: Long): VaultDocument?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(doc: VaultDocument): Long

    @Update
    suspend fun updateDocument(doc: VaultDocument)

    @Query("DELETE FROM vault_documents WHERE id = :id")
    suspend fun deleteDocumentById(id: Long)
}

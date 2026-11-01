package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MoneyNoteDao {
    @Query("SELECT * FROM money_notes ORDER BY isPinned DESC, updatedAt DESC, timestamp DESC")
    fun getAllNotes(): Flow<List<MoneyNote>>

    @Query("SELECT * FROM money_notes WHERE isArchived = 0 ORDER BY isPinned DESC, updatedAt DESC, timestamp DESC")
    fun getActiveNotes(): Flow<List<MoneyNote>>

    @Query("SELECT * FROM money_notes WHERE isArchived = 1 ORDER BY updatedAt DESC")
    fun getArchivedNotes(): Flow<List<MoneyNote>>

    @Query("SELECT * FROM money_notes WHERE id = :id")
    suspend fun getNoteById(id: Long): MoneyNote?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: MoneyNote): Long

    @Update
    suspend fun updateNote(note: MoneyNote)

    @Query("DELETE FROM money_notes WHERE id = :id")
    suspend fun deleteNoteById(id: Long)

    @Query("UPDATE money_notes SET isPinned = :isPinned, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updatePinStatus(id: Long, isPinned: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE money_notes SET isArchived = :isArchived, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateArchiveStatus(id: Long, isArchived: Boolean, updatedAt: Long = System.currentTimeMillis())
}

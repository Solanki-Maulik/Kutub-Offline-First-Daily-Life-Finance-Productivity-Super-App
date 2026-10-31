package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MoneyNoteDao {
    @Query("SELECT * FROM money_notes ORDER BY timestamp DESC")
    fun getAllNotes(): Flow<List<MoneyNote>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: MoneyNote)

    @Update
    suspend fun updateNote(note: MoneyNote)

    @Query("DELETE FROM money_notes WHERE id = :id")
    suspend fun deleteNoteById(id: Int)
}

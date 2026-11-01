package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UdhaarDao {

    // People
    @Query("SELECT * FROM people ORDER BY name ASC")
    fun getAllPeople(): Flow<List<Person>>

    @Query("SELECT * FROM people WHERE id = :personId LIMIT 1")
    fun getPersonById(personId: Long): Flow<Person?>

    @Query("SELECT * FROM people WHERE id = :personId LIMIT 1")
    suspend fun getPersonByIdSync(personId: Long): Person?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPerson(person: Person): Long

    @Update
    suspend fun updatePerson(person: Person)

    @Query("DELETE FROM people WHERE id = :personId")
    suspend fun deletePersonById(personId: Long)

    // Entries
    @Query("SELECT * FROM udhaar_entries ORDER BY date DESC")
    fun getAllEntries(): Flow<List<UdhaarEntry>>

    @Query("SELECT * FROM udhaar_entries WHERE personId = :personId ORDER BY date DESC")
    fun getEntriesForPerson(personId: Long): Flow<List<UdhaarEntry>>

    @Query("SELECT * FROM udhaar_entries WHERE personId = :personId ORDER BY date DESC")
    suspend fun getEntriesForPersonSync(personId: Long): List<UdhaarEntry>

    @Query("SELECT * FROM udhaar_entries WHERE id = :id LIMIT 1")
    suspend fun getEntryById(id: Long): UdhaarEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: UdhaarEntry): Long

    @Update
    suspend fun updateEntry(entry: UdhaarEntry)

    @Query("DELETE FROM udhaar_entries WHERE id = :id")
    suspend fun deleteEntryById(id: Long)

    @Query("DELETE FROM udhaar_entries WHERE personId = :personId")
    suspend fun deleteEntriesForPerson(personId: Long)

    // Repayments
    @Query("SELECT * FROM udhaar_repayments ORDER BY date DESC")
    fun getAllRepayments(): Flow<List<UdhaarRepayment>>

    @Query("SELECT * FROM udhaar_repayments WHERE entryId = :entryId ORDER BY date ASC")
    fun getRepaymentsForEntry(entryId: Long): Flow<List<UdhaarRepayment>>

    @Query("SELECT * FROM udhaar_repayments WHERE entryId = :entryId ORDER BY date ASC")
    suspend fun getRepaymentsForEntrySync(entryId: Long): List<UdhaarRepayment>

    @Query("SELECT * FROM udhaar_repayments WHERE personId = :personId ORDER BY date DESC")
    fun getRepaymentsForPerson(personId: Long): Flow<List<UdhaarRepayment>>

    @Query("SELECT * FROM udhaar_repayments WHERE id = :id LIMIT 1")
    suspend fun getRepaymentById(id: Long): UdhaarRepayment?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRepayment(repayment: UdhaarRepayment): Long

    @Query("DELETE FROM udhaar_repayments WHERE id = :id")
    suspend fun deleteRepaymentById(id: Long)

    @Query("DELETE FROM udhaar_repayments WHERE entryId = :entryId")
    suspend fun deleteRepaymentsForEntry(entryId: Long)

    @Query("DELETE FROM udhaar_repayments WHERE personId = :personId")
    suspend fun deleteRepaymentsForPerson(personId: Long)
}

package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedule_events ORDER BY startTime ASC")
    fun getAllEvents(): Flow<List<ScheduleEvent>>

    @Query("SELECT * FROM schedule_events WHERE startTime >= :start AND endTime <= :end ORDER BY startTime ASC")
    fun getEventsInRange(start: Long, end: Long): Flow<List<ScheduleEvent>>

    @Query("SELECT * FROM schedule_events WHERE id = :id")
    suspend fun getEventById(id: Long): ScheduleEvent?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: ScheduleEvent): Long

    @Update
    suspend fun updateEvent(event: ScheduleEvent)

    @Query("DELETE FROM schedule_events WHERE id = :id")
    suspend fun deleteEventById(id: Long)
}

package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY createdAt DESC")
    fun getAllTasks(): Flow<List<TaskItem>>

    @Query("SELECT * FROM tasks WHERE status != 'DONE' ORDER BY CASE WHEN dueDateTime IS NULL THEN 1 ELSE 0 END, dueDateTime ASC")
    fun getActiveTasks(): Flow<List<TaskItem>>

    @Query("SELECT * FROM tasks WHERE status = 'DONE' ORDER BY completedAt DESC")
    fun getCompletedTasks(): Flow<List<TaskItem>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun getTaskById(id: Long): Flow<TaskItem?>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskByIdSync(id: Long): TaskItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskItem): Long

    @Update
    suspend fun updateTask(task: TaskItem)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTaskById(id: Long)

    // Streak
    @Query("SELECT * FROM task_streaks WHERE id = 1")
    fun getStreak(): Flow<TaskStreak?>

    @Query("SELECT * FROM task_streaks WHERE id = 1")
    suspend fun getStreakSync(): TaskStreak?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateStreak(streak: TaskStreak)
}

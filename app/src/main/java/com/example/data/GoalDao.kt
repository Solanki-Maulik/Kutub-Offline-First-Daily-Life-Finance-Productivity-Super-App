package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY id DESC")
    fun getAllGoals(): Flow<List<Goal>>

    @Query("SELECT * FROM goals WHERE id = :id")
    fun getGoalById(id: Int): Flow<Goal?>

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun getGoalByIdSync(id: Int): Goal?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoal(goal: Goal): Long

    @Update
    suspend fun updateGoal(goal: Goal)

    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun deleteGoalById(id: Int)

    // Contributions
    @Query("SELECT * FROM goal_contributions WHERE goalId = :goalId ORDER BY date DESC")
    fun getContributionsForGoal(goalId: Int): Flow<List<GoalContribution>>

    @Query("SELECT * FROM goal_contributions WHERE goalId = :goalId ORDER BY date DESC")
    suspend fun getContributionsForGoalSync(goalId: Int): List<GoalContribution>

    @Query("SELECT * FROM goal_contributions ORDER BY date DESC")
    fun getAllContributions(): Flow<List<GoalContribution>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContribution(contribution: GoalContribution): Long

    @Query("DELETE FROM goal_contributions WHERE id = :id")
    suspend fun deleteContributionById(id: Long)

    @Query("DELETE FROM goal_contributions WHERE goalId = :goalId")
    suspend fun deleteContributionsForGoal(goalId: Int)
}

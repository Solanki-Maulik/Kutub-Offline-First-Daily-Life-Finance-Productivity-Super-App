package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
@Entity(
    tableName = "goal_contributions",
    foreignKeys = [
        ForeignKey(
            entity = Goal::class,
            parentColumns = ["id"],
            childColumns = ["goalId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["goalId"])]
)
data class GoalContribution(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val goalId: Int,
    val amount: Double,
    val date: Long = System.currentTimeMillis(),
    val note: String = "",
    val account: String? = null
)

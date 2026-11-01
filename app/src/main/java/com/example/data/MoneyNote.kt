package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class NoteChecklistItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isChecked: Boolean = false
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "money_notes")
data class MoneyNote(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String = "",
    val content: String = "",
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val colorHex: String = "#DEFAULT",
    val tagsJson: String = "[]",
    val checklistsJson: String = "[]",
    // Optional Attachment to financial or life elements
    val attachedType: String? = null, // "TRANSACTION", "PERSON", "TASK"
    val attachedId: Long? = null,
    val attachedTitle: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

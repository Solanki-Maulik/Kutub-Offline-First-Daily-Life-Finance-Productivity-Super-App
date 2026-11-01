package com.example.utils

import androidx.compose.ui.graphics.Color
import com.example.data.MoneyNote
import com.example.data.NoteChecklistItem
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

object NoteUtils {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    
    private val checklistType = Types.newParameterizedType(List::class.java, NoteChecklistItem::class.java)
    private val checklistAdapter = moshi.adapter<List<NoteChecklistItem>>(checklistType)
    
    private val stringListType = Types.newParameterizedType(List::class.java, String::class.java)
    private val stringListAdapter = moshi.adapter<List<String>>(stringListType)

    fun parseChecklist(json: String?): List<NoteChecklistItem> {
        if (json.isNullOrBlank() || json == "[]") return emptyList()
        return try {
            checklistAdapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun serializeChecklist(items: List<NoteChecklistItem>): String {
        return try {
            checklistAdapter.toJson(items)
        } catch (e: Exception) {
            "[]"
        }
    }

    fun parseTags(json: String?): List<String> {
        if (json.isNullOrBlank() || json == "[]") return emptyList()
        return try {
            stringListAdapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun serializeTags(tags: List<String>): String {
        return try {
            stringListAdapter.toJson(tags.map { it.trim().removePrefix("#") }.filter { it.isNotBlank() })
        } catch (e: Exception) {
            "[]"
        }
    }

    data class NoteColorOption(
        val hex: String,
        val label: String,
        val lightColor: Color,
        val darkColor: Color
    )

    val COLOR_OPTIONS = listOf(
        NoteColorOption("#DEFAULT", "Default", Color(0xFFFAFAFA), Color(0xFF1E1E1E)),
        NoteColorOption("#FFEBEE", "Coral", Color(0xFFFFEBEE), Color(0xFF3E2723)),
        NoteColorOption("#FFF3E0", "Peach", Color(0xFFFFF3E0), Color(0xFF3E2C1C)),
        NoteColorOption("#FFFDE7", "Lemon", Color(0xFFFFFDE7), Color(0xFF333010)),
        NoteColorOption("#E8F5E9", "Mint", Color(0xFFE8F5E9), Color(0xFF1B3320)),
        NoteColorOption("#E0F7FA", "Aqua", Color(0xFFE0F7FA), Color(0xFF0D3338)),
        NoteColorOption("#E3F2FD", "Sky", Color(0xFFE3F2FD), Color(0xFF132F4C)),
        NoteColorOption("#F3E5F5", "Lavender", Color(0xFFF3E5F5), Color(0xFF301B3B)),
        NoteColorOption("#FCE4EC", "Rose", Color(0xFFFCE4EC), Color(0xFF3B1A24))
    )

    fun getNoteColor(hex: String, isDark: Boolean): Color {
        val option = COLOR_OPTIONS.firstOrNull { it.hex.equals(hex, ignoreCase = true) }
        return if (option != null) {
            if (isDark) option.darkColor else option.lightColor
        } else {
            try {
                val parsed = android.graphics.Color.parseColor(hex)
                Color(parsed)
            } catch (e: Exception) {
                if (isDark) Color(0xFF1E1E1E) else Color(0xFFFFFFFF)
            }
        }
    }

    fun matchesQuery(note: MoneyNote, query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.trim().lowercase()
        if (note.title.lowercase().contains(q)) return true
        if (note.content.lowercase().contains(q)) return true
        if (note.attachedTitle?.lowercase()?.contains(q) == true) return true
        val tags = parseTags(note.tagsJson)
        if (tags.any { it.lowercase().contains(q) }) return true
        val checklists = parseChecklist(note.checklistsJson)
        if (checklists.any { it.text.lowercase().contains(q) }) return true
        return false
    }
}

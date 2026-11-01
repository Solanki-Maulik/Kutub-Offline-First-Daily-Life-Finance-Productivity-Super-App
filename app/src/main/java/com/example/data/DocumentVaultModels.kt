package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
@Entity(tableName = "vault_documents")
data class VaultDocument(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    // "Warranty", "Bill", "ID", "Insurance", "Education", "Vehicle", "Medical", "Other"
    val category: String = "Warranty",
    val issueDate: Long = System.currentTimeMillis(), // Also acts as Purchase Date for Warranty
    val expiryDate: Long? = null,
    val warrantyMonths: Int = 0, // >0 if warranty period specified
    val linkedTransactionId: Long? = null,
    val linkedTransactionSummary: String? = null,
    val localFilePath: String? = null,
    val mimeType: String? = null, // "image/jpeg" or "application/pdf"
    val fileSizeBytes: Long = 0L,
    val isSensitive: Boolean = false, // Locked behind App PIN
    val reminderEnabled: Boolean = true, // 30/7/1 days before expiry
    val tagsCsv: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class VaultStorageSummary(
    val totalDocuments: Int,
    val totalBytesUsed: Long,
    val maxRecommendedBytes: Long = 50L * 1024L * 1024L, // 50 MB warning threshold
    val usagePercent: Float,
    val isStorageWarning: Boolean,
    val expiringSoonCount: Int,
    val expiredCount: Int,
    val activeWarrantiesCount: Int
)

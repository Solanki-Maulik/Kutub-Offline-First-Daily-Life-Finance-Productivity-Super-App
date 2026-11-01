package com.example.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.data.VaultDocument
import com.example.data.VaultStorageSummary
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar
import java.util.Locale

object DocumentVaultUtils {

    val DOCUMENT_CATEGORIES = listOf(
        "Warranty",
        "Bill",
        "ID",
        "Insurance",
        "Education",
        "Vehicle",
        "Medical",
        "Other"
    )

    const val MAX_RECOMMENDED_STORAGE_BYTES = 50L * 1024L * 1024L // 50 MB local vault threshold
    const val WARNING_STORAGE_THRESHOLD_RATIO = 0.75f // Warn at 75% (37.5 MB)

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return "0 KB"
        val kb = bytes / 1024.0
        if (kb < 1024.0) {
            return String.format(Locale.US, "%.1f KB", kb)
        }
        val mb = kb / 1024.0
        return String.format(Locale.US, "%.2f MB", mb)
    }

    fun calculateExpiryFromWarrantyMonths(issueDateMillis: Long, warrantyMonths: Int): Long? {
        if (warrantyMonths <= 0) return null
        val cal = Calendar.getInstance().apply {
            timeInMillis = issueDateMillis
            add(Calendar.MONTH, warrantyMonths)
        }
        return cal.timeInMillis
    }

    fun daysUntilExpiry(expiryDateMillis: Long?, nowMillis: Long = System.currentTimeMillis()): Long? {
        if (expiryDateMillis == null) return null
        val diffMillis = expiryDateMillis - nowMillis
        return diffMillis / (24L * 60 * 60 * 1000)
    }

    fun calculateStorageSummary(
        documents: List<VaultDocument>,
        nowMillis: Long = System.currentTimeMillis()
    ): VaultStorageSummary {
        val totalBytes = documents.sumOf { it.fileSizeBytes.coerceAtLeast(0L) }
        val usagePct = ((totalBytes.toDouble() / MAX_RECOMMENDED_STORAGE_BYTES.toDouble()) * 100.0)
            .toFloat()
            .coerceIn(0f, 100f)

        var expiringSoon = 0
        var expired = 0
        var activeWarranties = 0

        documents.forEach { doc ->
            val days = daysUntilExpiry(doc.expiryDate, nowMillis)
            if (days != null) {
                if (days < 0) {
                    expired++
                } else if (days <= 30) {
                    expiringSoon++
                }
            }
            if (doc.category == "Warranty" && (days == null || days >= 0)) {
                activeWarranties++
            }
        }

        return VaultStorageSummary(
            totalDocuments = documents.size,
            totalBytesUsed = totalBytes,
            maxRecommendedBytes = MAX_RECOMMENDED_STORAGE_BYTES,
            usagePercent = usagePct,
            isStorageWarning = totalBytes >= (MAX_RECOMMENDED_STORAGE_BYTES * WARNING_STORAGE_THRESHOLD_RATIO).toLong(),
            expiringSoonCount = expiringSoon,
            expiredCount = expired,
            activeWarrantiesCount = activeWarranties
        )
    }

    /**
     * Copies a selected image or PDF into private local storage (`filesDir/vault_docs`).
     * Compresses images to JPEG (75% quality, max 1600px dimension) to save space.
     * Returns Triple(localFilePath, resolvedMimeType, sizeInBytes).
     */
    fun saveAttachmentLocally(
        context: Context,
        uri: Uri,
        explicitMimeType: String? = null
    ): Triple<String, String, Long>? {
        return try {
            val contentResolver = context.contentResolver
            val mime = explicitMimeType ?: contentResolver.getType(uri) ?: "image/jpeg"
            val vaultDir = File(context.filesDir, "vault_docs")
            if (!vaultDir.exists()) vaultDir.mkdirs()

            if (mime.contains("pdf", ignoreCase = true)) {
                val destFile = File(vaultDir, "doc_${System.currentTimeMillis()}.pdf")
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Triple(destFile.absolutePath, "application/pdf", destFile.length())
            } else {
                // Compress image locally
                val destFile = File(vaultDir, "img_${System.currentTimeMillis()}.jpg")
                val originalBitmap = contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input)
                }
                if (originalBitmap != null) {
                    val maxDim = 1600
                    val ratio = minOf(
                        1.0f,
                        maxDim.toFloat() / maxOf(originalBitmap.width, originalBitmap.height).coerceAtLeast(1)
                    )
                    val scaledBitmap = if (ratio < 1.0f) {
                        Bitmap.createScaledBitmap(
                            originalBitmap,
                            (originalBitmap.width * ratio).toInt().coerceAtLeast(1),
                            (originalBitmap.height * ratio).toInt().coerceAtLeast(1),
                            true
                        )
                    } else {
                        originalBitmap
                    }
                    FileOutputStream(destFile).use { out ->
                        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 75, out)
                    }
                    Triple(destFile.absolutePath, "image/jpeg", destFile.length())
                } else {
                    // Fallback stream copy
                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    Triple(destFile.absolutePath, "image/jpeg", destFile.length())
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    fun deleteLocalFileIfExists(path: String?) {
        if (path.isNullOrBlank()) return
        try {
            val f = File(path)
            if (f.exists()) f.delete()
        } catch (_: Exception) {
        }
    }

    /**
     * Encodes existing local Document Vault attachments as Base64 strings keyed by `localFilePath`
     * so that full JSON backups preserve the binary attachment files across wipe & restore.
     */
    fun encodeVaultFilesToBase64(documents: List<VaultDocument>): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        for (doc in documents) {
            val path = doc.localFilePath
            if (path.isNullOrBlank()) continue
            try {
                val f = File(path)
                if (f.exists() && f.isFile && f.length() <= MAX_RECOMMENDED_STORAGE_BYTES) {
                    val bytes = f.readBytes()
                    val encoded = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                    result[path] = encoded
                }
            } catch (_: Exception) {
            }
        }
        return result
    }

    /**
     * Restores Base64-encoded Document Vault attachments back to local private storage
     * and returns the updated [VaultDocument] list with valid local file paths and byte sizes.
     */
    fun restoreVaultFilesFromBase64(
        context: Context,
        documents: List<VaultDocument>,
        payloadsBase64: Map<String, String>
    ): List<VaultDocument> {
        if (documents.isEmpty()) return emptyList()
        val vaultDir = File(context.filesDir, "vault_docs").apply { if (!exists()) mkdirs() }
        return documents.map { doc ->
            val path = doc.localFilePath
            if (path.isNullOrBlank()) {
                doc
            } else {
                val b64 = payloadsBase64[path]
                if (b64.isNullOrBlank()) {
                    doc
                } else {
                    try {
                        val bytes = android.util.Base64.decode(b64, android.util.Base64.NO_WRAP)
                        val originalFile = File(path)
                        val targetFile = try {
                            originalFile.parentFile?.mkdirs()
                            FileOutputStream(originalFile).use { it.write(bytes) }
                            originalFile
                        } catch (_: Exception) {
                            val fallback = File(vaultDir, originalFile.name.ifBlank { "vault_${doc.id}.bin" })
                            FileOutputStream(fallback).use { it.write(bytes) }
                            fallback
                        }
                        doc.copy(
                            localFilePath = targetFile.absolutePath,
                            fileSizeBytes = targetFile.length()
                        )
                    } catch (_: Exception) {
                        doc
                    }
                }
            }
        }
    }
}

package com.example.utils

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.R
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

fun handleFirestoreError(exception: Exception, operationType: OperationType, path: String?): String {
    val auth = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", exception.message ?: exception.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@IgnoreExtraProperties
data class CloudBackupSnapshot(
    @DocumentId val id: String = "",
    val userId: String = "",
    val deviceName: String = "",
    val schemaVersion: Long = 2L,
    val summaryText: String = "",
    val transactionCount: Long = 0L,
    val totalItemsCount: Long = 0L,
    val backupPayload: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

class CloudSyncRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        FirebaseAuth.getInstance()
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid ?: throw FirebaseFirestoreException(
            "User must be authenticated to perform cloud sync operations",
            FirebaseFirestoreException.Code.PERMISSION_DENIED
        )
    }

    suspend fun uploadCloudBackup(
        backupId: String = "latest",
        summaryText: String,
        transactionCount: Int,
        totalItemsCount: Int,
        backupPayload: String,
        deviceName: String = "${Build.MANUFACTURER} ${Build.MODEL}".trim().ifBlank { "Android Device" }
    ): Result<String> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/unauthenticated/cloud_backups/$backupId")
            return Result.failure(e)
        }
        val safeBackupId = backupId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(64).ifBlank { "latest" }
        val docRef = db.collection("users").document(uid).collection("cloud_backups").document(safeBackupId)

        return try {
            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                val updatePayload = mapOf(
                    "deviceName" to deviceName.take(100).ifBlank { "Android Device" },
                    "schemaVersion" to 2L,
                    "summaryText" to summaryText.take(500).ifBlank { "RupeeStory Cloud Backup" },
                    "transactionCount" to transactionCount.toLong().coerceAtLeast(0L),
                    "totalItemsCount" to totalItemsCount.toLong().coerceAtLeast(0L),
                    "backupPayload" to backupPayload,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updatePayload).await()
            } else {
                val createPayload = mapOf(
                    "userId" to uid,
                    "deviceName" to deviceName.take(100).ifBlank { "Android Device" },
                    "schemaVersion" to 2L,
                    "summaryText" to summaryText.take(500).ifBlank { "RupeeStory Cloud Backup" },
                    "transactionCount" to transactionCount.toLong().coerceAtLeast(0L),
                    "totalItemsCount" to totalItemsCount.toLong().coerceAtLeast(0L),
                    "backupPayload" to backupPayload,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(createPayload).await()
            }
            Result.success(safeBackupId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun getCloudBackupById(backupId: String = "latest", targetUserId: String? = null): Result<CloudBackupSnapshot?> {
        val uid = targetUserId ?: try {
            requireUserId()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "users/unauthenticated/cloud_backups/$backupId")
            return Result.failure(e)
        }
        val docRef = db.collection("users").document(uid).collection("cloud_backups").document(backupId)
        return try {
            val snap = docRef.get().await()
            if (!snap.exists()) {
                Result.success(null)
            } else {
                val model = snap.toObject(
                    CloudBackupSnapshot::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                Result.success(model)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun getUserCloudBackups(): Result<List<CloudBackupSnapshot>> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "users/unauthenticated/cloud_backups")
            return Result.failure(e)
        }
        val colPath = "users/$uid/cloud_backups"
        return try {
            val querySnap = db.collection("users")
                .document(uid)
                .collection("cloud_backups")
                .whereEqualTo("userId", uid)
                .get()
                .await()
            val items = querySnap.toObjects(
                CloudBackupSnapshot::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ).sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
            Result.success(items)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, colPath)
            Result.failure(e)
        }
    }

    fun observeCloudBackups(): Flow<List<CloudBackupSnapshot>> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        return db.collection("users")
            .document(uid)
            .collection("cloud_backups")
            .whereEqualTo("userId", uid)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    CloudBackupSnapshot::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
    }

    suspend fun deleteCloudBackup(backupId: String): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/unauthenticated/cloud_backups/$backupId")
            return Result.failure(e)
        }
        val docRef = db.collection("users").document(uid).collection("cloud_backups").document(backupId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }
}

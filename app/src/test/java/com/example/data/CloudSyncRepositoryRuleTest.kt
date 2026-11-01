package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.example.utils.CloudSyncRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class CloudSyncRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun uploadAndFetchCloudBackup_authenticatedOwner_succeeds() = runBlocking {
    signInTestUser(ALICE_EMAIL)
    val repo = CloudSyncRepository(firestore, auth)
    val backupId = "backup_${UUID.randomUUID().toString().take(8)}"

    val uploadResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.uploadCloudBackup(
        backupId = backupId,
        summaryText = "10 Transactions, 3 Accounts",
        transactionCount = 10,
        totalItemsCount = 20,
        backupPayload = "{\"schemaVersion\":2,\"transactions\":[]}",
        deviceName = "Pixel Test"
      )
    }
    assertTrue("Expected upload success, got: ${uploadResult.exceptionOrNull()}", uploadResult.isSuccess)

    val listResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.getUserCloudBackups()
    }
    assertTrue(listResult.isSuccess)
    assertTrue(listResult.getOrThrow().any { it.id == backupId })
  }

  @Test
  fun getCloudBackupById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val aliceRepo = CloudSyncRepository(firestore, auth)
    val backupId = "backup_${UUID.randomUUID().toString().take(8)}"

    withTimeout(DEFAULT_TIMEOUT_MS) {
      aliceRepo.uploadCloudBackup(
        backupId = backupId,
        summaryText = "Alice Backup",
        transactionCount = 5,
        totalItemsCount = 10,
        backupPayload = "{\"schemaVersion\":2}"
      ).getOrThrow()
    }

    signInTestUser(BOB_EMAIL)
    val bobRepo = CloudSyncRepository(firestore, auth)
    val crossResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      bobRepo.getCloudBackupById(backupId = backupId, targetUserId = aliceUid)
    }
    assertTrue(crossResult.isFailure)
    val ex = crossResult.exceptionOrNull()?.unwrapFirestoreException()
    assertNotNull(ex)
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, ex?.code)
  }

  @Test
  fun observeCloudBackups_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
    auth.signOut()
    val repo = CloudSyncRepository(firestore, auth)

    var caught: Throwable? = null
    try {
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeCloudBackups().first()
      }
    } catch (e: Throwable) {
      caught = e
    }
    assertNotNull("Expected exception for unauthenticated listener", caught)
    val firestoreEx = caught?.unwrapFirestoreException()
    assertNotNull("Expected FirebaseFirestoreException, got $caught", firestoreEx)
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
  }

  private companion object {
    const val ALICE_EMAIL = "alice@rupeestory.test"
    const val BOB_EMAIL = "bob@rupeestory.test"
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 3000L
  }
}

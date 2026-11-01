package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.app.ActivityCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppDatabase
import com.example.data.TransactionRepository
import com.example.ui.MainScreen
import com.example.ui.NotificationHelper
import com.example.ui.screens.AppLockScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.FinanceViewModel
import com.example.viewmodel.FinanceViewModelFactory
import kotlinx.coroutines.delay

class MainActivity : FragmentActivity() {

    private val database by lazy { AppDatabase.getDatabase(this) }
    private val repository by lazy {
        TransactionRepository(
            database.transactionDao(),
            database.budgetDao(),
            database.goalDao(),
            database.debtDao(),
            database.accountDao(),
            database.moneyNoteDao(),
            database.categoryDao(),
            database.udhaarDao(),
            database.recurringDao(),
            database.billDao(),
            database.taskDao(),
            database.scheduleDao(),
            database.splitDao(),
            database.habitDao(),
            database.investmentDao(),
            database.documentVaultDao()
        )
    }

    private val viewModel: FinanceViewModel by viewModels {
        FinanceViewModelFactory(repository, applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI, ViewModels, or Firebase operations
        com.example.utils.configureAppCheck(this, intent)

        NotificationHelper.createNotificationChannel(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
        enableEdgeToEdge()

        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val appLockEnabled by viewModel.appLockEnabled.collectAsStateWithLifecycle()
            val isSessionLocked by viewModel.isSessionLocked.collectAsStateWithLifecycle()
            val appPin by viewModel.appPin.collectAsStateWithLifecycle()
            val biometricUnlockEnabled by viewModel.biometricUnlockEnabled.collectAsStateWithLifecycle()

            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner, appLockEnabled) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_STOP -> {
                            viewModel.checkInactivityAutoLock(onAppBackgrounded = true)
                        }
                        Lifecycle.Event.ON_RESUME -> {
                            viewModel.checkInactivityAutoLock(onAppBackgrounded = false)
                        }
                        else -> {}
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            // Periodic inactivity auto-lock check while app is open
            LaunchedEffect(appLockEnabled, isSessionLocked) {
                while (appLockEnabled && !isSessionLocked) {
                    delay(10_000L)
                    viewModel.checkInactivityAutoLock(onAppBackgrounded = false)
                }
            }

            val darkTheme = when (themeMode) {
                "Dark" -> true
                "Light" -> false
                else -> isSystemInDarkTheme()
            }

            MyApplicationTheme(darkTheme = darkTheme) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (appLockEnabled) {
                                Modifier.pointerInput(Unit) {
                                    var lastRecorded = 0L
                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                            if (event.type == androidx.compose.ui.input.pointer.PointerEventType.Press) {
                                                val now = System.currentTimeMillis()
                                                if (now - lastRecorded > 10_000L) {
                                                    lastRecorded = now
                                                    viewModel.recordUserInteraction()
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                Modifier
                            }
                        )
                ) {
                    if (appLockEnabled && isSessionLocked) {
                        AppLockScreen(
                            expectedPin = appPin,
                            biometricEnabled = biometricUnlockEnabled,
                            onUnlocked = { viewModel.unlockSession() }
                        )
                    } else {
                        MainScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}

package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.data.AppDatabase
import com.example.data.TransactionRepository
import com.example.ui.MainScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.FinanceViewModel
import com.example.viewmodel.FinanceViewModelFactory

import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import com.example.ui.NotificationHelper

import androidx.fragment.app.FragmentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.material3.Text
import com.example.ui.BiometricHelper

class MainActivity : FragmentActivity() {

    private val database by lazy { AppDatabase.getDatabase(this) }
    private val repository by lazy { 
        TransactionRepository(
            database.transactionDao(), 
            database.budgetDao(),
            database.goalDao(),
            database.debtDao(),
            database.accountDao(),
            database.moneyNoteDao()
        ) 
    }
    
    private val viewModel: FinanceViewModel by viewModels {
        FinanceViewModelFactory(repository, applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        NotificationHelper.createNotificationChannel(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
        enableEdgeToEdge()
        
        val appLockEnabled = applicationContext.getSharedPreferences("rupee_story_prefs", android.content.Context.MODE_PRIVATE).getBoolean("app_lock", false)
        
        setContent {
            var isUnlocked by androidx.compose.runtime.remember { mutableStateOf(!appLockEnabled) }
            
            androidx.compose.runtime.LaunchedEffect(Unit) {
                if (appLockEnabled && BiometricHelper.isBiometricAvailable(this@MainActivity)) {
                    BiometricHelper.showBiometricPrompt(
                        activity = this@MainActivity,
                        onSuccess = { isUnlocked = true },
                        onError = { finish() }
                    )
                }
            }

            val prefs = applicationContext.getSharedPreferences("user_prefs", android.content.Context.MODE_PRIVATE)
            val themeMode = prefs.getString("theme_mode", "System") ?: "System"
            val darkTheme = when(themeMode) { "Dark" -> true; "Light" -> false; else -> androidx.compose.foundation.isSystemInDarkTheme() }
            MyApplicationTheme(darkTheme = darkTheme) {
                if (isUnlocked) {
                    MainScreen(viewModel = viewModel)
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("App Locked. Use biometrics to unlock.")
                    }
                }
            }
        }
    }
}


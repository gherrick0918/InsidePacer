package app.insidepacer.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import app.insidepacer.analytics.PerformanceHelper
import app.insidepacer.di.Singleton
import app.insidepacer.ui.theme.AppTheme
import kotlinx.coroutines.launch
import app.insidepacer.backup.ui.ActivityTracker
import com.insidepacer.health.HealthConnectRepo
import com.insidepacer.health.HealthConnectRepoImpl
import com.insidepacer.health.HcAvailability

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Handle the permission grant or denial
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Track app startup performance
        val startupTrace = PerformanceHelper.startTrace(PerformanceHelper.Traces.APP_STARTUP)
        
        lifecycleScope.launch { Singleton.getSessionScheduler(this@MainActivity) }
        enableEdgeToEdge()
        setContent { AppTheme { AppNav() } }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        // Request Health Connect permission on launch if not granted
        lifecycleScope.launch {
            requestHealthConnectPermissionIfNeeded()
            // Stop startup trace after initialization
            PerformanceHelper.stopTrace(startupTrace)
        }
    }
    
    private suspend fun requestHealthConnectPermissionIfNeeded() {
        val healthConnectRepo: HealthConnectRepo = HealthConnectRepoImpl()
        
        // Check if Health Connect is available and installed
        val availability = healthConnectRepo.availability(this)
        if (availability != HcAvailability.SUPPORTED_INSTALLED) {
            return
        }
        
        // Check if permission is already granted
        val hasPermission = healthConnectRepo.hasWritePermission(this)
        if (hasPermission) {
            return
        }
        
        // Request the permission
        healthConnectRepo.requestWritePermission(this)
    }

    override fun onStart() {
        super.onStart()
        ActivityTracker.onStart(this)
    }

    override fun onStop() {
        ActivityTracker.onStop(this)
        super.onStop()
    }
}

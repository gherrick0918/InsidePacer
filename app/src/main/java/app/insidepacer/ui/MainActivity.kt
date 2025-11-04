package app.insidepacer.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import app.insidepacer.di.Singleton
import app.insidepacer.engine.CuePlayer
import app.insidepacer.engine.SessionScheduler
import app.insidepacer.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Handle the permission grant or denial
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cuePlayer = CuePlayer(this)
        Singleton.sessionScheduler = SessionScheduler(this, cuePlayer)
        enableEdgeToEdge()
        setContent { AppTheme { AppNav() } }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

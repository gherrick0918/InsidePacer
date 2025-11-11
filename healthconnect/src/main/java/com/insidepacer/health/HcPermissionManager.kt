package com.insidepacer.health

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

internal class HcPermissionManager(private val client: HealthConnectClient) {
    private val writePermissions = setOf(
        HealthPermission.createWritePermission(ExerciseSessionRecord::class)
    )

    suspend fun hasWritePermission(): Boolean {
        val granted = client.permissionController.getGrantedPermissions()
        return granted.containsAll(writePermissions)
    }

    suspend fun requestWritePermission(activity: ComponentActivity): Boolean = suspendCancellableCoroutine { cont ->
        val intent = runCatching {
            client.permissionController.createRequestPermissionIntent(writePermissions)
        }.getOrNull()

        if (intent == null) {
            if (cont.isActive) {
                cont.resume(false)
            }
            return@suspendCancellableCoroutine
        }

        val launcherKey = "hc-permission-${System.identityHashCode(this)}-${System.nanoTime()}"
        lateinit var launcher: ActivityResultLauncher<Intent>
        launcher = activity.activityResultRegistry.register(
            launcherKey,
            ActivityResultContracts.StartActivityForResult()
        ) {
            runCatching { launcher.unregister() }
            activity.lifecycleScope.launch {
                val granted = runCatching { hasWritePermission() }.getOrDefault(false)
                if (cont.isActive) {
                    cont.resume(granted)
                }
            }
        }
        launcher.launch(intent)
        cont.invokeOnCancellation { runCatching { launcher.unregister() } }
    }
}

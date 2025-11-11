package com.insidepacer.health

import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
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
        val launcherKey = "hc-permission-${System.identityHashCode(this)}-${System.nanoTime()}"
        var launcher: ActivityResultLauncher<Set<HealthPermission>>? = null
        launcher = activity.activityResultRegistry.register(
            launcherKey,
            client.permissionController.createRequestPermissionResultContract()
        ) { granted ->
            launcher?.unregister()
            val grantedAll = granted.containsAll(writePermissions)
            if (cont.isActive) {
                cont.resume(grantedAll)
            }
        }
        launcher.launch(writePermissions)
        cont.invokeOnCancellation { launcher.unregister() }
    }
}

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
        HealthPermission.getWritePermission(ExerciseSessionRecord::class)
    )

    suspend fun hasWritePermission(): Boolean {
        val granted = client.permissionController.getGrantedPermissions()
        return granted.containsAll(writePermissions)
    }

    suspend fun requestWritePermission(activity: ComponentActivity): Boolean = suspendCancellableCoroutine { cont ->
        val launcherKey = "hc-permission-${System.identityHashCode(this)}-${System.nanoTime()}"
        val contract = client.permissionController.createRequestPermissionActivityContract()
        var launcher: ActivityResultLauncher<Set<String>>? = null
        launcher = activity.activityResultRegistry.register<Set<String>, Set<String>>(
            launcherKey,
            contract
        ) { granted: Set<String> ->
            launcher?.unregister()
            val grantedAll = granted.containsAll(writePermissions)
            if (cont.isActive) {
                cont.resume(grantedAll)
            }
        }
        launcher?.launch(writePermissions)
        cont.invokeOnCancellation { launcher?.unregister() }
    }
}

package com.insidepacer.health

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

internal class HcPermissionManager(private val client: HealthConnectClient) {
    private val writePermissions: Set<String> = setOf(
        HealthPermission.getWritePermission(ExerciseSessionRecord::class)
    )

    suspend fun hasWritePermission(): Boolean {
        val granted = client.permissionController.getGrantedPermissions()
        return granted.containsAll(writePermissions)
    }

    suspend fun requestWritePermission(activity: ComponentActivity): Boolean =
        suspendCancellableCoroutine { cont ->
            val controller = client.permissionController
            val launcherKey = "hc-permission-${System.identityHashCode(this)}-${System.nanoTime()}"
            val contract = resolveRequestPermissionContract(controller)
            if (contract != null) {
                lateinit var launcher: ActivityResultLauncher<Set<String>>
                launcher = activity.activityResultRegistry.register(
                    launcherKey,
                    contract
                ) { grantedPermissions ->
                    runCatching { launcher.unregister() }
                    activity.lifecycleScope.launch {
                        val granted = if (grantedPermissions == null) {
                            runCatching { hasWritePermission() }.getOrDefault(false)
                        } else {
                            grantedPermissions.containsAll(writePermissions)
                        }
                        if (cont.isActive) {
                            cont.resume(granted)
                        }
                    }
                }
                launcher.launch(writePermissions)
                cont.invokeOnCancellation { runCatching { launcher.unregister() } }
            } else {
                val intent = resolveRequestPermissionIntent(controller, writePermissions)
                if (intent == null) {
                    if (cont.isActive) {
                        cont.resume(false)
                    }
                    return@suspendCancellableCoroutine
                }
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
}

@Suppress("UNCHECKED_CAST")
private fun resolveRequestPermissionContract(
    controller: PermissionController,
): ActivityResultContract<Set<String>, Set<String>>? {
    return runCatching {
        val method = controller::class.java.methods.firstOrNull { method ->
            method.name == "createRequestPermissionActivityContract" && method.parameterTypes.isEmpty()
        }
        method?.invoke(controller) as? ActivityResultContract<Set<String>, Set<String>>
    }.getOrNull()
}

private fun resolveRequestPermissionIntent(
    controller: PermissionController,
    permissions: Set<String>,
): Intent? {
    return runCatching {
        val method = controller::class.java.methods.firstOrNull { method ->
            method.name == "createRequestPermissionIntent" && method.parameterTypes.size == 1
        }
        method?.invoke(controller, permissions) as? Intent
    }.getOrNull()
}

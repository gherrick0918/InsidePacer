package com.insidepacer.health

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
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
    private val writePermissions: Set<Any> = setOf(
        HealthPermission.getWritePermission(ExerciseSessionRecord::class)
    )
    private val writePermissionTokens: Set<String> = writePermissions.mapNotNull(::permissionToken).toSet()

    suspend fun hasWritePermission(): Boolean {
        val granted = client.permissionController.getGrantedPermissions()
        val grantedTokens = granted.mapNotNull(::permissionToken).toSet()
        if (grantedTokens.isNotEmpty() && writePermissionTokens.isNotEmpty()) {
            return grantedTokens.containsAll(writePermissionTokens)
        }

        @Suppress("UNCHECKED_CAST")
        val grantedStrings = granted as? Set<String>
        if (grantedStrings != null) {
            return grantedStrings.containsAll(writePermissions.filterIsInstance<String>())
        }

        return granted.containsAll(writePermissions)
    }

    suspend fun requestWritePermission(activity: ComponentActivity): Boolean =
        suspendCancellableCoroutine { cont ->
            val controller = client.permissionController
            val launcherKey = "hc-permission-${System.identityHashCode(this)}-${System.nanoTime()}"
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

private fun resolveRequestPermissionIntent(
    controller: PermissionController,
    permissions: Set<Any>,
): Intent? {
    return runCatching {
        val method = controller::class.java.methods.firstOrNull { method ->
            method.name == "createRequestPermissionIntent" && method.parameterTypes.size == 1
        }
        method?.invoke(controller, permissions) as? Intent
    }.getOrNull()
}

private fun permissionToken(permission: Any?): String? {
    return when (permission) {
        null -> null
        is String -> permission
        else -> {
            val clazz = permission::class.java
            val getter = clazz.methods.firstOrNull { it.name == "getPermission" && it.parameterCount == 0 }
            if (getter != null) {
                runCatching { getter.invoke(permission) as? String }.getOrNull()
                    ?.let { return it }
            }

            val field = runCatching {
                clazz.getDeclaredField("permission").apply { isAccessible = true }
            }.getOrNull()
            if (field != null) {
                runCatching { field.get(permission) as? String }.getOrNull()?.let { return it }
            }

            null
        }
    }
}

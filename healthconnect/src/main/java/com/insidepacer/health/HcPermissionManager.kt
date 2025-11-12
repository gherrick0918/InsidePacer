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
    private val writePermissionsModern: Set<HealthPermission> = setOf(
        HealthPermission.getWritePermission(ExerciseSessionRecord::class)
    )
    private val writePermissionsLegacy: Set<String> = setOf(
        LEGACY_WRITE_PERMISSION
    )
    private val writePermissionTokens: Set<String> = buildSet {
        writePermissionsModern.mapNotNull(::permissionTokenModern).forEach { add(it) }
        writePermissionsLegacy.forEach { add(it) }
    }

    suspend fun hasWritePermission(): Boolean {
        val granted = client.permissionController.getGrantedPermissions()
        val grantedTokens = granted.mapNotNull(::permissionTokenAny).toSet()
        if (grantedTokens.isNotEmpty()) {
            return writePermissionTokens.all(grantedTokens::contains)
        }

        val grantedStrings = granted.filterIsInstance<String>()
        if (grantedStrings.isNotEmpty()) {
            return writePermissionsLegacy.all(grantedStrings::contains)
        }

        val grantedModern = granted.filterIsInstance<HealthPermission>()
        if (grantedModern.isNotEmpty()) {
            return writePermissionsModern.all { required ->
                grantedModern.any { granted -> permissionsMatch(granted, required) }
            }
        }

        return false
    }

    suspend fun requestWritePermission(activity: ComponentActivity): Boolean =
        suspendCancellableCoroutine { cont ->
            val controller = client.permissionController
            val launcherKey = "hc-permission-${System.identityHashCode(this)}-${System.nanoTime()}"
            val contract = resolveRequestPermissionContract(controller)
            if (contract != null) {
                lateinit var launcher: ActivityResultLauncher<Set<HealthPermission>>
                launcher = activity.activityResultRegistry.register(
                    launcherKey,
                    contract
                ) { grantedPermissions ->
                    runCatching { launcher.unregister() }
                    activity.lifecycleScope.launch {
                        val granted = if (grantedPermissions == null) {
                            runCatching { hasWritePermission() }.getOrDefault(false)
                        } else {
                            val tokens = grantedPermissions.mapNotNull(::permissionTokenModern).toSet()
                            if (tokens.isNotEmpty()) {
                                writePermissionTokens.all(tokens::contains)
                            } else {
                                grantedPermissions.containsAll(writePermissionsModern)
                            }
                        }
                        if (cont.isActive) {
                            cont.resume(granted)
                        }
                    }
                }
                launcher.launch(writePermissionsModern)
                cont.invokeOnCancellation { runCatching { launcher.unregister() } }
            } else {
                val intent = resolveRequestPermissionIntent(controller, writePermissionsModern)
                    ?: resolveRequestPermissionIntent(controller, writePermissionsLegacy)
                if (intent == null) {
                    if (cont.isActive) {
                        cont.resume(granted)
                    }
                }
            }
            launcher.launch(intent)
            cont.invokeOnCancellation { runCatching { launcher.unregister() } }
        }
}

@Suppress("UNCHECKED_CAST")
private fun resolveRequestPermissionContract(
    controller: PermissionController,
): ActivityResultContract<Set<HealthPermission>, Set<HealthPermission>>? {
    return runCatching {
        val method = controller::class.java.methods.firstOrNull { method ->
            method.name == "createRequestPermissionActivityContract" && method.parameterTypes.isEmpty()
        }
        method?.invoke(controller) as? ActivityResultContract<Set<HealthPermission>, Set<HealthPermission>>
    }.getOrNull()
}

private fun resolveRequestPermissionIntent(
    controller: PermissionController,
    permissions: Set<*>,
): Intent? {
    return runCatching {
        val method = controller::class.java.methods.firstOrNull { method ->
            method.name == "createRequestPermissionIntent" && method.parameterTypes.size == 1
        }
        method?.invoke(controller, permissions) as? Intent
    }.getOrNull()
}

private fun permissionTokenAny(permission: Any?): String? = when (permission) {
    null -> null
    is String -> permission
    is HealthPermission -> permissionTokenModern(permission)
    else -> permissionTokenReflective(permission)
}

private fun permissionTokenModern(permission: HealthPermission): String? =
    permissionTokenReflective(permission)

private fun permissionTokenReflective(permission: Any): String? {
    val clazz = permission::class.java
    for (name in METHOD_NAMES) {
        val method = clazz.methods.firstOrNull { it.name == name && it.parameterCount == 0 }
        if (method != null) {
            runCatching { method.invoke(permission) as? String }
                .getOrNull()?.let { return it }
        }
    }

    for (name in FIELD_NAMES) {
        val field = runCatching {
            clazz.getDeclaredField(name).apply { isAccessible = true }
        }.getOrNull() ?: continue
        runCatching { field.get(permission) as? String }
            .getOrNull()?.let { return it }
    }

    val description = permission.toString()
    val delimiter = description.indexOf('=')
    if (delimiter != -1) {
        val end = description.indexOf(')', startIndex = delimiter)
        val token = description.substring(delimiter + 1, if (end == -1) description.length else end)
            .trim()
        if (token.isNotEmpty() && !token.contains('@')) {
            return token
        }
    }
    return null
}

private fun permissionsMatch(a: HealthPermission, b: HealthPermission): Boolean {
    if (a == b) return true
    val tokens = listOf(permissionTokenModern(a), permissionTokenModern(b))
    if (tokens[0] != null && tokens[1] != null) {
        return tokens[0] == tokens[1]
    }
    return false
}

private const val LEGACY_WRITE_PERMISSION = "android.permission.health.WRITE_EXERCISE"

private val METHOD_NAMES = listOf(
    "getPermission",
    "getPermissionString",
    "getPermissionId",
    "getPermissionName",
)

private val FIELD_NAMES = listOf(
    "permission",
    "permissionString",
)

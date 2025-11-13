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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

internal class HcPermissionManager(private val client: HealthConnectClient) {
    private val writePermissionsModern: Set<String> by lazy {
        val modernPermission = HealthPermission.getWritePermission(ExerciseSessionRecord::class)
        // Ensure we store the string representation, handling both String and HealthPermission object cases
        val permissionString = when (modernPermission) {
            is String -> modernPermission
            else -> permissionTokenAny(modernPermission) ?: modernPermission.toString()
        }
        setOf(permissionString)
    }
    private val writePermissionsLegacy: Set<String> = setOf(
        LEGACY_WRITE_PERMISSION
    )
    private val writePermissionsAll: Set<String> by lazy { writePermissionsModern + writePermissionsLegacy }

    suspend fun hasWritePermission(): Boolean {
        val granted = client.permissionController.getGrantedPermissions()
        
        // Try extracting tokens from all granted permissions (most comprehensive approach)
        val grantedTokens = granted.mapNotNull(::permissionTokenAny).toSet()
        if (grantedTokens.isNotEmpty()) {
            val hasModern = writePermissionsModern.all(grantedTokens::contains)
            val hasLegacy = writePermissionsLegacy.all(grantedTokens::contains)
            if (hasModern || hasLegacy) {
                return true
            }
        }

        // Fallback 1: Try treating granted permissions as Strings directly
        val grantedStrings = granted.filterIsInstance<String>()
        if (grantedStrings.isNotEmpty()) {
            val hasModern = writePermissionsModern.all(grantedStrings::contains)
            val hasLegacy = writePermissionsLegacy.all(grantedStrings::contains)
            if (hasModern || hasLegacy) {
                return true
            }
        }

        // Fallback 2: Try treating them as HealthPermission objects and extract tokens
        val grantedModern = granted.filterIsInstance<HealthPermission>()
        if (grantedModern.isNotEmpty()) {
            val grantedModernTokens = grantedModern.mapNotNull(::permissionTokenModern).toSet()
            val hasModern = writePermissionsModern.all(grantedModernTokens::contains)
            val hasLegacy = writePermissionsLegacy.all(grantedModernTokens::contains)
            if (hasModern || hasLegacy) {
                return true
            }
        }

        // Fallback 3: Check if any granted permission equals our required permission strings
        // This handles cases where permissions might be returned as objects with toString()
        val grantedAsStrings = granted.map { it.toString() }.toSet()
        if (grantedAsStrings.isNotEmpty()) {
            val hasModern = writePermissionsModern.all { requiredPerm ->
                grantedAsStrings.any { it.equals(requiredPerm, ignoreCase = true) }
            }
            val hasLegacy = writePermissionsLegacy.all { requiredPerm ->
                grantedAsStrings.any { it.equals(requiredPerm, ignoreCase = true) }
            }
            if (hasModern || hasLegacy) {
                return true
            }
        }

        return false
    }

    private suspend fun hasWritePermissionWithRetry(maxAttempts: Int = 10, delayMs: Long = 1000): Boolean {
        repeat(maxAttempts) { attempt ->
            val hasPermission = runCatching { hasWritePermission() }.getOrDefault(false)
            if (hasPermission) {
                return true
            }
            if (attempt < maxAttempts - 1) {
                delay(delayMs)
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
                lateinit var launcher: ActivityResultLauncher<Set<String>>
                launcher = activity.activityResultRegistry.register(
                    launcherKey,
                    contract
                ) { grantedPermissions: Set<String>? ->
                    runCatching { launcher.unregister() }
                    activity.lifecycleScope.launch {
                        // Always check actual permission state after callback, as the returned
                        // grantedPermissions set may not accurately reflect the current state.
                        // Use retry mechanism to handle cases where permission updates are delayed,
                        // especially important on emulators where updates may be slower.
                        val granted = hasWritePermissionWithRetry()
                        if (cont.isActive) {
                            cont.resume(granted)
                        }
                    }
                }
                launcher.launch(writePermissionsAll)
                cont.invokeOnCancellation { runCatching { launcher.unregister() } }
            } else {
                val intent = resolveRequestPermissionIntent(controller, writePermissionsModern)
                    ?: resolveRequestPermissionIntent(controller, writePermissionsLegacy)
                if (intent == null) {
                    if (cont.isActive) {
                        cont.resume(false)
                    }
                } else {
                    lateinit var launcher: ActivityResultLauncher<Intent>
                    launcher = activity.activityResultRegistry.register(
                        launcherKey,
                        ActivityResultContracts.StartActivityForResult()
                    ) { _ ->
                        runCatching { launcher.unregister() }
                        activity.lifecycleScope.launch {
                            // Use retry mechanism to handle cases where permission updates are delayed,
                            // especially important on emulators where updates may be slower.
                            val granted = hasWritePermissionWithRetry()
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


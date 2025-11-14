package com.insidepacer.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
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

    suspend fun requestWritePermission(activity: ComponentActivity): Boolean {
        // First check if permission is already granted to avoid unnecessary dialog
        val alreadyGranted = try {
            hasWritePermission()
        } catch (e: Exception) {
            false
        }
        Log.d(TAG, "requestWritePermission: alreadyGranted=$alreadyGranted")
        if (alreadyGranted) {
            return true
        }
        
        return suspendCancellableCoroutine { cont ->
            
            val controller = client.permissionController
            val launcherKey = "hc-permission-${System.identityHashCode(this)}-${System.nanoTime()}"
            
            // Try to use direct API first (for newer versions)
            val contract = tryDirectPermissionContract(controller) 
                ?: resolveRequestPermissionContract(controller)
            Log.d(TAG, "requestWritePermission: contract=$contract")
            if (contract != null) {
                lateinit var launcher: ActivityResultLauncher<Set<String>>
                launcher = activity.activityResultRegistry.register(
                    launcherKey,
                    contract
                ) { grantedPermissions: Set<String>? ->
                    Log.d(TAG, "Permission dialog callback: grantedPermissions=$grantedPermissions")
                    runCatching { launcher.unregister() }
                    activity.lifecycleScope.launch {
                        // Always check actual permission state after callback, as the returned
                        // grantedPermissions set may not accurately reflect the current state.
                        // Use retry mechanism to handle cases where permission updates are delayed,
                        // especially important on emulators where updates may be slower.
                        val granted = hasWritePermissionWithRetry()
                        Log.d(TAG, "Permission check after retry: granted=$granted")
                        if (cont.isActive) {
                            cont.resume(granted)
                        }
                    }
                }
                // Wrap launcher.launch() in try-catch to handle exceptions that could occur
                // when launching the permission dialog, especially on emulators
                val launchResult = runCatching { 
                    launcher.launch(writePermissionsAll)
                }
                val launched = launchResult.isSuccess
                Log.d(TAG, "Launcher.launch() result: launched=$launched, exception=${launchResult.exceptionOrNull()}")
                if (!launched) {
                    // If launch failed, clean up and return false
                    runCatching { launcher.unregister() }
                    if (cont.isActive) {
                        cont.resume(false)
                    }
                } else {
                    cont.invokeOnCancellation { runCatching { launcher.unregister() } }
                }
            } else {
                val intent = resolveRequestPermissionIntent(controller, writePermissionsModern)
                    ?: resolveRequestPermissionIntent(controller, writePermissionsLegacy)
                Log.d(TAG, "requestWritePermission: intent=$intent")
                if (intent == null) {
                    // Neither contract nor intent methods are available
                    // This could happen if Health Connect SDK version doesn't support these APIs
                    // Try to open Health Connect app settings directly as a fallback
                    Log.w(TAG, "Unable to create permission contract or intent. Trying fallback to Health Connect settings.")
                    val fallbackIntent = createHealthConnectSettingsIntent(activity)
                    if (fallbackIntent != null) {
                        lateinit var launcher: ActivityResultLauncher<Intent>
                        launcher = activity.activityResultRegistry.register(
                            launcherKey,
                            ActivityResultContracts.StartActivityForResult()
                        ) { _ ->
                            Log.d(TAG, "Health Connect settings callback received")
                            runCatching { launcher.unregister() }
                            activity.lifecycleScope.launch {
                                // Check if permission was granted after returning from settings
                                val granted = hasWritePermissionWithRetry()
                                Log.d(TAG, "Permission check after settings: granted=$granted")
                                if (cont.isActive) {
                                    cont.resume(granted)
                                }
                            }
                        }
                        val launchResult = runCatching {
                            launcher.launch(fallbackIntent)
                        }
                        val launched = launchResult.isSuccess
                        Log.d(TAG, "Health Connect settings launch result: launched=$launched, exception=${launchResult.exceptionOrNull()}")
                        if (!launched) {
                            runCatching { launcher.unregister() }
                            if (cont.isActive) {
                                cont.resume(false)
                            }
                        } else {
                            cont.invokeOnCancellation { runCatching { launcher.unregister() } }
                        }
                    } else {
                        // Complete fallback failed too
                        Log.e(TAG, "Unable to create any intent to request Health Connect permissions")
                        if (cont.isActive) {
                            cont.resume(false)
                        }
                    }
                } else {
                    lateinit var launcher: ActivityResultLauncher<Intent>
                    launcher = activity.activityResultRegistry.register(
                        launcherKey,
                        ActivityResultContracts.StartActivityForResult()
                    ) { _ ->
                        Log.d(TAG, "Permission intent callback received")
                        runCatching { launcher.unregister() }
                        activity.lifecycleScope.launch {
                            // Use retry mechanism to handle cases where permission updates are delayed,
                            // especially important on emulators where updates may be slower.
                            val granted = hasWritePermissionWithRetry()
                            Log.d(TAG, "Permission check after retry (intent path): granted=$granted")
                            if (cont.isActive) {
                                cont.resume(granted)
                            }
                        }
                    }
                    // Wrap launcher.launch() in try-catch to handle exceptions that could occur
                    // when launching the permission dialog, especially on emulators
                    val launchResult = runCatching {
                        launcher.launch(intent)
                    }
                    val launched = launchResult.isSuccess
                    Log.d(TAG, "Launcher.launch(intent) result: launched=$launched, exception=${launchResult.exceptionOrNull()}")
                    if (!launched) {
                        // If launch failed, clean up and return false
                        runCatching { launcher.unregister() }
                        if (cont.isActive) {
                            cont.resume(false)
                        }
                    } else {
                        cont.invokeOnCancellation { runCatching { launcher.unregister() } }
                    }
                }
            }
        }
    }

    private fun createHealthConnectSettingsIntent(context: Context): Intent? {
        return runCatching {
            // Try to create an intent to open Health Connect app permissions screen
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$HEALTH_CONNECT_PACKAGE_NAME")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }.onFailure { e ->
            Log.e(TAG, "Error creating Health Connect settings intent", e)
        }.getOrNull()
    }

    @Suppress("UNCHECKED_CAST")
    private fun tryDirectPermissionContract(
        controller: PermissionController,
    ): ActivityResultContract<Set<String>, Set<String>>? {
        return runCatching {
            // Try calling the direct API method that should exist in newer versions
            // This avoids reflection if the method is available at compile time
            val method = PermissionController::class.java.getMethod("createRequestPermissionResultContract")
            val result = method.invoke(controller)
            Log.d(TAG, "Direct API call successful: ${result?.javaClass?.name}")
            result as? ActivityResultContract<Set<String>, Set<String>>
        }.onFailure { e ->
            Log.d(TAG, "Direct API call failed (expected for older SDK versions): ${e.message}")
        }.getOrNull()
    }
}

@Suppress("UNCHECKED_CAST")
private fun resolveRequestPermissionContract(
    controller: PermissionController,
): ActivityResultContract<Set<String>, Set<String>>? {
    return runCatching {
        // Log all available methods for debugging
        val allMethods = controller::class.java.methods
            .filter { it.name.contains("permission", ignoreCase = true) || it.name.contains("Request", ignoreCase = true) }
            .map { "${it.name}(${it.parameterTypes.joinToString { p -> p.simpleName }})" }
        Log.d(TAG, "Available permission methods: $allMethods")
        
        // Try multiple method names that have been used in different versions
        val methodNames = listOf(
            "createRequestPermissionResultContract",
            "createRequestPermissionActivityContract"
        )
        
        var method: java.lang.reflect.Method? = null
        for (name in methodNames) {
            method = controller::class.java.methods.firstOrNull { m ->
                m.name == name && m.parameterTypes.isEmpty()
            }
            if (method != null) {
                Log.d(TAG, "Found permission contract method: $name")
                break
            }
        }
        
        if (method == null) {
            Log.w(TAG, "No known permission contract method found")
        }
        
        if (method != null) {
            val result = method.invoke(controller)
            Log.d(TAG, "Contract invocation result: ${result?.javaClass?.name}")
            result as? ActivityResultContract<Set<String>, Set<String>>
        } else {
            null
        }
    }.onFailure { e ->
        Log.e(TAG, "Error resolving permission contract", e)
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
        Log.d(TAG, "Found createRequestPermissionIntent method: ${method != null}")
        
        if (method != null) {
            val result = method.invoke(controller, permissions)
            Log.d(TAG, "Intent invocation result: ${result != null}, type: ${result?.javaClass?.name}")
            result as? Intent
        } else {
            null
        }
    }.onFailure { e ->
        Log.e(TAG, "Error resolving permission intent", e)
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
private const val TAG = "HcPermissionManager"

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


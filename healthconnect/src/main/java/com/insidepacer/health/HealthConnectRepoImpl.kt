package com.insidepacer.health

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.metadata.Metadata
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class HealthConnectRepoImpl : HealthConnectRepo {
    override suspend fun availability(context: Context): HcAvailability {
        val status = HealthConnectClient.getSdkStatus(
            context,
            HEALTH_CONNECT_PACKAGE_NAME
        )
        return mapSdkStatusToAvailability(status)
    }

    override suspend fun ensureInstalled(context: Context): Boolean {
        val status = HealthConnectClient.getSdkStatus(
            context,
            HEALTH_CONNECT_PACKAGE_NAME
        )
        return when (mapSdkStatusToAvailability(status)) {
            HcAvailability.SUPPORTED_INSTALLED -> true
            HcAvailability.SUPPORTED_NOT_INSTALLED -> {
                when (status) {
                    sdkAvailabilityStatus.providerDisabled -> {
                        openAppSettings(context)
                    }
                    else -> {
                        openPlayStore(context)
                    }
                }
                false
            }
            HcAvailability.NOT_SUPPORTED -> false
        }
    }

    override suspend fun hasWritePermission(context: Context): Boolean {
        val client = HealthConnectClient.getOrCreate(context)
        return HcPermissionManager(client).hasWritePermission()
    }

    override suspend fun requestWritePermission(activity: ComponentActivity): Boolean {
        val client = HealthConnectClient.getOrCreate(activity)
        return HcPermissionManager(client).requestWritePermission(activity)
    }

    override suspend fun writeWalkingSession(
        context: Context,
        startTime: Instant,
        endTime: Instant,
        notes: String?
    ): Result<Unit> {
        val availability = availability(context)
        if (availability != HcAvailability.SUPPORTED_INSTALLED) {
            return Result.failure(IllegalStateException("Health Connect not installed"))
        }
        val client = HealthConnectClient.getOrCreate(context)
        val permissionManager = HcPermissionManager(client)
        val hasPermission = permissionManager.hasWritePermission()
        if (!hasPermission) {
            return Result.failure(IllegalStateException("Missing Health Connect permission"))
        }
        val zone = ZoneId.systemDefault()
        val startOffset = zone.rules.getOffset(startTime)
        val endOffset = zone.rules.getOffset(endTime)
        val record = buildExerciseRecord(
            startTime = startTime,
            startZoneOffset = startOffset,
            endTime = endTime,
            endZoneOffset = endOffset,
            notes = notes
        )
        return runCatching {
            client.insertRecords(listOf(record))
        }.map { Unit }
    }

    private fun openPlayStore(context: Context) {
        val packageName = HEALTH_CONNECT_PACKAGE_NAME
        val playIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(playIntent)
        } catch (notFound: ActivityNotFoundException) {
            val webIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(webIntent) }
        }
    }

    private fun openAppSettings(context: Context) {
        val packageName = HEALTH_CONNECT_PACKAGE_NAME
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

internal fun mapSdkStatusToAvailability(status: Int): HcAvailability {
    val codes = sdkAvailabilityStatus
    return when {
        status == codes.available -> HcAvailability.SUPPORTED_INSTALLED
        status == codes.providerDisabled -> HcAvailability.SUPPORTED_NOT_INSTALLED
        status == codes.providerInstallationRequired -> HcAvailability.SUPPORTED_NOT_INSTALLED
        status == codes.providerUpdateRequired -> HcAvailability.SUPPORTED_NOT_INSTALLED
        status == codes.unavailable -> HcAvailability.NOT_SUPPORTED
        status == codes.appNotVerified -> HcAvailability.NOT_SUPPORTED
        status == codes.providerPolicyRestriction -> HcAvailability.NOT_SUPPORTED
        else -> HcAvailability.NOT_SUPPORTED
    }
}

private fun buildExerciseRecord(
    startTime: Instant,
    startZoneOffset: ZoneOffset?,
    endTime: Instant,
    endZoneOffset: ZoneOffset?,
    notes: String?,
): ExerciseSessionRecord {
    buildExerciseRecordWithBuilder(
        startTime = startTime,
        startZoneOffset = startZoneOffset,
        endTime = endTime,
        endZoneOffset = endZoneOffset,
        notes = notes,
    )?.let { return it }

    buildExerciseRecordWithConstructor(
        startTime = startTime,
        startZoneOffset = startZoneOffset,
        endTime = endTime,
        endZoneOffset = endZoneOffset,
        notes = notes,
    )?.let { return it }

    error("Unable to create ExerciseSessionRecord instance")
}

private val sdkAvailabilityStatus: SdkAvailabilityStatus by lazy { SdkAvailabilityStatus.create() }

@Suppress("SpreadOperator")
private fun buildExerciseRecordWithBuilder(
    startTime: Instant,
    startZoneOffset: ZoneOffset?,
    endTime: Instant,
    endZoneOffset: ZoneOffset?,
    notes: String?,
): ExerciseSessionRecord? {
    val builderClass = runCatching {
        Class.forName("androidx.health.connect.client.records.ExerciseSessionRecord\$Builder")
    }.getOrNull() ?: return null

    val constructor = builderClass.constructors.firstOrNull { it.parameterTypes.size == 3 }
        ?: return null

    val builder = runCatching {
        constructor.newInstance(
            startTime,
            endTime,
            ExerciseSessionRecord.EXERCISE_TYPE_WALKING,
        )
    }.getOrNull() ?: return null

    invokeNullableMethod(builder, "setStartZoneOffset", startZoneOffset)
    invokeNullableMethod(builder, "setEndZoneOffset", endZoneOffset)
    invokeNullableMethod(builder, "setNotes", notes)

    return runCatching {
        builderClass.getMethod("build").invoke(builder) as ExerciseSessionRecord
    }.getOrNull()
}

@Suppress("SpreadOperator")
private fun buildExerciseRecordWithConstructor(
    startTime: Instant,
    startZoneOffset: ZoneOffset?,
    endTime: Instant,
    endZoneOffset: ZoneOffset?,
    notes: String?,
): ExerciseSessionRecord? {
    val recordClass = ExerciseSessionRecord::class.java
    val constructor = recordClass.declaredConstructors.maxByOrNull { it.parameterCount }
        ?: return null

    val instantValues = ArrayDeque(listOf(startTime, endTime))
    val zoneOffsets = ArrayDeque(listOf(startZoneOffset, endZoneOffset))
    val stringValues = ArrayDeque<String?>(listOf(null, notes, null))
    val listValues = ArrayDeque<List<*>>(listOf(emptyList<Any>(), emptyList<Any>()))

    val arguments = Array<Any?>(constructor.parameterCount) { null }
    constructor.parameterTypes.forEachIndexed { index, type ->
        arguments[index] = when (type) {
            Instant::class.java -> instantValues.removeFirstOrNull()
            ZoneOffset::class.java -> zoneOffsets.removeFirstOrNull()
            Metadata::class.java -> Metadata()
            java.lang.Integer.TYPE, java.lang.Integer::class.java ->
                ExerciseSessionRecord.EXERCISE_TYPE_WALKING
            String::class.java -> stringValues.removeFirstOrNull()
            List::class.java -> listValues.removeFirstOrNull() ?: emptyList<Any>()
            else -> null
        }
    }

    return runCatching {
        constructor.isAccessible = true
        constructor.newInstance(*arguments) as ExerciseSessionRecord
    }.getOrNull()
}

private fun invokeNullableMethod(target: Any, name: String, value: Any?) {
    val method = target.javaClass.methods.firstOrNull { it.name == name && it.parameterTypes.size == 1 }
        ?: return
    if (value == null && method.parameterTypes[0].isPrimitive) {
        return
    }
    if (value == null) {
        method.invoke(target, *arrayOfNulls<Any>(1))
    } else {
        method.invoke(target, value)
    }
}

private fun <T> ArrayDeque<T>.removeFirstOrNull(): T? = if (isEmpty()) null else removeFirst()

private class SdkAvailabilityStatus(
    val available: Int,
    val unavailable: Int?,
    val providerDisabled: Int?,
    val providerInstallationRequired: Int?,
    val providerPolicyRestriction: Int?,
    val providerUpdateRequired: Int?,
    val appNotVerified: Int?,
) {
    companion object {
        fun create(): SdkAvailabilityStatus {
            val companion = runCatching {
                HealthConnectClient::class.java.getDeclaredField("Companion").apply { isAccessible = true }
                    .get(null)
            }.getOrNull()

            fun findIn(target: Any?, type: Class<*>?, name: String): Int? {
                if (type == null) return null
                runCatching { type.getField(name).getInt(target) }.getOrNull()?.let { return it }
                return runCatching {
                    type.getDeclaredField(name).apply { isAccessible = true }.getInt(target)
                }.getOrNull()
            }

            fun findStatus(name: String): Int? {
                val clientClass = HealthConnectClient::class.java
                findIn(null, clientClass, name)?.let { return it }

                val companionClass = companion?.javaClass
                findIn(companion, companionClass, name)?.let { return it }

                val nestedStatuses = companionClass?.declaredClasses?.firstOrNull { it.simpleName == "SdkAvailabilityStatus" }
                if (nestedStatuses != null) {
                    val instance = runCatching { nestedStatuses.getField("INSTANCE").get(null) }
                        .getOrNull() ?: companion
                    findIn(instance, nestedStatuses, name)?.let { return it }
                }
                return null
            }

            val available = checkNotNull(findStatus("SDK_AVAILABLE")) {
                "Health Connect SDK_AVAILABLE constant not found"
            }

            return SdkAvailabilityStatus(
                available = available,
                unavailable = findStatus("SDK_UNAVAILABLE"),
                providerDisabled = findStatus("SDK_UNAVAILABLE_PROVIDER_DISABLED"),
                providerInstallationRequired = findStatus("SDK_UNAVAILABLE_PROVIDER_INSTALLATION_REQUIRED"),
                providerPolicyRestriction = findStatus("SDK_UNAVAILABLE_PROVIDER_POLICY_RESTRICTION"),
                providerUpdateRequired = findStatus("SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED"),
                appNotVerified = findStatus("SDK_UNAVAILABLE_APP_NOT_VERIFIED"),
            )
        }
    }
}

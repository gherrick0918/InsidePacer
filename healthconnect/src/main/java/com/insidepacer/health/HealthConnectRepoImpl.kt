package com.insidepacer.health

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ExerciseSessionRecord
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
                    HealthConnectClient.SdkAvailabilityStatus.SDK_UNAVAILABLE_PROVIDER_DISABLED -> {
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
        }.map { }
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

internal fun mapSdkStatusToAvailability(status: Int): HcAvailability = when (status) {
    HealthConnectClient.SdkAvailabilityStatus.SDK_AVAILABLE -> HcAvailability.SUPPORTED_INSTALLED
    HealthConnectClient.SdkAvailabilityStatus.SDK_UNAVAILABLE_PROVIDER_DISABLED,
    HealthConnectClient.SdkAvailabilityStatus.SDK_UNAVAILABLE_PROVIDER_INSTALLATION_REQUIRED,
    HealthConnectClient.SdkAvailabilityStatus.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HcAvailability.SUPPORTED_NOT_INSTALLED
    HealthConnectClient.SdkAvailabilityStatus.SDK_UNAVAILABLE,
    HealthConnectClient.SdkAvailabilityStatus.SDK_UNAVAILABLE_APP_NOT_VERIFIED,
    HealthConnectClient.SdkAvailabilityStatus.SDK_UNAVAILABLE_PROVIDER_POLICY_RESTRICTION -> HcAvailability.NOT_SUPPORTED
    else -> HcAvailability.NOT_SUPPORTED
}

private fun buildExerciseRecord(
    startTime: Instant,
    startZoneOffset: ZoneOffset?,
    endTime: Instant,
    endZoneOffset: ZoneOffset?,
    notes: String?,
): ExerciseSessionRecord {
    val builder = ExerciseSessionRecord.Builder(
        startTime,
        startZoneOffset,
        endTime,
        endZoneOffset,
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING
    )
    notes?.let { builder.setNotes(it) }
    return builder.build()
}

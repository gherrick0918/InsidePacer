package com.insidepacer.health

import android.content.Context
import androidx.activity.ComponentActivity
import java.time.Instant

interface HealthConnectRepo {
    suspend fun availability(context: Context): HcAvailability
    suspend fun ensureInstalled(context: Context): Boolean
    suspend fun hasWritePermission(context: Context): Boolean
    suspend fun requestWritePermission(activity: ComponentActivity): Boolean
    suspend fun writeWalkingSession(
        context: Context,
        startTime: Instant,
        endTime: Instant,
        notes: String? = null,
    ): Result<Unit>
}

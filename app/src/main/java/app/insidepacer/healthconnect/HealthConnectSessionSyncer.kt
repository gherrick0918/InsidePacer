package app.insidepacer.healthconnect

import android.content.Context
import app.insidepacer.data.SettingsRepo
import app.insidepacer.domain.SessionLog
import com.insidepacer.health.HcAvailability
import com.insidepacer.health.HealthConnectRepo
import java.time.Instant
import kotlinx.coroutines.flow.first

class HealthConnectSessionSyncer(
    private val context: Context,
    private val settingsRepo: SettingsRepo,
    private val healthConnectRepo: HealthConnectRepo,
    private val onFailure: (Throwable) -> Unit = {},
) {
    suspend fun onSessionLogged(log: SessionLog) {
        if (log.aborted) return
        if (log.endMillis <= log.startMillis) return
        val enabled = settingsRepo.healthConnectEnabled.first()
        if (!enabled) return
        val availability = healthConnectRepo.availability(context)
        if (availability != HcAvailability.SUPPORTED_INSTALLED) return
        val hasPermission = healthConnectRepo.hasWritePermission(context)
        if (!hasPermission) return
        val start = Instant.ofEpochMilli(log.startMillis)
        val end = Instant.ofEpochMilli(log.endMillis)
        val notes = log.programId?.let { "Program: $it" }
        val result = healthConnectRepo.writeWalkingSession(
            context = context,
            startTime = start,
            endTime = end,
            notes = notes,
        )
        result.exceptionOrNull()?.let(onFailure)
    }
}

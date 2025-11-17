package app.insidepacer.healthconnect

import android.content.Context
import app.insidepacer.domain.SessionLog
import com.insidepacer.health.HcAvailability
import com.insidepacer.health.HealthConnectRepo
import com.insidepacer.health.SpeedSample
import java.time.Instant
import kotlin.math.abs

class HealthConnectSessionSyncer(
    private val context: Context,
    private val healthConnectRepo: HealthConnectRepo,
    private val onFailure: (Throwable) -> Unit = {},
) {
    suspend fun onSessionLogged(log: SessionLog) {
        if (log.aborted) return
        if (log.endMillis <= log.startMillis) return
        val availability = healthConnectRepo.availability(context)
        if (availability != HcAvailability.SUPPORTED_INSTALLED) return
        val hasPermission = healthConnectRepo.hasWritePermission(context)
        if (!hasPermission) return
        
        val start = Instant.ofEpochMilli(log.startMillis)
        val end = Instant.ofEpochMilli(log.endMillis)
        
        // Calculate distance from segments (speed in mph/kmh * duration in seconds)
        // Segments have speed in mph, need to convert to meters
        val distanceMeters = calculateDistanceMeters(log)
        
        // Generate speed samples from segments
        val speedSamples = generateSpeedSamples(log, start)
        
        // Create a descriptive title
        val title = createSessionTitle(log)
        
        // Create notes with program info if available
        val notes = log.programId?.let { "Program: $it" }
        
        val result = healthConnectRepo.writeWalkingSession(
            context = context,
            startTime = start,
            endTime = end,
            notes = notes,
            title = title,
            distanceMeters = distanceMeters,
            speedSamples = speedSamples,
        )
        result.exceptionOrNull()?.let(onFailure)
    }
    
    private fun calculateDistanceMeters(log: SessionLog): Double {
        // Sum up distance from each segment
        // Speed is in mph, duration is in seconds
        // Convert: mph * seconds * (1609.34 meters/mile) / (3600 seconds/hour)
        val mphToMetersPerSecond = 1609.34 / 3600.0
        return log.segments.sumOf { segment ->
            segment.speed * segment.seconds * mphToMetersPerSecond
        }
    }
    
    private fun generateSpeedSamples(log: SessionLog, startTime: Instant): List<SpeedSample> {
        val samples = mutableListOf<SpeedSample>()
        var currentTime = startTime
        
        // Add a sample at the start of each segment
        log.segments.forEach { segment ->
            val speedMetersPerSecond = segment.speed * 1609.34 / 3600.0 // mph to m/s
            samples.add(SpeedSample(currentTime, speedMetersPerSecond))
            currentTime = currentTime.plusSeconds(segment.seconds.toLong())
        }
        
        return samples
    }
    
    private fun createSessionTitle(log: SessionLog): String {
        val durationMinutes = log.totalSeconds / 60
        val segments = log.segments
        
        if (segments.isEmpty()) {
            return "Walking Session - ${durationMinutes}min"
        }
        
        // Check if it's an interval workout (varying speeds)
        val speeds = segments.map { it.speed }.distinct()
        if (speeds.size > 1) {
            val minSpeed = speeds.minOrNull() ?: 0.0
            val maxSpeed = speeds.maxOrNull() ?: 0.0
            return "Walking Intervals - ${durationMinutes}min (${formatSpeed(minSpeed)}-${formatSpeed(maxSpeed)} mph)"
        }
        
        // Single speed workout
        val speed = speeds.firstOrNull() ?: 0.0
        return "Walking - ${durationMinutes}min @ ${formatSpeed(speed)} mph"
    }
    
    private fun formatSpeed(speed: Double): String {
        return if (speed == speed.toInt().toDouble()) {
            speed.toInt().toString()
        } else {
            String.format("%.1f", speed)
        }
    }
}

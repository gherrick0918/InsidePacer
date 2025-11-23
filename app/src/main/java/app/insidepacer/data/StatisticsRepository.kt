package app.insidepacer.data

import android.content.Context
import app.insidepacer.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * Repository for calculating and retrieving workout statistics
 */
class StatisticsRepository(private val ctx: Context) {
    private val sessionRepo = SessionRepo(ctx)

    /**
     * Calculate overall workout statistics
     */
    suspend fun getOverallStatistics(): WorkoutStatistics = withContext(Dispatchers.IO) {
        val sessions = sessionRepo.loadAll()
        
        if (sessions.isEmpty()) {
            return@withContext WorkoutStatistics()
        }

        val totalTimeSeconds = sessions.sumOf { it.totalSeconds.toLong() }
        val totalDistanceMiles = sessions.sumOf { calculateDistance(it) }
        val completedWorkouts = sessions.count { !it.aborted }
        val abortedWorkouts = sessions.count { it.aborted }
        
        // Calculate average speed across all sessions
        val totalWeightedSpeed = sessions.sumOf { session ->
            if (session.totalSeconds > 0) {
                val avgSpeed = session.segments.sumOf { it.speed * it.seconds } / session.totalSeconds
                avgSpeed * session.totalSeconds
            } else {
                0.0
            }
        }
        val averageSpeedMph = if (totalTimeSeconds > 0) totalWeightedSpeed / totalTimeSeconds else 0.0

        WorkoutStatistics(
            totalWorkouts = sessions.size,
            totalTimeSeconds = totalTimeSeconds,
            totalDistanceMiles = totalDistanceMiles,
            averageSpeedMph = averageSpeedMph,
            completedWorkouts = completedWorkouts,
            abortedWorkouts = abortedWorkouts
        )
    }

    /**
     * Get personal records
     */
    suspend fun getPersonalRecords(): List<PersonalRecord> = withContext(Dispatchers.IO) {
        val sessions = sessionRepo.loadAll()
        
        if (sessions.isEmpty()) {
            return@withContext emptyList()
        }

        val records = mutableListOf<PersonalRecord>()

        // Fastest average speed
        val fastestSession = sessions.maxByOrNull { session ->
            if (session.totalSeconds > 0) {
                session.segments.sumOf { it.speed * it.seconds } / session.totalSeconds
            } else {
                0.0
            }
        }
        fastestSession?.let { session ->
            val avgSpeed = session.segments.sumOf { it.speed * it.seconds } / session.totalSeconds
            records.add(
                PersonalRecord(
                    type = RecordType.FASTEST_SPEED,
                    value = avgSpeed,
                    sessionId = session.id,
                    dateMillis = session.startMillis
                )
            )
        }

        // Longest duration
        val longestSession = sessions.maxByOrNull { it.totalSeconds }
        longestSession?.let { session ->
            records.add(
                PersonalRecord(
                    type = RecordType.LONGEST_DURATION,
                    value = session.totalSeconds.toDouble(),
                    sessionId = session.id,
                    dateMillis = session.startMillis
                )
            )
        }

        // Longest distance
        val longestDistanceSession = sessions.maxByOrNull { calculateDistance(it) }
        longestDistanceSession?.let { session ->
            records.add(
                PersonalRecord(
                    type = RecordType.LONGEST_DISTANCE,
                    value = calculateDistance(session),
                    sessionId = session.id,
                    dateMillis = session.startMillis
                )
            )
        }

        // Fastest pace (highest speed in any segment)
        val fastestPaceSession = sessions.maxByOrNull { session ->
            session.segments.maxOfOrNull { it.speed } ?: 0.0
        }
        fastestPaceSession?.let { session ->
            val fastestSpeed = session.segments.maxOf { it.speed }
            records.add(
                PersonalRecord(
                    type = RecordType.FASTEST_PACE,
                    value = fastestSpeed,
                    sessionId = session.id,
                    dateMillis = session.startMillis
                )
            )
        }

        records
    }

    /**
     * Get statistics for recent periods (last 4 weeks)
     */
    suspend fun getWeeklyTrends(): List<PeriodStatistics> = withContext(Dispatchers.IO) {
        val sessions = sessionRepo.loadAll()
        val now = Instant.now()
        val weekFields = WeekFields.of(Locale.getDefault())
        
        // Group sessions by week
        val weeklyData = sessions
            .filter { session ->
                val sessionDate = Instant.ofEpochMilli(session.startMillis)
                ChronoUnit.DAYS.between(sessionDate, now) <= 28 // Last 4 weeks
            }
            .groupBy { session ->
                val date = Instant.ofEpochMilli(session.startMillis)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                val week = date.get(weekFields.weekOfWeekBasedYear())
                val year = date.get(weekFields.weekBasedYear())
                "$year-W$week"
            }
        
        // Create statistics for each week
        weeklyData.map { (weekKey, sessionsInWeek) ->
            PeriodStatistics(
                periodLabel = weekKey,
                workoutCount = sessionsInWeek.size,
                totalTimeSeconds = sessionsInWeek.sumOf { it.totalSeconds.toLong() },
                totalDistanceMiles = sessionsInWeek.sumOf { calculateDistance(it) }
            )
        }.sortedBy { it.periodLabel }
    }

    /**
     * Get recent workouts (last 5)
     */
    suspend fun getRecentWorkouts(limit: Int = 5): List<RecentWorkout> = withContext(Dispatchers.IO) {
        val sessions = sessionRepo.loadAll()
            .sortedByDescending { it.startMillis }
            .take(limit)
        
        sessions.map { session ->
            val avgSpeed = if (session.totalSeconds > 0) {
                session.segments.sumOf { it.speed * it.seconds } / session.totalSeconds
            } else {
                0.0
            }
            val distance = calculateDistance(session)
            
            RecentWorkout(
                sessionLog = session,
                averageSpeedMph = avgSpeed,
                distanceMiles = distance
            )
        }
    }

    /**
     * Calculate distance in miles for a session
     */
    private fun calculateDistance(session: SessionLog): Double {
        return session.segments.sumOf { segment ->
            // Distance = speed (mph) * time (hours)
            segment.speed * (segment.seconds / 3600.0)
        }
    }
}

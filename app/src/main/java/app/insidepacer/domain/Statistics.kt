package app.insidepacer.domain

import kotlinx.serialization.Serializable

/**
 * Overall statistics for all workouts
 */
@Serializable
data class WorkoutStatistics(
    val totalWorkouts: Int = 0,
    val totalTimeSeconds: Long = 0,
    val totalDistanceMiles: Double = 0.0,
    val averageSpeedMph: Double = 0.0,
    val completedWorkouts: Int = 0,
    val abortedWorkouts: Int = 0
)

/**
 * Personal record for a specific metric
 */
@Serializable
data class PersonalRecord(
    val type: RecordType,
    val value: Double,
    val sessionId: String,
    val dateMillis: Long
)

enum class RecordType {
    FASTEST_PACE,
    LONGEST_DURATION,
    LONGEST_DISTANCE,
    FASTEST_SPEED
}

/**
 * Statistics for a time period (week/month)
 */
@Serializable
data class PeriodStatistics(
    val periodLabel: String,
    val workoutCount: Int,
    val totalTimeSeconds: Long,
    val totalDistanceMiles: Double
)

/**
 * Recent workout summary
 */
@Serializable
data class RecentWorkout(
    val sessionLog: SessionLog,
    val averageSpeedMph: Double,
    val distanceMiles: Double
)

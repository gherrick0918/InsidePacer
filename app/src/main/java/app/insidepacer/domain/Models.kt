package app.insidepacer.domain
import kotlinx.serialization.Serializable

@Serializable data class Segment(val speed: Double, val seconds: Int)

@Serializable data class SessionState(
    val active: Boolean = false,
    val elapsedSec: Int = 0,
    val currentSegment: Int = 0,
    val nextChangeInSec: Int = 0,
    val speed: Double = 0.0,
    val upcomingSpeed: Double? = null
)

@Serializable data class SessionLog(
    val id: String,                // e.g., "sess_1730400000000"
    val startMillis: Long,
    val endMillis: Long,
    val totalSeconds: Int,
    val segments: List<Segment>
)

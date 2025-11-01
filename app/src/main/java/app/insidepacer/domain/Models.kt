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

@kotlinx.serialization.Serializable
data class SessionLog(
    val id: String,
    val startMillis: Long,
    val endMillis: Long,
    val totalSeconds: Int,
    val segments: List<Segment>,
    val aborted: Boolean
)

@kotlinx.serialization.Serializable
data class Template(
    val id: String,
    val name: String,
    val segments: List<Segment>
)
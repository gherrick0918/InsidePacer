package app.insidepacer.domain

import android.os.Parcelable
import app.insidepacer.data.Units
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
data class SessionState(
    val active: Boolean = false,
    val isPaused: Boolean = false,
    val elapsedSec: Int = 0,
    val currentSegment: Int = 0,
    val nextChangeInSec: Int = 0,
    val speed: Double = 0.0,
    val upcomingSpeed: Double? = null,
    val segments: List<Segment> = emptyList(),
    val sessionId: String? = null,
    val sessionStartTime: Long = 0L,
    val totalDurationSec: Int = 0,
    val remainingSec: Int = 0,
    val currentSegmentLabel: String? = null,
    val units: Units = Units.MPH
) : Parcelable

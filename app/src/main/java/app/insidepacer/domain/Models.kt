package app.insidepacer.domain

import kotlinx.serialization.Serializable

@Serializable data class Segment(val speed: Double, val seconds: Int)
@Serializable data class SessionState(
    val active: Boolean = false,
    val elapsedSec: Int = 0,
    val currentSegment: Int = 0,
    val nextChangeInSec: Int = 0,
    val speed: Double = 0.0
)
package app.insidepacer.domain

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
data class Segment(val speed: Double, val seconds: Int) : Parcelable

@Serializable
data class SessionLog(
    val id: String,
    val programId: String?,
    val startMillis: Long,
    val endMillis: Long,
    val totalSeconds: Int,
    val segments: List<Segment>,
    val aborted: Boolean,
    val notes: String? = null
)

@Serializable
data class Template(
    val id: String,
    val name: String,
    val segments: List<Segment>
)

@Serializable
data class Program(
    val id: String,
    val name: String,
    val startEpochDay: Long,    // LocalDate.toEpochDay()
    val weeks: Int,
    val daysPerWeek: Int = 7,
    val grid: List<List<String?>> // templateId or null (Rest)
)

@Serializable
data class UserProfile(
    val age: Int = 35,
    val heightCm: Int = 175,
    val weightKg: Double = 85.0,
    val targetWeightKg: Double? = null,
    val preferredDaysPerWeek: Int = 5,   // 2..7
    val sessionMinMin: Int = 20,         // minutes
    val sessionMaxMin: Int = 40,         // minutes
    val level: String = "Beginner",      // "Beginner" | "Intermediate"
    val startEpochDay: Long = java.time.LocalDate.now().toEpochDay(),
    val units: String = "mph"            // "mph" | "kmh" (display only; no conversion yet)
)

package app.insidepacer.core

import app.insidepacer.data.Units
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToLong

private const val MPH_TO_KMH = 1.609344
private const val SECONDS_PER_HOUR = 3600.0

fun formatDuration(totalSec: Long): String {
    val clamped = totalSec.coerceAtLeast(0)
    val hours = clamped / 3600
    val minutes = ((clamped % 3600) / 60).toInt()
    val seconds = (clamped % 60).toInt()
    val locale = Locale.getDefault()
    return String.format(locale, "%d:%02d:%02d", hours, minutes, seconds)
}

fun formatDuration(totalSec: Int): String = formatDuration(totalSec.toLong())

fun formatDuration(totalSec: Double): String = formatDuration(totalSec.roundToLong())

fun formatDurationForSpeech(totalSec: Long): String {
    val clamped = totalSec.coerceAtLeast(0)
    val hours = clamped / 3600
    val minutes = ((clamped % 3600) / 60).toInt()
    val seconds = (clamped % 60).toInt()
    
    val parts = mutableListOf<String>()
    if (hours > 0) {
        parts.add(if (hours == 1L) "1 hour" else "$hours hours")
    }
    if (minutes > 0) {
        parts.add(if (minutes == 1) "1 minute" else "$minutes minutes")
    }
    if (seconds > 0) {
        parts.add(if (seconds == 1) "1 second" else "$seconds seconds")
    }
    
    return when (parts.size) {
        0 -> "0 seconds"
        1 -> parts[0]
        2 -> "${parts[0]} and ${parts[1]}"
        else -> "${parts[0]}, ${parts[1]}, and ${parts[2]}"
    }
}

fun formatDurationForSpeech(totalSec: Int): String = formatDurationForSpeech(totalSec.toLong())

fun formatSpeed(valueMpsOrMph: Double, units: Units): String {
    val displayValue = speedToUnits(valueMpsOrMph, units)
    val numberFormat = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 1
    }
    return numberFormat.format(displayValue) + " " + speedUnitLabel(units)
}

fun formatPace(value: Double, units: Units): String {
    require(value > 0) { "Speed must be positive to compute pace" }
    val speedForUnits = speedToUnits(value, units)
    val paceSeconds = (SECONDS_PER_HOUR / speedForUnits).roundToLong()
    val minutes = (paceSeconds / 60).toInt()
    val seconds = (paceSeconds % 60).toInt()
    val locale = Locale.getDefault()
    val label = when (units) {
        Units.MPH -> "min/mi"
        Units.KMH -> "min/km"
    }
    return String.format(locale, "%d:%02d %s", minutes, seconds, label)
}

fun speedToUnits(valueMph: Double, units: Units): Double = when (units) {
    Units.MPH -> valueMph
    Units.KMH -> valueMph * MPH_TO_KMH
}

fun speedFromUnits(value: Double, units: Units): Double = when (units) {
    Units.MPH -> value
    Units.KMH -> value / MPH_TO_KMH
}

fun speedUnitLabel(units: Units): String = when (units) {
    Units.MPH -> "mph"
    Units.KMH -> "km/h"
}

fun speedUnitToken(units: Units): String = when (units) {
    Units.MPH -> "mph"
    Units.KMH -> "kmh"
}

fun formatDistance(distanceMiles: Double, units: Units): String {
    val distance = if (units == Units.MPH) distanceMiles else distanceMiles * MPH_TO_KMH
    val unitLabel = if (units == Units.MPH) "mi" else "km"
    return String.format(Locale.getDefault(), "%.1f %s", distance, unitLabel)
}

fun formatDistancePrecise(distanceMiles: Double, units: Units): String {
    val distance = if (units == Units.MPH) distanceMiles else distanceMiles * MPH_TO_KMH
    val unitLabel = if (units == Units.MPH) "mi" else "km"
    return String.format(Locale.getDefault(), "%.2f %s", distance, unitLabel)
}

fun csvNumberFormat(): DecimalFormat = DecimalFormat("0.0", DecimalFormatSymbols(Locale.US)).apply {
    minimumFractionDigits = 1
    maximumFractionDigits = 1
}

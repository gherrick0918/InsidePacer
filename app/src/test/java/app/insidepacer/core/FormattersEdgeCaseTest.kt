package app.insidepacer.core

import app.insidepacer.data.Units
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

/**
 * Additional edge case tests for Formatters
 */
class FormattersEdgeCaseTest {

    @Test
    fun `formatDuration handles very large values`() {
        val result = formatDuration(86400L) // 24 hours
        assertTrue(result.contains("24:00:00"))
    }

    @Test
    fun `formatDuration handles negative values by clamping to zero`() {
        val result = formatDuration(-100L)
        assertEquals("0:00:00", result)
    }

    @Test
    fun `formatSpeed with very small speeds`() {
        Locale.setDefault(Locale.US)
        val result = formatSpeed(0.1, Units.MPH)
        assertEquals("0.1 mph", result)
    }

    @Test
    fun `formatSpeed with very high speeds`() {
        Locale.setDefault(Locale.US)
        val result = formatSpeed(15.0, Units.MPH)
        assertEquals("15.0 mph", result)
    }

    @Test
    fun `formatSpeed with zero speed`() {
        Locale.setDefault(Locale.US)
        val result = formatSpeed(0.0, Units.MPH)
        assertEquals("0.0 mph", result)
    }

    @Test
    fun `formatPace with very slow speed`() {
        Locale.setDefault(Locale.US)
        val result = formatPace(2.0, Units.MPH)
        // At 2 mph, pace should be 30:00 min/mi (3600 / 2 = 1800 seconds = 30 minutes)
        assertEquals("30:00 min/mi", result)
    }

    @Test
    fun `formatPace with very fast speed`() {
        Locale.setDefault(Locale.US)
        val result = formatPace(12.0, Units.MPH)
        // At 12 mph, pace should be 5:00 min/mi (3600 / 12 = 300 seconds = 5 minutes)
        assertEquals("5:00 min/mi", result)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `formatPace throws on zero speed`() {
        formatPace(0.0, Units.MPH)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `formatPace throws on negative speed`() {
        formatPace(-5.0, Units.MPH)
    }

    @Test
    fun `speedToUnits converts MPH to KMH correctly`() {
        val result = speedToUnits(10.0, Units.KMH)
        // 10 mph * 1.609344 = 16.09344 km/h
        assertEquals(16.09344, result, 0.00001)
    }

    @Test
    fun `speedToUnits returns same value for MPH units`() {
        val result = speedToUnits(10.0, Units.MPH)
        assertEquals(10.0, result, 0.001)
    }

    @Test
    fun `formatDurationForSpeech with zero seconds`() {
        val result = formatDurationForSpeech(0L)
        assertEquals("0 seconds", result)
    }

    @Test
    fun `formatDurationForSpeech with large hours value`() {
        val result = formatDurationForSpeech(10800L) // 3 hours
        assertEquals("3 hours", result)
    }

    @Test
    fun `formatDurationForSpeech with multiple hours and components`() {
        val result = formatDurationForSpeech(7325L) // 2 hours, 2 minutes, 5 seconds
        assertEquals("2 hours, 2 minutes, and 5 seconds", result)
    }

    @Test
    fun `formatDuration with Int parameter`() {
        val result = formatDuration(150)
        assertEquals("0:02:30", result)
    }

    @Test
    fun `formatDuration with Double parameter rounds correctly`() {
        val result = formatDuration(150.7)
        // Should round to 151 seconds = 2:31
        assertEquals("0:02:31", result)
    }

    @Test
    fun `formatDurationForSpeech with Int parameter`() {
        val result = formatDurationForSpeech(125)
        assertEquals("2 minutes and 5 seconds", result)
    }

    @Test
    fun `formatSpeed handles decimal precision`() {
        Locale.setDefault(Locale.US)
        val result = formatSpeed(6.789, Units.MPH)
        assertEquals("6.8 mph", result)
    }

    @Test
    fun `formatSpeed KMH conversion and formatting`() {
        Locale.setDefault(Locale.US)
        val result = formatSpeed(5.0, Units.KMH)
        // 5 mph * 1.609344 = 8.04672 km/h, formatted to 1 decimal place
        assertEquals("8.0 km/h", result)
    }
}

package app.insidepacer.core

import app.insidepacer.data.Units
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class FormattersTest {
    private lateinit var originalLocale: Locale

    @Before
    fun setUp() {
        originalLocale = Locale.getDefault()
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun formatDurationRoundsToMinute() {
        Locale.setDefault(Locale.US)
        assertEquals("0:01:00", formatDuration(59.5))
    }

    @Test
    fun formatDurationRollsToHour() {
        Locale.setDefault(Locale.US)
        assertEquals("1:00:00", formatDuration(3599.5))
    }

    @Test
    fun formatSpeedConvertsToKmh() {
        Locale.setDefault(Locale.US)
        assertEquals("9.7 km/h", formatSpeed(6.0, Units.KMH))
    }

    @Test
    fun formatPaceRoundsSeconds() {
        Locale.setDefault(Locale.US)
        assertEquals("8:34 min/mi", formatPace(7.0, Units.MPH))
    }

    @Test
    fun localeSnapshots() {
        Locale.setDefault(Locale.US)
        val usSpeed = formatSpeed(6.2, Units.MPH)
        Locale.setDefault(Locale.UK)
        val ukSpeed = formatSpeed(6.21371192, Units.KMH)
        assertEquals("6.2 mph", usSpeed)
        assertEquals("10.0 km/h", ukSpeed)
    }
}

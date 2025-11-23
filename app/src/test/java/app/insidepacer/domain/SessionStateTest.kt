package app.insidepacer.domain

import app.insidepacer.data.Units
import org.junit.Assert.*
import org.junit.Test

class SessionStateTest {

    @Test
    fun `SessionState default values`() {
        val state = SessionState()
        
        assertFalse(state.active)
        assertFalse(state.isPaused)
        assertEquals(0, state.elapsedSec)
        assertEquals(0, state.currentSegment)
        assertEquals(0, state.nextChangeInSec)
        assertEquals(0.0, state.speed, 0.001)
        assertNull(state.upcomingSpeed)
        assertTrue(state.segments.isEmpty())
        assertNull(state.sessionId)
        assertEquals(0L, state.sessionStartTime)
        assertEquals(0, state.totalDurationSec)
        assertEquals(0, state.remainingSec)
        assertNull(state.currentSegmentLabel)
        assertEquals(Units.MPH, state.units)
    }

    @Test
    fun `SessionState with active session`() {
        val segments = listOf(
            Segment(5.0, 60),
            Segment(6.0, 120)
        )
        val state = SessionState(
            active = true,
            isPaused = false,
            elapsedSec = 30,
            currentSegment = 0,
            nextChangeInSec = 30,
            speed = 5.0,
            upcomingSpeed = 6.0,
            segments = segments,
            sessionId = "session123",
            sessionStartTime = 1000000L,
            totalDurationSec = 180,
            remainingSec = 150,
            currentSegmentLabel = "Warm-up",
            units = Units.MPH
        )
        
        assertTrue(state.active)
        assertFalse(state.isPaused)
        assertEquals(30, state.elapsedSec)
        assertEquals(0, state.currentSegment)
        assertEquals(30, state.nextChangeInSec)
        assertEquals(5.0, state.speed, 0.001)
        assertEquals(6.0, state.upcomingSpeed!!, 0.001)
        assertEquals(2, state.segments.size)
        assertEquals("session123", state.sessionId)
        assertEquals(1000000L, state.sessionStartTime)
        assertEquals(180, state.totalDurationSec)
        assertEquals(150, state.remainingSec)
        assertEquals("Warm-up", state.currentSegmentLabel)
        assertEquals(Units.MPH, state.units)
    }

    @Test
    fun `SessionState with paused session`() {
        val state = SessionState(
            active = true,
            isPaused = true,
            elapsedSec = 90,
            currentSegment = 1,
            speed = 6.0,
            units = Units.KMH
        )
        
        assertTrue(state.active)
        assertTrue(state.isPaused)
        assertEquals(90, state.elapsedSec)
        assertEquals(1, state.currentSegment)
        assertEquals(Units.KMH, state.units)
    }

    @Test
    fun `SessionState without upcoming speed`() {
        val segments = listOf(Segment(5.0, 60))
        val state = SessionState(
            active = true,
            segments = segments,
            speed = 5.0,
            upcomingSpeed = null
        )
        
        assertNull(state.upcomingSpeed)
    }

    @Test
    fun `SessionState at segment transition`() {
        val segments = listOf(
            Segment(4.0, 300),
            Segment(7.0, 180),
            Segment(5.0, 240)
        )
        val state = SessionState(
            active = true,
            elapsedSec = 300,
            currentSegment = 1,
            nextChangeInSec = 180,
            speed = 7.0,
            upcomingSpeed = 5.0,
            segments = segments,
            totalDurationSec = 720,
            remainingSec = 420
        )
        
        assertEquals(1, state.currentSegment)
        assertEquals(7.0, state.speed, 0.001)
        assertEquals(5.0, state.upcomingSpeed!!, 0.001)
        assertEquals(180, state.nextChangeInSec)
    }

    @Test
    fun `SessionState on last segment`() {
        val segments = listOf(
            Segment(5.0, 60),
            Segment(6.0, 60)
        )
        val state = SessionState(
            active = true,
            elapsedSec = 90,
            currentSegment = 1,
            nextChangeInSec = 30,
            speed = 6.0,
            upcomingSpeed = null,  // No upcoming speed on last segment
            segments = segments,
            totalDurationSec = 120,
            remainingSec = 30
        )
        
        assertEquals(1, state.currentSegment)
        assertNull(state.upcomingSpeed)
        assertEquals(30, state.remainingSec)
    }

    @Test
    fun `SessionState copy with different units`() {
        val original = SessionState(
            active = true,
            speed = 5.0,
            units = Units.MPH
        )
        
        val modified = original.copy(units = Units.KMH)
        
        assertEquals(Units.MPH, original.units)
        assertEquals(Units.KMH, modified.units)
        // Other fields should remain the same
        assertTrue(modified.active)
        assertEquals(5.0, modified.speed, 0.001)
    }
}

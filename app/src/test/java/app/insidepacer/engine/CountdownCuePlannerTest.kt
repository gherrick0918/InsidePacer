package app.insidepacer.engine

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CountdownCuePlannerTest {
    private val planner = CountdownCuePlanner()

    @Test
    fun `no ticks before segment start`() {
        assertFalse(planner.allowTick(3, voiceOn = false))
    }

    @Test
    fun `voice on suppresses ticks`() {
        planner.onSegmentStarted(12)
        assertFalse(planner.allowTick(3, voiceOn = true))
    }

    @Test
    fun `ticks allowed only for last three seconds`() {
        planner.onSegmentStarted(12)
        assertFalse(planner.allowTick(5, voiceOn = false))
        assertTrue(planner.allowTick(3, voiceOn = false))
        assertTrue(planner.allowTick(2, voiceOn = false))
        assertTrue(planner.allowTick(1, voiceOn = false))
    }

    @Test
    fun `short segments skip countdown ticks`() {
        planner.onSegmentStarted(2)
        assertFalse(planner.allowTick(2, voiceOn = false))
        assertFalse(planner.allowTick(1, voiceOn = false))
    }

    @Test
    fun `segment restart resets tick window`() {
        planner.onSegmentStarted(10)
        assertTrue(planner.allowTick(2, voiceOn = false))
        planner.onSegmentStarted(6)
        assertTrue(planner.allowTick(3, voiceOn = false))
    }
}

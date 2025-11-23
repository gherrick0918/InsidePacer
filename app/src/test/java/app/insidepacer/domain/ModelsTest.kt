package app.insidepacer.domain

import org.junit.Assert.*
import org.junit.Test

class ModelsTest {

    @Test
    fun `Segment creation with valid data`() {
        val segment = Segment(speed = 5.5, seconds = 60)
        
        assertEquals(5.5, segment.speed, 0.001)
        assertEquals(60, segment.seconds)
    }

    @Test
    fun `Segment with zero speed`() {
        val segment = Segment(speed = 0.0, seconds = 30)
        
        assertEquals(0.0, segment.speed, 0.001)
        assertEquals(30, segment.seconds)
    }

    @Test
    fun `SessionLog with all fields`() {
        val segments = listOf(
            Segment(5.0, 60),
            Segment(6.0, 120)
        )
        val sessionLog = SessionLog(
            id = "session123",
            programId = "program456",
            startMillis = 1000000L,
            endMillis = 1180000L,
            totalSeconds = 180,
            segments = segments,
            aborted = false,
            notes = "Great workout"
        )
        
        assertEquals("session123", sessionLog.id)
        assertEquals("program456", sessionLog.programId)
        assertEquals(1000000L, sessionLog.startMillis)
        assertEquals(1180000L, sessionLog.endMillis)
        assertEquals(180, sessionLog.totalSeconds)
        assertEquals(2, sessionLog.segments.size)
        assertFalse(sessionLog.aborted)
        assertEquals("Great workout", sessionLog.notes)
    }

    @Test
    fun `SessionLog without program and notes`() {
        val segments = listOf(Segment(5.0, 60))
        val sessionLog = SessionLog(
            id = "session123",
            programId = null,
            startMillis = 1000000L,
            endMillis = 1060000L,
            totalSeconds = 60,
            segments = segments,
            aborted = true,
            notes = null
        )
        
        assertNull(sessionLog.programId)
        assertNull(sessionLog.notes)
        assertTrue(sessionLog.aborted)
    }

    @Test
    fun `Template with segments`() {
        val segments = listOf(
            Segment(4.0, 300),
            Segment(6.0, 180),
            Segment(5.0, 240)
        )
        val template = Template(
            id = "template789",
            name = "Interval Training",
            segments = segments
        )
        
        assertEquals("template789", template.id)
        assertEquals("Interval Training", template.name)
        assertEquals(3, template.segments.size)
        assertEquals(4.0, template.segments[0].speed, 0.001)
    }

    @Test
    fun `Template with empty segments`() {
        val template = Template(
            id = "template_empty",
            name = "Empty Template",
            segments = emptyList()
        )
        
        assertTrue(template.segments.isEmpty())
    }

    @Test
    fun `Program with full grid`() {
        val grid = listOf(
            listOf("template1", "template2", "template1", null, "template3", null, null),
            listOf("template2", null, "template1", "template3", null, null, null)
        )
        val program = Program(
            id = "program123",
            name = "8-Week Plan",
            startEpochDay = 19000L,
            weeks = 2,
            daysPerWeek = 7,
            grid = grid
        )
        
        assertEquals("program123", program.id)
        assertEquals("8-Week Plan", program.name)
        assertEquals(19000L, program.startEpochDay)
        assertEquals(2, program.weeks)
        assertEquals(7, program.daysPerWeek)
        assertEquals(2, program.grid.size)
        assertEquals(7, program.grid[0].size)
    }

    @Test
    fun `Program with custom days per week`() {
        val grid = listOf(
            listOf("template1", null, "template2", null, "template3")
        )
        val program = Program(
            id = "program456",
            name = "Custom Week",
            startEpochDay = 19100L,
            weeks = 1,
            daysPerWeek = 5,
            grid = grid
        )
        
        assertEquals(5, program.daysPerWeek)
        assertEquals(5, program.grid[0].size)
    }

    @Test
    fun `UserProfile with default values`() {
        val profile = UserProfile()
        
        assertEquals(35, profile.age)
        assertEquals(175, profile.heightCm)
        assertEquals(85.0, profile.weightKg, 0.001)
        assertNull(profile.targetWeightKg)
        assertEquals(5, profile.preferredDaysPerWeek)
        assertEquals(20, profile.sessionMinMin)
        assertEquals(40, profile.sessionMaxMin)
        assertEquals("Beginner", profile.level)
        assertEquals("mph", profile.units)
    }

    @Test
    fun `UserProfile with custom values`() {
        val profile = UserProfile(
            age = 28,
            heightCm = 180,
            weightKg = 75.0,
            targetWeightKg = 70.0,
            preferredDaysPerWeek = 3,
            sessionMinMin = 30,
            sessionMaxMin = 60,
            level = "Intermediate",
            startEpochDay = 19200L,
            units = "kmh"
        )
        
        assertEquals(28, profile.age)
        assertEquals(180, profile.heightCm)
        assertEquals(75.0, profile.weightKg, 0.001)
        assertEquals(70.0, profile.targetWeightKg!!, 0.001)
        assertEquals(3, profile.preferredDaysPerWeek)
        assertEquals(30, profile.sessionMinMin)
        assertEquals(60, profile.sessionMaxMin)
        assertEquals("Intermediate", profile.level)
        assertEquals(19200L, profile.startEpochDay)
        assertEquals("kmh", profile.units)
    }

    @Test
    fun `UserProfile boundary values for days per week`() {
        val profile = UserProfile(preferredDaysPerWeek = 7)
        assertEquals(7, profile.preferredDaysPerWeek)
        
        val profile2 = UserProfile(preferredDaysPerWeek = 2)
        assertEquals(2, profile2.preferredDaysPerWeek)
    }
}

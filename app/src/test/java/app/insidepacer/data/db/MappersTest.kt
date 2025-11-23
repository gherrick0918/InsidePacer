package app.insidepacer.data.db

import app.insidepacer.domain.Program
import app.insidepacer.domain.Segment
import app.insidepacer.domain.SessionLog
import app.insidepacer.domain.Template
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for entity/domain model mappers.
 */
class MappersTest {

    @Test
    fun `SessionLog to SessionEntity and back`() {
        val session = SessionLog(
            id = "session_123",
            programId = "prog_456",
            startMillis = 1234567890000L,
            endMillis = 1234567890000L + 3600000L,
            totalSeconds = 3600,
            segments = listOf(
                Segment(5.0, 1800),
                Segment(6.0, 1800)
            ),
            aborted = false
        )

        val entity = session.toEntity()
        val restored = entity.toDomain()

        assertEquals(session.id, restored.id)
        assertEquals(session.programId, restored.programId)
        assertEquals(session.startMillis, restored.startMillis)
        assertEquals(session.endMillis, restored.endMillis)
        assertEquals(session.totalSeconds, restored.totalSeconds)
        assertEquals(session.segments.size, restored.segments.size)
        assertEquals(session.aborted, restored.aborted)
    }

    @Test
    fun `Template to TemplateEntity and back`() {
        val template = Template(
            id = "tmpl_123",
            name = "5K Training",
            segments = listOf(
                Segment(4.0, 300),
                Segment(5.0, 600),
                Segment(4.0, 300)
            )
        )

        val entity = template.toEntity()
        val restored = entity.toDomain()

        assertEquals(template.id, restored.id)
        assertEquals(template.name, restored.name)
        assertEquals(template.segments.size, restored.segments.size)
        assertEquals(template.segments[0].speed, restored.segments[0].speed, 0.01)
        assertEquals(template.segments[0].seconds, restored.segments[0].seconds)
    }

    @Test
    fun `Program to ProgramEntity and back`() {
        val program = Program(
            id = "prog_123",
            name = "Marathon Training",
            startEpochDay = 19000L,
            weeks = 12,
            daysPerWeek = 7,
            grid = listOf(
                listOf("tmpl_1", null, "tmpl_2", null, "tmpl_3", null, null),
                listOf("tmpl_4", null, "tmpl_5", null, "tmpl_6", null, null)
            )
        )

        val entity = program.toEntity()
        val restored = entity.toDomain()

        assertEquals(program.id, restored.id)
        assertEquals(program.name, restored.name)
        assertEquals(program.startEpochDay, restored.startEpochDay)
        assertEquals(program.weeks, restored.weeks)
        assertEquals(program.daysPerWeek, restored.daysPerWeek)
        assertEquals(program.grid.size, restored.grid.size)
        assertEquals(program.grid[0][0], restored.grid[0][0])
        assertNull(restored.grid[0][1])
        assertEquals(program.grid[0][2], restored.grid[0][2])
    }

    @Test
    fun `SessionLog with null programId`() {
        val session = SessionLog(
            id = "session_123",
            programId = null,
            startMillis = 1234567890000L,
            endMillis = 1234567890000L + 1800000L,
            totalSeconds = 1800,
            segments = listOf(Segment(5.0, 1800)),
            aborted = false
        )

        val entity = session.toEntity()
        val restored = entity.toDomain()

        assertNull(restored.programId)
        assertEquals(session.id, restored.id)
    }

    @Test
    fun `Template with empty segments`() {
        val template = Template(
            id = "tmpl_empty",
            name = "Empty Template",
            segments = emptyList()
        )

        val entity = template.toEntity()
        val restored = entity.toDomain()

        assertEquals(template.id, restored.id)
        assertTrue(restored.segments.isEmpty())
    }
}

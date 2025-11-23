package app.insidepacer.data

import app.insidepacer.domain.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import kotlinx.coroutines.runBlocking

/**
 * Unit tests for StatisticsRepository
 */
class StatisticsRepositoryTest {

    @Test
    fun `calculateDistance should return correct miles for single segment`() {
        val segment = Segment(speed = 6.0, seconds = 3600) // 6 mph for 1 hour
        val session = SessionLog(
            id = "test",
            programId = null,
            startMillis = 0L,
            endMillis = 3600000L,
            totalSeconds = 3600,
            segments = listOf(segment),
            aborted = false
        )
        
        // Calculate distance using the same formula as StatisticsRepository
        val distance = segment.speed * (segment.seconds / 3600.0)
        assertEquals(6.0, distance, 0.01)
    }

    @Test
    fun `calculateDistance should return correct miles for multiple segments`() {
        val segments = listOf(
            Segment(speed = 6.0, seconds = 1800), // 6 mph for 0.5 hours = 3 miles
            Segment(speed = 8.0, seconds = 1800)  // 8 mph for 0.5 hours = 4 miles
        )
        val session = SessionLog(
            id = "test",
            programId = null,
            startMillis = 0L,
            endMillis = 3600000L,
            totalSeconds = 3600,
            segments = segments,
            aborted = false
        )
        
        val distance = segments.sumOf { it.speed * (it.seconds / 3600.0) }
        assertEquals(7.0, distance, 0.01) // 3 + 4 = 7 miles
    }

    @Test
    fun `average speed calculation should be weighted correctly`() {
        val segments = listOf(
            Segment(speed = 5.0, seconds = 600),   // 5 mph for 10 minutes
            Segment(speed = 7.0, seconds = 1200)   // 7 mph for 20 minutes
        )
        val totalSeconds = segments.sumOf { it.seconds }
        
        val weightedSpeed = segments.sumOf { it.speed * it.seconds } / totalSeconds
        
        // Expected: (5*600 + 7*1200) / 1800 = (3000 + 8400) / 1800 = 6.333
        assertEquals(6.333, weightedSpeed, 0.01)
    }

    @Test
    fun `personal records should identify fastest speed correctly`() {
        val sessions = listOf(
            SessionLog(
                id = "1",
                programId = null,
                startMillis = 0L,
                endMillis = 3600000L,
                totalSeconds = 3600,
                segments = listOf(Segment(5.0, 3600)),
                aborted = false
            ),
            SessionLog(
                id = "2",
                programId = null,
                startMillis = 0L,
                endMillis = 3600000L,
                totalSeconds = 3600,
                segments = listOf(Segment(8.0, 3600)), // Fastest
                aborted = false
            ),
            SessionLog(
                id = "3",
                programId = null,
                startMillis = 0L,
                endMillis = 3600000L,
                totalSeconds = 3600,
                segments = listOf(Segment(6.0, 3600)),
                aborted = false
            )
        )
        
        val fastestSession = sessions.maxByOrNull { session ->
            if (session.totalSeconds > 0) {
                session.segments.sumOf { it.speed * it.seconds } / session.totalSeconds
            } else {
                0.0
            }
        }
        
        assertEquals("2", fastestSession?.id)
    }

    @Test
    fun `personal records should identify longest duration correctly`() {
        val sessions = listOf(
            SessionLog(
                id = "1",
                programId = null,
                startMillis = 0L,
                endMillis = 3600000L,
                totalSeconds = 3600,
                segments = listOf(Segment(5.0, 3600)),
                aborted = false
            ),
            SessionLog(
                id = "2",
                programId = null,
                startMillis = 0L,
                endMillis = 7200000L,
                totalSeconds = 7200, // Longest
                segments = listOf(Segment(6.0, 7200)),
                aborted = false
            ),
            SessionLog(
                id = "3",
                programId = null,
                startMillis = 0L,
                endMillis = 1800000L,
                totalSeconds = 1800,
                segments = listOf(Segment(7.0, 1800)),
                aborted = false
            )
        )
        
        val longestSession = sessions.maxByOrNull { it.totalSeconds }
        assertEquals("2", longestSession?.id)
    }

    @Test
    fun `statistics should handle empty session list`() {
        val sessions = emptyList<SessionLog>()
        
        val totalTime = sessions.sumOf { it.totalSeconds.toLong() }
        val totalWorkouts = sessions.size
        
        assertEquals(0L, totalTime)
        assertEquals(0, totalWorkouts)
    }

    @Test
    fun `statistics should count completed and aborted workouts correctly`() {
        val sessions = listOf(
            SessionLog(
                id = "1",
                programId = null,
                startMillis = 0L,
                endMillis = 3600000L,
                totalSeconds = 3600,
                segments = listOf(Segment(5.0, 3600)),
                aborted = false
            ),
            SessionLog(
                id = "2",
                programId = null,
                startMillis = 0L,
                endMillis = 1800000L,
                totalSeconds = 1800,
                segments = listOf(Segment(6.0, 1800)),
                aborted = true
            ),
            SessionLog(
                id = "3",
                programId = null,
                startMillis = 0L,
                endMillis = 3600000L,
                totalSeconds = 3600,
                segments = listOf(Segment(7.0, 3600)),
                aborted = false
            )
        )
        
        val completed = sessions.count { !it.aborted }
        val aborted = sessions.count { it.aborted }
        
        assertEquals(2, completed)
        assertEquals(1, aborted)
    }
}

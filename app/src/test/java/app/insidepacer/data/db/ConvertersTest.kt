package app.insidepacer.data.db

import app.insidepacer.domain.Segment
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for Room type converters.
 * These tests validate JSON serialization/deserialization of complex types.
 */
class ConvertersTest {
    private val converters = Converters()

    @Test
    fun `segment list roundtrip conversion`() {
        val segments = listOf(
            Segment(speed = 5.0, seconds = 60),
            Segment(speed = 6.0, seconds = 120),
            Segment(speed = 4.5, seconds = 30)
        )

        val json = converters.fromSegmentList(segments)
        val restored = converters.toSegmentList(json)

        assertEquals(segments.size, restored.size)
        assertEquals(segments[0].speed, restored[0].speed, 0.01)
        assertEquals(segments[0].seconds, restored[0].seconds)
        assertEquals(segments[1].speed, restored[1].speed, 0.01)
        assertEquals(segments[1].seconds, restored[1].seconds)
    }

    @Test
    fun `empty segment list conversion`() {
        val segments = emptyList<Segment>()
        
        val json = converters.fromSegmentList(segments)
        val restored = converters.toSegmentList(json)
        
        assertTrue(restored.isEmpty())
    }

    @Test
    fun `grid with nulls roundtrip conversion`() {
        val grid = listOf(
            listOf("tmpl_1", null, "tmpl_2"),
            listOf(null, "tmpl_3", null),
            listOf("tmpl_4", "tmpl_5", "tmpl_6")
        )

        val json = converters.fromGrid(grid)
        val restored = converters.toGrid(json)

        assertEquals(grid.size, restored.size)
        assertEquals(grid[0][0], restored[0][0])
        assertNull(restored[0][1])
        assertEquals(grid[0][2], restored[0][2])
        assertNull(restored[1][0])
        assertEquals(grid[1][1], restored[1][1])
    }

    @Test
    fun `empty grid conversion`() {
        val grid = emptyList<List<String?>>()
        
        val json = converters.fromGrid(grid)
        val restored = converters.toGrid(json)
        
        assertTrue(restored.isEmpty())
    }

    @Test
    fun `grid with all nulls conversion`() {
        val grid = listOf(
            listOf(null, null, null),
            listOf(null, null, null)
        )

        val json = converters.fromGrid(grid)
        val restored = converters.toGrid(json)

        assertEquals(grid.size, restored.size)
        assertEquals(grid[0].size, restored[0].size)
        assertTrue(restored[0].all { it == null })
        assertTrue(restored[1].all { it == null })
    }
}

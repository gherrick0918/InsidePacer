package app.insidepacer.data.db

import app.insidepacer.domain.Segment
import org.junit.Test
import org.junit.Assert.*

/**
 * Additional edge case tests for Converters
 */
class ConvertersEdgeCaseTest {
    private val converters = Converters()

    @Test
    fun `single segment list conversion`() {
        val segments = listOf(Segment(speed = 7.5, seconds = 90))
        
        val json = converters.fromSegmentList(segments)
        val restored = converters.toSegmentList(json)
        
        assertEquals(1, restored.size)
        assertEquals(7.5, restored[0].speed, 0.01)
        assertEquals(90, restored[0].seconds)
    }

    @Test
    fun `segment with zero values conversion`() {
        val segments = listOf(Segment(speed = 0.0, seconds = 0))
        
        val json = converters.fromSegmentList(segments)
        val restored = converters.toSegmentList(json)
        
        assertEquals(1, restored.size)
        assertEquals(0.0, restored[0].speed, 0.01)
        assertEquals(0, restored[0].seconds)
    }

    @Test
    fun `large segment list conversion`() {
        val segments = (1..100).map { Segment(speed = it.toDouble(), seconds = it * 10) }
        
        val json = converters.fromSegmentList(segments)
        val restored = converters.toSegmentList(json)
        
        assertEquals(100, restored.size)
        assertEquals(1.0, restored[0].speed, 0.01)
        assertEquals(10, restored[0].seconds)
        assertEquals(100.0, restored[99].speed, 0.01)
        assertEquals(1000, restored[99].seconds)
    }

    @Test
    fun `grid with all nulls conversion`() {
        val grid = listOf(
            listOf(null, null, null),
            listOf(null, null, null)
        )
        
        val json = converters.fromGrid(grid)
        val restored = converters.toGrid(json)
        
        assertEquals(2, restored.size)
        assertEquals(3, restored[0].size)
        assertNull(restored[0][0])
        assertNull(restored[1][2])
    }

    @Test
    fun `grid with no nulls conversion`() {
        val grid = listOf(
            listOf("a", "b", "c"),
            listOf("d", "e", "f")
        )
        
        val json = converters.fromGrid(grid)
        val restored = converters.toGrid(json)
        
        assertEquals(2, restored.size)
        assertEquals(3, restored[0].size)
        assertEquals("a", restored[0][0])
        assertEquals("f", restored[1][2])
    }

    @Test
    fun `empty grid conversion`() {
        val grid = emptyList<List<String?>>()
        
        val json = converters.fromGrid(grid)
        val restored = converters.toGrid(json)
        
        assertTrue(restored.isEmpty())
    }

    @Test
    fun `grid with empty rows conversion`() {
        val grid = listOf(emptyList<String?>())
        
        val json = converters.fromGrid(grid)
        val restored = converters.toGrid(json)
        
        assertEquals(1, restored.size)
        assertTrue(restored[0].isEmpty())
    }

    @Test
    fun `single cell grid conversion`() {
        val grid = listOf(listOf("single"))
        
        val json = converters.fromGrid(grid)
        val restored = converters.toGrid(json)
        
        assertEquals(1, restored.size)
        assertEquals(1, restored[0].size)
        assertEquals("single", restored[0][0])
    }

    @Test
    fun `segment with very large values conversion`() {
        val segments = listOf(Segment(speed = 999.999, seconds = 86400))
        
        val json = converters.fromSegmentList(segments)
        val restored = converters.toSegmentList(json)
        
        assertEquals(1, restored.size)
        assertEquals(999.999, restored[0].speed, 0.01)
        assertEquals(86400, restored[0].seconds)
    }

    @Test
    fun `grid with long strings conversion`() {
        val longId = "template_" + "x".repeat(100)
        val grid = listOf(listOf(longId, null))
        
        val json = converters.fromGrid(grid)
        val restored = converters.toGrid(json)
        
        assertEquals(1, restored.size)
        assertEquals(2, restored[0].size)
        assertEquals(longId, restored[0][0])
        assertNull(restored[0][1])
    }

    @Test
    fun `segments with decimal precision conversion`() {
        val segments = listOf(
            Segment(speed = 5.123456789, seconds = 30),
            Segment(speed = 7.987654321, seconds = 45)
        )
        
        val json = converters.fromSegmentList(segments)
        val restored = converters.toSegmentList(json)
        
        assertEquals(2, restored.size)
        assertEquals(5.123456789, restored[0].speed, 0.000000001)
        assertEquals(7.987654321, restored[1].speed, 0.000000001)
    }
}

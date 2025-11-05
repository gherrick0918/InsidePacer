package app.insidepacer.csv

import app.insidepacer.data.Units
import java.io.ByteArrayOutputStream
import kotlin.text.Charsets
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class CsvWriterTest {
    @Test
    fun writesBomAndCrLfWithEscaping() {
        val buffer = ByteArrayOutputStream()
        CsvWriter.from(buffer).use { writer ->
            writer.writeRow(listOf("header1", "header2"))
            writer.writeRow(listOf("value", "he said \"hi\""))
            writer.writeRow(listOf("multi,line", "second\nline"))
        }
        val bytes = buffer.toByteArray()
        assertEquals(0xEF.toByte(), bytes[0])
        assertEquals(0xBB.toByte(), bytes[1])
        assertEquals(0xBF.toByte(), bytes[2])
        val text = String(bytes, Charsets.UTF_8)
        assertTrue(text.contains("\r\n"))
        assertTrue(text.contains("\"he said \"\"hi\"\"\""))
        assertTrue(text.contains("\"multi,line\""))
        assertTrue(text.contains("\"second\nline\""))
    }

    @Test
    fun avgSpeedHeaderReflectsUnits() {
        assertEquals("avg_speed_mph", CsvFields.avgSpeed(Units.MPH))
        assertEquals("avg_speed_kmh", CsvFields.avgSpeed(Units.KMH))
        assertEquals("avg_speed_with_unit_mph", CsvFields.avgSpeedWithUnit(Units.MPH))
        assertEquals("avg_speed_with_unit_kmh", CsvFields.avgSpeedWithUnit(Units.KMH))
        assertEquals("max_speed_with_unit_mph", CsvFields.maxSpeedWithUnit(Units.MPH))
        assertEquals("max_speed_with_unit_kmh", CsvFields.maxSpeedWithUnit(Units.KMH))
    }
}

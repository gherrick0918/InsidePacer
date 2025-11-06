package app.insidepacer.data

import android.content.Context
import app.insidepacer.core.csvNumberFormat
import app.insidepacer.core.formatDuration
import app.insidepacer.core.speedToUnits
import app.insidepacer.core.speedUnitLabel
import app.insidepacer.domain.Segment
import app.insidepacer.domain.SessionLog
import app.insidepacer.data.Units
import app.insidepacer.csv.CsvFields
import app.insidepacer.csv.CsvWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class SessionRepo(private val ctx: Context) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val file: File get() = File(ctx.filesDir, "sessions.json")

    fun loadAll(): List<SessionLog> = try {
        if (!file.exists()) emptyList()
        else json.decodeFromString(ListSerializer(SessionLog.serializer()), file.readText())
    } catch (_: Throwable) { emptyList() }

    suspend fun append(log: SessionLog) = withContext(Dispatchers.IO) {
        val all = loadAll().toMutableList()
        all += log
        writeAll(all)
    }

    suspend fun replaceAll(logs: List<SessionLog>) = withContext(Dispatchers.IO) {
        writeAll(logs)
    }

    private fun writeAll(logs: List<SessionLog>) {
        val tmp = File.createTempFile("sessions", ".json", ctx.cacheDir)
        tmp.writeText(json.encodeToString(ListSerializer(SessionLog.serializer()), logs))
        tmp.copyTo(file, overwrite = true)
        tmp.delete()
    }

    /** Given the plan and actual elapsed, return truncated segments actually completed. */
    fun realizedSegments(plan: List<Segment>, elapsed: Int): List<Segment> {
        var remain = elapsed
        val out = mutableListOf<Segment>()
        for (seg in plan) {
            if (remain <= 0) break
            val d = minOf(seg.seconds, remain)
            out += Segment(seg.speed, d)
            remain -= d
        }
        return out
    }

    /** Export CSVs to cacheDir and return both files (summary, segments). */
    suspend fun exportCsv(units: Units): Pair<File, File> = withContext(Dispatchers.IO) {
        val items = loadAll()
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val numberFormat = csvNumberFormat()
        val sessionsCsv = File(ctx.cacheDir, "sessions.csv")
        val segsCsv = File(ctx.cacheDir, "session_segments.csv")

        CsvWriter.open(sessionsCsv).use { writer ->
            val header = listOf(
                CsvFields.SESSION_ID,
                CsvFields.SESSION_START,
                CsvFields.SESSION_END,
                CsvFields.TOTAL_DURATION_HMS,
                CsvFields.SEGMENTS_COUNT,
                CsvFields.ABORTED,
                CsvFields.avgSpeed(units),
                CsvFields.UNITS,
                CsvFields.DURATION_HMS,
                CsvFields.avgSpeedWithUnit(units),
                CsvFields.maxSpeedWithUnit(units),
                CsvFields.NOTES,
            )
            writer.writeRow(header)
            items.forEach { s ->
                val avgSpeedMph = if (s.totalSeconds > 0) {
                    s.segments.sumOf { it.speed * it.seconds } / s.totalSeconds
                } else {
                    0.0
                }
                val avgSpeed = numberFormat.format(speedToUnits(avgSpeedMph, units))
                val avgSpeedWithUnit = if (s.totalSeconds > 0) {
                    formatSpeedWithUnit(avgSpeedMph, units, numberFormat)
                } else {
                    ""
                }
                val duration = formatDuration(s.totalSeconds)
                val maxSpeed = s.segments.maxOfOrNull { it.speed }?.let { value ->
                    formatSpeedWithUnit(value, units, numberFormat)
                } ?: ""
                // TODO: session notes are not captured yet; export empty column for stability.
                writer.writeRow(
                    listOf(
                        s.id,
                        df.format(Date(s.startMillis)),
                        df.format(Date(s.endMillis)),
                        duration,
                        s.segments.size.toString(),
                        s.aborted.toString(),
                        avgSpeed,
                        units.name,
                        duration,
                        avgSpeedWithUnit,
                        maxSpeed,
                        ""
                    )
                )
            }
        }

        CsvWriter.open(segsCsv).use { writer ->
            val header = listOf(
                CsvFields.SEGMENT_SESSION_ID,
                CsvFields.SEGMENT_INDEX,
                CsvFields.speed(units),
                CsvFields.DURATION_HMS,
                CsvFields.targetSpeed(units),
                CsvFields.actualAvgSpeed(units),
                CsvFields.PRE_CHANGE_WARN_SEC
            )
            writer.writeRow(header)
            items.forEach { s ->
                s.segments.forEachIndexed { i, seg ->
                    val speed = numberFormat.format(speedToUnits(seg.speed, units))
                    val duration = formatDuration(seg.seconds)
                    // TODO: target speed and pre-change warning seconds are not tracked per segment.
                    writer.writeRow(
                        listOf(
                            s.id,
                            (i + 1).toString(),
                            speed,
                            duration,
                            "",
                            speed,
                            ""
                        )
                    )
                }
            }
        }
        sessionsCsv to segsCsv
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        val f = File(ctx.filesDir, "sessions.json")
        if (f.exists()) f.writeText("[]")  // or: f.delete()
    }

    private fun formatSpeedWithUnit(
        valueMph: Double,
        units: Units,
        numberFormat: NumberFormat
    ): String {
        val display = numberFormat.format(speedToUnits(valueMph, units))
        return "$display ${speedUnitLabel(units)}"
    }
}

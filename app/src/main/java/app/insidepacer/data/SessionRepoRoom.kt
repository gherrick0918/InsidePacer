package app.insidepacer.data

import android.content.Context
import app.insidepacer.core.csvNumberFormat
import app.insidepacer.core.formatDuration
import app.insidepacer.core.speedToUnits
import app.insidepacer.core.speedUnitLabel
import app.insidepacer.csv.CsvFields
import app.insidepacer.csv.CsvWriter
import app.insidepacer.data.db.AppDatabase
import app.insidepacer.data.db.toDomain
import app.insidepacer.data.db.toEntity
import app.insidepacer.domain.Segment
import app.insidepacer.domain.SessionLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class SessionRepoRoom(private val ctx: Context, private val database: AppDatabase) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    suspend fun loadAll(): List<SessionLog> = withContext(Dispatchers.IO) {
        database.sessionDao().getAll().map { it.toDomain() }
    }

    suspend fun append(log: SessionLog) = withContext(Dispatchers.IO) {
        database.sessionDao().insert(log.toEntity())
    }

    suspend fun replaceAll(logs: List<SessionLog>) = withContext(Dispatchers.IO) {
        database.sessionDao().deleteAll()
        database.sessionDao().insertAll(logs.map { it.toEntity() })
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
        database.sessionDao().deleteAll()
    }

    private fun formatSpeedWithUnit(
        valueMph: Double,
        units: Units,
        numberFormat: NumberFormat
    ): String {
        val display = numberFormat.format(speedToUnits(valueMph, units))
        return "$display ${speedUnitLabel(units)}"
    }

    /**
     * Export sessions to JSON format for backup purposes.
     * This maintains compatibility with the backup/restore system.
     */
    suspend fun exportToJson(): String = withContext(Dispatchers.IO) {
        val sessions = loadAll()
        json.encodeToString(ListSerializer(SessionLog.serializer()), sessions)
    }

    /**
     * Import sessions from JSON format for restore purposes.
     * This maintains compatibility with the backup/restore system.
     */
    suspend fun importFromJson(jsonString: String) = withContext(Dispatchers.IO) {
        val sessions = json.decodeFromString(
            ListSerializer(SessionLog.serializer()),
            jsonString
        )
        replaceAll(sessions)
    }
}

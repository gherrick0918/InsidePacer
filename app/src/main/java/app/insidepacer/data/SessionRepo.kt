package app.insidepacer.data

import android.content.Context
import app.insidepacer.domain.Segment
import app.insidepacer.domain.SessionLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
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
        val tmp = File.createTempFile("sessions", ".json", ctx.cacheDir)
        tmp.writeText(json.encodeToString(ListSerializer(SessionLog.serializer()), all))
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
    suspend fun exportCsv(): Pair<File, File> = withContext(Dispatchers.IO) {
        val items = loadAll()
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        val sessionsCsv = File(ctx.cacheDir, "sessions.csv")
        val segsCsv = File(ctx.cacheDir, "session_segments.csv")

        sessionsCsv.bufferedWriter().use { w ->
            w.appendLine("id,start,end,total_seconds,segments_count,aborted")
            items.forEach { s ->
                w.appendLine("${s.id},${df.format(Date(s.startMillis))},${df.format(Date(s.endMillis))},${s.totalSeconds},${s.segments.size},${s.aborted}")
            }
        }

        segsCsv.bufferedWriter().use { w ->
            w.appendLine("session_id,index,speed,seconds")
            items.forEach { s ->
                s.segments.forEachIndexed { i, seg ->
                    w.appendLine("${s.id},${i + 1},${seg.speed},${seg.seconds}")
                }
            }
        }
        sessionsCsv to segsCsv
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        val f = File(ctx.filesDir, "sessions.json")
        if (f.exists()) f.writeText("[]")  // or: f.delete()
    }
}

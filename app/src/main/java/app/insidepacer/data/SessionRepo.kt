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
import app.insidepacer.di.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

/**
 * Session repository using Room Database.
 * This replaces the old JSON file-based storage.
 */
class SessionRepo(private val ctx: Context) {
    private val roomRepo: SessionRepoRoom by lazy {
        runBlocking {
            val db = Singleton.getDatabase(ctx)
            SessionRepoRoom(ctx, db)
        }
    }

    fun loadAll(): List<SessionLog> = runBlocking {
        roomRepo.loadAll()
    }

    suspend fun append(log: SessionLog) {
        roomRepo.append(log)
    }

    suspend fun replaceAll(logs: List<SessionLog>) {
        roomRepo.replaceAll(logs)
    }

    /** Given the plan and actual elapsed, return truncated segments actually completed. */
    fun realizedSegments(plan: List<Segment>, elapsed: Int): List<Segment> {
        return roomRepo.realizedSegments(plan, elapsed)
    }

    /** Export CSVs to cacheDir and return both files (summary, segments). */
    suspend fun exportCsv(units: Units): Pair<File, File> {
        return roomRepo.exportCsv(units)
    }

    suspend fun clear() {
        roomRepo.clear()
    }

    suspend fun updateNotes(sessionId: String, notes: String?) {
        roomRepo.updateNotes(sessionId, notes)
    }
}

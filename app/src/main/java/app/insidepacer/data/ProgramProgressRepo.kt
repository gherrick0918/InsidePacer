package app.insidepacer.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class ProgramProgress(val programId: String, val doneEpochDays: List<Long>)

class ProgramProgressRepo(private val ctx: Context) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val file get() = File(ctx.filesDir, "program_progress.json")

    private fun readAll(): MutableList<ProgramProgress> = try {
        if (!file.exists()) mutableListOf()
        else json.decodeFromString(ListSerializer(ProgramProgress.serializer()), file.readText()).toMutableList()
    } catch (_: Throwable) { mutableListOf() }

    private fun writeAll(items: List<ProgramProgress>) {
        val tmp = File.createTempFile("program_progress", ".json", ctx.cacheDir)
        tmp.writeText(json.encodeToString(ListSerializer(ProgramProgress.serializer()), items))
        tmp.copyTo(file, overwrite = true); tmp.delete()
    }

    fun isDone(programId: String, epochDay: Long): Boolean =
        readAll().firstOrNull { it.programId == programId }?.doneEpochDays?.contains(epochDay) == true

    fun markDone(programId: String, epochDay: Long) {
        val all = readAll()
        val idx = all.indexOfFirst { it.programId == programId }
        if (idx >= 0) {
            val set = all[idx].doneEpochDays.toMutableSet(); set += epochDay
            all[idx] = all[idx].copy(doneEpochDays = set.sorted())
        } else {
            all += ProgramProgress(programId, listOf(epochDay))
        }
        writeAll(all)
    }

    fun clearDone(programId: String, epochDay: Long) {
        val all = readAll()
        val idx = all.indexOfFirst { it.programId == programId }
        if (idx >= 0) {
            val set = all[idx].doneEpochDays.toMutableSet(); set.remove(epochDay)
            all[idx] = all[idx].copy(doneEpochDays = set.sorted())
            writeAll(all)
        }
    }
}

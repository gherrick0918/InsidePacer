package app.insidepacer.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class ProgramProgress(val programId: String, val doneEpochDays: List<Long>)

class ProgramProgressRepo private constructor(private val ctx: Context) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val file get() = File(ctx.filesDir, "program_progress.json")

    private val _progress = MutableStateFlow<List<ProgramProgress>>(emptyList())
    val progress = _progress.asStateFlow()

    init {
        _progress.value = try {
            if (!file.exists()) emptyList()
            else json.decodeFromString(ListSerializer(ProgramProgress.serializer()), file.readText())
        } catch (_: Throwable) { emptyList() }
    }

    private fun writeAll(items: List<ProgramProgress>) {
        val tmp = File.createTempFile("program_progress", ".json", ctx.cacheDir)
        tmp.writeText(json.encodeToString(ListSerializer(ProgramProgress.serializer()), items))
        tmp.copyTo(file, overwrite = true)
        tmp.delete()
        _progress.value = items
    }

    fun isDone(programId: String, epochDay: Long): Boolean =
        _progress.value.firstOrNull { it.programId == programId }?.doneEpochDays?.contains(epochDay) == true

    fun markDone(programId: String, epochDay: Long) {
        val all = _progress.value.toMutableList()
        val idx = all.indexOfFirst { it.programId == programId }
        if (idx >= 0) {
            val set = all[idx].doneEpochDays.toMutableSet()
            set += epochDay
            all[idx] = all[idx].copy(doneEpochDays = set.sorted())
        } else {
            all += ProgramProgress(programId, listOf(epochDay))
        }
        writeAll(all)
    }

    fun clearDone(programId: String, epochDay: Long) {
        val all = _progress.value.toMutableList()
        val idx = all.indexOfFirst { it.programId == programId }
        if (idx >= 0) {
            val set = all[idx].doneEpochDays.toMutableSet()
            set.remove(epochDay)
            all[idx] = all[idx].copy(doneEpochDays = set.sorted())
            writeAll(all)
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: ProgramProgressRepo? = null

        fun getInstance(context: Context): ProgramProgressRepo {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ProgramProgressRepo(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}

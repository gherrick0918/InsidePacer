package app.insidepacer.data

import android.content.Context
import app.insidepacer.domain.Program
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

class ProgramRepo(private val ctx: Context) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val file get() = File(ctx.filesDir, "programs.json")

    private val _programs = MutableStateFlow<List<Program>>(emptyList())
    val programs: StateFlow<List<Program>> = _programs.asStateFlow()

    init {
        _programs.value = readAll().sortedBy { it.name }
    }

    private fun readAll(): MutableList<Program> = try {
        if (!file.exists()) mutableListOf()
        else json.decodeFromString(ListSerializer(Program.serializer()), file.readText()).toMutableList()
    } catch (_: Throwable) { mutableListOf() }

    private fun writeAll(items: List<Program>) {
        val tmp = File.createTempFile("programs", ".json", ctx.cacheDir)
        tmp.writeText(json.encodeToString(ListSerializer(Program.serializer()), items))
        tmp.copyTo(file, overwrite = true); tmp.delete()
        _programs.value = items.sortedBy { it.name }
    }

    fun loadAll(): List<Program> = _programs.value
    fun get(id: String): Program? = _programs.value.firstOrNull { it.id == id }
    fun findByName(name: String): Program? = _programs.value.firstOrNull { it.name.equals(name, ignoreCase = true) }
    fun newId(): String = "prog_" + System.currentTimeMillis()

    fun create(name: String, startEpochDay: Long, weeks: Int, days: Int = 7): Program {
        val grid = List(weeks) { List(days) { null as String? } }
        val p = Program(newId(), name.ifBlank { "Program" }, startEpochDay, weeks, days, grid)
        val all = _programs.value.toMutableList(); all += p; writeAll(all); return p
    }

    fun save(program: Program) {
        val all = _programs.value.toMutableList()
        val i = all.indexOfFirst { it.id == program.id }
        if (i >= 0) all[i] = program else all += program
        writeAll(all)
    }

    fun delete(id: String) = writeAll(_programs.value.filterNot { it.id == id })
}

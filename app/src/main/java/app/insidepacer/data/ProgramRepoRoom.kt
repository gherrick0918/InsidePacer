package app.insidepacer.data

import android.content.Context
import app.insidepacer.data.db.AppDatabase
import app.insidepacer.data.db.toDomain
import app.insidepacer.data.db.toEntity
import app.insidepacer.domain.Program
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class ProgramRepoRoom(private val ctx: Context, private val database: AppDatabase) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    val programs: Flow<List<Program>> = database.programDao().getAllFlow()
        .map { entities -> entities.map { it.toDomain() } }

    suspend fun loadAll(): List<Program> = withContext(Dispatchers.IO) {
        database.programDao().getAll().map { it.toDomain() }
    }

    suspend fun get(id: String): Program? = withContext(Dispatchers.IO) {
        database.programDao().getById(id)?.toDomain()
    }

    suspend fun findByName(name: String): Program? = withContext(Dispatchers.IO) {
        database.programDao().getByName(name)?.toDomain()
    }

    fun newId(): String = "prog_" + System.currentTimeMillis()

    suspend fun create(name: String, startEpochDay: Long, weeks: Int, days: Int = 7): Program = 
        withContext(Dispatchers.IO) {
            val grid = List(weeks) { List(days) { null as String? } }
            val program = Program(newId(), name.ifBlank { "Program" }, startEpochDay, weeks, days, grid)
            database.programDao().insert(program.toEntity())
            program
        }

    suspend fun save(program: Program) = withContext(Dispatchers.IO) {
        database.programDao().insert(program.toEntity())
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        database.programDao().deleteById(id)
    }

    /**
     * Export programs to JSON format for backup purposes.
     * This maintains compatibility with the backup/restore system.
     */
    suspend fun exportToJson(): String = withContext(Dispatchers.IO) {
        val programs = loadAll()
        json.encodeToString(ListSerializer(Program.serializer()), programs)
    }

    /**
     * Import programs from JSON format for restore purposes.
     * This maintains compatibility with the backup/restore system.
     */
    suspend fun importFromJson(jsonString: String) = withContext(Dispatchers.IO) {
        val programs = json.decodeFromString(
            ListSerializer(Program.serializer()),
            jsonString
        )
        database.programDao().deleteAll()
        database.programDao().insertAll(programs.map { it.toEntity() })
    }
}

package app.insidepacer.data

import android.content.Context
import app.insidepacer.domain.Program
import app.insidepacer.di.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking

/**
 * Program repository using Room Database.
 * This replaces the old JSON file-based storage.
 */
class ProgramRepo(private val ctx: Context) {
    private val roomRepo: ProgramRepoRoom by lazy {
        runBlocking {
            val db = Singleton.getDatabase(ctx)
            ProgramRepoRoom(ctx, db)
        }
    }

    val programs: Flow<List<Program>> get() = roomRepo.programs

    fun loadAll(): List<Program> = runBlocking {
        roomRepo.loadAll()
    }

    fun get(id: String): Program? = runBlocking {
        roomRepo.get(id)
    }

    fun findByName(name: String): Program? = runBlocking {
        roomRepo.findByName(name)
    }

    fun newId(): String = roomRepo.newId()

    fun create(name: String, startEpochDay: Long, weeks: Int, days: Int = 7): Program = runBlocking {
        roomRepo.create(name, startEpochDay, weeks, days)
    }

    fun save(program: Program) = runBlocking {
        roomRepo.save(program)
    }

    fun delete(id: String) = runBlocking {
        roomRepo.delete(id)
    }
}

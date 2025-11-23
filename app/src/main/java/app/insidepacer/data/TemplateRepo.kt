package app.insidepacer.data

import android.content.Context
import app.insidepacer.domain.Template
import app.insidepacer.domain.Segment
import app.insidepacer.di.Singleton
import kotlinx.coroutines.runBlocking

/**
 * Template repository using Room Database.
 * This replaces the old JSON file-based storage.
 */
class TemplateRepo(private val ctx: Context) {
    private val roomRepo: TemplateRepoRoom by lazy {
        runBlocking {
            val db = Singleton.getDatabase(ctx)
            TemplateRepoRoom(ctx, db)
        }
    }

    fun loadAll(): List<Template> = runBlocking {
        roomRepo.loadAll()
    }

    fun get(id: String): Template? = runBlocking {
        roomRepo.get(id)
    }

    fun newId(): String = roomRepo.newId()

    suspend fun create(name: String, segments: List<Segment>): Template {
        return roomRepo.create(name, segments)
    }

    suspend fun save(template: Template) {
        roomRepo.save(template)
    }

    suspend fun delete(id: String) {
        roomRepo.delete(id)
    }
}

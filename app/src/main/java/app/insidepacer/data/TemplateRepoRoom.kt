package app.insidepacer.data

import android.content.Context
import app.insidepacer.data.db.AppDatabase
import app.insidepacer.data.db.toDomain
import app.insidepacer.data.db.toEntity
import app.insidepacer.domain.Segment
import app.insidepacer.domain.Template
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class TemplateRepoRoom(private val ctx: Context, private val database: AppDatabase) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    suspend fun loadAll(): List<Template> = withContext(Dispatchers.IO) {
        database.templateDao().getAll().map { it.toDomain() }
    }

    suspend fun get(id: String): Template? = withContext(Dispatchers.IO) {
        database.templateDao().getById(id)?.toDomain()
    }

    fun newId(): String = "tmpl_" + System.currentTimeMillis()

    suspend fun create(name: String, segments: List<Segment>): Template = withContext(Dispatchers.IO) {
        val template = Template(newId(), name.ifBlank { "Template" }, segments)
        database.templateDao().insert(template.toEntity())
        template
    }

    suspend fun save(template: Template) = withContext(Dispatchers.IO) {
        database.templateDao().insert(template.toEntity())
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        database.templateDao().deleteById(id)
    }

    /**
     * Export templates to JSON format for backup purposes.
     * This maintains compatibility with the backup/restore system.
     */
    suspend fun exportToJson(): String = withContext(Dispatchers.IO) {
        val templates = loadAll()
        json.encodeToString(ListSerializer(Template.serializer()), templates)
    }

    /**
     * Import templates from JSON format for restore purposes.
     * This maintains compatibility with the backup/restore system.
     */
    suspend fun importFromJson(jsonString: String) = withContext(Dispatchers.IO) {
        val templates = json.decodeFromString(
            ListSerializer(Template.serializer()),
            jsonString
        )
        database.templateDao().deleteAll()
        database.templateDao().insertAll(templates.map { it.toEntity() })
    }
}

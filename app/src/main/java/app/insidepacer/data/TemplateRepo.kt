package app.insidepacer.data

import android.content.Context
import app.insidepacer.domain.Template
import app.insidepacer.domain.Segment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

class TemplateRepo(private val ctx: Context) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val file: File get() = File(ctx.filesDir, "templates.json")

    private fun readAll(): MutableList<Template> = try {
        if (!file.exists()) mutableListOf()
        else json.decodeFromString(ListSerializer(Template.serializer()), file.readText()).toMutableList()
    } catch (_: Throwable) { mutableListOf() }

    private suspend fun writeAll(items: List<Template>) = withContext(Dispatchers.IO) {
        val tmp = File.createTempFile("templates", ".json", ctx.cacheDir)
        tmp.writeText(json.encodeToString(ListSerializer(Template.serializer()), items))
        tmp.copyTo(file, overwrite = true)
        tmp.delete()
    }

    fun loadAll(): List<Template> = readAll().sortedBy { it.name }
    fun get(id: String): Template? = readAll().firstOrNull { it.id == id }

    fun newId(): String = "tmpl_" + System.currentTimeMillis()

    suspend fun create(name: String, segments: List<Segment>): Template {
        val all = readAll()
        val t = Template(newId(), name.ifBlank { "Template" }, segments)
        all += t
        writeAll(all)
        return t
    }

    suspend fun save(template: Template) {
        val all = readAll()
        val idx = all.indexOfFirst { it.id == template.id }
        if (idx >= 0) all[idx] = template else all += template
        writeAll(all)
    }

    suspend fun delete(id: String) {
        val all = readAll().filterNot { it.id == id }
        writeAll(all)
    }
}

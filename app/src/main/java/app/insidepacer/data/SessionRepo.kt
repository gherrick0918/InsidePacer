package app.insidepacer.data

import android.content.Context
import app.insidepacer.domain.SessionLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

class SessionRepo(private val ctx: Context) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val file: File get() = File(ctx.filesDir, "sessions.json")

    suspend fun append(log: SessionLog) = withContext(Dispatchers.IO) {
        val all = loadAll().toMutableList()
        all += log
        val tmp = File.createTempFile("sessions", ".json", ctx.cacheDir)
        tmp.writeText(json.encodeToString(ListSerializer(SessionLog.serializer()), all))
        tmp.copyTo(file, overwrite = true)
        tmp.delete()
    }

    fun loadAll(): List<SessionLog> = try {
        if (!file.exists()) emptyList()
        else json.decodeFromString(ListSerializer(SessionLog.serializer()), file.readText())
    } catch (_: Throwable) { emptyList() }

    suspend fun clear() = withContext(Dispatchers.IO) { if (file.exists()) file.delete() }
}

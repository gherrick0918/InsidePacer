package app.insidepacer.backup

import android.content.Context
import java.io.File
import kotlinx.datetime.Instant

class BackupCacheStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("backup_cache", Context.MODE_PRIVATE)
    private val cacheDir: File = File(appContext.filesDir, "backup_cache").apply { mkdirs() }
    private val cacheFile = File(cacheDir, "last_backup.json.enc")

    fun write(bytes: ByteArray, fileName: String, timestamp: Instant) {
        cacheFile.outputStream().use { it.write(bytes) }
        prefs.edit()
            .putString(KEY_FILENAME, fileName)
            .putString(KEY_TIMESTAMP, timestamp.toString())
            .apply()
    }

    fun read(): ByteArray? = if (cacheFile.exists()) cacheFile.readBytes() else null

    fun meta(): CachedBackupMeta? {
        val name = prefs.getString(KEY_FILENAME, null) ?: return null
        val ts = prefs.getString(KEY_TIMESTAMP, null) ?: return null
        val instant = runCatching { Instant.parse(ts) }.getOrElse { return null }
        return CachedBackupMeta(fileName = name, savedAt = instant)
    }

    fun clear() {
        if (cacheFile.exists()) {
            cacheFile.delete()
        }
        prefs.edit().clear().apply()
    }

    data class CachedBackupMeta(val fileName: String, val savedAt: Instant)

    companion object {
        private const val KEY_FILENAME = "cache_file"
        private const val KEY_TIMESTAMP = "cache_ts"
    }
}

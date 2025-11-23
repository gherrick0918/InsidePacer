package app.insidepacer.data.db

import android.content.Context
import android.util.Log
import app.insidepacer.domain.Program
import app.insidepacer.domain.SessionLog
import app.insidepacer.domain.Template
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Helper class to migrate data from JSON files to Room database.
 * This is a one-time migration that runs when the app first uses Room.
 */
class DatabaseMigrationHelper(private val context: Context) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val TAG = "DatabaseMigration"

    /**
     * Migrates all data from JSON files to Room database.
     * Returns true if migration was successful or if already migrated.
     */
    suspend fun migrateIfNeeded(database: AppDatabase): Boolean = withContext(Dispatchers.IO) {
        try {
            // Check if migration marker exists
            val migrationMarker = File(context.filesDir, ".room_migration_complete")
            if (migrationMarker.exists()) {
                Log.d(TAG, "Migration already completed, skipping")
                return@withContext true
            }

            Log.d(TAG, "Starting migration from JSON to Room")

            // Migrate sessions
            val sessionsFile = File(context.filesDir, "sessions.json")
            if (sessionsFile.exists()) {
                val sessions = try {
                    json.decodeFromString(
                        ListSerializer(SessionLog.serializer()),
                        sessionsFile.readText()
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading sessions.json", e)
                    emptyList()
                }
                
                if (sessions.isNotEmpty()) {
                    Log.d(TAG, "Migrating ${sessions.size} sessions")
                    database.sessionDao().insertAll(sessions.map { it.toEntity() })
                }
            }

            // Migrate templates
            val templatesFile = File(context.filesDir, "templates.json")
            if (templatesFile.exists()) {
                val templates = try {
                    json.decodeFromString(
                        ListSerializer(Template.serializer()),
                        templatesFile.readText()
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading templates.json", e)
                    emptyList()
                }
                
                if (templates.isNotEmpty()) {
                    Log.d(TAG, "Migrating ${templates.size} templates")
                    database.templateDao().insertAll(templates.map { it.toEntity() })
                }
            }

            // Migrate programs
            val programsFile = File(context.filesDir, "programs.json")
            if (programsFile.exists()) {
                val programs = try {
                    json.decodeFromString(
                        ListSerializer(Program.serializer()),
                        programsFile.readText()
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading programs.json", e)
                    emptyList()
                }
                
                if (programs.isNotEmpty()) {
                    Log.d(TAG, "Migrating ${programs.size} programs")
                    database.programDao().insertAll(programs.map { it.toEntity() })
                }
            }

            // Create migration marker
            migrationMarker.createNewFile()
            Log.d(TAG, "Migration completed successfully")

            true
        } catch (e: Exception) {
            Log.e(TAG, "Migration failed", e)
            false
        }
    }

    /**
     * Archives the old JSON files after successful migration.
     * This keeps them as backup but prevents them from being used.
     */
    suspend fun archiveJsonFiles() = withContext(Dispatchers.IO) {
        try {
            val timestamp = System.currentTimeMillis()
            listOf("sessions.json", "templates.json", "programs.json").forEach { filename ->
                val file = File(context.filesDir, filename)
                if (file.exists()) {
                    val archiveFile = File(context.filesDir, "$filename.backup_$timestamp")
                    file.renameTo(archiveFile)
                    Log.d(TAG, "Archived $filename to ${archiveFile.name}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error archiving JSON files", e)
        }
    }
}

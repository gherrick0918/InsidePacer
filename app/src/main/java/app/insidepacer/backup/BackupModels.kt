package app.insidepacer.backup

import kotlinx.datetime.Instant

data class AccountInfo(
    val email: String,
    val accountId: String
)

data class BackupLog(
    val timestamp: Instant,
    val success: Boolean,
    val message: String? = null
)

data class BackupStatus(
    val account: AccountInfo? = null,
    val lastBackup: BackupLog? = null,
    val lastRestore: BackupLog? = null,
    val hasCachedBackup: Boolean = false,
    val remoteBackups: List<DriveBackupMeta> = emptyList(),
    val available: Boolean = true,
    val errorMessage: String? = null
)

data class DriveBackupMeta(
    val id: String,
    val name: String,
    val modifiedTime: Instant,
    val sizeBytes: Long? = null
)

data class RestoreReport(
    val templatesUpserted: Int,
    val programsUpserted: Int,
    val sessionsInserted: Int
)

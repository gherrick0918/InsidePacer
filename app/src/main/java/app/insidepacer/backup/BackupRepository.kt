package app.insidepacer.backup

import kotlinx.coroutines.flow.StateFlow

interface BackupRepository {
    val lastBackupStatus: StateFlow<BackupStatus>
    suspend fun signIn(): Result<AccountInfo>
    suspend fun signOut()
    suspend fun backupNow(): Result<Unit>
    suspend fun restoreLatest(): Result<RestoreReport>
    suspend fun listBackups(): List<DriveBackupMeta>
}

package app.insidepacer.backup.drive

import app.insidepacer.backup.DriveBackupMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface DriveBackupDataSource {
    suspend fun ensureSignedIn(): GoogleAccount
    suspend fun listBackups(limit: Int = 10): List<DriveBackupMeta>
    suspend fun uploadEncrypted(bytes: ByteArray, fileName: String): DriveBackupMeta
    suspend fun download(meta: DriveBackupMeta): ByteArray
    suspend fun isAvailable(): Boolean
    suspend fun signOut()
}

data class GoogleAccount(
    val email: String,
    val accountId: String,
    val displayName: String? = null
)

suspend fun <T> DriveBackupDataSource.withSignIn(block: suspend () -> T): T {
    ensureSignedIn()
    return withContext(Dispatchers.IO) { block() }
}

package app.insidepacer.backup

import android.content.Context
import kotlinx.datetime.Instant

class BackupAccountStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("backup_account", Context.MODE_PRIVATE)

    fun readAccount(): AccountInfo? {
        val email = prefs.getString(KEY_EMAIL, null) ?: return null
        val id = prefs.getString(KEY_ID, null) ?: return null
        return AccountInfo(email = email, accountId = id)
    }

    fun writeAccount(info: AccountInfo) {
        prefs.edit()
            .putString(KEY_EMAIL, info.email)
            .putString(KEY_ID, info.accountId)
            .apply()
    }

    fun clearAccount() {
        prefs.edit()
            .remove(KEY_EMAIL)
            .remove(KEY_ID)
            .apply()
    }

    fun readLastBackup(): BackupLog? = readLog(KEY_BACKUP_TS, KEY_BACKUP_SUCCESS, KEY_BACKUP_MESSAGE)

    fun readLastRestore(): BackupLog? = readLog(KEY_RESTORE_TS, KEY_RESTORE_SUCCESS, KEY_RESTORE_MESSAGE)

    fun writeLastBackup(log: BackupLog) {
        writeLog(log, KEY_BACKUP_TS, KEY_BACKUP_SUCCESS, KEY_BACKUP_MESSAGE)
    }

    fun writeLastRestore(log: BackupLog) {
        writeLog(log, KEY_RESTORE_TS, KEY_RESTORE_SUCCESS, KEY_RESTORE_MESSAGE)
    }

    fun clearLogs() {
        prefs.edit()
            .remove(KEY_BACKUP_TS)
            .remove(KEY_BACKUP_SUCCESS)
            .remove(KEY_BACKUP_MESSAGE)
            .remove(KEY_RESTORE_TS)
            .remove(KEY_RESTORE_SUCCESS)
            .remove(KEY_RESTORE_MESSAGE)
            .apply()
    }

    private fun writeLog(log: BackupLog, keyTs: String, keySuccess: String, keyMessage: String) {
        prefs.edit()
            .putString(keyTs, log.timestamp.toString())
            .putBoolean(keySuccess, log.success)
            .putString(keyMessage, log.message)
            .apply()
    }

    private fun readLog(keyTs: String, keySuccess: String, keyMessage: String): BackupLog? {
        val ts = prefs.getString(keyTs, null) ?: return null
        val success = prefs.getBoolean(keySuccess, true)
        val message = prefs.getString(keyMessage, null)
        return BackupLog(
            timestamp = runCatching { Instant.parse(ts) }.getOrElse { return null },
            success = success,
            message = message
        )
    }

    companion object {
        private const val KEY_EMAIL = "account_email"
        private const val KEY_ID = "account_id"
        private const val KEY_BACKUP_TS = "last_backup_ts"
        private const val KEY_BACKUP_SUCCESS = "last_backup_success"
        private const val KEY_BACKUP_MESSAGE = "last_backup_message"
        private const val KEY_RESTORE_TS = "last_restore_ts"
        private const val KEY_RESTORE_SUCCESS = "last_restore_success"
        private const val KEY_RESTORE_MESSAGE = "last_restore_message"
    }
}

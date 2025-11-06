package app.insidepacer.backup.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.insidepacer.backup.BackupFeatureConfig
import app.insidepacer.backup.BackupRepository
import app.insidepacer.backup.BackupRepositoryImpl
import app.insidepacer.backup.BackupStatus
import app.insidepacer.backup.LocalCrypto
import app.insidepacer.R
import app.insidepacer.backup.drive.DriveBackupDataSourceImpl
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant

class BackupSettingsViewModel(private val appContext: Context) : ViewModel() {
    private val repository: BackupRepository by lazy {
        BackupRepositoryImpl(
            appContext,
            DriveBackupDataSourceImpl(appContext),
            LocalCrypto.create(appContext)
        )
    }

    private val _uiState = MutableStateFlow(
        BackupSettingsUiState(
            available = true,
            isSignedIn = false,
            accountEmail = null,
            lastBackupText = appContext.getString(R.string.backup_last_backup, formatTimestamp(null)),
            lastRestoreText = appContext.getString(R.string.backup_last_restore, formatTimestamp(null)),
            hasBackups = false,
            isBusy = false,
            errorMessage = null,
            privacyMessage = appContext.getString(R.string.backup_privacy)
        )
    )
    val uiState: StateFlow<BackupSettingsUiState> = _uiState

    private val _events = MutableSharedFlow<BackupUiEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            repository.lastBackupStatus.collect { status ->
                updateFromStatus(status)
            }
        }
    }

    fun onSignInClicked() {
        if (!BackupFeatureConfig.enabled) return
        viewModelScope.launch {
            setBusy(true)
            val result = repository.signIn()
            setBusy(false)
            result.onSuccess { account ->
                emitMessage(appContext.getString(R.string.backup_signed_in, account.email))
            }.onFailure { err ->
                emitError(err.message ?: appContext.getString(R.string.backup_error_generic))
            }
        }
    }

    fun onSignOutClicked() {
        viewModelScope.launch {
            setBusy(true)
            repository.signOut()
            setBusy(false)
            emitMessage(appContext.getString(R.string.backup_signed_out))
        }
    }

    fun onBackupNowClicked() {
        viewModelScope.launch {
            setBusy(true)
            val result = repository.backupNow()
            setBusy(false)
            result.onSuccess {
                emitMessage(appContext.getString(R.string.backup_success_backup))
            }.onFailure { err ->
                emitError(err.message ?: appContext.getString(R.string.backup_error_generic))
            }
        }
    }

    fun onRestoreClicked() {
        viewModelScope.launch {
            setBusy(true)
            val result = repository.restoreLatest()
            setBusy(false)
            result.onSuccess { report ->
                emitMessage(appContext.getString(R.string.backup_success_restore, report.templatesUpserted, report.programsUpserted, report.sessionsInserted))
            }.onFailure { err ->
                emitError(err.message ?: appContext.getString(R.string.backup_error_generic))
            }
        }
    }

    private fun setBusy(busy: Boolean) {
        _uiState.update { it.copy(isBusy = busy) }
    }

    private fun emitMessage(text: String) {
        _events.tryEmit(BackupUiEvent.Message(text))
    }

    private fun emitError(text: String) {
        _events.tryEmit(BackupUiEvent.Error(text))
    }

    private fun updateFromStatus(status: BackupStatus) {
        val accountEmail = status.account?.email
        val lastBackup = appContext.getString(R.string.backup_last_backup, formatTimestamp(status.lastBackup?.timestamp))
        val lastRestore = appContext.getString(R.string.backup_last_restore, formatTimestamp(status.lastRestore?.timestamp))
        val hasBackups = status.remoteBackups.isNotEmpty() || status.hasCachedBackup
        _uiState.update {
            it.copy(
                available = status.available,
                isSignedIn = accountEmail != null,
                accountEmail = accountEmail,
                lastBackupText = lastBackup,
                lastRestoreText = lastRestore,
                hasBackups = hasBackups,
                errorMessage = status.errorMessage
            )
        }
    }

    private fun formatTimestamp(instant: Instant?): String {
        if (instant == null) return appContext.getString(R.string.backup_time_never)
        val zoned = instant.toJavaInstant().atZone(ZoneId.systemDefault())
        val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy • HH:mm", Locale.getDefault())
        return formatter.format(zoned)
    }

    data class BackupSettingsUiState(
        val available: Boolean,
        val isSignedIn: Boolean,
        val accountEmail: String?,
        val lastBackupText: String,
        val lastRestoreText: String,
        val hasBackups: Boolean,
        val isBusy: Boolean,
        val errorMessage: String?,
        val privacyMessage: String
    ) {
        val canSignIn: Boolean get() = !isSignedIn && !isBusy && available
        val canBackupNow: Boolean get() = isSignedIn && !isBusy && available
        val canRestore: Boolean get() = hasBackups && !isBusy
        val canSignOut: Boolean get() = isSignedIn && !isBusy
    }

    sealed class BackupUiEvent {
        data class Message(val text: String) : BackupUiEvent()
        data class Error(val text: String) : BackupUiEvent()
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(BackupSettingsViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return BackupSettingsViewModel(context.applicationContext) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

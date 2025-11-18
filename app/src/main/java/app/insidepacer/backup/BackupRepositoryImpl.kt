package app.insidepacer.backup

import android.content.Context
import app.insidepacer.backup.drive.DriveBackupDataSource
import app.insidepacer.backup.store.ProgramStore
import app.insidepacer.backup.store.SessionStore
import app.insidepacer.backup.store.SettingsStore
import app.insidepacer.backup.store.TemplateStore
import app.insidepacer.backup.store.createProgramStore
import app.insidepacer.backup.store.createSessionStore
import app.insidepacer.backup.store.createSettingsStore
import app.insidepacer.backup.store.createTemplateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

class BackupRepositoryImpl(
    private val context: Context,
    private val drive: DriveBackupDataSource,
    private val crypto: LocalCrypto,
    private val templateStore: TemplateStore = createTemplateStore(context),
    private val programStore: ProgramStore = createProgramStore(context),
    private val sessionStore: SessionStore = createSessionStore(context),
    private val settingsStore: SettingsStore = createSettingsStore(context),
    private val accountStore: BackupAccountStore = BackupAccountStore(context),
    private val cacheStore: BackupCacheStore = BackupCacheStore(context),
    private val clock: Clock = Clock.System,
    private val json: Json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
) : BackupRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val importer = BackupImporter(templateStore, programStore, sessionStore, settingsStore)

    private val _status = MutableStateFlow(
        BackupStatus(
            account = accountStore.readAccount(),
            lastBackup = accountStore.readLastBackup(),
            lastRestore = accountStore.readLastRestore(),
            hasCachedBackup = cacheStore.meta() != null,
            remoteBackups = emptyList(),
            available = true,
            errorMessage = null
        )
    )
    override val lastBackupStatus: StateFlow<BackupStatus> = _status

    init {
        scope.launch {
            val available = runCatching { drive.isAvailable() }.getOrDefault(true)
            _status.update { it.copy(available = available) }
            if (available && _status.value.account != null) {
                refreshBackups()
            }
        }
    }

    override suspend fun signIn(): Result<AccountInfo> = runCatching {
        val account = drive.ensureSignedIn()
        val info = AccountInfo(email = account.email, accountId = account.accountId)
        accountStore.writeAccount(info)
        _status.update { it.copy(account = info, errorMessage = null) }
        
        // Track successful backup sign-in
        try {
            val analytics = app.insidepacer.di.Singleton.getAnalyticsService(context)
            analytics.logBackupSignIn()
            analytics.setHasBackup(true)
        } catch (e: Exception) {
            // Silently fail if analytics is not available
        }
        
        refreshBackups()
        info
    }.onFailure { err ->
        _status.update { it.copy(errorMessage = err.message) }
        
        // Track backup error
        try {
            app.insidepacer.analytics.CrashlyticsHelper.trackBackupError("sign_in", err)
        } catch (e: Exception) {
            // Silently fail if Crashlytics is not available
        }
    }

    override suspend fun signOut() {
        runCatching { drive.signOut() }
        accountStore.clearAccount()
        accountStore.clearLogs()
        _status.update {
            it.copy(
                account = null,
                remoteBackups = emptyList(),
                errorMessage = null
            )
        }
    }

    override suspend fun backupNow(): Result<Unit> = runCatching {
        // Track backup start
        try {
            val analytics = app.insidepacer.di.Singleton.getAnalyticsService(context)
            analytics.logBackupStart()
        } catch (e: Exception) {
            // Silently fail if analytics is not available
        }
        
        ensureSignedInForAction()
        val bundle = buildBundle()
        val jsonBytes = json.encodeToString(BackupBundle.serializer(), bundle).encodeToByteArray()
        val encrypted = crypto.encrypt(jsonBytes)
        val timestamp = clock.now()
        val fileName = buildFileName(timestamp)
        val meta = drive.uploadEncrypted(encrypted, fileName)
        cacheStore.write(encrypted, meta.name, timestamp)
        val log = BackupLog(timestamp = timestamp, success = true)
        accountStore.writeLastBackup(log)
        _status.update {
            it.copy(
                lastBackup = log,
                hasCachedBackup = true,
                remoteBackups = listOf(meta) + it.remoteBackups.filter { existing -> existing.id != meta.id },
                errorMessage = null
            )
        }
        
        // Track backup success
        try {
            val analytics = app.insidepacer.di.Singleton.getAnalyticsService(context)
            analytics.logBackupSuccess(encrypted.size.toLong())
        } catch (e: Exception) {
            // Silently fail if analytics is not available
        }
        
        Unit
    }.onFailure { err ->
        recordFailure(err, FailureKind.BACKUP)
        
        // Track backup failure
        try {
            val analytics = app.insidepacer.di.Singleton.getAnalyticsService(context)
            analytics.logBackupFailure(err.message ?: "Unknown error")
            app.insidepacer.analytics.CrashlyticsHelper.trackBackupError("backup", err)
        } catch (e: Exception) {
            // Silently fail if analytics/crashlytics is not available
        }
    }

    override suspend fun restoreLatest(): Result<RestoreReport> = runCatching {
        // Track restore start
        try {
            val analytics = app.insidepacer.di.Singleton.getAnalyticsService(context)
            analytics.logRestoreStart()
        } catch (e: Exception) {
            // Silently fail if analytics is not available
        }
        
        val account = _status.value.account
        val remoteBackups = if (account != null) listBackupsInternal() else emptyList()
        val primary = remoteBackups.firstOrNull()
        val bytes = when {
            primary != null -> drive.download(primary)
            else -> cacheStore.read() ?: throw IllegalStateException("No backups available to restore")
        }
        if (primary != null) {
            cacheStore.write(bytes, primary.name, clock.now())
        }
        val decrypted = crypto.decrypt(bytes)
        val bundle = json.decodeFromString(BackupBundle.serializer(), decrypted.decodeToString())
        val report = importer.import(bundle)
        val timestamp = clock.now()
        val log = BackupLog(timestamp = timestamp, success = true)
        accountStore.writeLastRestore(log)
        _status.update { current ->
            current.copy(
                lastRestore = log,
                hasCachedBackup = cacheStore.meta() != null,
                errorMessage = null,
                remoteBackups = if (remoteBackups.isNotEmpty()) remoteBackups else current.remoteBackups
            )
        }
        
        // Track restore success
        try {
            val analytics = app.insidepacer.di.Singleton.getAnalyticsService(context)
            analytics.logRestoreSuccess()
        } catch (e: Exception) {
            // Silently fail if analytics is not available
        }
        
        report
    }.onFailure { err ->
        recordFailure(err, FailureKind.RESTORE)
        
        // Track restore failure
        try {
            val analytics = app.insidepacer.di.Singleton.getAnalyticsService(context)
            analytics.logRestoreFailure(err.message ?: "Unknown error")
            app.insidepacer.analytics.CrashlyticsHelper.trackBackupError("restore", err)
        } catch (e: Exception) {
            // Silently fail if analytics/crashlytics is not available
        }
    }

    override suspend fun listBackups(): List<DriveBackupMeta> = runCatching {
        ensureSignedInForAction()
        listBackupsInternal()
    }.onFailure { err ->
        recordFailure(err, FailureKind.OTHER)
    }.getOrDefault(emptyList())

    private suspend fun listBackupsInternal(): List<DriveBackupMeta> {
        val list = drive.listBackups(limit = 10)
        _status.update { it.copy(remoteBackups = list, errorMessage = null) }
        return list
    }

    private suspend fun refreshBackups() {
        runCatching { listBackupsInternal() }.onFailure { recordFailure(it, FailureKind.OTHER) }
    }

    private suspend fun ensureSignedInForAction() {
        if (_status.value.account == null) {
            val info = signIn().getOrThrow()
            _status.update { it.copy(account = info) }
        } else {
            runCatching { drive.ensureSignedIn() }.getOrThrow()
        }
    }

    private suspend fun buildBundle(): BackupBundle {
        val templates = templateStore.loadAll()
        val programs = programStore.loadAll()
        val sessions = sessionStore.loadAll()
        val settings = settingsStore.read()
        val segments = templates.flatMap { it.segments }
            .distinctBy { it.speed to it.seconds }
            .map { SegmentDto.from(it) }
        return BackupBundle(
            exportedAtUtc = clock.now().toString(),
            programs = programs.map { ProgramDto.from(it) },
            templates = templates.map { TemplateDto.from(it) },
            sessions = sessions.map { SessionDto.from(it) },
            segments = segments,
            settings = SettingsDto.from(settings)
        )
    }

    private fun buildFileName(timestamp: Instant): String {
        val formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss'Z'", Locale.US)
            .withZone(ZoneOffset.UTC)
        val formatted = formatter.format(timestamp.toJavaInstant())
        return "insidepacer_backup_v1_${formatted}.json.enc"
    }

    private fun recordFailure(err: Throwable, kind: FailureKind) {
        _status.update { it.copy(errorMessage = err.message) }
        val log = BackupLog(timestamp = clock.now(), success = false, message = err.message)
        when (kind) {
            FailureKind.BACKUP -> {
                accountStore.writeLastBackup(log)
                _status.update { it.copy(lastBackup = log) }
            }
            FailureKind.RESTORE -> {
                accountStore.writeLastRestore(log)
                _status.update { it.copy(lastRestore = log) }
            }
            FailureKind.OTHER -> Unit
        }
    }

    private enum class FailureKind { BACKUP, RESTORE, OTHER }
}

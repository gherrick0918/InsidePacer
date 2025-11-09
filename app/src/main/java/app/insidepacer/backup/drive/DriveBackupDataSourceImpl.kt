package app.insidepacer.backup.drive

import android.accounts.Account
import android.app.Activity
import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import app.insidepacer.R
import app.insidepacer.backup.DriveBackupMeta
import app.insidepacer.backup.ui.ActivityTracker
import com.google.android.gms.common.GoogleApiAvailability
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.google.api.client.util.DateTime
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import org.json.JSONObject

class DriveBackupDataSourceImpl(
    private val context: Context,
    private val credentialManager: CredentialManager = CredentialManager.create(context)
) : DriveBackupDataSource {
    private val appContext = context.applicationContext
    private val transport = NetHttpTransport()
    private val jsonFactory = GsonFactory.getDefaultInstance()
    private val driveRef = AtomicReference<Drive?>()
    private val accountRef = AtomicReference<GoogleAccount?>()
    private val mutex = Mutex()

    override suspend fun ensureSignedIn(): GoogleAccount = mutex.withLock {
        accountRef.get()?.let { existing ->
            ensureDrive(existing)
            return@withLock existing
        }

        val activity = ActivityTracker.currentActivity()
            ?: throw IllegalStateException("Sign-in requires an active InsidePacer screen")

        val account = requestGoogleAccount(activity)
        ensureDrive(account)
        accountRef.set(account)
        return@withLock account
    }

    override suspend fun listBackups(limit: Int): List<DriveBackupMeta> = withContext(Dispatchers.IO) {
        val drive = ensureDrive(ensureSignedIn())
        val files = drive.files().list()
            .setSpaces("appDataFolder")
            .setPageSize(limit)
            .setOrderBy("modifiedTime desc")
            .setQ("mimeType='application/octet-stream' or mimeType='application/vnd.insidepacer.backup+json+enc'")
            .setFields("files(id,name,modifiedTime,size)")
            .execute()
            .files
            ?: emptyList()
        files.map { file ->
            DriveBackupMeta(
                id = file.id,
                name = file.name,
                modifiedTime = parseInstant(file.modifiedTime),
                sizeBytes = file.getSize()?.toLong()
            )
        }
    }

    override suspend fun uploadEncrypted(bytes: ByteArray, fileName: String): DriveBackupMeta = withContext(Dispatchers.IO) {
        val drive = ensureDrive(ensureSignedIn())
        val metadata = File().apply {
            name = fileName
            parents = Collections.singletonList(APP_DATA_FOLDER)
            mimeType = BACKUP_MIME
        }
        val media = ByteArrayContent("application/octet-stream", bytes)
        val created = drive.files()
            .create(metadata, media)
            .setFields("id,name,modifiedTime,size")
            .execute()
        DriveBackupMeta(
            id = created.id,
            name = created.name,
            modifiedTime = parseInstant(created.modifiedTime),
            sizeBytes = created.getSize()?.toLong()
        )
    }

    override suspend fun download(meta: DriveBackupMeta): ByteArray = withContext(Dispatchers.IO) {
        val drive = ensureDrive(ensureSignedIn())
        val output = ByteArrayOutputStream()
        drive.files().get(meta.id).executeMediaAndDownloadTo(output)
        output.toByteArray()
    }

    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(appContext) == 0
    }

    override suspend fun signOut() {
        mutex.withLock {
            driveRef.getAndSet(null)
            accountRef.getAndSet(null)
        }
        runCatching {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        }
    }

    private fun ensureDrive(account: GoogleAccount): Drive {
        driveRef.get()?.let { return it }
        val acct = Account(account.email, GOOGLE_ACCOUNT_TYPE)
        val credential = GoogleAccountCredential.usingOAuth2(
            appContext,
            setOf(DriveScopes.DRIVE_APPDATA)
        ).apply {
            selectedAccount = acct
        }
        val drive = Drive.Builder(transport, jsonFactory, credential)
            .setApplicationName(appContext.getString(R.string.app_name))
            .build()
        driveRef.set(drive)
        return drive
    }

    private suspend fun requestGoogleAccount(activity: Activity): GoogleAccount {
        val serverClientId = readServerClientId(activity)
        Log.d(TAG, "Using serverClientId: $serverClientId")
        if (serverClientId == null) {
            throw IllegalStateException(activity.getString(R.string.backup_error_missing_server_client_id))
        }

        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(serverClientId)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        try {
            val response = credentialManager.getCredential(activity, request)
            val credential = GoogleIdTokenCredential.createFrom(response.credential.data)
            val email = parseEmailFromIdToken(credential.idToken) ?: credential.displayName
                ?: throw IllegalStateException("Unable to determine Google account email")
            val accountId = credential.id
            val displayName = credential.displayName
            return GoogleAccount(email = email, accountId = accountId, displayName = displayName)
        } catch (ex: GetCredentialException) {
            Log.e(TAG, "Google sign-in failed. Type: ${ex.type}. Message: ${ex.message}", ex)
            val detailedMessage = "Google sign-in failed. Type: ${ex.type}. Message: ${ex.message}"
            throw IllegalStateException(detailedMessage, ex)
        }
    }

    private fun parseInstant(dateTime: DateTime?): Instant {
        val text = dateTime?.toStringRfc3339() ?: Instant.DISTANT_PAST.toString()
        return runCatching { Instant.parse(text) }.getOrElse { Instant.DISTANT_PAST }
    }

    private fun parseEmailFromIdToken(idToken: String?): String? {
        if (idToken.isNullOrBlank()) return null
        val parts = idToken.split('.')
        if (parts.size < 2) return null
        val payload = Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val payloadJson = JSONObject(String(payload, StandardCharsets.UTF_8))
        val email = payloadJson.optString("email", "")
        return email.takeIf { it.isNotBlank() }
    }

    private fun readServerClientId(context: Context): String? {
        val explicit = runCatching { context.getString(R.string.backup_google_server_client_id) }
            .getOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        if (explicit != null) return explicit

        val fallbackResId = context.resources.getIdentifier(
            "default_web_client_id",
            "string",
            context.packageName
        )
        if (fallbackResId != 0) {
            val fallback = runCatching { context.getString(fallbackResId) }
                .getOrNull()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
            if (fallback != null) return fallback
        }

        return null
    }

    companion object {
        private const val APP_DATA_FOLDER = "appDataFolder"
        private const val BACKUP_MIME = "application/vnd.insidepacer.backup+json+enc"
        private const val GOOGLE_ACCOUNT_TYPE = "com.google"
        private val TAG = DriveBackupDataSourceImpl::class.java.simpleName
    }
}

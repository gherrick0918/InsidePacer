package app.insidepacer.backup.drive

import android.accounts.Account
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Base64
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import app.insidepacer.R
import app.insidepacer.backup.DriveBackupMeta
import app.insidepacer.backup.ui.ActivityTracker
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import org.json.JSONObject
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

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

        if (serverClientId != null) {
            val credentialAccount = runCatching {
                requestWithCredentialManager(activity, serverClientId)
            }.onFailure { err ->
                Log.w(TAG, "CredentialManager sign-in failed: ${err.message}", err)
            }.getOrNull()

            if (credentialAccount != null) {
                return credentialAccount
            }
        } else {
            Log.w(TAG, "Server client ID missing. Falling back to GoogleSignIn API")
        }

        return requestWithGoogleSignIn(activity, serverClientId)
    }

    private suspend fun requestWithCredentialManager(
        activity: Activity,
        serverClientId: String
    ): GoogleAccount = withContext(Dispatchers.Main) {
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
            GoogleAccount(email = email, accountId = accountId, displayName = displayName)
        } catch (ex: GetCredentialException) {
            Log.e(TAG, "Google sign-in failed. Type: ${ex.type}. Message: ${ex.message}", ex)
            throw ex
        }
    }

    private suspend fun requestWithGoogleSignIn(
        activity: Activity,
        serverClientId: String?
    ): GoogleAccount {
        val componentActivity = activity as? ComponentActivity
            ?: throw IllegalStateException("Google sign-in requires a ComponentActivity host")

        val account = try {
            withContext(Dispatchers.Main) {
                val driveScope = Scope(DriveScopes.DRIVE_APPDATA)
                val gsoBuilder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestEmail()
                    .requestScopes(driveScope)
                if (serverClientId != null) {
                    gsoBuilder.requestServerAuthCode(serverClientId, false)
                }
                val options = gsoBuilder.build()
                val client = GoogleSignIn.getClient(componentActivity, options)

                val existing = GoogleSignIn.getLastSignedInAccount(componentActivity)
                if (existing != null && GoogleSignIn.hasPermissions(existing, driveScope)) {
                    return@withContext existing
                }

                suspendCancellableCoroutine<GoogleSignInAccount> { cont ->
                    val launcherKey = "googleSignIn:${UUID.randomUUID()}"
                    lateinit var launcher: ActivityResultLauncher<Intent>
                    launcher = componentActivity.activityResultRegistry.register(
                        launcherKey,
                        ActivityResultContracts.StartActivityForResult()
                    ) { result ->
                        if (!cont.isActive) {
                            launcher.unregister()
                            return@register
                        }
                        try {
                            if (result.resultCode == Activity.RESULT_OK) {
                                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                                val signedIn = task.getResult(ApiException::class.java)
                                cont.resume(signedIn)
                            } else {
                                cont.resumeWithException(
                                    CancellationException("Google sign-in canceled")
                                )
                            }
                        } catch (err: ApiException) {
                            cont.resumeWithException(err)
                        } finally {
                            launcher.unregister()
                        }
                    }

                    cont.invokeOnCancellation {
                        launcher.unregister()
                    }

                    launcher.launch(client.signInIntent)
                }
            }
        } catch (err: CancellationException) {
            throw IllegalStateException("Google sign-in canceled", err)
        } catch (err: ApiException) {
            throw IllegalStateException("Google sign-in failed: ${err.statusCode}", err)
        } catch (err: Exception) {
            throw IllegalStateException("Google sign-in failed: ${err.message}", err)
        }

        val email = account.email
            ?: throw IllegalStateException("Unable to determine Google account email")
        val accountId = account.id
            ?: throw IllegalStateException("Unable to determine Google account id")
        return GoogleAccount(
            email = email,
            accountId = accountId,
            displayName = account.displayName
        )
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

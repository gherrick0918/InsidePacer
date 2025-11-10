package app.insidepacer.backup.drive

import android.accounts.Account
import android.app.Activity
import android.accounts.AccountManager
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
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
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
        val lastSignedInAccount = withContext(Dispatchers.Main) {
            GoogleSignIn.getLastSignedInAccount(appContext)
        }
        if (lastSignedInAccount != null && GoogleSignIn.hasPermissions(lastSignedInAccount, Scope(DriveScopes.DRIVE_APPDATA))) {
            Log.d(TAG, "Found existing signed-in account with Drive permission.")
            val googleAccount = GoogleAccount(
                email = lastSignedInAccount.email!!,
                accountId = lastSignedInAccount.id!!,
                displayName = lastSignedInAccount.displayName
            )
            accountRef.set(googleAccount)
            ensureDrive(googleAccount)
            return@withLock googleAccount
        }

        Log.i(TAG, "No existing account with Drive permission. Starting sign-in flow.")
        val activity = withContext(Dispatchers.Main) {
            ActivityTracker.currentActivity()
        } ?: throw IllegalStateException("Sign-in requires an active InsidePacer screen")

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

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
        GoogleSignIn.getClient(appContext, gso).signOut()

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
        // The CredentialManager flow (requestWithCredentialManager) is problematic because
        // it can successfully return a Google ID token without the user having granted the
        // DRIVE_APPDATA scope. This leads to an authorization failure (NEED_REMOTE_CONSENT)
        // when the app then tries to access Google Drive.
        //
        // To fix this, we bypass the CredentialManager for now and go straight to the
        // traditional Google Sign-In account picker, which correctly requests the
        // necessary permissions upfront.
        Log.i(TAG, "Starting Google Sign-In to request Drive permission.")
        return requestWithAccountPicker(activity)
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

    private suspend fun requestWithAccountPicker(
        activity: Activity
    ): GoogleAccount {
        val componentActivity = activity as? ComponentActivity
            ?: throw IllegalStateException("Google sign-in requires a ComponentActivity host")

        return try {
            withContext(Dispatchers.Main) {
                val serverClientId = readServerClientId(componentActivity)
                val gsoBuilder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestEmail()
                    .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))

                if (serverClientId != null) {
                    gsoBuilder.requestIdToken(serverClientId)
                }
                val gso = gsoBuilder.build()
                val googleSignInClient = GoogleSignIn.getClient(componentActivity, gso)

                suspendCancellableCoroutine { cont ->
                    lateinit var launcher: ActivityResultLauncher<Intent>
                    launcher = componentActivity.activityResultRegistry.register(
                        "googleSignIn:${UUID.randomUUID()}",
                        ActivityResultContracts.StartActivityForResult()
                    ) { result ->
                        if (!cont.isActive) {
                            runCatching { launcher.unregister() }
                            return@register
                        }

                        try {
                            if (result.resultCode != Activity.RESULT_OK) {
                                if (result.resultCode == Activity.RESULT_CANCELED) {
                                    Log.i(TAG, "Google Sign-In canceled")
                                    cont.cancel(CancellationException("Google Sign-In canceled"))
                                } else {
                                    cont.resumeWithException(
                                        IllegalStateException("Google Sign-In failed with resultCode ${result.resultCode}")
                                    )
                                }
                                return@register
                            }

                            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                            val account = task.getResult(ApiException::class.java)

                            val grantedScopes = account.grantedScopes.joinToString(" ") { it.scopeUri }
                            Log.d(TAG, "Account picker success. Granted scopes: $grantedScopes")

                            val email = account.email
                                ?: throw IllegalStateException("Unable to determine Google account email")
                            val accountId = account.id
                                ?: throw IllegalStateException("Unable to determine Google account id")

                            cont.resume(
                                GoogleAccount(
                                    email = email,
                                    accountId = accountId,
                                    displayName = account.displayName
                                )
                            )
                        } catch (e: ApiException) {
                            Log.e(TAG, "Google sign-in failed: ${googleStatusString(e.statusCode)}", e)
                            cont.resumeWithException(e)
                        } catch (err: Exception) {
                            cont.resumeWithException(err)
                        } finally {
                            runCatching { launcher.unregister() }
                        }
                    }

                    cont.invokeOnCancellation {
                        runCatching { launcher.unregister() }
                    }

                    launcher.launch(googleSignInClient.signInIntent)
                }
            }
        } catch (err: CancellationException) {
            throw IllegalStateException("Google account selection canceled", err)
        } catch (err: Exception) {
            throw IllegalStateException("Google account selection failed: ${err.message}", err)
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

    private fun googleStatusString(statusCode: Int): String {
        val name = GoogleSignInStatusCodes.getStatusCodeString(statusCode)
        return "$name ($statusCode)"
    }

    companion object {
        private const val APP_DATA_FOLDER = "appDataFolder"
        private const val BACKUP_MIME = "application/vnd.insidepacer.backup+json+enc"
        private const val GOOGLE_ACCOUNT_TYPE = "com.google"
        private const val TAG = "DriveBackupDataSourceImpl"
    }
}

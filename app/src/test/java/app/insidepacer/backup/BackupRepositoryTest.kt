package app.insidepacer.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.insidepacer.backup.drive.DriveBackupDataSource
import app.insidepacer.backup.drive.GoogleAccount
import app.insidepacer.backup.store.ProgramStore
import app.insidepacer.backup.store.SessionStore
import app.insidepacer.backup.store.SettingsSnapshot
import app.insidepacer.backup.store.SettingsStore
import app.insidepacer.backup.store.TemplateStore
import app.insidepacer.data.Biometrics
import app.insidepacer.data.Units
import app.insidepacer.domain.Program
import app.insidepacer.domain.Segment
import app.insidepacer.domain.SessionLog
import app.insidepacer.domain.Template
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json

class BackupRepositoryTest {
    private val crypto = LocalCrypto(object : LocalCrypto.KeyProvider {
        private val key = javax.crypto.spec.SecretKeySpec(ByteArray(32) { (it + 5).toByte() }, "AES")
        override fun getOrCreateKey() = key
    })

    @Test
    fun restoreIsIdempotent() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val template = Template(id = "tmpl_1", name = "Tempo", segments = listOf(Segment(6.0, 60)))
        val program = Program(id = "prog_1", name = "5k", startEpochDay = 10, weeks = 1, daysPerWeek = 7, grid = listOf(List(7) { "tmpl_1" }))
        val session = SessionLog(
            id = "sess_1",
            programId = program.id,
            startMillis = 1000L,
            endMillis = 2000L,
            totalSeconds = 300,
            segments = listOf(Segment(6.0, 300)),
            aborted = false
        )
        val settings = SettingsSnapshot(
            voiceEnabled = true,
            beepEnabled = true,
            hapticsEnabled = false,
            preChangeSeconds = 10,
            units = Units.MPH,
            speeds = listOf(6.0),
            biometrics = Biometrics(heightCm = 180, weightKg = 75.0, age = 30)
        )

        val templateStore = FakeTemplateStore(mutableListOf(template))
        val programStore = FakeProgramStore(mutableListOf(program))
        val sessionStore = FakeSessionStore(mutableListOf(session))
        val settingsStore = FakeSettingsStore(settings)
        val drive = FakeDriveDataSource()
        val repository = BackupRepositoryImpl(
            context = context,
            drive = drive,
            crypto = crypto,
            templateStore = templateStore,
            programStore = programStore,
            sessionStore = sessionStore,
            settingsStore = settingsStore,
            accountStore = BackupAccountStore(context),
            cacheStore = BackupCacheStore(context),
            clock = Clock.System,
            json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
        )

        repository.signIn().getOrThrow()
        repository.backupNow().getOrThrow()

        templateStore.clear()
        programStore.clear()
        sessionStore.clear()

        val first = repository.restoreLatest().getOrThrow()
        assertEquals(1, templateStore.items.size)
        assertEquals(1, programStore.items.size)
        assertEquals(1, sessionStore.items.size)
        assertTrue(first.sessionsInserted >= 1)

        val second = repository.restoreLatest().getOrThrow()
        assertEquals(0, second.sessionsInserted)
        assertEquals(1, sessionStore.items.size)
    }

    private class FakeDriveDataSource : DriveBackupDataSource {
        private val counter = AtomicInteger(0)
        private val stored = mutableListOf<Pair<DriveBackupMeta, ByteArray>>()

        override suspend fun ensureSignedIn(): GoogleAccount = GoogleAccount(
            email = "user@example.com",
            accountId = "acct-1",
            displayName = "Test User"
        )

        override suspend fun listBackups(limit: Int): List<DriveBackupMeta> = stored.map { it.first }.sortedByDescending { it.modifiedTime }.take(limit)

        override suspend fun uploadEncrypted(bytes: ByteArray, fileName: String): DriveBackupMeta {
            val meta = DriveBackupMeta(
                id = "id_${counter.incrementAndGet()}",
                name = fileName,
                modifiedTime = Instant.parse("2024-01-01T00:00:00Z"),
                sizeBytes = bytes.size.toLong()
            )
            stored += meta to bytes
            return meta
        }

        override suspend fun download(meta: DriveBackupMeta): ByteArray = stored.first { it.first.id == meta.id }.second

        override suspend fun isAvailable(): Boolean = true

        override suspend fun signOut() {
            // no-op
        }
    }

    private class FakeTemplateStore(val items: MutableList<Template>) : TemplateStore {
        override suspend fun loadAll(): List<Template> = items.toList()
        override suspend fun save(template: Template) {
            val index = items.indexOfFirst { it.id == template.id }
            if (index >= 0) items[index] = template else items.add(template)
        }
        fun clear() = items.clear()
    }

    private class FakeProgramStore(val items: MutableList<Program>) : ProgramStore {
        override suspend fun loadAll(): List<Program> = items.toList()
        override suspend fun save(program: Program) {
            val index = items.indexOfFirst { it.id == program.id }
            if (index >= 0) items[index] = program else items.add(program)
        }
        fun clear() = items.clear()
    }

    private class FakeSessionStore(val items: MutableList<SessionLog>) : SessionStore {
        override suspend fun loadAll(): List<SessionLog> = items.toList()
        override suspend fun replaceAll(logs: List<SessionLog>) {
            items.clear()
            items.addAll(logs)
        }
        fun clear() = items.clear()
    }

    private class FakeSettingsStore(initial: SettingsSnapshot) : SettingsStore {
        private var snapshot: SettingsSnapshot = initial
        override suspend fun read(): SettingsSnapshot = snapshot
        override suspend fun write(snapshot: SettingsSnapshot) {
            this.snapshot = snapshot
        }
    }
}

package app.insidepacer.healthconnect

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.insidepacer.data.SettingsRepo
import app.insidepacer.data.settingsDataStore
import app.insidepacer.domain.SessionLog
import com.insidepacer.health.HcAvailability
import com.insidepacer.health.HealthConnectRepo
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HealthConnectSessionSyncerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun resetSettings() = runBlocking {
        context.settingsDataStore.edit { it.clear() }
    }

    @Test
    fun toggleOff_doesNotWrite() = runTest {
        val settingsRepo = SettingsRepo(context)
        val fakeRepo = FakeHealthConnectRepo()
        val syncer = HealthConnectSessionSyncer(context, settingsRepo, fakeRepo)

        syncer.onSessionLogged(createLog())

        assertEquals(0, fakeRepo.writeCount)
    }

    @Test
    fun toggleOnWithoutPermission_doesNotWrite() = runTest {
        val settingsRepo = SettingsRepo(context)
        settingsRepo.setHealthConnectEnabled(true)
        val fakeRepo = FakeHealthConnectRepo().apply {
            availability = HcAvailability.SUPPORTED_INSTALLED
            hasPermission = false
        }
        val syncer = HealthConnectSessionSyncer(context, settingsRepo, fakeRepo)

        syncer.onSessionLogged(createLog())

        assertEquals(0, fakeRepo.writeCount)
    }

    @Test
    fun toggleOnWithPermission_writesSession() = runTest {
        val settingsRepo = SettingsRepo(context)
        settingsRepo.setHealthConnectEnabled(true)
        val fakeRepo = FakeHealthConnectRepo().apply {
            availability = HcAvailability.SUPPORTED_INSTALLED
            hasPermission = true
        }
        val syncer = HealthConnectSessionSyncer(context, settingsRepo, fakeRepo)

        val log = createLog()
        syncer.onSessionLogged(log)

        assertEquals(1, fakeRepo.writeCount)
        assertEquals(Instant.ofEpochMilli(log.startMillis), fakeRepo.lastStart)
        assertEquals(Instant.ofEpochMilli(log.endMillis), fakeRepo.lastEnd)
    }

    private fun createLog(): SessionLog = SessionLog(
        id = "sess",
        programId = "program-1",
        startMillis = 1000L,
        endMillis = 5000L,
        totalSeconds = 4,
        segments = emptyList(),
        aborted = false,
    )

    private class FakeHealthConnectRepo : HealthConnectRepo {
        var availability: HcAvailability = HcAvailability.NOT_SUPPORTED
        var hasPermission: Boolean = false
        var writeCount: Int = 0
        var lastStart: Instant? = null
        var lastEnd: Instant? = null

        override suspend fun availability(context: Context): HcAvailability = availability

        override suspend fun ensureInstalled(context: Context): Boolean = availability == HcAvailability.SUPPORTED_INSTALLED

        override suspend fun hasWritePermission(context: Context): Boolean = hasPermission

        override suspend fun requestWritePermission(activity: ComponentActivity): Boolean = false

        override suspend fun writeWalkingSession(
            context: Context,
            startTime: Instant,
            endTime: Instant,
            notes: String?,
        ): Result<Unit> {
            writeCount += 1
            lastStart = startTime
            lastEnd = endTime
            return Result.success(Unit)
        }
    }
}

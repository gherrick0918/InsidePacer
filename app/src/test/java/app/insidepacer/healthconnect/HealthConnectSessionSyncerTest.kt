package app.insidepacer.healthconnect

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.insidepacer.data.settingsDataStore
import app.insidepacer.domain.Segment
import app.insidepacer.domain.SessionLog
import com.insidepacer.health.HcAvailability
import com.insidepacer.health.HealthConnectRepo
import com.insidepacer.health.SpeedSample
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
    fun withoutPermission_doesNotWrite() = runTest {
        val fakeRepo = FakeHealthConnectRepo()
        val syncer = HealthConnectSessionSyncer(context, fakeRepo)

        syncer.onSessionLogged(createLog())

        assertEquals(0, fakeRepo.writeCount)
    }

    @Test
    fun notInstalled_doesNotWrite() = runTest {
        val fakeRepo = FakeHealthConnectRepo().apply {
            availability = HcAvailability.SUPPORTED_NOT_INSTALLED
            hasPermission = false
        }
        val syncer = HealthConnectSessionSyncer(context, fakeRepo)

        syncer.onSessionLogged(createLog())

        assertEquals(0, fakeRepo.writeCount)
    }

    @Test
    fun withPermission_writesSession() = runTest {
        val fakeRepo = FakeHealthConnectRepo().apply {
            availability = HcAvailability.SUPPORTED_INSTALLED
            hasPermission = true
        }
        val syncer = HealthConnectSessionSyncer(context, fakeRepo)

        val log = createLog()
        syncer.onSessionLogged(log)

        assertEquals(1, fakeRepo.writeCount)
        assertEquals(Instant.ofEpochMilli(log.startMillis), fakeRepo.lastStart)
        assertEquals(Instant.ofEpochMilli(log.endMillis), fakeRepo.lastEnd)
    }

    @Test
    fun withSegments_calculatesDistanceAndSpeed() = runTest {
        val fakeRepo = FakeHealthConnectRepo().apply {
            availability = HcAvailability.SUPPORTED_INSTALLED
            hasPermission = true
        }
        val syncer = HealthConnectSessionSyncer(context, fakeRepo)

        val log = createLogWithSegments()
        syncer.onSessionLogged(log)

        assertEquals(1, fakeRepo.writeCount)
        assertNotNull(fakeRepo.lastDistance)
        assertTrue(fakeRepo.lastDistance!! > 0.0)
        assertNotNull(fakeRepo.lastSpeedSamples)
        assertTrue(fakeRepo.lastSpeedSamples!!.isNotEmpty())
    }

    @Test
    fun withSegments_createsDescriptiveTitle() = runTest {
        val fakeRepo = FakeHealthConnectRepo().apply {
            availability = HcAvailability.SUPPORTED_INSTALLED
            hasPermission = true
        }
        val syncer = HealthConnectSessionSyncer(context, fakeRepo)

        val log = createLogWithSegments()
        syncer.onSessionLogged(log)

        assertNotNull(fakeRepo.lastTitle)
        assertTrue(fakeRepo.lastTitle!!.contains("Walking"))
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

    private fun createLogWithSegments(): SessionLog = SessionLog(
        id = "sess",
        programId = "program-1",
        startMillis = 1000L,
        endMillis = 1000L + 600_000L, // 10 minutes
        totalSeconds = 600,
        segments = listOf(
            Segment(speed = 3.0, seconds = 300), // 3 mph for 5 minutes
            Segment(speed = 4.0, seconds = 300), // 4 mph for 5 minutes
        ),
        aborted = false,
    )

    private class FakeHealthConnectRepo : HealthConnectRepo {
        var availability: HcAvailability = HcAvailability.NOT_SUPPORTED
        var hasPermission: Boolean = false
        var writeCount: Int = 0
        var lastStart: Instant? = null
        var lastEnd: Instant? = null
        var lastDistance: Double? = null
        var lastSpeedSamples: List<SpeedSample>? = null
        var lastTitle: String? = null

        override suspend fun availability(context: Context): HcAvailability = availability

        override suspend fun ensureInstalled(context: Context): Boolean = availability == HcAvailability.SUPPORTED_INSTALLED

        override suspend fun hasWritePermission(context: Context): Boolean = hasPermission

        override suspend fun requestWritePermission(activity: ComponentActivity): Boolean = false

        override suspend fun writeWalkingSession(
            context: Context,
            startTime: Instant,
            endTime: Instant,
            notes: String?,
            title: String?,
            distanceMeters: Double?,
            speedSamples: List<SpeedSample>?,
        ): Result<Unit> {
            writeCount += 1
            lastStart = startTime
            lastEnd = endTime
            lastDistance = distanceMeters
            lastSpeedSamples = speedSamples
            lastTitle = title
            return Result.success(Unit)
        }
    }
}

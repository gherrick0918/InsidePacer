package app.insidepacer.ui.settings

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.insidepacer.data.settingsDataStore
import com.insidepacer.health.HcAvailability
import com.insidepacer.health.HealthConnectRepo
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clearDataStore() = runBlocking {
        context.settingsDataStore.edit { it.clear() }
    }

    @Test
    fun developerCard_isReachableViaScroll() {
        composeRule.setContent {
            SettingsScreen(
                healthConnectRepo = FakeHealthConnectRepo(),
                showDeveloperOptions = true,
            )
        }

        composeRule.onNodeWithTag("developerCard").performScrollTo().assertIsDisplayed()
    }

    private class FakeHealthConnectRepo : HealthConnectRepo {
        override suspend fun availability(context: Context): HcAvailability = HcAvailability.NOT_SUPPORTED

        override suspend fun ensureInstalled(context: Context): Boolean = false

        override suspend fun hasWritePermission(context: Context): Boolean = false

        override suspend fun requestWritePermission(activity: ComponentActivity): Boolean = false

        override suspend fun writeWalkingSession(
            context: Context,
            startTime: Instant,
            endTime: Instant,
            notes: String?,
            title: String?,
            distanceMeters: Double?,
            speedSamples: List<com.insidepacer.health.SpeedSample>?,
        ): Result<Unit> = Result.failure(IllegalStateException("Not supported"))
    }
}

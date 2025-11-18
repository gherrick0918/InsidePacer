package app.insidepacer.analytics

import android.content.Context
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Tests for AnalyticsService
 * Note: These are basic smoke tests. Firebase Analytics behavior is tested by Google.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AnalyticsServiceTest {

    private lateinit var context: Context
    private lateinit var analyticsService: AnalyticsService

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        // Note: In tests, Firebase will use test mode and not send real events
        analyticsService = AnalyticsService(context)
    }

    @Test
    fun `test analytics service creation`() {
        // Just verify we can create the service without crashing
        val service = AnalyticsService(context)
        assert(service != null)
    }

    @Test
    fun `test screen view logging does not crash`() {
        // Firebase Analytics doesn't throw exceptions, so we just verify no crash
        analyticsService.logScreenView(AnalyticsService.SCREEN_TEMPLATES)
        analyticsService.logScreenView(AnalyticsService.SCREEN_HISTORY)
    }

    @Test
    fun `test session events do not crash`() {
        analyticsService.logSessionStart("template123", "program456", 1800)
        analyticsService.logSessionPause()
        analyticsService.logSessionResume()
        analyticsService.logSessionComplete(
            sessionId = "session123",
            durationSeconds = 1800,
            segments = 10,
            aborted = false
        )
    }

    @Test
    fun `test template events do not crash`() {
        analyticsService.logTemplateCreate("Test Template", 5)
        analyticsService.logTemplateEdit("template123")
        analyticsService.logTemplateUsage("template123", "Morning Run")
        analyticsService.logTemplateDelete("template123")
    }

    @Test
    fun `test program events do not crash`() {
        analyticsService.logProgramGenerate("Beginner", 8, 5)
        analyticsService.logProgramCreate(12, 5)
        analyticsService.logProgramEdit("program123")
        analyticsService.logProgramDelete("program123")
    }

    @Test
    fun `test backup events do not crash`() {
        analyticsService.logBackupStart()
        analyticsService.logBackupSuccess(1024L)
        analyticsService.logBackupFailure("Network error")
        analyticsService.logRestoreStart()
        analyticsService.logRestoreSuccess()
        analyticsService.logRestoreFailure("File not found")
        analyticsService.logBackupSignIn()
    }

    @Test
    fun `test health connect events do not crash`() {
        analyticsService.logHealthConnectSync(5, true)
        analyticsService.logHealthConnectPermissionRequest()
        analyticsService.logHealthConnectPermissionGranted()
    }

    @Test
    fun `test settings events do not crash`() {
        analyticsService.logSettingsChange("voice_enabled", "true")
        analyticsService.logUnitsChange("mph", "kmh")
    }

    @Test
    fun `test export events do not crash`() {
        analyticsService.logExportCsv(10)
        analyticsService.logExportProgram()
    }

    @Test
    fun `test user properties do not crash`() {
        analyticsService.setUserLevel("Intermediate")
        analyticsService.setUserUnits("mph")
        analyticsService.setHasHealthConnect(true)
        analyticsService.setHasBackup(true)
    }

    @Test
    fun `test generic event logging with various parameter types`() {
        analyticsService.logEvent("custom_event", mapOf(
            "string_param" to "value",
            "int_param" to 42,
            "long_param" to 1000L,
            "double_param" to 3.14,
            "boolean_param" to true
        ))
    }

    @Test
    fun `test generic event logging without parameters`() {
        analyticsService.logEvent("simple_event")
    }

    @Test
    fun `test all screen name constants exist`() {
        // Verify all screen constants are accessible
        val screens = listOf(
            AnalyticsService.SCREEN_HOME,
            AnalyticsService.SCREEN_QUICK_SESSION,
            AnalyticsService.SCREEN_TEMPLATES,
            AnalyticsService.SCREEN_TEMPLATE_EDITOR,
            AnalyticsService.SCREEN_PROGRAMS,
            AnalyticsService.SCREEN_PROGRAM_EDITOR,
            AnalyticsService.SCREEN_GENERATE_PLAN,
            AnalyticsService.SCREEN_TODAY,
            AnalyticsService.SCREEN_SCHEDULE,
            AnalyticsService.SCREEN_HISTORY,
            AnalyticsService.SCREEN_HISTORY_DETAIL,
            AnalyticsService.SCREEN_SESSION_RUN,
            AnalyticsService.SCREEN_PROFILE,
            AnalyticsService.SCREEN_SETTINGS,
            AnalyticsService.SCREEN_ONBOARDING_SPEEDS
        )
        
        // Verify each screen can be logged
        screens.forEach { screenName ->
            analyticsService.logScreenView(screenName)
        }
    }
}

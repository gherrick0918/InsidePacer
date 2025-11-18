package app.insidepacer.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import com.google.firebase.analytics.logEvent

/**
 * Wrapper for Firebase Analytics to track app events and user behavior.
 * All Firebase services remain within the free tier limits.
 */
class AnalyticsService(context: Context) {
    
    private val analytics: FirebaseAnalytics = Firebase.analytics
    
    // Screen tracking
    fun logScreenView(screenName: String) {
        analytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW) {
            param(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
            param(FirebaseAnalytics.Param.SCREEN_CLASS, screenName)
        }
    }
    
    // Session events
    fun logSessionStart(templateId: String?, programId: String?, durationSeconds: Int) {
        analytics.logEvent("session_start") {
            templateId?.let { param("template_id", it) }
            programId?.let { param("program_id", it) }
            param("planned_duration_sec", durationSeconds.toLong())
        }
    }
    
    fun logSessionComplete(
        sessionId: String,
        durationSeconds: Int,
        segments: Int,
        aborted: Boolean,
        templateId: String? = null,
        programId: String? = null
    ) {
        analytics.logEvent("session_complete") {
            param("session_id", sessionId)
            param("duration_sec", durationSeconds.toLong())
            param("segments", segments.toLong())
            param("aborted", if (aborted) 1L else 0L)
            templateId?.let { param("template_id", it) }
            programId?.let { param("program_id", it) }
        }
    }
    
    fun logSessionPause() {
        analytics.logEvent("session_pause", null)
    }
    
    fun logSessionResume() {
        analytics.logEvent("session_resume", null)
    }
    
    fun logSessionAbort(reason: String) {
        analytics.logEvent("session_abort") {
            param("reason", reason)
        }
    }
    
    // Template events
    fun logTemplateCreate(name: String, segmentCount: Int) {
        analytics.logEvent("template_create") {
            param("name", name)
            param("segment_count", segmentCount.toLong())
        }
    }
    
    fun logTemplateEdit(templateId: String) {
        analytics.logEvent("template_edit") {
            param("template_id", templateId)
        }
    }
    
    fun logTemplateDelete(templateId: String) {
        analytics.logEvent("template_delete") {
            param("template_id", templateId)
        }
    }
    
    fun logTemplateUsage(templateId: String, name: String) {
        analytics.logEvent("template_usage") {
            param("template_id", templateId)
            param("template_name", name)
        }
    }
    
    // Program events
    fun logProgramCreate(weeks: Int, daysPerWeek: Int) {
        analytics.logEvent("program_create") {
            param("weeks", weeks.toLong())
            param("days_per_week", daysPerWeek.toLong())
        }
    }
    
    fun logProgramEdit(programId: String) {
        analytics.logEvent("program_edit") {
            param("program_id", programId)
        }
    }
    
    fun logProgramDelete(programId: String) {
        analytics.logEvent("program_delete") {
            param("program_id", programId)
        }
    }
    
    fun logProgramGenerate(level: String, weeks: Int, daysPerWeek: Int) {
        analytics.logEvent("program_generate") {
            param("level", level)
            param("weeks", weeks.toLong())
            param("days_per_week", daysPerWeek.toLong())
        }
    }
    
    // Backup events
    fun logBackupStart() {
        analytics.logEvent("backup_start", null)
    }
    
    fun logBackupSuccess(sizeBytes: Long) {
        analytics.logEvent("backup_success") {
            param("size_bytes", sizeBytes)
        }
    }
    
    fun logBackupFailure(error: String) {
        analytics.logEvent("backup_failure") {
            param("error", error)
        }
    }
    
    fun logRestoreStart() {
        analytics.logEvent("restore_start", null)
    }
    
    fun logRestoreSuccess() {
        analytics.logEvent("restore_success", null)
    }
    
    fun logRestoreFailure(error: String) {
        analytics.logEvent("restore_failure") {
            param("error", error)
        }
    }
    
    fun logBackupSignIn(provider: String = "google_drive") {
        analytics.logEvent(FirebaseAnalytics.Event.LOGIN) {
            param(FirebaseAnalytics.Param.METHOD, provider)
        }
    }
    
    // Health Connect events
    fun logHealthConnectSync(recordCount: Int, success: Boolean) {
        analytics.logEvent("health_connect_sync") {
            param("record_count", recordCount.toLong())
            param("success", if (success) 1L else 0L)
        }
    }
    
    fun logHealthConnectPermissionRequest() {
        analytics.logEvent("health_connect_permission_request", null)
    }
    
    fun logHealthConnectPermissionGranted() {
        analytics.logEvent("health_connect_permission_granted", null)
    }
    
    // Settings events
    fun logSettingsChange(setting: String, value: String) {
        analytics.logEvent("settings_change") {
            param("setting", setting)
            param("value", value)
        }
    }
    
    fun logUnitsChange(oldUnits: String, newUnits: String) {
        analytics.logEvent("units_change") {
            param("old_units", oldUnits)
            param("new_units", newUnits)
        }
    }
    
    // Export events
    fun logExportCsv(sessionCount: Int) {
        analytics.logEvent("export_csv") {
            param("session_count", sessionCount.toLong())
        }
    }
    
    fun logExportProgram() {
        analytics.logEvent("export_program", null)
    }
    
    // User properties (for cohort analysis)
    fun setUserLevel(level: String) {
        analytics.setUserProperty("user_level", level)
    }
    
    fun setUserUnits(units: String) {
        analytics.setUserProperty("preferred_units", units)
    }
    
    fun setHasHealthConnect(enabled: Boolean) {
        analytics.setUserProperty("health_connect_enabled", if (enabled) "yes" else "no")
    }
    
    fun setHasBackup(enabled: Boolean) {
        analytics.setUserProperty("backup_enabled", if (enabled) "yes" else "no")
    }
    
    // Generic event logging for flexibility
    fun logEvent(eventName: String, params: Map<String, Any>? = null) {
        analytics.logEvent(eventName) {
            params?.forEach { (key, value) ->
                when (value) {
                    is String -> param(key, value)
                    is Long -> param(key, value)
                    is Double -> param(key, value)
                    is Int -> param(key, value.toLong())
                    is Boolean -> param(key, if (value) 1L else 0L)
                }
            }
        }
    }
    
    companion object {
        // Screen names constants
        const val SCREEN_HOME = "home"
        const val SCREEN_QUICK_SESSION = "quick_session"
        const val SCREEN_TEMPLATES = "templates"
        const val SCREEN_TEMPLATE_EDITOR = "template_editor"
        const val SCREEN_PROGRAMS = "programs"
        const val SCREEN_PROGRAM_EDITOR = "program_editor"
        const val SCREEN_GENERATE_PLAN = "generate_plan"
        const val SCREEN_TODAY = "today"
        const val SCREEN_SCHEDULE = "schedule"
        const val SCREEN_HISTORY = "history"
        const val SCREEN_HISTORY_DETAIL = "history_detail"
        const val SCREEN_SESSION_RUN = "session_run"
        const val SCREEN_PROFILE = "profile"
        const val SCREEN_SETTINGS = "settings"
        const val SCREEN_ONBOARDING_SPEEDS = "onboarding_speeds"
    }
}

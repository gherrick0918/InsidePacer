package app.insidepacer.analytics

import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase

/**
 * Helper for Firebase Crashlytics to track crashes and non-fatal errors.
 * Provides better error tracking than silent failures.
 */
object CrashlyticsHelper {
    
    private val crashlytics = Firebase.crashlytics
    
    /**
     * Log a non-fatal exception to Crashlytics
     */
    fun recordException(throwable: Throwable) {
        crashlytics.recordException(throwable)
    }
    
    /**
     * Log a message to Crashlytics
     */
    fun log(message: String) {
        crashlytics.log(message)
    }
    
    /**
     * Set a custom key-value pair for crash reports
     */
    fun setCustomKey(key: String, value: String) {
        crashlytics.setCustomKey(key, value)
    }
    
    fun setCustomKey(key: String, value: Boolean) {
        crashlytics.setCustomKey(key, value)
    }
    
    fun setCustomKey(key: String, value: Int) {
        crashlytics.setCustomKey(key, value)
    }
    
    fun setCustomKey(key: String, value: Long) {
        crashlytics.setCustomKey(key, value)
    }
    
    fun setCustomKey(key: String, value: Float) {
        crashlytics.setCustomKey(key, value)
    }
    
    fun setCustomKey(key: String, value: Double) {
        crashlytics.setCustomKey(key, value)
    }
    
    /**
     * Set user identifier (anonymized) for crash tracking
     */
    fun setUserId(userId: String) {
        crashlytics.setUserId(userId)
    }
    
    /**
     * Enable/disable crash reporting (useful for debug builds or user preference)
     */
    fun setCrashlyticsCollectionEnabled(enabled: Boolean) {
        crashlytics.setCrashlyticsCollectionEnabled(enabled)
    }
    
    /**
     * Record a handled error with context
     */
    fun recordError(context: String, throwable: Throwable) {
        log("Error in $context: ${throwable.message}")
        recordException(throwable)
    }
    
    /**
     * Convenience method for tracking specific app errors
     */
    fun trackSessionError(sessionId: String, error: Throwable) {
        setCustomKey("session_id", sessionId)
        log("Session error: ${error.message}")
        recordException(error)
    }
    
    fun trackBackupError(operation: String, error: Throwable) {
        setCustomKey("backup_operation", operation)
        log("Backup error in $operation: ${error.message}")
        recordException(error)
    }
    
    fun trackHealthConnectError(error: Throwable) {
        setCustomKey("health_connect_error", true)
        log("Health Connect error: ${error.message}")
        recordException(error)
    }
}

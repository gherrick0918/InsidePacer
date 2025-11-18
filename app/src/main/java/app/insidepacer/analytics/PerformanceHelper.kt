package app.insidepacer.analytics

import com.google.firebase.perf.FirebasePerformance
import com.google.firebase.perf.metrics.Trace

/**
 * Helper for Firebase Performance Monitoring to track app performance.
 * Monitors critical operations and network requests.
 */
object PerformanceHelper {
    
    private val performance = FirebasePerformance.getInstance()
    
    /**
     * Start a custom trace
     */
    fun startTrace(traceName: String): Trace {
        return performance.newTrace(traceName).apply { start() }
    }
    
    /**
     * Stop a trace
     */
    fun stopTrace(trace: Trace) {
        trace.stop()
    }
    
    /**
     * Execute a block with performance tracing
     */
    inline fun <T> trace(traceName: String, block: (Trace) -> T): T {
        val trace = startTrace(traceName)
        return try {
            block(trace)
        } finally {
            stopTrace(trace)
        }
    }
    
    /**
     * Execute a suspend block with performance tracing
     */
    suspend inline fun <T> traceSuspend(traceName: String, crossinline block: suspend (Trace) -> T): T {
        val trace = startTrace(traceName)
        return try {
            block(trace)
        } finally {
            stopTrace(trace)
        }
    }
    
    /**
     * Add a metric to a trace
     */
    fun putMetric(trace: Trace, metricName: String, value: Long) {
        trace.putMetric(metricName, value)
    }
    
    /**
     * Increment a metric on a trace
     */
    fun incrementMetric(trace: Trace, metricName: String, value: Long = 1) {
        trace.incrementMetric(metricName, value)
    }
    
    /**
     * Add an attribute to a trace
     */
    fun putAttribute(trace: Trace, attribute: String, value: String) {
        trace.putAttribute(attribute, value)
    }
    
    /**
     * Enable/disable performance monitoring
     */
    fun setPerformanceCollectionEnabled(enabled: Boolean) {
        performance.isPerformanceCollectionEnabled = enabled
    }
    
    // Common trace names
    object Traces {
        const val APP_STARTUP = "app_startup"
        const val SESSION_START = "session_start"
        const val SESSION_COMPLETE = "session_complete"
        const val BACKUP_CREATE = "backup_create"
        const val BACKUP_RESTORE = "backup_restore"
        const val HEALTH_CONNECT_SYNC = "health_connect_sync"
        const val TEMPLATE_LOAD = "template_load"
        const val PROGRAM_LOAD = "program_load"
        const val HISTORY_LOAD = "history_load"
        const val CSV_EXPORT = "csv_export"
        const val PROGRAM_GENERATE = "program_generate"
    }
}

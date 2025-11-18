# Firebase Integration Examples

This document provides practical examples of Firebase integration in InsidePacer.

## Analytics Examples

### Example 1: Track Screen Views (in Composables)

```kotlin
@Composable
fun TemplatesListScreen() {
    val context = LocalContext.current
    val analytics = remember { Singleton.getAnalyticsService(context) }
    
    LaunchedEffect(Unit) {
        analytics.logScreenView(AnalyticsService.SCREEN_TEMPLATES)
    }
    
    // Rest of the screen implementation
}
```

### Example 2: Track Session Start

```kotlin
fun startSession(template: Template?, program: Program?) {
    val analytics = Singleton.getAnalyticsService(context)
    
    val totalDuration = template?.segments?.sumOf { it.seconds } ?: 0
    analytics.logSessionStart(
        templateId = template?.id,
        programId = program?.id,
        durationSeconds = totalDuration
    )
    
    // Start the session
}
```

### Example 3: Track Session Complete

```kotlin
fun completeSession(session: SessionLog) {
    val analytics = Singleton.getAnalyticsService(context)
    
    analytics.logSessionComplete(
        sessionId = session.id,
        durationSeconds = session.totalSeconds,
        segments = session.segments.size,
        aborted = session.aborted,
        templateId = null,  // If you have this info
        programId = session.programId
    )
}
```

### Example 4: Track Template Creation

```kotlin
fun saveTemplate(name: String, segments: List<Segment>) {
    val analytics = Singleton.getAnalyticsService(context)
    
    // Save template
    val template = Template(
        id = UUID.randomUUID().toString(),
        name = name,
        segments = segments
    )
    templateRepo.save(template)
    
    // Track analytics
    analytics.logTemplateCreate(name, segments.size)
}
```

### Example 5: Track Program Generation

```kotlin
fun generateProgram(level: String, weeks: Int, daysPerWeek: Int) {
    val analytics = Singleton.getAnalyticsService(context)
    
    analytics.logProgramGenerate(level, weeks, daysPerWeek)
    
    // Generate program
    val program = programGenerator.generate(level, weeks, daysPerWeek)
    
    // Save program
    programRepo.save(program)
}
```

### Example 6: Set User Properties

```kotlin
fun updateUserProfile(profile: UserProfile) {
    val analytics = Singleton.getAnalyticsService(context)
    
    // Set user properties for cohort analysis
    analytics.setUserLevel(profile.level)
    analytics.setUserUnits(profile.units)
    
    // Save profile
    profileRepo.save(profile)
}
```

## Crashlytics Examples

### Example 7: Track Non-Fatal Errors

```kotlin
suspend fun syncWithHealthConnect() {
    try {
        val records = healthConnectRepo.syncRecords()
        // Success
    } catch (e: Exception) {
        // Log the error to Crashlytics
        CrashlyticsHelper.trackHealthConnectError(e)
        // Show error to user
        showError("Failed to sync with Health Connect")
    }
}
```

### Example 8: Add Context to Crashes

```kotlin
fun processSession(sessionId: String) {
    // Set context for crash reports
    CrashlyticsHelper.setCustomKey("session_id", sessionId)
    CrashlyticsHelper.setCustomKey("processing", true)
    CrashlyticsHelper.log("Starting session processing")
    
    try {
        // Process session
        val session = sessionRepo.load(sessionId)
        processSegments(session.segments)
        
        CrashlyticsHelper.setCustomKey("processing", false)
    } catch (e: Exception) {
        CrashlyticsHelper.trackSessionError(sessionId, e)
        throw e
    }
}
```

### Example 9: Record Specific Error Context

```kotlin
suspend fun exportToCsv(sessions: List<SessionLog>) {
    try {
        val csv = CsvWriter.generate(sessions)
        saveToFile(csv)
    } catch (e: Exception) {
        CrashlyticsHelper.log("Export failed with ${sessions.size} sessions")
        CrashlyticsHelper.setCustomKey("session_count", sessions.size)
        CrashlyticsHelper.recordException(e)
        
        // Show error to user
        showError("Failed to export sessions")
    }
}
```

## Performance Monitoring Examples

### Example 10: Trace Backup Operations

```kotlin
suspend fun performBackup() {
    val trace = PerformanceHelper.startTrace(PerformanceHelper.Traces.BACKUP_CREATE)
    
    try {
        val backup = createBackupBundle()
        val encrypted = encryptBackup(backup)
        val uploaded = uploadToCloud(encrypted)
        
        // Add metrics
        trace.putMetric("backup_size_bytes", encrypted.size.toLong())
        trace.putAttribute("backup_type", "full")
        
        PerformanceHelper.stopTrace(trace)
    } catch (e: Exception) {
        trace.putAttribute("error", e.message ?: "unknown")
        PerformanceHelper.stopTrace(trace)
        throw e
    }
}
```

### Example 11: Trace with Helper Method

```kotlin
suspend fun loadTemplates(): List<Template> {
    return PerformanceHelper.traceSuspend(PerformanceHelper.Traces.TEMPLATE_LOAD) { trace ->
        val templates = templateRepo.loadAll()
        trace.putMetric("template_count", templates.size.toLong())
        templates
    }
}
```

### Example 12: Trace CSV Export

```kotlin
suspend fun exportSessionsToCsv(sessions: List<SessionLog>): File {
    return PerformanceHelper.traceSuspend(PerformanceHelper.Traces.CSV_EXPORT) { trace ->
        trace.putMetric("session_count", sessions.size.toLong())
        
        val startTime = System.currentTimeMillis()
        val csv = CsvWriter.generate(sessions)
        val duration = System.currentTimeMillis() - startTime
        
        trace.putMetric("generation_time_ms", duration)
        trace.putMetric("csv_size_bytes", csv.toByteArray().size.toLong())
        
        saveToFile(csv)
    }
}
```

## Remote Config Examples

### Example 13: Use Feature Flags

```kotlin
@Composable
fun SettingsScreen() {
    val remoteConfig = remember { RemoteConfigHelper() }
    
    LaunchedEffect(Unit) {
        remoteConfig.fetchAndActivate()
    }
    
    // Conditionally show features based on remote config
    if (remoteConfig.isProgramGeneratorEnabled()) {
        ProgramGeneratorSection()
    }
    
    if (remoteConfig.isHealthConnectEnabled()) {
        HealthConnectSection()
    }
}
```

### Example 14: Use Configuration Values

```kotlin
fun validateTemplate(template: Template): Boolean {
    val remoteConfig = RemoteConfigHelper()
    val maxTemplates = remoteConfig.getMaxTemplates()
    
    val currentCount = templateRepo.count()
    if (currentCount >= maxTemplates) {
        showError("Maximum number of templates reached ($maxTemplates)")
        return false
    }
    
    return true
}
```

### Example 15: Use Remote Defaults

```kotlin
fun getDefaultSettings(): Settings {
    val remoteConfig = RemoteConfigHelper()
    
    return Settings(
        preChangeSeconds = remoteConfig.getDefaultPreChangeSeconds().toInt(),
        voiceEnabled = remoteConfig.isVoicePromptsEnabled(),
        beepEnabled = remoteConfig.isBeepsEnabled(),
        hapticsEnabled = remoteConfig.isHapticsEnabled()
    )
}
```

## Combined Examples

### Example 16: Complete Session Flow with All Firebase Services

```kotlin
class SessionManager(private val context: Context) {
    private val analytics = Singleton.getAnalyticsService(context)
    
    suspend fun runSession(template: Template, program: Program?) {
        // Track analytics
        analytics.logSessionStart(
            templateId = template.id,
            programId = program?.id,
            durationSeconds = template.segments.sumOf { it.seconds }
        )
        
        // Start performance trace
        val trace = PerformanceHelper.startTrace(PerformanceHelper.Traces.SESSION_START)
        
        // Add crash context
        CrashlyticsHelper.setCustomKey("template_id", template.id)
        CrashlyticsHelper.setCustomKey("session_active", true)
        
        try {
            val session = executeSession(template)
            
            // Track completion
            analytics.logSessionComplete(
                sessionId = session.id,
                durationSeconds = session.totalSeconds,
                segments = session.segments.size,
                aborted = session.aborted
            )
            
            trace.putMetric("duration_seconds", session.totalSeconds.toLong())
            trace.putAttribute("aborted", session.aborted.toString())
            
            PerformanceHelper.stopTrace(trace)
            CrashlyticsHelper.setCustomKey("session_active", false)
            
        } catch (e: Exception) {
            // Track error
            analytics.logSessionAbort("error")
            CrashlyticsHelper.trackSessionError(template.id, e)
            
            trace.putAttribute("error", "true")
            PerformanceHelper.stopTrace(trace)
            
            throw e
        }
    }
}
```

### Example 17: Complete Backup Flow

```kotlin
suspend fun performBackupWithTracking(): Result<Unit> {
    val analytics = Singleton.getAnalyticsService(context)
    analytics.logBackupStart()
    
    return PerformanceHelper.traceSuspend(PerformanceHelper.Traces.BACKUP_CREATE) { trace ->
        try {
            val backup = createBackup()
            val size = backup.size
            
            uploadBackup(backup)
            
            analytics.logBackupSuccess(size.toLong())
            trace.putMetric("backup_size", size.toLong())
            trace.putAttribute("success", "true")
            
            Result.success(Unit)
        } catch (e: Exception) {
            analytics.logBackupFailure(e.message ?: "Unknown error")
            CrashlyticsHelper.trackBackupError("backup", e)
            
            trace.putAttribute("success", "false")
            trace.putAttribute("error_type", e.javaClass.simpleName)
            
            Result.failure(e)
        }
    }
}
```

## Best Practices

1. **Always wrap Firebase calls in try-catch** - Firebase might not be configured
2. **Fail silently** - Don't crash the app if Firebase is unavailable
3. **Add meaningful context** - Use custom keys and attributes in Crashlytics
4. **Track both success and failure** - Complete picture of user experience
5. **Use performance traces sparingly** - Focus on critical operations
6. **Set user properties early** - For better cohort analysis
7. **Test with DebugView** - Verify events are being sent correctly
8. **Respect user privacy** - Never log PII or sensitive data

## Testing Firebase Integration

### Enable Debug Mode

```bash
# Enable debug mode for analytics
adb shell setprop debug.firebase.analytics.app app.insidepacer

# Disable debug mode
adb shell setprop debug.firebase.analytics.app .none.
```

### Test Crash Reporting

```kotlin
// Add a debug button to force a crash
Button(onClick = { 
    throw RuntimeException("Test Crash") 
}) {
    Text("Test Crash")
}
```

### Verify Performance Traces

Performance traces appear in Firebase Console within 12 hours. Use the traces defined in `PerformanceHelper.Traces` for consistency.

# Firebase Integration Guide for InsidePacer

This guide explains how to set up and use Firebase services in InsidePacer. All services are configured to stay within Firebase's generous free tier.

## Overview

InsidePacer uses the following Firebase services:

1. **Firebase Analytics** - Track app usage, user engagement, and feature adoption
2. **Firebase Crashlytics** - Automatic crash reporting and error tracking
3. **Firebase Performance Monitoring** - Monitor app performance and identify bottlenecks
4. **Firebase Remote Config** - Feature flags and remote configuration (optional)

All services are free for moderate usage and perfect for personal projects and small apps.

## Setup Instructions

### 1. Create a Firebase Project

1. Go to the [Firebase Console](https://console.firebase.google.com/)
2. Click "Add project" or select an existing project
3. Follow the setup wizard (you can disable Google Analytics for the project if you only want Crashlytics/Performance)

### 2. Add Android App to Firebase

1. In your Firebase project, click "Add app" and select Android
2. Enter the package name: `app.insidepacer`
3. (Optional) Add an app nickname: "InsidePacer"
4. (Optional) Add SHA-1 certificate fingerprint (not required for these services)
5. Click "Register app"

### 3. Download Configuration File

1. Download the `google-services.json` file
2. Place it in the `app/` directory of the project:
   ```
   InsidePacer/
   ├── app/
   │   ├── google-services.json          ← Place here
   │   ├── google-services.json.template  ← Template for reference
   │   └── build.gradle.kts
   └── build.gradle.kts
   ```
3. The file is already added to `.gitignore` to keep your credentials secure

### 4. Enable Firebase Services

#### Enable Crashlytics
1. In Firebase Console, go to Crashlytics
2. Click "Enable Crashlytics"
3. The app will start sending crash reports after the next build

#### Enable Performance Monitoring
1. In Firebase Console, go to Performance
2. Click "Get started"
3. Performance data will automatically be collected

#### Enable Remote Config (Optional)
1. In Firebase Console, go to Remote Config
2. Click "Get started"
3. You can add parameters later as needed

## Free Tier Limits

All Firebase services used stay well within free tier limits:

### Analytics
- **Unlimited events and user properties**
- Free forever with no quotas

### Crashlytics
- **Unlimited crash reports**
- Free forever with no quotas

### Performance Monitoring
- **Free tier**: 500 traces per day
- Our app uses ~10-20 custom traces per day per user
- Network monitoring is automatic

### Remote Config
- **Free tier**: 1 million fetches per day
- 2000 requests per hour
- More than sufficient for personal use

## What Gets Tracked

### Analytics Events

#### Session Events
- `session_start` - When a workout session starts
- `session_complete` - When a session finishes (with duration, segments)
- `session_pause` / `session_resume` - Session controls
- `session_abort` - When a session is stopped early

#### Template Events
- `template_create` - Creating a new template
- `template_edit` - Editing a template
- `template_delete` - Deleting a template
- `template_usage` - Using a template for a session

#### Program Events
- `program_create` - Creating a training program
- `program_generate` - Using AI program generator
- `program_edit` / `program_delete` - Program management

#### Backup Events
- `backup_start` / `backup_success` / `backup_failure` - Backup operations
- `restore_start` / `restore_success` / `restore_failure` - Restore operations

#### Health Connect Events
- `health_connect_sync` - Syncing with Health Connect
- `health_connect_permission_request` / `health_connect_permission_granted`

#### Screen Views
All major screens are tracked automatically for navigation flow analysis.

### Crashlytics

Automatic crash reports include:
- Stack traces
- Device information
- OS version
- Custom keys (session IDs, operation context)
- Breadcrumb logs

### Performance Monitoring

Custom traces for:
- App startup time
- Session operations (start, complete)
- Backup/restore operations
- Health Connect sync
- Data loading (templates, programs, history)
- CSV export

## Privacy Considerations

### What We Track
- App usage patterns (which features are used)
- Technical errors and crashes
- Performance metrics
- Feature engagement

### What We DON'T Track
- Personal information (name, email)
- Health data or workout content
- Location data
- Any user-created content (template names, etc. are hashed)

### User Control
Users can:
- Disable crash reporting in settings (if implemented)
- All data is anonymous and aggregated
- No personal identifiers are sent

## Usage in Code

### Analytics

```kotlin
val analytics = Singleton.getAnalyticsService(context)

// Track screen views
analytics.logScreenView(AnalyticsService.SCREEN_TEMPLATES)

// Track session events
analytics.logSessionStart(templateId, programId, durationSeconds)
analytics.logSessionComplete(sessionId, duration, segments, aborted)

// Track feature usage
analytics.logTemplateCreate(name, segmentCount)
analytics.logProgramGenerate(level, weeks, daysPerWeek)
```

### Crashlytics

```kotlin
try {
    // Risky operation
} catch (e: Exception) {
    CrashlyticsHelper.recordException(e)
    CrashlyticsHelper.setCustomKey("operation", "backup")
}

// Or use convenience methods
CrashlyticsHelper.trackBackupError("restore", exception)
CrashlyticsHelper.trackSessionError(sessionId, exception)
```

### Performance Monitoring

```kotlin
// Trace a block of code
PerformanceHelper.trace(PerformanceHelper.Traces.BACKUP_CREATE) { trace ->
    // Perform backup operation
    trace.putMetric("file_size", sizeInBytes)
}

// Trace a suspend function
PerformanceHelper.traceSuspend(PerformanceHelper.Traces.HEALTH_CONNECT_SYNC) { trace ->
    // Perform sync
    trace.incrementMetric("records_synced", recordCount.toLong())
}
```

### Remote Config

```kotlin
val remoteConfig = RemoteConfigHelper()

// Fetch latest configuration
remoteConfig.fetchAndActivate()

// Use feature flags
if (remoteConfig.isProgramGeneratorEnabled()) {
    // Show program generator feature
}

// Use configuration values
val maxTemplates = remoteConfig.getMaxTemplates()
```

## Testing

### Debug Mode

To see analytics events in real-time during development:

```bash
adb shell setprop debug.firebase.analytics.app app.insidepacer
```

Then view events in Firebase Console under Analytics > DebugView

### Crashlytics Testing

Force a test crash:

```kotlin
throw RuntimeException("Test Crash")
```

Crashes appear in Firebase Console under Crashlytics within a few minutes.

### Performance Testing

Performance traces appear in Firebase Console under Performance within 12 hours.

## Best Practices

1. **Don't Track PII**: Never send personally identifiable information
2. **Use Event Parameters**: Add context to events for better insights
3. **Set User Properties**: Use for cohort analysis (e.g., user level, units preference)
4. **Monitor Budgets**: Check Firebase Console monthly to ensure staying within free tier
5. **Handle Errors Gracefully**: Catch Firebase initialization errors for users without Google Play Services

## Troubleshooting

### "google-services.json not found"
- Ensure the file is in `app/` directory
- Check that it's not gitignored in the wrong location
- Try "Sync Project with Gradle Files"

### Analytics Events Not Showing
- Enable Debug Mode (see Testing section)
- Events can take up to 24 hours to appear in regular reporting
- Check DebugView for real-time events

### Build Errors
- Ensure all Firebase dependencies are the same version (using BOM)
- Clean and rebuild project: `./gradlew clean build`
- Update Google Play Services if needed

### "Firebase not initialized"
- Ensure `google-services.json` is present
- Check that package name matches exactly: `app.insidepacer`
- Verify the app is registered in Firebase Console

## Resources

- [Firebase Android Documentation](https://firebase.google.com/docs/android/setup)
- [Firebase Analytics](https://firebase.google.com/docs/analytics)
- [Firebase Crashlytics](https://firebase.google.com/docs/crashlytics)
- [Firebase Performance](https://firebase.google.com/docs/perf-mon)
- [Firebase Remote Config](https://firebase.google.com/docs/remote-config)

## Support

If you encounter issues:
1. Check the Firebase Console for service status
2. Review the [Firebase Support](https://firebase.google.com/support) documentation
3. Search [Stack Overflow](https://stackoverflow.com/questions/tagged/firebase) for similar issues

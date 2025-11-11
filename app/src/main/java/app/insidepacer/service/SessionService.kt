package app.insidepacer.service

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Parcelable
import app.insidepacer.BuildConfig
import app.insidepacer.R
import app.insidepacer.core.formatDuration
import app.insidepacer.core.formatPace
import app.insidepacer.core.formatSpeed
import app.insidepacer.data.ProgramProgressRepo
import app.insidepacer.data.SessionRepo
import app.insidepacer.data.Units
import app.insidepacer.data.SettingsRepo
import app.insidepacer.di.Singleton
import app.insidepacer.domain.Segment
import app.insidepacer.domain.SessionLog
import app.insidepacer.domain.SessionState
import app.insidepacer.engine.SessionScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SessionService : Service() {
    companion object {
        const val NOTIFICATION_ID = 42

        const val ACTION_START = "app.insidepacer.action.START"
        const val ACTION_OBSERVE = "app.insidepacer.action.OBSERVE"

        const val EXTRA_SEGMENTS = "segments"
        const val EXTRA_PRECHANGE_SEC = "prechange"
        const val EXTRA_VOICE = "voice"
        const val EXTRA_BEEP = "beep"
        const val EXTRA_HAPTICS = "haptics"
        const val EXTRA_UNITS = "units"
        const val EXTRA_PROGRAM_ID = "program_id"
        const val EXTRA_EPOCH_DAY = "epoch_day"
        const val EXTRA_SESSION_ID = "session_id"
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var scheduler: SessionScheduler? = null
    private lateinit var sessionRepo: SessionRepo
    private lateinit var progressRepo: ProgramProgressRepo
    private lateinit var settingsRepo: SettingsRepo
    private lateinit var notificationManager: NotificationManager
    @Volatile
    private var isInitialized = false
    private var debugShowNotifSubtext: Boolean = BuildConfig.DEBUG
    private var startedAtEpochMs: Long? = null
    private var elapsedMs: Long = 0L
    private var tickerJob: Job? = null
    private var lastState: SessionState? = null
    private var lastUiBits: SessionNotifications.SessionUiBits? = null
    private var inForeground = false

    private data class StartRequest(val intent: Intent, val startId: Int)

    private val pendingStartRequests = mutableListOf<StartRequest>()

    @Volatile
    private var hasPendingStartCommand = false

    @Volatile
    private var hasObservedActiveSession = false

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        settingsRepo = SettingsRepo(applicationContext)
        SessionNotifications.ensureChannel(this)
        if (BuildConfig.DEBUG) {
            scope.launch {
                settingsRepo.debugShowNotifSubtext.collectLatest { enabled ->
                    debugShowNotifSubtext = enabled
                    lastState?.let { state ->
                        val uiBits = buildSessionUiBits(state, startedAtEpochMs)
                        lastUiBits = uiBits
                        if (uiBits != null && inForeground) {
                            notificationManager.notify(
                                NOTIFICATION_ID,
                                SessionNotifications.build(this@SessionService, uiBits),
                            )
                        }
                    }
                }
            }
        } else {
            debugShowNotifSubtext = false
        }
        scope.launch {
            scheduler = Singleton.getSessionScheduler(applicationContext)
            sessionRepo = SessionRepo(applicationContext)
            progressRepo = ProgramProgressRepo.getInstance(applicationContext)
            isInitialized = true
            drainPendingStartRequests()
            scheduler?.state
                ?.collectLatest { state ->
                    updateNotification(state)
                }
            scheduler?.state?.value?.takeIf { it.active }?.let { updateNotification(it) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val scheduler = scheduler
        when (intent?.action) {
            ACTION_START -> handleStart(intent, startId)
            NotifActions.ACTION_STOP -> {
                if (scheduler != null && matchesSessionIntent(intent)) {
                    scheduler.stop()
                }
                onSessionCompleted()
            }
            NotifActions.ACTION_PAUSE -> if (scheduler != null && matchesSessionIntent(intent)) scheduler.pause()
            NotifActions.ACTION_RESUME -> if (scheduler != null && matchesSessionIntent(intent)) scheduler.resume()
            ACTION_OBSERVE, null -> {
                // no-op: observation happens via state collector
            }
        }
        return START_NOT_STICKY
    }

    private fun <T : Parcelable> Intent.getParcelableArrayList(key: String, clazz: Class<T>): ArrayList<T>? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableArrayListExtra(key, clazz)
        } else {
            @Suppress("DEPRECATION")
            getParcelableArrayListExtra(key)
        }
    }

    private fun handleStart(intent: Intent, startId: Int) {
        val currentScheduler = scheduler
        val currentState = currentScheduler?.state?.value
        if (currentState?.active == true) {
            hasPendingStartCommand = false
            hasObservedActiveSession = true
            startedAtEpochMs = computeStartedAt(currentState)
            elapsedMs = currentState.elapsedSec.coerceAtLeast(0) * 1000L
            val uiBits = buildSessionUiBits(currentState, startedAtEpochMs)
            if (uiBits != null) {
                lastUiBits = uiBits
                startForegroundNotification(uiBits)
            }
            ensureTicker()
            return
        }

        hasPendingStartCommand = true
        hasObservedActiveSession = false
        if (startedAtEpochMs == null) {
            startedAtEpochMs = System.currentTimeMillis()
        }
        elapsedMs = 0L
        val startingUiBits = buildStartingUiBits()
        lastUiBits = startingUiBits
        startForegroundNotification(startingUiBits)

        if (!isInitialized || currentScheduler == null) {
            synchronized(pendingStartRequests) {
                pendingStartRequests += StartRequest(Intent(intent), startId)
            }
            return
        }

        beginSession(currentScheduler, intent, startId)
    }

    private fun beginSession(scheduler: SessionScheduler, intent: Intent, startId: Int) {
        scope.launch {
            val segments: List<Segment> = intent.getParcelableArrayList(EXTRA_SEGMENTS, Segment::class.java) ?: emptyList()
            val playableSegments = segments.filter { it.seconds > 0 }
            if (playableSegments.isEmpty()) {
                hasPendingStartCommand = false
                withContext(Dispatchers.Main) { stopSelfResult(startId) }
                return@launch
            }

            val preChange = intent.getIntExtra(EXTRA_PRECHANGE_SEC, 10)
            val voiceOn = intent.getBooleanExtra(EXTRA_VOICE, true)
            val beepOn = intent.getBooleanExtra(EXTRA_BEEP, true)
            val hapticsOn = intent.getBooleanExtra(EXTRA_HAPTICS, false)
            val unitsName = intent.getStringExtra(EXTRA_UNITS)
            val programId = intent.getStringExtra(EXTRA_PROGRAM_ID)
            val epochDay = if (intent.hasExtra(EXTRA_EPOCH_DAY)) intent.getLongExtra(EXTRA_EPOCH_DAY, 0L) else null

            val units = unitsName?.let { runCatching { Units.valueOf(it) }.getOrNull() } ?: Units.MPH

            scheduler.setVoiceEnabled(voiceOn)
            scheduler.setBeepsEnabled(beepOn)
            scheduler.setHapticsEnabled(hapticsOn)
            hasPendingStartCommand = false
            scheduler.start(playableSegments, units, preChange) { startMs, endMs, elapsedSec, aborted ->
                val sessionId = scheduler.state.value.sessionId ?: return@start
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    withContext(Dispatchers.IO + NonCancellable) {
                        logSession(sessionId, programId, startMs, endMs, playableSegments, elapsedSec, aborted)
                        if (!aborted && programId != null && epochDay != null) {
                            progressRepo.markDone(programId, epochDay)
                        }
                    }
                }
            }
        }
    }

    private suspend fun drainPendingStartRequests() {
        val requests = synchronized(pendingStartRequests) {
            val copy = pendingStartRequests.toList()
            pendingStartRequests.clear()
            copy
        }
        requests.forEach { request ->
            withContext(Dispatchers.Main) {
                handleStart(request.intent, request.startId)
            }
        }
    }

    private suspend fun logSession(
        sessionId: String,
        programId: String?,
        startMs: Long,
        endMs: Long,
        segments: List<Segment>,
        elapsedSec: Int,
        aborted: Boolean
    ) {
        val realized = sessionRepo.realizedSegments(segments, elapsedSec)
        val log = SessionLog(
            id = sessionId,
            programId = programId,
            startMillis = startMs,
            endMillis = endMs,
            totalSeconds = elapsedSec,
            segments = realized,
            aborted = aborted
        )
        sessionRepo.append(log)
    }

    override fun onDestroy() {
        super.onDestroy()
        tickerJob?.cancel()
        tickerJob = null
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildStartingUiBits(): SessionNotifications.SessionUiBits {
        return SessionNotifications.SessionUiBits(
            startedAtEpochMs = startedAtEpochMs,
            elapsedMs = elapsedMs,
            title = getString(R.string.app_name),
            subtitle = getString(R.string.session_starting_up),
            publicSubtitle = getString(R.string.session_notification_public_ready),
            pauseOrResumeAction = null,
            stopAction = NotifActions.stop(this, null),
            debugSegmentId = "-",
            showDebugSubtext = false,
            isActive = true,
            isPaused = false,
        )
    }

    private fun startForegroundNotification(uiBits: SessionNotifications.SessionUiBits) {
        val notification = SessionNotifications.build(this, uiBits)
        startForegroundCompat(notification)
        inForeground = true
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(state: SessionState) {
        lastState = state
        if (state.active) {
            hasObservedActiveSession = true
            val startedAt = computeStartedAt(state)
            elapsedMs = state.elapsedSec.coerceAtLeast(0) * 1000L
            val uiBits = buildSessionUiBits(state, startedAt)
            lastUiBits = uiBits
            if (uiBits != null) {
                if (!inForeground) {
                    startForegroundNotification(uiBits)
                } else {
                    notificationManager.notify(
                        NOTIFICATION_ID,
                        SessionNotifications.build(this, uiBits),
                    )
                }
            }
            ensureTicker()
        } else {
            tickerJob?.cancel()
            tickerJob = null
            lastUiBits = null
            startedAtEpochMs = null
            elapsedMs = state.elapsedSec.coerceAtLeast(0) * 1000L
            if (hasPendingStartCommand) {
                val uiBits = buildStartingUiBits()
                lastUiBits = uiBits
                if (!inForeground) {
                    startForegroundNotification(uiBits)
                } else {
                    notificationManager.notify(
                        NOTIFICATION_ID,
                        SessionNotifications.build(this, uiBits),
                    )
                }
                return
            }

            if (hasObservedActiveSession) {
                hasObservedActiveSession = false
                onSessionCompleted()
            }
        }
    }

    private fun ensureTicker() {
        if (tickerJob?.isActive == true) return
        if (lastState?.active != true) return
        tickerJob = scope.launch {
            while (true) {
                delay(1000)
                val state = lastState ?: break
                if (!state.active) break
                if (!state.isPaused) {
                    elapsedMs += 1000
                }
                val uiBits = buildSessionUiBits(state, startedAtEpochMs)
                lastUiBits = uiBits
                if (uiBits != null && inForeground) {
                    notificationManager.notify(
                        NOTIFICATION_ID,
                        SessionNotifications.build(this@SessionService, uiBits),
                    )
                }
            }
        }
    }

    private fun computeStartedAt(state: SessionState): Long? {
        if (!state.active) {
            startedAtEpochMs = null
            return null
        }
        val persisted = state.sessionStartTime.takeIf { it > 0L }
        val resolved = when {
            persisted != null -> persisted
            startedAtEpochMs != null -> startedAtEpochMs
            else -> System.currentTimeMillis() - state.elapsedSec.coerceAtLeast(0) * 1000L
        }
        startedAtEpochMs = resolved
        return resolved
    }

    private fun buildSessionUiBits(
        state: SessionState,
        startedAt: Long?,
    ): SessionNotifications.SessionUiBits? {
        val title = buildTitle(state)
        val subtitle = buildSubtitle(state)
        val publicSubtitle = buildPublicSubtitle(state)
        val pauseResumeAction = when {
            !state.active -> null
            state.isPaused -> NotifActions.resume(this, state.sessionId)
            else -> NotifActions.pause(this, state.sessionId)
        }
        val stopAction = if (state.active) NotifActions.stop(this, state.sessionId) else null
        val debugId = buildDebugSegmentId(state)
        val showDebug = debugShowNotifSubtext && state.active
        return SessionNotifications.SessionUiBits(
            startedAtEpochMs = startedAt,
            elapsedMs = elapsedMs.coerceAtLeast(0L),
            title = title,
            subtitle = subtitle,
            publicSubtitle = publicSubtitle,
            pauseOrResumeAction = pauseResumeAction,
            stopAction = stopAction,
            debugSegmentId = debugId,
            showDebugSubtext = showDebug,
            isActive = state.active,
            isPaused = state.isPaused,
        )
    }

    private fun buildTitle(state: SessionState): String {
        val segmentLabel = state.currentSegmentLabel
        val baseTitle = if (state.active && !segmentLabel.isNullOrBlank()) {
            segmentLabel
        } else {
            getString(R.string.app_name)
        }
        return if (state.active && state.isPaused) {
            "$baseTitle (${getString(R.string.session_status_paused)})"
        } else {
            baseTitle
        }
    }

    private fun buildSubtitle(state: SessionState): String {
        if (!state.active) {
            return getString(R.string.session_ready)
        }
        val progressSummary = buildProgressSummary(state)
        val nextSummary = buildNextChangeSummary(state)
        val timeline = if (state.isPaused) {
            listOfNotNull(progressSummary, nextSummary).joinToString(" • ")
        } else {
            progressSummary
        }
        val status = buildSpeedSummary(state)
        return listOfNotNull(timeline, status.takeIf { it.isNotBlank() })
            .filter { it.isNotBlank() }
            .joinToString(" • ")
    }

    private fun buildPublicSubtitle(state: SessionState): String {
        if (!state.active) {
            return getString(R.string.session_notification_public_ready)
        }
        return if (state.isPaused) {
            getString(R.string.session_notification_public_paused)
        } else {
            getString(R.string.session_notification_public_active)
        }
    }

    private fun buildDebugSegmentId(state: SessionState): String {
        if (!state.active || state.segments.isEmpty()) return "-"
        val total = state.segments.size
        val current = state.currentSegment.coerceAtLeast(0)
        val safeIndex = current.coerceIn(0, total - 1)
        return "${safeIndex + 1}/$total"
    }

    private fun buildProgressSummary(state: SessionState): String {
        val elapsed = state.elapsedSec.coerceAtLeast(0)
        val total = state.totalDurationSec.coerceAtLeast(0)
        val remaining = if (total > 0) {
            (total - elapsed).coerceAtLeast(0)
        } else {
            state.remainingSec.coerceAtLeast(0)
        }
        return if (remaining > 0 && (total > 0 || state.remainingSec > 0)) {
            getString(
                R.string.session_notification_elapsed_remaining,
                formatDuration(elapsed),
                formatDuration(remaining)
            )
        } else {
            getString(R.string.session_notification_elapsed_only, formatDuration(elapsed))
        }
    }

    private fun buildNextChangeSummary(state: SessionState): String? {
        val upcomingSpeed = state.upcomingSpeed ?: return null
        val secondsUntil = state.nextChangeInSec.coerceAtLeast(0)
        if (secondsUntil <= 0 && !state.isPaused) return null
        val formattedSpeed = formatSpeed(upcomingSpeed, state.units)
        val formattedTime = formatDuration(secondsUntil)
        return getString(R.string.session_notification_next_speed, formattedSpeed, formattedTime)
    }

    private fun buildSpeedSummary(state: SessionState): String {
        if (!state.active) return getString(R.string.session_status_ready)
        val parts = mutableListOf<String>()
        if (state.isPaused) {
            parts += getString(R.string.session_status_paused)
        } else {
            parts += getString(R.string.session_status_running)
        }
        if (state.speed > 0) {
            parts += "Speed ${formatSpeed(state.speed, state.units)}"
            runCatching { formatPace(state.speed, state.units) }.getOrNull()?.let { parts += "Pace $it" }
        }
        return parts.joinToString(" • ")
    }

    private fun onSessionCompleted() {
        tickerJob?.cancel()
        tickerJob = null
        hasObservedActiveSession = false
        if (inForeground) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(Service.STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            inForeground = false
        }
        notificationManager.cancel(NOTIFICATION_ID)
        hasPendingStartCommand = false
        stopSelf()
    }

    private fun matchesSessionIntent(intent: Intent?): Boolean {
        val currentSessionId = scheduler?.state?.value?.sessionId
        val requestedId = intent?.getStringExtra(EXTRA_SESSION_ID)
        return requestedId == null || requestedId == currentSessionId
    }
}

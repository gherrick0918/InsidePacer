package app.insidepacer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import android.os.Parcelable
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media.app.NotificationCompat.MediaStyle
import app.insidepacer.R
import app.insidepacer.core.formatDuration
import app.insidepacer.core.formatPace
import app.insidepacer.core.formatSpeed
import app.insidepacer.data.ProgramProgressRepo
import app.insidepacer.data.SessionRepo
import app.insidepacer.data.Units
import app.insidepacer.di.Singleton
import app.insidepacer.domain.Segment
import app.insidepacer.domain.SessionLog
import app.insidepacer.domain.SessionState
import app.insidepacer.engine.SessionScheduler
import app.insidepacer.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SessionService : Service() {
    companion object {
        const val CHANNEL_ID = "insidepacer_sessions_v3"
        const val NOTIFICATION_ID = 42

        const val ACTION_START = "app.insidepacer.action.START"
        const val ACTION_STOP = "app.insidepacer.action.STOP"
        const val ACTION_PAUSE = "app.insidepacer.action.PAUSE"
        const val ACTION_RESUME = "app.insidepacer.action.RESUME"
        const val ACTION_SKIP = "app.insidepacer.action.SKIP"
        const val ACTION_OBSERVE = "app.insidepacer.action.OBSERVE"

        const val EXTRA_SEGMENTS = "segments"
        const val EXTRA_PRECHANGE_SEC = "prechange"
        const val EXTRA_VOICE = "voice"
        const val EXTRA_UNITS = "units"
        const val EXTRA_PROGRAM_ID = "program_id"
        const val EXTRA_EPOCH_DAY = "epoch_day"
        const val EXTRA_SESSION_ID = "session_id"

        private const val REQUEST_PAUSE = 1001
        private const val REQUEST_RESUME = 1002
        private const val REQUEST_STOP = 1003
        private const val REQUEST_SKIP = 1004
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var scheduler: SessionScheduler
    private lateinit var sessionRepo: SessionRepo
    private lateinit var progressRepo: ProgramProgressRepo
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        scheduler = Singleton.getSessionScheduler(applicationContext)
        sessionRepo = SessionRepo(applicationContext)
        progressRepo = ProgramProgressRepo.getInstance(applicationContext)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel()
        scope.launch {
            scheduler.state
                .collectLatest { updateNotification(it) }
        }
        scheduler.state.value.takeIf { it.active }?.let { updateNotification(it) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent, startId)
            ACTION_STOP -> if (matchesSessionIntent(intent)) handleStop()
            ACTION_SKIP -> if (matchesSessionIntent(intent)) scheduler.skip()
            ACTION_PAUSE -> if (matchesSessionIntent(intent)) scheduler.pause()
            ACTION_RESUME -> if (matchesSessionIntent(intent)) scheduler.resume()
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
        if (scheduler.state.value.active) return
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildStartingNotification(),
            foregroundType()
        )

        scope.launch {
            val segments: List<Segment> = intent.getParcelableArrayList(EXTRA_SEGMENTS, Segment::class.java) ?: emptyList()
            val playableSegments = segments.filter { it.seconds > 0 }
            if (playableSegments.isEmpty()) {
                withContext(Dispatchers.Main) { stopSelfResult(startId) }
                return@launch
            }

            val preChange = intent.getIntExtra(EXTRA_PRECHANGE_SEC, 10)
            val voiceOn = intent.getBooleanExtra(EXTRA_VOICE, true)
            val unitsName = intent.getStringExtra(EXTRA_UNITS)
            val programId = intent.getStringExtra(EXTRA_PROGRAM_ID)
            val epochDay = if (intent.hasExtra(EXTRA_EPOCH_DAY)) intent.getLongExtra(EXTRA_EPOCH_DAY, 0L) else null

            val units = unitsName?.let { runCatching { Units.valueOf(it) }.getOrNull() } ?: Units.MPH

            scheduler.setVoiceEnabled(voiceOn)
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

    private fun handleStop() {
        scheduler.stop()
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
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureChannel() {
        if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "InsidePacer sessions",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active InsidePacer session"
                setSound(null, null)
                enableVibration(false)
                lightColor = Color.BLUE
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun actionIntent(action: String, sessionId: String?, requestCode: Int): PendingIntent {
        val intent = Intent(this, SessionBroadcastReceiver::class.java).setAction(action)
        if (sessionId != null) {
            intent.putExtra(EXTRA_SESSION_ID, sessionId)
        }
        return PendingIntent.getBroadcast(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildStartingNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.session_starting_up))
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(true)
            .build()
    }

    private fun buildNotification(state: SessionState): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(contentIntent())
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setOngoing(state.active)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setSilent(true)

        val segmentLabel = state.currentSegmentLabel
        val baseTitle = if (state.active && !segmentLabel.isNullOrBlank()) {
            segmentLabel
        } else {
            getString(R.string.app_name)
        }
        val title = if (state.active && state.isPaused) {
            "$baseTitle (${getString(R.string.session_status_paused)})"
        } else {
            baseTitle
        }
        builder.setContentTitle(title)

        val (contentText, subText) = buildContentText(state)
        builder.setContentText(contentText)
        subText?.let { builder.setSubText(it) }

        builder.applyTimeInfo(state)
        builder.applyProgress(state)

        val actions = mutableListOf<NotificationCompat.Action>()
        val sessionId = state.sessionId

        if (state.active) {
            val pauseResumeAction = if (!state.isPaused) {
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_media_pause,
                    getString(R.string.session_action_pause),
                    actionIntent(ACTION_PAUSE, sessionId, REQUEST_PAUSE)
                ).build()
            } else {
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_media_play,
                    getString(R.string.session_action_resume),
                    actionIntent(ACTION_RESUME, sessionId, REQUEST_RESUME)
                ).build()
            }
            actions += pauseResumeAction

            actions += NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_next,
                getString(R.string.session_action_skip),
                actionIntent(ACTION_SKIP, sessionId, REQUEST_SKIP)
            ).build()
            actions += NotificationCompat.Action.Builder(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.session_action_stop),
                actionIntent(ACTION_STOP, sessionId, REQUEST_STOP)
            ).build()
        } else {
            actions += NotificationCompat.Action.Builder(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.session_action_stop),
                actionIntent(ACTION_STOP, sessionId, REQUEST_STOP)
            ).build()
        }

        actions.forEach { builder.addAction(it) }

        val compactIndices = when {
            actions.size >= 3 -> intArrayOf(0, 1, 2)
            actions.size == 2 -> intArrayOf(0, 1)
            else -> intArrayOf(0)
        }
        builder.setStyle(MediaStyle().setShowActionsInCompactView(*compactIndices))

        return builder.build()
    }

    private fun updateNotification(state: SessionState) {
        if (state.active) {
            val notification = buildNotification(state)
            notificationManager.notify(NOTIFICATION_ID, notification)
        } else {
            notificationManager.cancel(NOTIFICATION_ID)
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun foregroundType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0

    private fun buildContentText(state: SessionState): Pair<String, String?> {
        if (!state.active) {
            val readyText = getString(R.string.session_ready)
            return readyText to getString(R.string.session_status_ready)
        }

        val progressSummary = buildProgressSummary(state)
        val nextSummary = buildNextChangeSummary(state)
        val content = when {
            state.isPaused -> listOfNotNull(progressSummary, nextSummary).joinToString(" • ")
            else -> progressSummary
        }
        val status = buildSpeedSummary(state)
        return content to status
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

    private fun NotificationCompat.Builder.applyTimeInfo(state: SessionState): NotificationCompat.Builder {
        if (state.active && state.sessionStartTime > 0L) {
            setShowWhen(true)
            setWhen(state.sessionStartTime)
            setUsesChronometer(true)
        } else {
            setShowWhen(false)
            setUsesChronometer(false)
        }
        return this
    }

    private fun NotificationCompat.Builder.applyProgress(state: SessionState): NotificationCompat.Builder {
        val total = state.totalDurationSec.coerceAtLeast(0)
        val elapsed = state.elapsedSec.coerceAtLeast(0)
        if (state.active && total > 0) {
            setProgress(total, elapsed.coerceAtMost(total), false)
        } else {
            setProgress(0, 0, false)
        }
        return this
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

    private fun matchesSessionIntent(intent: Intent?): Boolean {
        val currentSessionId = scheduler.state.value.sessionId
        val requestedId = intent?.getStringExtra(EXTRA_SESSION_ID)
        return requestedId == null || requestedId == currentSessionId
    }
}

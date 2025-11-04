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
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media.app.NotificationCompat.MediaStyle
import app.insidepacer.R
import app.insidepacer.data.ProgramProgressRepo
import app.insidepacer.data.SessionRepo
import app.insidepacer.data.Units
import app.insidepacer.di.Singleton
import app.insidepacer.domain.Segment
import app.insidepacer.domain.SessionLog
import app.insidepacer.domain.SessionState
import app.insidepacer.engine.SessionScheduler
import app.insidepacer.ui.MainActivity
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class SessionService : Service() {
    companion object {
        const val CHANNEL_ID = "insidepacer_sessions"
        const val NOTIFICATION_ID = 42

        const val ACTION_START = "app.insidepacer.action.START"
        const val ACTION_STOP = "app.insidepacer.action.STOP"
        const val ACTION_PAUSE = "app.insidepacer.action.PAUSE"
        const val ACTION_RESUME = "app.insidepacer.action.RESUME"
        const val ACTION_SKIP = "app.insidepacer.action.SKIP"
        const val ACTION_OBSERVE = "app.insidepacer.action.OBSERVE"

        const val EXTRA_SEGMENTS_JSON = "segments_json"
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

    private val json = Json { ignoreUnknownKeys = true }

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
            ACTION_START -> handleStart(intent)
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

    private fun handleStart(intent: Intent) {
        if (scheduler.state.value.active) return
        val segJson = intent.getStringExtra(EXTRA_SEGMENTS_JSON) ?: return
        val segments = runCatching {
            json.decodeFromString(ListSerializer(Segment.serializer()), segJson)
        }.getOrDefault(emptyList())
        if (segments.isEmpty()) return

        val preChange = intent.getIntExtra(EXTRA_PRECHANGE_SEC, 10)
        val voiceOn = intent.getBooleanExtra(EXTRA_VOICE, true)
        val unitsName = intent.getStringExtra(EXTRA_UNITS)
        val programId = intent.getStringExtra(EXTRA_PROGRAM_ID)
        val epochDay = if (intent.hasExtra(EXTRA_EPOCH_DAY)) intent.getLongExtra(EXTRA_EPOCH_DAY, 0L) else null

        val units = unitsName?.let { runCatching { Units.valueOf(it) }.getOrNull() } ?: Units.MPH

        scheduler.setVoiceEnabled(voiceOn)
        val state = scheduler.state.value
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(state),
            foregroundType()
        )
        scheduler.start(segments, units, preChange) { startMs, endMs, elapsedSec, aborted ->
            val sessionId = scheduler.state.value.sessionId ?: return@start
            scope.launch {
                logSession(sessionId, programId, startMs, endMs, segments, elapsedSec, aborted)
                if (!aborted) {
                    if (programId != null && epochDay != null) {
                        progressRepo.markDone(programId, epochDay)
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
        withContext(Dispatchers.IO) {
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
                enableLights(false)
                enableVibration(false)
                lightColor = Color.BLUE
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
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

    private fun buildNotification(state: SessionState): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(contentIntent())
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setOngoing(state.active)
            .setAutoCancel(false)

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

        if (!state.active) {
            builder.setContentText(getString(R.string.session_ready))
            builder.setSubText(getString(R.string.session_status_ready))
        } else {
            val elapsed = state.elapsedSec.coerceAtLeast(0)
            val total = state.totalDurationSec.coerceAtLeast(0)
            val remaining = if (total > 0) (total - elapsed).coerceAtLeast(0) else state.remainingSec.coerceAtLeast(0)
            val parts = mutableListOf("Elapsed ${formatDuration(elapsed)}")
            if (total > 0 || remaining > 0) {
                parts += "Remaining ${formatDuration(remaining)}"
            }
            builder.setContentText(parts.joinToString(" • "))
            builder.setSubText(buildSpeedSummary(state))
        }

        if (state.sessionStartTime > 0L) {
            builder.setShowWhen(true)
            builder.setWhen(state.sessionStartTime)
            builder.setUsesChronometer(true)
        } else {
            builder.setShowWhen(false)
        }

        val total = state.totalDurationSec.coerceAtLeast(0)
        val elapsed = state.elapsedSec.coerceAtLeast(0)
        if (state.active && total > 0) {
            builder.setProgress(total, elapsed.coerceAtMost(total), false)
        } else {
            builder.setProgress(0, 0, false)
        }

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
        val notification = buildNotification(state)
        if (state.active) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                foregroundType()
            )
        } else {
            notificationManager.notify(NOTIFICATION_ID, notification)
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
            stopSelf()
        }
    }

    private fun foregroundType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH else 0

    private fun buildSpeedSummary(state: SessionState): String {
        if (!state.active) return getString(R.string.session_status_ready)
        val parts = mutableListOf<String>()
        if (state.isPaused) {
            parts += getString(R.string.session_status_paused)
        } else {
            parts += getString(R.string.session_status_running)
        }
        if (state.speed > 0) {
            parts += "Speed ${formatSpeed(state.units, state.speed)}"
            formatPace(state.units, state.speed)?.let { parts += "Pace $it" }
        }
        return parts.joinToString(" • ")
    }

    private fun formatSpeed(units: Units, speed: Double): String {
        val unitLabel = when (units) {
            Units.MPH -> "mph"
            Units.KMH -> "km/h"
        }
        return String.format(Locale.getDefault(), "%.1f %s", speed, unitLabel)
    }

    private fun formatPace(units: Units, speed: Double): String? {
        if (speed <= 0) return null
        val minutesTotal = 60.0 / speed
        var minutes = minutesTotal.toInt()
        var seconds = ((minutesTotal - minutes) * 60).roundToInt()
        if (seconds == 60) {
            seconds = 0
            minutes += 1
        }
        val label = when (units) {
            Units.MPH -> "min/mi"
            Units.KMH -> "min/km"
        }
        return String.format(Locale.getDefault(), "%d:%02d %s", minutes, seconds, label)
    }

    private fun formatDuration(seconds: Int): String {
        if (seconds <= 0) return "0:00"
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format(Locale.getDefault(), "%d:%02d", minutes, secs)
        }
    }

    private fun matchesSessionIntent(intent: Intent?): Boolean {
        val currentSessionId = scheduler.state.value.sessionId
        val requestedId = intent?.getStringExtra(EXTRA_SESSION_ID)
        return requestedId == null || requestedId == currentSessionId
    }
}

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
import app.insidepacer.R
import app.insidepacer.data.ProgramProgressRepo
import app.insidepacer.data.SessionRepo
import app.insidepacer.data.Units
import app.insidepacer.domain.Segment
import app.insidepacer.domain.SessionLog
import app.insidepacer.domain.SessionState
import app.insidepacer.di.Singleton
import app.insidepacer.engine.SessionScheduler
import app.insidepacer.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

class SessionService : Service() {
    companion object {
        const val CHANNEL_ID = "session_channel"
        const val NOTIFICATION_ID = 42

        const val ACTION_START = "app.insidepacer.action.START"
        const val ACTION_STOP = "app.insidepacer.action.STOP"
        const val ACTION_PAUSE = "app.insidepacer.action.PAUSE"
        const val ACTION_RESUME = "app.insidepacer.action.RESUME"
        const val ACTION_OBSERVE = "app.insidepacer.action.OBSERVE"

        const val EXTRA_SEGMENTS_JSON = "segments_json"
        const val EXTRA_PRECHANGE_SEC = "prechange"
        const val EXTRA_VOICE = "voice"
        const val EXTRA_UNITS = "units"
        const val EXTRA_PROGRAM_ID = "program_id"
        const val EXTRA_EPOCH_DAY = "epoch_day"
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var scheduler: SessionScheduler
    private lateinit var sessionRepo: SessionRepo
    private lateinit var progressRepo: ProgramProgressRepo

    private val json = Json { ignoreUnknownKeys = true }

    private var currentSegments: List<Segment> = emptyList()
    private var currentProgramId: String? = null
    private var currentEpochDay: Long? = null
    private var currentUnits: Units = Units.MPH

    override fun onCreate() {
        super.onCreate()
        scheduler = Singleton.getSessionScheduler(applicationContext)
        sessionRepo = SessionRepo(applicationContext)
        progressRepo = ProgramProgressRepo.getInstance(applicationContext)
        ensureChannel()
        scope.launch {
            scheduler.state.collectLatest { updateNotification(it) }
        }
        scheduler.state.value.takeIf { it.active }?.let { updateNotification(it) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_STOP -> handleStop()
            ACTION_PAUSE -> scheduler.pause()
            ACTION_RESUME -> scheduler.resume()
            ACTION_OBSERVE, null -> {
                // no-op: observation happens via state collector
            }
        }
        return START_NOT_STICKY
    }

    private fun handleStart(intent: Intent) {
        scheduler.stop()
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

        scheduler.setVoiceEnabled(voiceOn)
        currentSegments = segments
        currentProgramId = programId
        currentEpochDay = epochDay
        currentUnits = unitsName?.let { runCatching { Units.valueOf(it) }.getOrNull() } ?: Units.MPH

        val state = scheduler.state.value
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(state),
            foregroundType()
        )
        scheduler.start(segments, currentUnits, preChange) { startMs, endMs, elapsedSec, aborted ->
            scope.launch {
                logSession(startMs, endMs, elapsedSec, aborted)
                if (!aborted) {
                    markDone()
                }
                ServiceCompat.stopForeground(this@SessionService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                clearNotification()
                stopSelf()
            }
        }
    }

    private fun handleStop() {
        scheduler.stop()
        clearNotification()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun logSession(startMs: Long, endMs: Long, elapsedSec: Int, aborted: Boolean) {
        withContext(Dispatchers.IO) {
            val realized = sessionRepo.realizedSegments(currentSegments, elapsedSec)
            val log = SessionLog(
                id = UUID.randomUUID().toString(),
                programId = currentProgramId,
                startMillis = startMs,
                endMillis = endMs,
                totalSeconds = elapsedSec,
                segments = realized,
                aborted = aborted
            )
            sessionRepo.append(log)
        }
    }

    private fun markDone() {
        val pid = currentProgramId ?: return
        val epoch = currentEpochDay ?: return
        progressRepo.markDone(pid, epoch)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "InsidePacer session",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Active walking session"
                    enableLights(false)
                    enableVibration(false)
                    lightColor = Color.BLUE
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
            )
        }
    }

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun actionIntent(action: String): PendingIntent = PendingIntent.getService(
        this,
        action.hashCode(),
        Intent(this, SessionService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun buildNotification(state: SessionState): Notification {
        val isActive = state.active
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(
                when {
                    !isActive -> "InsidePacer"
                    state.isPaused -> "InsidePacer – Paused"
                    else -> "InsidePacer – In session"
                }
            )
            .setContentText(primaryNotificationText(state))
            .setStyle(NotificationCompat.BigTextStyle().bigText(detailNotificationText(state)))
            .setContentIntent(contentIntent())
            .setOngoing(isActive)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .addAction(
                if (isActive) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isActive) "Pause" else "Resume",
                actionIntent(if (isActive) ACTION_PAUSE else ACTION_RESUME)
            )
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", actionIntent(ACTION_STOP))
        return builder.build()
    }

    private fun updateNotification(state: SessionState) {
        if (state.active) {
            val notification = buildNotification(state)
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                foregroundType()
            )
        } else {
            clearNotification()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun foregroundType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH else 0

    private fun primaryNotificationText(state: SessionState): String {
        if (!state.active) return "Ready"

        val segmentSummary = state.segments.takeIf { it.isNotEmpty() }?.let {
            val current = (state.currentSegment + 1).coerceIn(1, it.size)
            "Segment $current/${it.size}"
        }

        val elapsed = formatDuration(state.elapsedSec)
        val speed = if (state.isPaused) {
            "Paused at ${formatSpeed(state.speed)}"
        } else {
            "Speed ${formatSpeed(state.speed)}"
        }

        return buildString {
            append(speed)
            if (segmentSummary != null) {
                append(" • ")
                append(segmentSummary)
            }
            append(" • Elapsed $elapsed")
        }
    }

    private fun detailNotificationText(state: SessionState): CharSequence {
        if (!state.active) return "Ready to start your next session."

        val primary = primaryNotificationText(state)
        val nextChange = when {
            state.isPaused -> "Resume to continue"
            state.upcomingSpeed != null -> "Next ${formatSpeed(state.upcomingSpeed!!)} in ${formatDuration(state.nextChangeInSec)}"
            state.nextChangeInSec > 0 -> "Hold for ${formatDuration(state.nextChangeInSec)}"
            else -> "Finishing up"
        }

        return "$primary\n$nextChange"
    }

    private fun formatSpeed(speed: Double): String {
        val unit = when (currentUnits) {
            Units.MPH -> "mph"
            Units.KMH -> "km/h"
        }
        return String.format("%.1f %s", speed, unit)
    }

    private fun formatDuration(seconds: Int): String {
        if (seconds <= 0) return "0:00"
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format("%d:%02d", minutes, secs)
        }
    }

    private fun clearNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFICATION_ID)
    }
}

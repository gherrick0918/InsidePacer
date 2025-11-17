package app.insidepacer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import app.insidepacer.BuildConfig
import app.insidepacer.R
import app.insidepacer.ui.MainActivity

object SessionNotifications {
    const val CHANNEL_ID = "session_foreground"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.app_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.session_ready)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(channel)
    }

    data class SessionUiBits(
        val startedAtEpochMs: Long?,
        val elapsedMs: Long,
        val title: String,
        val subtitle: String,
        val publicSubtitle: String,
        val pauseOrResumeAction: NotificationCompat.Action?,
        val stopAction: NotificationCompat.Action?,
        val debugSegmentId: String,
        val showDebugSubtext: Boolean,
        val isActive: Boolean,
        val isPaused: Boolean,
    )

    fun build(context: Context, state: SessionUiBits): Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val whenMs = (state.startedAtEpochMs ?: (System.currentTimeMillis() - state.elapsedMs))
            .coerceAtLeast(0L)
        val showChronometer = state.startedAtEpochMs != null && state.isActive && !state.isPaused

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_walk)
            .setContentTitle(state.title)
            .setContentText(state.subtitle)
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(state.isActive)
            .setAutoCancel(false)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setLocalOnly(false)
            .setShowWhen(true)
            .setUsesChronometer(showChronometer)
            .setWhen(whenMs)

        val publicNotification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_walk)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(state.publicSubtitle)
            .setShowWhen(true)
            .setUsesChronometer(showChronometer)
            .setWhen(whenMs)
            .setOngoing(state.isActive)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .build()

        builder.setPublicVersion(publicNotification)

        if (!showChronometer) {
            builder.setUsesChronometer(false)
        }

        val actions = buildList {
            state.pauseOrResumeAction?.let { add(it) }
            state.stopAction?.let { add(it) }
        }
        actions.forEach { builder.addAction(it) }
        if (actions.isNotEmpty()) {
            val compactIndices = when (actions.size) {
                1 -> intArrayOf(0)
                else -> intArrayOf(0, 1)
            }
            builder.setStyle(MediaStyle().setShowActionsInCompactView(*compactIndices))
        }

        if (BuildConfig.DEBUG && state.showDebugSubtext && state.debugSegmentId.isNotBlank()) {
            builder.setSubText("seg=${state.debugSegmentId} • svc=${state.elapsedMs / 1000}s")
        }

        return builder.build()
    }
}

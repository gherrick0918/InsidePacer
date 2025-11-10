package app.insidepacer.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import app.insidepacer.R

object NotifActions {
    const val ACTION_PAUSE = "app.insidepacer.action.PAUSE"
    const val ACTION_RESUME = "app.insidepacer.action.RESUME"
    const val ACTION_STOP = "app.insidepacer.action.STOP"

    fun pause(context: Context, sessionId: String?): NotificationCompat.Action {
        return NotificationCompat.Action.Builder(
            android.R.drawable.ic_media_pause,
            context.getString(R.string.session_action_pause),
            pendingIntent(context, ACTION_PAUSE, sessionId),
        ).build()
    }

    fun resume(context: Context, sessionId: String?): NotificationCompat.Action {
        return NotificationCompat.Action.Builder(
            android.R.drawable.ic_media_play,
            context.getString(R.string.session_action_resume),
            pendingIntent(context, ACTION_RESUME, sessionId),
        ).build()
    }

    fun stop(context: Context, sessionId: String?): NotificationCompat.Action {
        return NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_close_clear_cancel,
            context.getString(R.string.session_action_stop),
            pendingIntent(context, ACTION_STOP, sessionId),
        ).build()
    }

    private fun pendingIntent(context: Context, action: String, sessionId: String?): PendingIntent {
        val intent = Intent(context, SessionBroadcastReceiver::class.java).setAction(action)
        if (sessionId != null) {
            intent.putExtra(SessionService.EXTRA_SESSION_ID, sessionId)
        }
        val requestCode = action.hashCode()
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

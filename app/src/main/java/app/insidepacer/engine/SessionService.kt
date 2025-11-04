package app.insidepacer.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import app.insidepacer.R
import app.insidepacer.di.Singleton
import app.insidepacer.ui.MainActivity
import app.insidepacer.ui.utils.formatSeconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SessionService : Service() {
    private lateinit var notificationManager: NotificationManager
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    companion object {
        const val CHANNEL_ID = "session_channel"
        const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        scope.launch {
            Singleton.sessionScheduler.state.collectLatest { state ->
                if (state.active) {
                    val totalDuration = state.segments.sumOf { it.seconds }
                    val remaining = totalDuration - state.elapsedSec

                    val notification = NotificationCompat.Builder(this@SessionService, CHANNEL_ID)
                        .setContentTitle("Inside Pacer Session")
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentIntent(
                            PendingIntent.getActivity(
                                this@SessionService,
                                0,
                                Intent(this@SessionService, MainActivity::class.java),
                                PendingIntent.FLAG_IMMUTABLE
                            )
                        )

                    if (state.isPaused) {
                        notification.setContentText("Session is paused")
                        notification.addAction(0, "Resume", pendingIntent("app.insidepacer.RESUME"))
                    } else {
                        notification.setContentText("Speed: ${state.speed}, next change in ${formatSeconds(state.nextChangeInSec)}")
                        notification.setStyle(NotificationCompat.BigTextStyle()
                            .bigText("Elapsed: ${formatSeconds(state.elapsedSec)} / ${formatSeconds(totalDuration)}\nTime left: ${formatSeconds(remaining)}"))
                        notification.addAction(0, "Pause", pendingIntent("app.insidepacer.PAUSE"))
                    }
                    notification.addAction(0, "Stop", pendingIntent("app.insidepacer.STOP"))

                    startForeground(NOTIFICATION_ID, notification.build())
                } else {
                    ServiceCompat.stopForeground(this@SessionService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun pendingIntent(action: String) = PendingIntent.getBroadcast(
        this, 
        action.hashCode(), 
        Intent(action), 
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Session Notifications",
                NotificationManager.IMPORTANCE_DEFAULT // Changed to default to make it pop up
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}

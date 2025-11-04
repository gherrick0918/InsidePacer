package app.insidepacer.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.insidepacer.di.Singleton

class SessionBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val scheduler = Singleton.getSessionScheduler(context)
        val targetSessionId = intent.getStringExtra(SessionService.EXTRA_SESSION_ID)
        val currentSessionId = scheduler.state.value.sessionId
        if (targetSessionId != null && targetSessionId != currentSessionId) {
            return
        }
        when (action) {
            SessionService.ACTION_PAUSE -> scheduler.pause()
            SessionService.ACTION_RESUME -> scheduler.resume()
            SessionService.ACTION_STOP -> scheduler.stop()
            SessionService.ACTION_SKIP -> scheduler.skip()
        }
    }
}

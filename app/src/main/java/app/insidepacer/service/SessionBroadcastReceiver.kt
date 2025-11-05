package app.insidepacer.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.insidepacer.di.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SessionBroadcastReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        scope.launch {
            try {
                val action = intent?.action ?: return@launch
                val scheduler = Singleton.getSessionScheduler(context)
                val targetSessionId = intent.getStringExtra(SessionService.EXTRA_SESSION_ID)
                val currentSessionId = scheduler.state.value.sessionId
                if (targetSessionId != null && targetSessionId != currentSessionId) {
                    return@launch
                }
                when (action) {
                    SessionService.ACTION_PAUSE -> scheduler.pause()
                    SessionService.ACTION_RESUME -> scheduler.resume()
                    SessionService.ACTION_STOP -> scheduler.stop()
                    SessionService.ACTION_SKIP -> scheduler.skip()
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

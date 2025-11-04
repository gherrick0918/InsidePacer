package app.insidepacer.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.insidepacer.di.Singleton

class SessionBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "app.insidepacer.PAUSE" -> Singleton.sessionScheduler.pause()
            "app.insidepacer.RESUME" -> Singleton.sessionScheduler.resume()
            "app.insidepacer.STOP" -> Singleton.sessionScheduler.stop()
        }
    }
}
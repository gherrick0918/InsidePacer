package app.insidepacer.di

import android.content.Context
import app.insidepacer.engine.CuePlayer
import app.insidepacer.engine.SessionScheduler

object Singleton {
    @Volatile
    private var _sessionScheduler: SessionScheduler? = null

    fun getSessionScheduler(context: Context): SessionScheduler {
        val existing = _sessionScheduler
        if (existing != null) return existing

        return synchronized(this) {
            val cached = _sessionScheduler
            if (cached != null) {
                cached
            } else {
                val appContext = context.applicationContext
                SessionScheduler(appContext, CuePlayer(appContext)).also {
                    _sessionScheduler = it
                }
            }
        }
    }
}

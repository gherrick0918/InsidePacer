package app.insidepacer.di

import android.content.Context
import app.insidepacer.engine.CueDuckingManager
import app.insidepacer.engine.CuePlayer
import app.insidepacer.engine.SessionScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object Singleton {
    @Volatile
    private var _sessionScheduler: SessionScheduler? = null
    private val mutex = Mutex()

    suspend fun getSessionScheduler(context: Context): SessionScheduler {
        _sessionScheduler?.let { return it }
        return mutex.withLock {
            _sessionScheduler?.let { return@withLock it }
            val newScheduler = withContext(Dispatchers.IO) {
                val appContext = context.applicationContext
                val duckingManager = CueDuckingManager(appContext)
                SessionScheduler(appContext, CuePlayer(appContext, duckingManager))
            }
            _sessionScheduler = newScheduler
            newScheduler
        }
    }
}

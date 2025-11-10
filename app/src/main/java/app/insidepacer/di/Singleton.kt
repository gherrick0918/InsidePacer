package app.insidepacer.di

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import app.insidepacer.audio.CueDuckingManager
import app.insidepacer.engine.CuePlayer
import app.insidepacer.engine.SessionScheduler
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object Singleton {
    @Volatile
    private var _sessionScheduler: SessionScheduler? = null
    private val mutex = Mutex()

    private val looperThread by lazy {
        HandlerThread("scheduler-init").apply { start() }
    }
    private val looperDispatcher by lazy {
        Handler(looperThread.looper).asCoroutineDispatcher()
    }

    suspend fun getSessionScheduler(context: Context): SessionScheduler {
        _sessionScheduler?.let { return it }
        return mutex.withLock {
            _sessionScheduler?.let { return@withLock it }
            val newScheduler = withContext(looperDispatcher) {
                val appContext = context.applicationContext
                val duckingManager = CueDuckingManager(appContext)
                val cuePlayer = CuePlayer(appContext, duckingManager)
                SessionScheduler(appContext, cuePlayer)
            }
            _sessionScheduler = newScheduler
            newScheduler
        }
    }
}

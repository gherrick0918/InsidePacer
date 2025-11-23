package app.insidepacer.di

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import app.insidepacer.analytics.AnalyticsService
import app.insidepacer.audio.CueDuckingManager
import app.insidepacer.data.db.AppDatabase
import app.insidepacer.data.db.DatabaseMigrationHelper
import app.insidepacer.engine.CuePlayer
import app.insidepacer.engine.SessionScheduler
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object Singleton {
    @Volatile
    private var _sessionScheduler: SessionScheduler? = null
    
    @Volatile
    private var _analyticsService: AnalyticsService? = null
    
    @Volatile
    private var _database: AppDatabase? = null
    
    private val mutex = Mutex()
    private val dbMutex = Mutex()

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
    
    fun getAnalyticsService(context: Context): AnalyticsService {
        _analyticsService?.let { return it }
        synchronized(this) {
            _analyticsService?.let { return it }
            val newService = AnalyticsService(context.applicationContext)
            _analyticsService = newService
            return newService
        }
    }
    
    suspend fun getDatabase(context: Context): AppDatabase {
        _database?.let { return it }
        return dbMutex.withLock {
            _database?.let { return@withLock it }
            val appContext = context.applicationContext
            val db = AppDatabase.getInstance(appContext)
            
            // Perform one-time migration from JSON to Room
            val migrationHelper = DatabaseMigrationHelper(appContext)
            val migrationSuccess = migrationHelper.migrateIfNeeded(db)
            if (migrationSuccess) {
                // Archive JSON files after successful migration
                migrationHelper.archiveJsonFiles()
            }
            
            _database = db
            db
        }
    }
}

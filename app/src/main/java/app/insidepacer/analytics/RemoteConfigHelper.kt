package app.insidepacer.analytics

import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import kotlinx.coroutines.tasks.await

/**
 * Helper for Firebase Remote Config to enable feature flags and A/B testing.
 * Allows remote configuration of app features without app updates.
 */
class RemoteConfigHelper {
    
    private val remoteConfig = Firebase.remoteConfig
    
    init {
        // Set in-app defaults
        remoteConfig.setDefaultsAsync(getDefaults())
        
        // Configure fetch settings
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 3600 // 1 hour in production
        }
        remoteConfig.setConfigSettingsAsync(configSettings)
    }
    
    /**
     * Fetch and activate remote config
     */
    suspend fun fetchAndActivate(): Boolean {
        return try {
            remoteConfig.fetchAndActivate().await()
        } catch (e: Exception) {
            CrashlyticsHelper.recordError("RemoteConfig fetch", e)
            false
        }
    }
    
    /**
     * Get default configuration values
     */
    private fun getDefaults(): Map<String, Any> = mapOf(
        // Feature flags
        KEY_ENABLE_VOICE_PROMPTS to true,
        KEY_ENABLE_HAPTICS to false,
        KEY_ENABLE_BEEPS to true,
        KEY_ENABLE_HEALTH_CONNECT to true,
        KEY_ENABLE_BACKUP to true,
        KEY_ENABLE_PROGRAM_GENERATOR to true,
        KEY_ENABLE_REMOTE_ANALYTICS to true,
        
        // Configuration values
        KEY_DEFAULT_PRE_CHANGE_SECONDS to 10,
        KEY_MAX_TEMPLATES to 50,
        KEY_MAX_PROGRAMS to 20,
        KEY_SESSION_MIN_MINUTES to 5,
        KEY_SESSION_MAX_MINUTES to 180,
        
        // UI configurations
        KEY_SHOW_ADVANCED_SETTINGS to false,
        KEY_ENABLE_EXPERIMENTAL_FEATURES to false
    )
    
    // Feature flags
    fun isVoicePromptsEnabled(): Boolean = 
        remoteConfig.getBoolean(KEY_ENABLE_VOICE_PROMPTS)
    
    fun isHapticsEnabled(): Boolean = 
        remoteConfig.getBoolean(KEY_ENABLE_HAPTICS)
    
    fun isBeepsEnabled(): Boolean = 
        remoteConfig.getBoolean(KEY_ENABLE_BEEPS)
    
    fun isHealthConnectEnabled(): Boolean = 
        remoteConfig.getBoolean(KEY_ENABLE_HEALTH_CONNECT)
    
    fun isBackupEnabled(): Boolean = 
        remoteConfig.getBoolean(KEY_ENABLE_BACKUP)
    
    fun isProgramGeneratorEnabled(): Boolean = 
        remoteConfig.getBoolean(KEY_ENABLE_PROGRAM_GENERATOR)
    
    fun isRemoteAnalyticsEnabled(): Boolean = 
        remoteConfig.getBoolean(KEY_ENABLE_REMOTE_ANALYTICS)
    
    // Configuration values
    fun getDefaultPreChangeSeconds(): Long = 
        remoteConfig.getLong(KEY_DEFAULT_PRE_CHANGE_SECONDS)
    
    fun getMaxTemplates(): Long = 
        remoteConfig.getLong(KEY_MAX_TEMPLATES)
    
    fun getMaxPrograms(): Long = 
        remoteConfig.getLong(KEY_MAX_PROGRAMS)
    
    fun getSessionMinMinutes(): Long = 
        remoteConfig.getLong(KEY_SESSION_MIN_MINUTES)
    
    fun getSessionMaxMinutes(): Long = 
        remoteConfig.getLong(KEY_SESSION_MAX_MINUTES)
    
    // UI configurations
    fun shouldShowAdvancedSettings(): Boolean = 
        remoteConfig.getBoolean(KEY_SHOW_ADVANCED_SETTINGS)
    
    fun areExperimentalFeaturesEnabled(): Boolean = 
        remoteConfig.getBoolean(KEY_ENABLE_EXPERIMENTAL_FEATURES)
    
    companion object {
        // Feature flag keys
        private const val KEY_ENABLE_VOICE_PROMPTS = "enable_voice_prompts"
        private const val KEY_ENABLE_HAPTICS = "enable_haptics"
        private const val KEY_ENABLE_BEEPS = "enable_beeps"
        private const val KEY_ENABLE_HEALTH_CONNECT = "enable_health_connect"
        private const val KEY_ENABLE_BACKUP = "enable_backup"
        private const val KEY_ENABLE_PROGRAM_GENERATOR = "enable_program_generator"
        private const val KEY_ENABLE_REMOTE_ANALYTICS = "enable_remote_analytics"
        
        // Configuration keys
        private const val KEY_DEFAULT_PRE_CHANGE_SECONDS = "default_pre_change_seconds"
        private const val KEY_MAX_TEMPLATES = "max_templates"
        private const val KEY_MAX_PROGRAMS = "max_programs"
        private const val KEY_SESSION_MIN_MINUTES = "session_min_minutes"
        private const val KEY_SESSION_MAX_MINUTES = "session_max_minutes"
        
        // UI configuration keys
        private const val KEY_SHOW_ADVANCED_SETTINGS = "show_advanced_settings"
        private const val KEY_ENABLE_EXPERIMENTAL_FEATURES = "enable_experimental_features"
    }
}

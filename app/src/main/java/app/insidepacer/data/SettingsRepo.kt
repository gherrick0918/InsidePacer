package app.insidepacer.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.insidepacer.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val Context.settingsDataStore by preferencesDataStore("settings")

enum class Units { MPH, KMH }

@Serializable
data class Biometrics(val heightCm: Int? = null, val weightKg: Double? = null, val age: Int? = null)

class SettingsRepo(private val context: Context) {
    private val SPEEDS = stringSetPreferencesKey("speeds")
    private val UNITS = stringPreferencesKey("units")
    private val BIOM = stringPreferencesKey("biometrics")
    private val json = Json { ignoreUnknownKeys = true }

    private val KEY_VOICE_ENABLED   = booleanPreferencesKey("voice_enabled")
    private val KEY_PRECHANGE_SEC   = intPreferencesKey("prechange_seconds")
    private val KEY_BEEP_ENABLED    = booleanPreferencesKey("beep_enabled")
    private val KEY_HAPTIC_ENABLED  = booleanPreferencesKey("haptic_enabled")
    private val KEY_DEBUG_NOTIF_SUBTEXT = booleanPreferencesKey("debug_notif_subtext")
    private val KEY_HEALTH_CONNECT_ENABLED = booleanPreferencesKey("health_connect_enabled")

    // Defaults
    val voiceEnabled = context.settingsDataStore.data.map { it[KEY_VOICE_ENABLED] ?: true }
    val preChangeSeconds = context.settingsDataStore.data.map { it[KEY_PRECHANGE_SEC] ?: 10 }
    val beepEnabled = context.settingsDataStore.data.map { it[KEY_BEEP_ENABLED] ?: true }
    val hapticsEnabled = context.settingsDataStore.data.map { it[KEY_HAPTIC_ENABLED] ?: false }
    val debugShowNotifSubtext = context.settingsDataStore.data.map {
        it[KEY_DEBUG_NOTIF_SUBTEXT] ?: BuildConfig.DEBUG
    }
    val healthConnectEnabled = context.settingsDataStore.data.map {
        it[KEY_HEALTH_CONNECT_ENABLED] ?: false
    }

    suspend fun setVoiceEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_VOICE_ENABLED] = enabled }
    }

    suspend fun setPreChangeSeconds(sec: Int) {
        context.settingsDataStore.edit { it[KEY_PRECHANGE_SEC] = sec.coerceIn(3, 30) }
    }

    suspend fun setBeepEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_BEEP_ENABLED] = enabled }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_HAPTIC_ENABLED] = enabled }
    }

    suspend fun setDebugShowNotifSubtext(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_DEBUG_NOTIF_SUBTEXT] = enabled }
    }

    suspend fun setHealthConnectEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_HEALTH_CONNECT_ENABLED] = enabled }
    }


    val units: Flow<Units> = context.settingsDataStore.data.map { p ->
        when (p[UNITS]) { "KMH" -> Units.KMH; else -> Units.MPH }
    }

    val speeds: Flow<List<Double>> = context.settingsDataStore.data.map { p ->
        p[SPEEDS]?.mapNotNull { it.toDoubleOrNull() }?.distinct()?.sorted() ?: emptyList()
    }

    val biometrics: Flow<Biometrics?> = context.settingsDataStore.data.map { p ->
        p[BIOM]?.let { json.decodeFromString(Biometrics.serializer(), it) }
    }

    suspend fun setUnits(u: Units) = context.settingsDataStore.edit { it[UNITS] = u.name }
    suspend fun setSpeeds(list: List<Double>) = context.settingsDataStore.edit { it[SPEEDS] = list.map { d -> d.toString() }.toSet() }
    suspend fun setBiometrics(b: Biometrics?) = context.settingsDataStore.edit { preferences ->
        if (b == null) {
            preferences.remove(BIOM)
        } else {
            preferences[BIOM] = json.encodeToString(Biometrics.serializer(), b)
        }
    }
}
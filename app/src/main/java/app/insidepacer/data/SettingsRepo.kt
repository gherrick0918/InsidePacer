package app.insidepacer.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
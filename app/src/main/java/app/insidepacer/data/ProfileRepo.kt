package app.insidepacer.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.insidepacer.domain.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.profileDataStore by preferencesDataStore("user_profile")
private val KEY_PROFILE = stringPreferencesKey("profile")

class ProfileRepo(private val ctx: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    val profile: Flow<UserProfile> = ctx.profileDataStore.data.map { prefs ->
        prefs[KEY_PROFILE]?.let { stored ->
            runCatching { json.decodeFromString(UserProfile.serializer(), stored) }
                .getOrElse { UserProfile() }
        } ?: UserProfile()
    }

    suspend fun save(profile: UserProfile) {
        ctx.profileDataStore.edit { prefs ->
            prefs[KEY_PROFILE] = json.encodeToString(UserProfile.serializer(), profile)
        }
    }
}

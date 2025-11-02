package app.insidepacer.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.programPrefs by preferencesDataStore("program_prefs")
private val KEY_ACTIVE = stringPreferencesKey("active_program_id")

class ProgramPrefs(private val ctx: Context) {
    val activeProgramId = ctx.programPrefs.data.map { it[KEY_ACTIVE] }
    suspend fun setActiveProgramId(id: String?) {
        ctx.programPrefs.edit { p ->
            if (id == null) p.remove(KEY_ACTIVE) else p[KEY_ACTIVE] = id
        }
    }
}

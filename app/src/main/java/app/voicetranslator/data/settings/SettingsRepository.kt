package app.voicetranslator.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val ttsSpeed: Float = 1.0f,
    val autoPlay: Boolean = true,
    val baseUrlOverride: String = "",
)

class SettingsRepository(private val context: Context) {

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            ttsSpeed = prefs[KEY_TTS_SPEED] ?: 1.0f,
            autoPlay = prefs[KEY_AUTO_PLAY] ?: true,
            baseUrlOverride = prefs[KEY_BASE_URL] ?: "",
        )
    }

    suspend fun setTtsSpeed(value: Float) {
        context.dataStore.edit { it[KEY_TTS_SPEED] = value.coerceIn(0.5f, 2.0f) }
    }

    suspend fun setAutoPlay(value: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_PLAY] = value }
    }

    suspend fun setBaseUrlOverride(value: String) {
        context.dataStore.edit { it[KEY_BASE_URL] = value.trim() }
    }

    private companion object {
        val KEY_TTS_SPEED = floatPreferencesKey("tts_speed")
        val KEY_AUTO_PLAY = booleanPreferencesKey("auto_play")
        val KEY_BASE_URL = stringPreferencesKey("base_url")
    }
}

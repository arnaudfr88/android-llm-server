package de.cyclenerd.android.llm.server.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import de.cyclenerd.android.llm.server.utils.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Repository for app preferences.
 *
 * Manages application-wide settings like auto-start behavior.
 *
 * @param context Application context
 */
class AppPreferences(
    private val context: Context,
) {
    private val dataStore: DataStore<Preferences> = context.appPreferencesDataStore

    /**
     * Whether to auto-start the server when the app is launched.
     */
    val autoStartServer: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[AUTO_START_SERVER] ?: false
    }

    suspend fun setAutoStartServer(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[AUTO_START_SERVER] = enabled
        }
        Logger.i(TAG, "Auto-start server: $enabled")
    }

    suspend fun getAutoStartServer(): Boolean = autoStartServer.first()

    companion object {
        private const val TAG = "AppPreferences"
        private val AUTO_START_SERVER = booleanPreferencesKey("auto_start_server")
    }
}

private val Context.appPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "app_preferences",
)

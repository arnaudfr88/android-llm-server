package de.cyclenerd.android.llm.server.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.cyclenerd.android.llm.server.data.AppPreferences
import de.cyclenerd.android.llm.server.utils.Logger
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the Settings screen.
 *
 * Manages app preferences for auto-start behavior.
 */
class SettingsViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val preferences = AppPreferences(application)

    val autoStartServer: StateFlow<Boolean> =
        preferences.autoStartServer.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false,
        )

    fun setAutoStartServer(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setAutoStartServer(enabled)
            Logger.i(TAG, "Auto-start server preference changed: $enabled")
        }
    }

    companion object {
        private const val TAG = "SettingsViewModel"
    }
}

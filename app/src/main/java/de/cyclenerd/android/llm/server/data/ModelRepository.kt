package de.cyclenerd.android.llm.server.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.cyclenerd.android.llm.server.utils.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Repository for persisting model metadata.
 *
 * This stores information about downloaded models that we can't get
 * from the filesystem alone (download URL, user notes, etc.).
 *
 * Why persist metadata separately?
 * - Filesystem only tells us: name, size, date modified
 * - We want to track: download URL, checksum, user preferences
 * - Database/DataStore lets us query models efficiently
 *
 * Why DataStore over Room?
 * - DataStore: Key-value storage, simple, lightweight
 * - Room: SQLite database, powerful queries, more complex
 *
 * We use DataStore because:
 * - We have ~10 models max (small dataset)
 * - Simple queries (get all, get by name, set active)
 * - No complex relationships
 * - Room would be overkill
 *
 * DataStore vs SharedPreferences?
 * - DataStore is type-safe (Preferences uses typed keys)
 * - DataStore is async (coroutines, no UI blocking)
 * - DataStore handles errors better (no silent failures)
 * - SharedPreferences is deprecated for new code
 *
 * Active Model Concept:
 * Only one model can be loaded at a time because:
 * - Models are 2-4GB in RAM
 * - Most phones have 4-8GB total RAM
 * - Loading multiple would cause out-of-memory
 *
 * @param context Application context
 */
class ModelRepository(
    private val context: Context,
) {
    private val dataStore: DataStore<Preferences> = context.modelsDataStore

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    /**
     * Saves model metadata.
     *
     * This is called after a successful download to persist info
     * about the model.
     *
     * @param info Model information to save
     */
    suspend fun saveModelInfo(info: ModelInfo) {
        dataStore.edit { prefs ->
            val key = stringPreferencesKey(info.fileName)
            val json = json.encodeToString(info)
            prefs[key] = json
        }
        Logger.i(TAG, "Saved model info: ${info.fileName}")
    }

    /**
     * Gets metadata for a specific model.
     *
     * @param fileName Model filename
     * @return ModelInfo if found, null otherwise
     */
    suspend fun getModelInfo(fileName: String): ModelInfo? {
        val key = stringPreferencesKey(fileName)
        val json = dataStore.data.first()[key] ?: return null

        return try {
            this.json.decodeFromString<ModelInfo>(json)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to decode model info: $fileName", e)
            null
        }
    }

    /**
     * Gets all saved models as a Flow.
     *
     * Why Flow?
     * Flow is a reactive stream that automatically updates when
     * data changes. UI can collect this Flow and will automatically
     * re-compose when models are added/removed.
     *
     * Example:
     * ```kotlin
     * val models = modelRepository.getAllModels()
     * LaunchedEffect(Unit) {
     *     models.collect { modelList ->
     *         // UI updates automatically
     *     }
     * }
     * ```
     *
     * @return Flow of model list
     */
    fun getAllModels(): Flow<List<ModelInfo>> =
        dataStore.data.map { prefs ->
            prefs
                .asMap()
                .values
                .filterIsInstance<String>()
                .mapNotNull { jsonString ->
                    try {
                        json.decodeFromString<ModelInfo>(jsonString)
                    } catch (e: Exception) {
                        Logger.e(TAG, "Failed to decode model", e)
                        null
                    }
                }.sortedByDescending { it.downloadDate } // Newest first
        }

    /**
     * Sets which model is currently active.
     *
     * Active model = the model loaded into LiteRT engine.
     *
     * This updates the isActive flag for all models:
     * - Sets new active model to true
     * - Sets all others to false
     *
     * Why only one active?
     * Models are huge (2-4GB RAM). We can only load one at a time.
     *
     * @param fileName Model to activate
     */
    suspend fun setActiveModel(fileName: String) {
        dataStore.edit { prefs ->
            // First, deactivate all models
            prefs.asMap().keys.forEach { key ->
                if (key.name.endsWith(".litertlm")) {
                    val jsonString = prefs[key] as? String
                    if (jsonString != null) {
                        try {
                            val model = json.decodeFromString<ModelInfo>(jsonString)
                            val updated = model.copy(isActive = false)
                            @Suppress("UNCHECKED_CAST")
                            prefs[key as Preferences.Key<String>] = json.encodeToString(updated)
                        } catch (e: Exception) {
                            Logger.e(TAG, "Failed to update model", e)
                        }
                    }
                }
            }

            // Then activate the selected model
            val key = stringPreferencesKey(fileName)
            val jsonString = prefs[key]
            if (jsonString != null) {
                try {
                    val model = json.decodeFromString<ModelInfo>(jsonString)
                    val updated = model.copy(isActive = true)
                    prefs[key] = json.encodeToString(updated)
                } catch (e: Exception) {
                    Logger.e(TAG, "Failed to activate model", e)
                }
            }
        }
        Logger.i(TAG, "Set active model: $fileName")
    }

    /**
     * Gets the currently active model.
     *
     * @return Active ModelInfo, or null if none set
     */
    suspend fun getActiveModel(): ModelInfo? = getAllModels().first().find { it.isActive }

    /**
     * Deletes model metadata.
     *
     * This should be called when a model file is deleted.
     *
     * @param fileName Model filename
     */
    suspend fun deleteModelInfo(fileName: String) {
        dataStore.edit { prefs ->
            val key = stringPreferencesKey(fileName)
            prefs.remove(key)
        }
        Logger.i(TAG, "Deleted model info: $fileName")
    }

    /**
     * Clears all model metadata.
     *
     * Useful for testing or resetting the app.
     */
    suspend fun clearAll() {
        dataStore.edit { prefs ->
            prefs.clear()
        }
        Logger.i(TAG, "Cleared all model info")
    }

    companion object {
        private const val TAG = "ModelRepository"
    }
}

/**
 * Extension property for DataStore.
 *
 * This creates a singleton DataStore instance for the app.
 * The delegate ensures it's only created once.
 */
private val Context.modelsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "models",
)

package de.cyclenerd.android.llm.server.ui

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.cyclenerd.android.llm.server.data.ModelDownloader
import de.cyclenerd.android.llm.server.data.ModelInfo
import de.cyclenerd.android.llm.server.data.ModelRepository
import de.cyclenerd.android.llm.server.data.ModelStorage
import de.cyclenerd.android.llm.server.data.RecommendedModels
import de.cyclenerd.android.llm.server.utils.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/**
 * ViewModel for the Model Management screen.
 *
 * Manages the state of:
 * - Downloaded models
 * - Recommended models
 * - Download progress
 * - Model deletion
 *
 * @param application Application context
 */
class ModelManagementViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val modelStorage = ModelStorage(application.applicationContext)
    private val modelDownloader = ModelDownloader(modelStorage)
    private val modelRepository = ModelRepository(application.applicationContext)

    private val _downloadedModels = MutableStateFlow<List<ModelInfo>>(emptyList())
    val downloadedModels: StateFlow<List<ModelInfo>> = _downloadedModels.asStateFlow()

    private val _recommendedModels = MutableStateFlow<List<RecommendedModels.RecommendedModel>>(emptyList())
    val recommendedModels: StateFlow<List<RecommendedModels.RecommendedModel>> = _recommendedModels.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _deviceRamGb = MutableStateFlow(getDeviceRamGb())
    val deviceRamGb: StateFlow<Int> = _deviceRamGb.asStateFlow()

    init {
        loadModels()
        loadRecommendedModels()
    }

    private fun getDeviceRamGb(): Int {
        val activityManager = getApplication<Application>().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        // Convert total RAM from bytes to GB with proper rounding
        val totalRamBytes = memoryInfo.totalMem
        val totalRamMb = totalRamBytes / (1024.0 * 1024.0)
        val totalRamGb = totalRamBytes / (1024.0 * 1024.0 * 1024.0)
        val totalRamGbRounded = kotlin.math.round(totalRamGb).toInt()
        Logger.i(
            TAG,
            "Device total RAM: $totalRamBytes bytes / ${String.format(
                Locale.US,
                "%.2f",
                totalRamMb,
            )} MB / ${String.format(Locale.US, "%.2f", totalRamGb)} GB (rounded to $totalRamGbRounded GB)",
        )
        return totalRamGbRounded
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private fun loadModels() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val models = modelStorage.listModels()

                // Merge with repository data to get isActive flags
                val mergedModels =
                    models.map { fileModel ->
                        val repoModel = modelRepository.getModelInfo(fileModel.fileName)
                        repoModel ?: fileModel
                    }

                // Auto-select first model if only one exists and no model is active
                if (mergedModels.size == 1 && mergedModels.none { it.isActive }) {
                    Logger.i(TAG, "Auto-selecting single model: ${mergedModels.first().displayName}")
                    modelRepository.setActiveModel(mergedModels.first().fileName)
                    // Reload to get updated active state
                    val reloadedModels =
                        modelStorage.listModels().map { fileModel ->
                            val repoModel = modelRepository.getModelInfo(fileModel.fileName)
                            repoModel ?: fileModel
                        }
                    _downloadedModels.value = reloadedModels
                } else {
                    _downloadedModels.value = mergedModels
                }

                Logger.i(TAG, "Loaded ${mergedModels.size} models")
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to load models", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun loadRecommendedModels() {
        viewModelScope.launch {
            try {
                val recommended = RecommendedModels.getRecommendedModels()
                _recommendedModels.value = recommended
                Logger.i(TAG, "Loaded ${recommended.size} recommended models")
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to load recommended models", e)
            }
        }
    }

    fun downloadModel(model: RecommendedModels.RecommendedModel) {
        viewModelScope.launch {
            try {
                Logger.i(TAG, "Starting download: ${model.name}")

                val result =
                    modelDownloader.downloadModel(
                        url = model.downloadUrl,
                        fileName = model.fileName,
                        expectedSha256 = model.sha256,
                        onProgress = { downloaded: Long, total: Long ->
                            val progress = if (total > 0) downloaded.toFloat() / total else 0f
                            _downloadProgress.value = _downloadProgress.value + (model.fileName to progress)
                        },
                    )

                result.onSuccess { file: File ->
                    // Save model info to repository
                    val modelInfo =
                        ModelInfo(
                            fileName = model.fileName,
                            displayName = model.name,
                            fileSizeBytes = file.length(),
                            downloadDate = System.currentTimeMillis(),
                            sourceUrl = model.downloadUrl,
                            isActive = false,
                        )
                    modelRepository.saveModelInfo(modelInfo)

                    // Clear progress and reload models
                    _downloadProgress.value = _downloadProgress.value - model.fileName
                    loadModels()

                    Logger.i(TAG, "Download complete: ${model.name}")
                }

                result.onFailure { e: Throwable ->
                    Logger.e(TAG, "Download failed: ${model.name}", e)
                    _downloadProgress.value = _downloadProgress.value - model.fileName
                    _errorMessage.value = "Download failed: ${e.message ?: "Unknown error"}"
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Download failed: ${model.name}", e)
                _downloadProgress.value = _downloadProgress.value - model.fileName
                _errorMessage.value = "Download failed: ${e.message ?: "Unknown error"}"
            }
        }
    }

    fun deleteModel(modelInfo: ModelInfo) {
        viewModelScope.launch {
            try {
                Logger.i(TAG, "Deleting model: ${modelInfo.fileName}")
                modelStorage.deleteModel(modelInfo.fileName)
                modelRepository.deleteModelInfo(modelInfo.fileName)
                loadModels()
                Logger.i(TAG, "Model deleted: ${modelInfo.fileName}")
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to delete model: ${modelInfo.fileName}", e)
                _errorMessage.value = "Failed to delete model: ${e.message ?: "Unknown error"}"
            }
        }
    }

    /**
     * Activates a model for use by the server.
     *
     * Sets the selected model as active and deactivates all others.
     * Only one model can be active at a time.
     *
     * @param modelInfo The model to activate
     */
    fun activateModel(modelInfo: ModelInfo) {
        viewModelScope.launch {
            try {
                Logger.i(TAG, "Activating model: ${modelInfo.fileName}")
                modelRepository.setActiveModel(modelInfo.fileName)
                loadModels()
                Logger.i(TAG, "Model activated: ${modelInfo.fileName}")
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to activate model: ${modelInfo.fileName}", e)
                _errorMessage.value = "Failed to activate model: ${e.message ?: "Unknown error"}"
            }
        }
    }

    companion object {
        private const val TAG = "ModelManagementViewModel"
    }
}

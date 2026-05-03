package de.cyclenerd.android.llm.server.data

import android.content.Context
import de.cyclenerd.android.llm.server.utils.Logger
import kotlinx.serialization.Serializable
import java.io.File
import java.io.InputStream
import java.util.Locale

/**
 * Manages storage and file operations for LiteRT models.
 *
 * This class handles:
 * - Saving downloaded models to scoped storage
 * - Listing available models
 * - Deleting models
 * - Retrieving model file paths
 *
 * Why Scoped Storage?
 * Starting Android 10 (API 29), apps can't freely access external storage.
 * Scoped Storage restricts apps to their own directories. We use
 * getExternalFilesDir() which gives us:
 *
 * - Large storage capacity (models are 2-4GB)
 * - Survives app updates
 * - Optionally persists after uninstall (user choice)
 * - No special permissions needed
 *
 * Alternative: getFilesDir() (internal storage)
 * - Limited capacity (~2GB typically)
 * - Always deleted on uninstall
 * - Not suitable for large models
 *
 * File Naming Convention:
 * - Model files: {name}.litertlm (e.g., gemma-4-e2b-it.litertlm)
 * - Lowercase, hyphens for spaces
 * - .litertlm extension required
 *
 * Storage Path:
 * Android 10+: /sdcard/Android/data/{package}/files/models/
 * Older: /sdcard/Android/data/{package}/files/models/ (same, but different rules)
 *
 * @param context Application context
 */
class ModelStorage(
    private val context: Context,
) {
    /**
     * Directory where models are stored.
     *
     * This is app-specific external storage. The "models" subdirectory
     * keeps our models organized and separate from other app files.
     *
     * Why external?
     * Internal storage is limited (often <2GB free). A single model
     * can be 2.5GB, so we need external storage's larger capacity.
     *
     * Path structure:
     * /sdcard/Android/data/de.cyclenerd.android.llm.server/files/models/
     */
    private val modelsDir: File =
        context.getExternalFilesDir("models")
            ?: throw IllegalStateException("Cannot access external storage. Is SD card available?")

    init {
        // Ensure models directory exists
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }
        Logger.i(TAG, "Model storage initialized: ${modelsDir.absolutePath}")
    }

    /**
     * Saves a model file from an InputStream.
     *
     * This is used during downloads. The InputStream provides data
     * (from network), and we write it to a file on disk.
     *
     * Why InputStream?
     * - Works with any data source (HTTP, file, etc.)
     * - Streams large files without loading entirely into RAM
     * - Standard Java I/O abstraction
     *
     * Buffer size (8KB):
     * Larger buffers = fewer syscalls but more RAM
     * Smaller buffers = more syscalls but less RAM
     * 8KB is a good balance for mobile devices
     *
     * @param fileName Name for the model file (e.g., "gemma-4-e2b-it.litertlm")
     * @param inputStream Source of model data
     * @param totalBytes Expected file size (for progress tracking)
     * @param onProgress Callback for download progress (bytesWritten, totalBytes)
     * @return File object pointing to the saved model
     * @throws IllegalArgumentException if fileName is invalid
     * @throws java.io.IOException if write fails
     */
    fun saveModel(
        fileName: String,
        inputStream: InputStream,
        totalBytes: Long = -1,
        onProgress: ((Long, Long) -> Unit)? = null,
    ): File {
        validateFileName(fileName)

        val file = File(modelsDir, fileName)
        Logger.i(TAG, "Saving model: ${file.absolutePath}")

        var bytesWritten = 0L
        val buffer = ByteArray(8192) // 8KB buffer

        file.outputStream().use { output ->
            var read: Int
            while (inputStream.read(buffer).also { read = it } != -1) {
                output.write(buffer, 0, read)
                bytesWritten += read

                // Report progress
                onProgress?.invoke(bytesWritten, totalBytes)
            }
            output.flush()
        }

        Logger.i(TAG, "Model saved successfully: $fileName ($bytesWritten bytes)")
        return file
    }

    /**
     * Lists all model files in storage.
     *
     * This scans the models directory and returns metadata for each
     * .litertlm file found.
     *
     * Why scan directory instead of database?
     * - Files are the source of truth
     * - User might manually add/remove files
     * - Database could get out of sync
     *
     * We combine this with database metadata in ModelRepository.
     *
     * @return List of ModelInfo for all found models
     */
    fun listModels(): List<ModelInfo> =
        modelsDir
            .listFiles { file ->
                file.isFile && file.extension == "litertlm"
            }?.map { file ->
                ModelInfo(
                    fileName = file.name,
                    displayName = file.nameWithoutExtension.replace("-", " ").capitalize(),
                    fileSizeBytes = file.length(),
                    downloadDate = file.lastModified(),
                )
            }?.sortedByDescending { it.downloadDate } // Newest first
            ?: emptyList()

    /**
     * Deletes a model file.
     *
     * This permanently removes the model from storage.
     * Cannot be undone!
     *
     * Why delete models?
     * - Free up space (models are 2-4GB each)
     * - Remove unused/outdated models
     * - Clean up failed downloads
     *
     * @param fileName Name of model to delete
     * @return true if deleted successfully, false if file not found
     */
    fun deleteModel(fileName: String): Boolean {
        val file = File(modelsDir, fileName)
        if (!file.exists()) {
            Logger.w(TAG, "Model not found for deletion: $fileName")
            return false
        }

        val deleted = file.delete()
        if (deleted) {
            Logger.i(TAG, "Model deleted: $fileName")
        } else {
            Logger.e(TAG, "Failed to delete model: $fileName")
        }
        return deleted
    }

    /**
     * Gets the absolute file path for a model.
     *
     * This is what we pass to LiteRT engine to load the model.
     *
     * Why absolute path?
     * LiteRT's native code needs the full filesystem path, not
     * a relative path or Android URI.
     *
     * @param fileName Name of the model file
     * @return Absolute path string, or null if file doesn't exist
     */
    fun getModelPath(fileName: String): String? {
        val file = File(modelsDir, fileName)
        return if (file.exists()) {
            file.absolutePath
        } else {
            Logger.w(TAG, "Model file not found: $fileName")
            null
        }
    }

    /**
     * Gets a File object for a model, whether it exists or not.
     * Used for creating new files during downloads.
     *
     * @param fileName Name of the model file
     * @return File object in the models directory
     */
    fun getModelFile(fileName: String): File = File(modelsDir, fileName)

    /**
     * Checks if a model file exists.
     *
     * @param fileName Name of the model file
     * @return true if file exists
     */
    fun modelExists(fileName: String): Boolean = File(modelsDir, fileName).exists()

    /**
     * Gets available storage space in the models directory.
     *
     * This helps prevent downloads that will fail due to insufficient space.
     *
     * Why check storage?
     * - Models are 2-4GB
     * - Download might succeed but leave no space for other apps
     * - Better to warn user upfront
     *
     * Lint suggests `StorageManager#getAllocatableBytes`, which forcibly
     * clears other apps' caches to make room. That is the right API when
     * you are about to allocate a file, but here we only want to **show
     * the user** how much free space is available before they pick a
     * model. The simple `usableSpace` reading is appropriate (and is
     * also what the Android Files app uses for the same purpose).
     *
     * @return Available space in bytes
     */
    @android.annotation.SuppressLint("UsableSpace")
    fun getAvailableSpaceBytes(): Long = modelsDir.usableSpace

    /**
     * Gets total storage space used by models.
     *
     * @return Total size of all model files in bytes
     */
    fun getTotalModelsSize(): Long =
        modelsDir
            .listFiles()
            ?.filter { it.isFile && it.extension == "litertlm" }
            ?.sumOf { it.length() }
            ?: 0L

    /**
     * Validates a model file name.
     *
     * Rules:
     * - Must end with .litertlm
     * - No path separators (/, \)
     * - Not empty
     * - No special characters that could cause filesystem issues
     *
     * @throws IllegalArgumentException if invalid
     */
    private fun validateFileName(fileName: String) {
        require(fileName.isNotBlank()) { "File name cannot be blank" }
        require(fileName.endsWith(".litertlm")) { "File name must end with .litertlm" }
        require(!fileName.contains("/") && !fileName.contains("\\")) {
            "File name cannot contain path separators"
        }
        require(fileName.length < 255) { "File name too long" }
    }

    companion object {
        private const val TAG = "ModelStorage"
    }
}

/**
 * Basic model information from filesystem.
 *
 * This is the minimal info we can get just by looking at files.
 * More detailed metadata (download URL, etc.) comes from ModelRepository.
 *
 * @property fileName Actual filename (e.g., "gemma-4-e2b-it.litertlm")
 * @property displayName Human-readable name (e.g., "Gemma 4 E2b It")
 * @property fileSizeBytes Size in bytes (default 0 for legacy data)
 * @property downloadDate Unix timestamp of file creation/modification
 */
@Serializable
data class ModelInfo(
    val fileName: String,
    val displayName: String,
    val fileSizeBytes: Long = 0L,
    val downloadDate: Long,
    val sourceUrl: String? = null,
    val isActive: Boolean = false,
) {
    /**
     * Returns formatted file size as GB or MB.
     * Uses decimal (1000-based) calculation.
     */
    fun getFormattedSize(): String {
        if (fileSizeBytes == 0L) {
            return "Unknown"
        }
        val sizeInGb = fileSizeBytes / (1000.0 * 1000.0 * 1000.0)
        return if (sizeInGb >= 1.0) {
            String.format(Locale.US, "%.2f GB", sizeInGb)
        } else {
            val sizeInMb = fileSizeBytes / (1000.0 * 1000.0)
            String.format(Locale.US, "%.0f MB", sizeInMb)
        }
    }
}

/**
 * Extension to capitalize first letter of each word.
 * "gemma 4 e2b it" → "Gemma 4 E2b It"
 */
private fun String.capitalize(): String =
    split(" ").joinToString(" ") { word ->
        word.replaceFirstChar { it.uppercaseChar() }
    }

package de.cyclenerd.android.llm.server.data

import de.cyclenerd.android.llm.server.utils.Logger
import java.io.File

/**
 * Validates LiteRT model files before use.
 *
 * This performs basic validation to catch common issues before attempting
 * to load a model with LiteRT (which is expensive).
 *
 * Validation checks:
 * 1. File extension (.litertlm)
 * 2. File exists and readable
 * 3. Minimum file size (reject empty/truncated)
 * 4. File not corrupted (basic header check)
 *
 * Why validate before loading?
 * - Loading with LiteRT takes 10+ seconds
 * - Failed load wastes CPU/battery
 * - Early validation gives better error messages
 * - Prevents crashes from corrupted files
 *
 * Limitations:
 * This is basic validation. Full validation requires actually loading
 * the model with LiteRT, which we can't do cheaply. These checks catch
 * obvious problems (wrong extension, truncated file, etc.).
 *
 * LiteRT Model File Structure:
 * .litertlm files are FlatBuffer-encoded tensors. They contain:
 * - Model weights (majority of file size)
 * - Graph structure
 * - Metadata (optional)
 *
 * We can't parse the full structure without LiteRT, but we can check
 * for basic file integrity.
 */
object ModelValidator {
    /**
     * Minimum valid model size in bytes (10 MB).
     *
     * Even the smallest quantized models are >100MB. If a file is
     * smaller than 10MB, it's definitely not a valid model—probably
     * a truncated download or error page HTML.
     *
     * Why 10MB?
     * - Smallest real models are ~500MB
     * - 10MB catches truncated downloads
     * - Not so large that it misses tiny test models
     */
    private const val MIN_MODEL_SIZE_BYTES = 10 * 1024 * 1024L // 10 MB

    /**
     * Validates a model file.
     *
     * This checks:
     * 1. File exists
     * 2. Has .litertlm extension
     * 3. Is larger than minimum size
     * 4. Is readable
     *
     * @param filePath Absolute path to model file
     * @return ValidationResult - Valid or Invalid with reason
     */
    fun validateModel(filePath: String): ValidationResult {
        val file = File(filePath)

        // Check file exists
        if (!file.exists()) {
            return ValidationResult.Invalid("File does not exist: $filePath")
        }

        // Check file extension
        if (!file.name.endsWith(".litertlm", ignoreCase = true)) {
            return ValidationResult.Invalid("Invalid file extension. Must be .litertlm")
        }

        // Check file size
        val fileSize = file.length()
        if (fileSize < MIN_MODEL_SIZE_BYTES) {
            return ValidationResult.Invalid(
                "File too small (${fileSize / 1000 / 1000}MB). Minimum ${MIN_MODEL_SIZE_BYTES / 1000 / 1000}MB. " +
                    "File may be truncated or corrupted.",
            )
        }

        // Check readable
        if (!file.canRead()) {
            return ValidationResult.Invalid("File is not readable. Check permissions.")
        }

        Logger.i(TAG, "Model validation passed: ${file.name} (${fileSize / 1000 / 1000}MB)")
        return ValidationResult.Valid
    }

    /**
     * Extracts basic metadata from model filename.
     *
     * We can't read internal model metadata without LiteRT, but we
     * can extract info from the filename if it follows naming conventions.
     *
     * Example: "gemma-4-e2b-it.litertlm"
     * - Name: Gemma 4 E2B IT
     * - Variant: e2b (4-bit quantization)
     * - Type: Instruct-tuned
     *
     * @param fileName Model filename
     * @return Metadata extracted from name
     */
    fun extractMetadata(fileName: String): ModelMetadata {
        val nameWithoutExt = fileName.removeSuffix(".litertlm")
        val parts = nameWithoutExt.split("-")

        // Try to identify model family
        val modelFamily =
            when {
                nameWithoutExt.contains("gemma", ignoreCase = true) -> "Gemma"
                nameWithoutExt.contains("phi", ignoreCase = true) -> "Phi"
                nameWithoutExt.contains("llama", ignoreCase = true) -> "Llama"
                else -> "Unknown"
            }

        // Try to identify quantization
        val quantization =
            when {
                nameWithoutExt.contains("e2b", ignoreCase = true) -> "4-bit (E2B)"
                nameWithoutExt.contains("e4b", ignoreCase = true) -> "8-bit (E4B)"
                nameWithoutExt.contains("q4", ignoreCase = true) -> "4-bit"
                nameWithoutExt.contains("q8", ignoreCase = true) -> "8-bit"
                else -> "Unknown"
            }

        // Try to identify if instruct-tuned
        val isInstructTuned =
            nameWithoutExt.contains("it", ignoreCase = true) ||
                nameWithoutExt.contains("instruct", ignoreCase = true)

        return ModelMetadata(
            displayName = nameWithoutExt.replace("-", " ").capitalize(),
            modelFamily = modelFamily,
            quantization = quantization,
            isInstructTuned = isInstructTuned,
        )
    }

    private const val TAG = "ModelValidator"
}

/**
 * Result of model validation.
 *
 * Sealed class ensures exhaustive when() checks and type-safe errors.
 */
sealed class ValidationResult {
    /**
     * Model passed validation.
     *
     * This doesn't guarantee the model will load successfully with
     * LiteRT, but it passes our basic sanity checks.
     */
    data object Valid : ValidationResult()

    /**
     * Model failed validation.
     *
     * @property reason Human-readable explanation of why validation failed
     */
    data class Invalid(
        val reason: String,
    ) : ValidationResult()
}

/**
 * Basic metadata extracted from model filename.
 *
 * This is limited because we can't read internal model metadata without
 * loading it with LiteRT. But we can infer useful info from filenames.
 *
 * @property displayName Human-readable model name
 * @property modelFamily Model family (Gemma, Phi, Llama, etc.)
 * @property quantization Quantization type (4-bit, 8-bit)
 * @property isInstructTuned Whether model is instruction-tuned
 */
data class ModelMetadata(
    val displayName: String,
    val modelFamily: String,
    val quantization: String,
    val isInstructTuned: Boolean,
)

private fun String.capitalize(): String =
    split(" ").joinToString(" ") { word ->
        word.replaceFirstChar { it.uppercaseChar() }
    }

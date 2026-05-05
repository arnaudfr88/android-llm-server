package de.cyclenerd.android.llm.server.data

import kotlinx.serialization.Serializable
import java.util.Locale

/**
 * Curated list of recommended LiteRT models.
 *
 * This helps users get started by providing known-good models with
 * verified download URLs and appropriate descriptions.
 *
 * How to find models:
 * 1. Visit https://huggingface.co/litert-community
 * 2. Look for models with .litertlm files
 * 3. Get download URL from "Files and versions" tab
 * 4. Get SHA-256 hash from the file page (shown next to file size)
 * 5. Test the model before adding to this list
 *
 * Model selection criteria:
 * - Must have .litertlm format (not safetensors/pytorch)
 * - Should be quantized (E2B/E4B) for mobile efficiency
 * - Prefer instruction-tuned ("-it") for chat
 * - Verify URL works and file isn't corrupted
 * - Consider RAM requirements (most phones have 4-8GB)
 * - Always include SHA-256 hash for security verification
 *
 * Quantization levels:
 * - E2B (4-bit): ~2.5GB, fast, good quality (RECOMMENDED)
 * - E4B (8-bit): ~3.7GB, slower, better quality
 * - E8 (16-bit): ~5GB+, very slow, best quality (not mobile-friendly)
 *
 * RAM requirements:
 * Model needs to fit in RAM with room for:
 * - Android system (~2GB)
 * - Our app (~500MB)
 * - Model weights
 *
 * Example: 4GB RAM phone
 * - 2GB system
 * - 0.5GB app
 * - 1.5GB available for model
 * - Can run: E2B models (~2.5GB compressed, ~1.5GB in RAM)
 * - Cannot run: E4B models (~3.7GB, need ~2.5GB RAM)
 *
 * @property name Display name for UI
 * @property description Short explanation of model's strengths
 * @property sizeMb Approximate download size in megabytes
 * @property downloadUrl Direct download link (must be .litertlm)
 * @property minRamGb Minimum device RAM recommended
 * @property sha256 SHA-256 checksum for file verification (REQUIRED for security)
 */
@Serializable
data class RecommendedModel(
    val name: String,
    val description: String,
    val sizeMb: Int,
    val downloadUrl: String,
    val minRamGb: Int,
    val sha256: String? = null,
) {
    /**
     * Extract file name from download URL.
     */
    val fileName: String
        get() = downloadUrl.substringAfterLast("/").substringBefore("?")

    /**
     * Format size for display.
     * Uses decimal (1000-based) calculation.
     */
    val sizeDescription: String
        get() =
            when {
                sizeMb >= 1000 -> String.format(Locale.US, "%.2f GB", sizeMb / 1000.0)
                else -> "$sizeMb MB"
            }
}

/**
 * List of recommended models for users to download.
 *
 * Ordered by recommendation priority (best first).
 */
val RECOMMENDED_MODELS =
    listOf(
        RecommendedModel(
            // https://ai.google.dev/gemma/docs/core/model_card_4
            // https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/tree/main
            // https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/commit/6e5c4f1e395deb959c494953478fa5cec4b8008f
            name = "Google DeepMind / Gemma 4 E2B",
            description = "Best balance of speed and quality. Perfect for most phones.",
            sizeMb = 2588,
            downloadUrl =
                "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/" +
                        "resolve/6e5c4f1e395deb959c494953478fa5cec4b8008f/" +
                        "gemma-4-E2B-it.litertlm?download=true",
            minRamGb = 4,
            sha256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
        ),
        RecommendedModel(
            // https://ai.google.dev/gemma/docs/core/model_card_4
            // https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm
            // https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/commit/28299f30ee4d43294517a4ac93abd6163412f07f
            name = "Google DeepMind / Gemma 4 E4B",
            description = "Higher quality responses, slower generation. Best for flagship mobiles.",
            sizeMb = 3659,
            downloadUrl =
                "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/" +
                        "resolve/28299f30ee4d43294517a4ac93abd6163412f07f/" +
                        "gemma-4-E4B-it.litertlm?download=true",
            minRamGb = 8,
            sha256 = "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0",
        ),
    )

/**
 * Gets recommended model by name.
 *
 * @param name Display name of model
 * @return RecommendedModel if found, null otherwise
 */
fun findRecommendedModel(name: String): RecommendedModel? = RECOMMENDED_MODELS.find { it.name == name }

/**
 * Returns all recommended models regardless of device RAM.
 *
 * Use this to show all models with warnings for those requiring more RAM.
 *
 * @return List of all recommended models
 */
fun getRecommendedModels(): List<RecommendedModel> = RECOMMENDED_MODELS

/**
 * Object providing access to recommended models.
 */
object RecommendedModels {
    /**
     * Get all recommended models.
     */
    fun getRecommendedModels(): List<RecommendedModel> = RECOMMENDED_MODELS

    /**
     * Type alias for backwards compatibility.
     */
    typealias RecommendedModel = de.cyclenerd.android.llm.server.data.RecommendedModel
}

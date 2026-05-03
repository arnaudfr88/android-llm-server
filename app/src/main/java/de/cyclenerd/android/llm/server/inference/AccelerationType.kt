package de.cyclenerd.android.llm.server.inference

import com.google.ai.edge.litertlm.Backend

/**
 * Hardware acceleration types for LiteRT inference.
 *
 * This sealed class represents different compute backends that LiteRT
 * can use for inference. Each backend has different performance
 * characteristics and device compatibility.
 *
 * Why sealed class instead of enum?
 * NPU requires a parameter (nativeLibraryDir), which enums can't handle.
 * Sealed classes give us enum-like exhaustive when() checks while allowing
 * different data for each type.
 */
sealed class AccelerationType {
    /**
     * CPU backend - uses device's main processor.
     *
     * Performance characteristics:
     * - Slowest option (baseline performance)
     * - Always available on all devices
     * - Prefill: ~2-5 tokens/second
     * - Decode: ~8-12 tokens/second
     *
     * When to use:
     * - GPU/NPU not available
     * - Debugging and development
     * - Very low battery scenarios (CPU is most power efficient)
     * - Devices with weak/unsupported GPUs
     *
     * Memory usage: ~4-6GB RAM for Gemma 4 2B model
     */
    data object CPU : AccelerationType()

    /**
     * GPU backend - uses device's graphics processor.
     *
     * Performance characteristics:
     * - 3-5x faster than CPU for prefill phase
     * - 1.5-2x faster than CPU for decode phase
     * - Prefill: ~8-20 tokens/second
     * - Decode: ~12-20 tokens/second
     *
     * When to use:
     * - Default choice for most devices
     * - Best balance of speed and compatibility
     * - Supported on virtually all Android devices
     *
     * Requirements:
     * - AndroidManifest.xml must include:
     *   <uses-native-library android:name="libOpenCL.so" android:required="false"/>
     *
     * Memory usage: ~4-6GB RAM + ~1-2GB VRAM
     * Battery impact: Moderate (GPU uses more power than CPU)
     */
    data object GPU : AccelerationType()

    /**
     * NPU backend - uses dedicated AI/ML accelerator chip.
     *
     * Performance characteristics:
     * - 10-15x faster than CPU (on supported devices)
     * - Most power efficient for sustained inference
     * - Prefill: ~30-50 tokens/second
     * - Decode: ~25-40 tokens/second
     *
     * When to use:
     * - High-end devices with dedicated NPU (Google Tensor, Snapdragon 8 Gen 2+)
     * - Battery-constrained scenarios (NPU is most efficient)
     * - Maximum performance needed
     *
     * Requirements:
     * - Device must have compatible NPU chip
     * - Vendor-specific native libraries must be available
     * - Path to native libraries must be provided
     *
     * Availability:
     * - Google Pixel 6+ (Google Tensor TPU)
     * - Samsung Galaxy S23+ (Snapdragon 8 Gen 2)
     * - Limited support on older/budget devices
     *
     * @param nativeLibraryDir Absolute path to NPU native libraries
     */
    data class NPU(
        val nativeLibraryDir: String,
    ) : AccelerationType()
}

/**
 * Converts AccelerationType to LiteRT Backend.
 *
 * This extension function maps our application's acceleration types
 * to LiteRT's native Backend types. The mapping is straightforward
 * but important for type safety and API consistency.
 *
 * Why this function?
 * - Decouples our app from LiteRT's internal types
 * - Makes it easier to add custom acceleration logic later
 * - Provides a single place to configure backend parameters
 *
 * @return Corresponding LiteRT Backend instance
 */
fun AccelerationType.toLiteRtBackend(): Backend =
    when (this) {
        is AccelerationType.CPU -> Backend.CPU()
        is AccelerationType.GPU -> Backend.GPU()
        is AccelerationType.NPU -> Backend.NPU(nativeLibraryDir = nativeLibraryDir)
    }

/**
 * Returns a human-readable name for this acceleration type.
 *
 * Useful for logging and UI display.
 *
 * @return Display name (e.g., "GPU", "NPU", "CPU")
 */
fun AccelerationType.displayName(): String =
    when (this) {
        is AccelerationType.CPU -> "CPU"
        is AccelerationType.GPU -> "GPU"
        is AccelerationType.NPU -> "NPU"
    }

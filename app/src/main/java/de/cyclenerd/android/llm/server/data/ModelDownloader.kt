package de.cyclenerd.android.llm.server.data

import de.cyclenerd.android.llm.server.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Downloads LiteRT model files from URLs with progress tracking and resume support.
 *
 * Features:
 * - Progress callbacks for UI updates
 * - Resume interrupted downloads (HTTP Range headers)
 * - Checksum verification (SHA-256)
 * - Cancellation support
 * - Large file handling (multi-GB)
 *
 * Why Dispatchers.IO?
 * Network and file operations are blocking (they wait for I/O).
 * Dispatchers.IO is optimized for I/O operations:
 * - Uses a thread pool sized for I/O (64+ threads)
 * - Threads can block without affecting UI
 * - Automatically suspends coroutine during waits
 *
 * Why HTTP Range headers?
 * If a 2.5GB download fails at 2.4GB, we don't want to re-download
 * the entire file. Range headers let us request only the missing bytes:
 * ```
 * GET /model.litertlm HTTP/1.1
 * Range: bytes=2400000000-
 * ```
 * Server responds with just the final 100MB.
 *
 * @param modelStorage Storage manager for saving files
 */
class ModelDownloader(
    private val modelStorage: ModelStorage,
) {
    private var isCancelled = false

    /**
     * Downloads a model from a URL.
     *
     * This function:
     * 1. Checks available storage space
     * 2. Determines if partial file exists (for resume)
     * 3. Opens HTTP connection with Range header if resuming
     * 4. Downloads data in chunks with progress callbacks
     * 5. Verifies checksum if provided
     * 6. Returns File on success
     *
     * Progress callback:
     * Called frequently (every 8KB) to update UI. Don't do heavy
     * work in the callback—just update a Flow or LiveData.
     *
     * Checksum verification:
     * If a checksum is provided, we compute SHA-256 of the downloaded
     * file and compare. This ensures:
     * - File downloaded completely (no truncation)
     * - File not corrupted during transfer
     * - File matches what provider published (security)
     *
     * @param url HTTP(S) URL to download from
     * @param fileName Destination filename (e.g., "gemma-4-e2b-it.litertlm")
     * @param expectedSha256 Optional SHA-256 checksum for verification
     * @param onProgress Callback (bytesDownloaded, totalBytes)
     * @return Result<File> - Success with file, or Failure with exception
     */
    suspend fun downloadModel(
        url: String,
        fileName: String,
        expectedSha256: String? = null,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit,
    ): Result<File> =
        withContext(Dispatchers.IO) {
            isCancelled = false

            try {
                Logger.i(TAG, "Starting download: $url → $fileName")

                // Check if we're resuming a partial download
                val partialFile = File(modelStorage.getModelPath(fileName)?.plus(".part") ?: "")
                val existingBytes = if (partialFile.exists()) partialFile.length() else 0L

                // Open HTTP connection
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 30000
                connection.readTimeout = 30000

                // Set Range header for resume
                if (existingBytes > 0) {
                    connection.setRequestProperty("Range", "bytes=$existingBytes-")
                    Logger.i(TAG, "Resuming download from byte $existingBytes")
                }

                connection.connect()

                // Check response code
                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_PARTIAL) {
                    throw Exception("HTTP error: $responseCode ${connection.responseMessage}")
                }

                // Get total file size
                val contentLength = connection.contentLengthLong
                val totalBytes =
                    if (existingBytes > 0) {
                        existingBytes + contentLength
                    } else {
                        contentLength
                    }

                Logger.i(TAG, "Total size: ${totalBytes / 1000 / 1000} MB")

                // Check available storage
                val availableSpace = modelStorage.getAvailableSpaceBytes()
                if (totalBytes > availableSpace) {
                    throw Exception("Insufficient storage: need ${totalBytes / 1000 / 1000}MB, have ${availableSpace / 1000 / 1000}MB")
                }

                // Download to temporary file
                val tempFile = File(modelStorage.getModelFile(fileName).absolutePath + ".tmp")
                var bytesDownloaded = existingBytes

                FileOutputStream(tempFile, existingBytes > 0).use { output ->
                    connection.inputStream.use { input ->
                        val buffer = ByteArray(8192)
                        var read: Int

                        while (input.read(buffer).also { read = it } != -1) {
                            if (isCancelled) {
                                Logger.i(TAG, "Download cancelled")
                                throw Exception("Download cancelled by user")
                            }

                            output.write(buffer, 0, read)
                            bytesDownloaded += read

                            // Report progress
                            onProgress(bytesDownloaded, totalBytes)
                        }
                        output.flush()
                    }
                }

                connection.disconnect()

                // Verify checksum if provided
                if (expectedSha256 != null) {
                    Logger.i(TAG, "Verifying checksum...")
                    val actualSha256 = computeSha256(tempFile)
                    if (actualSha256 != expectedSha256.lowercase()) {
                        tempFile.delete()
                        throw Exception("Checksum mismatch: expected $expectedSha256, got $actualSha256")
                    }
                    Logger.i(TAG, "Checksum verified")
                }

                // Move to final location
                val finalFile = modelStorage.getModelFile(fileName)
                if (tempFile.renameTo(finalFile)) {
                    Logger.i(TAG, "Download complete: $fileName")
                    Result.success(finalFile)
                } else {
                    throw Exception("Failed to move file to final location")
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Download failed", e)
                Result.failure(e)
            }
        }

    /**
     * Cancels an ongoing download.
     *
     * This sets a flag that the download loop checks. The download
     * will stop at the next buffer read and throw an exception.
     *
     * Why not Thread.interrupt()?
     * - Coroutines don't use thread interruption
     * - Flag is simpler and more reliable
     * - Allows cleanup before cancelling
     */
    fun cancel() {
        isCancelled = true
        Logger.i(TAG, "Cancel requested")
    }

    /**
     * Computes SHA-256 checksum of a file.
     *
     * SHA-256 is a cryptographic hash function that produces a 256-bit
     * (32-byte) hash. Even one bit difference in the file produces a
     * completely different hash.
     *
     * Why SHA-256?
     * - Industry standard for file verification
     * - Collision-resistant (can't find two files with same hash)
     * - Fast enough for multi-GB files
     * - HuggingFace provides SHA-256 checksums
     *
     * Output format:
     * 64 hex characters (e.g., "a3d2f8...")
     *
     * @param file File to hash
     * @return SHA-256 checksum as hex string
     */
    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)

        file.inputStream().use { input ->
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }

        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TAG = "ModelDownloader"
    }
}

/**
 * Result type for download operations.
 *
 * Kotlin's Result type provides type-safe error handling:
 * - Success(value): Operation succeeded
 * - Failure(exception): Operation failed
 *
 * Why Result instead of throwing?
 * - Forces caller to handle errors (can't ignore)
 * - More functional programming style
 * - Easier to test
 */

package de.cyclenerd.android.llm.server.inference

import com.google.ai.edge.litertlm.Conversation
import de.cyclenerd.android.llm.server.utils.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

/**
 * Streams LLM responses token-by-token.
 *
 * Hot-path notes:
 *  - We map [com.google.ai.edge.litertlm.Message] → [String] without any
 *    intermediate allocations beyond the JNI->JVM string already produced
 *    by LiteRT.
 *  - All `onStart`/`onCompletion` callbacks are gated by [Logger.isDebug]
 *    so that release builds don't pay the timestamp + thread-name cost
 *    for every token emission (which fires hundreds of times per second).
 *  - No per-token coroutine context switch — the underlying [Flow] runs
 *    on whatever dispatcher the collector is on (we want
 *    [kotlinx.coroutines.Dispatchers.Default] which is pinned to the
 *    big cores via [de.cyclenerd.android.llm.server.perf.PerformanceManager]).
 */
object StreamingHandler {
    private const val TAG = "StreamingHandler"

    /**
     * Stream a response token-by-token from the given [conversation].
     *
     * Cancellation: if the collector cancels (e.g. the HTTP client closed
     * the SSE connection), LiteRT stops generation immediately, freeing
     * GPU/NPU time. This is the recommended way to honour `max_tokens`
     * — `Flow.take(n)` cancels after n emissions.
     */
    fun streamResponse(
        conversation: Conversation,
        userMessage: String,
    ): Flow<String> {
        if (Logger.isDebug) {
            Logger.d(TAG, "Starting streaming response for: ${userMessage.take(50)}…")
        }

        val flow =
            conversation
                .sendMessageAsync(userMessage)
                .map { it.toString() }

        // Only attach the lifecycle hooks in debug builds. They are not
        // free — every operator adds a wrapper Flow + a continuation
        // allocation per emission.
        return if (Logger.isDebug) {
            flow
                .onStart { Logger.d(TAG, "Stream started") }
                .catch { e ->
                    Logger.e(TAG, "Error during streaming", e)
                    throw e
                }.onCompletion { error ->
                    if (error == null) {
                        Logger.d(TAG, "Stream completed successfully")
                    } else {
                        Logger.e(TAG, "Stream completed with error: ${error.message}")
                    }
                }
        } else {
            flow
        }
    }

    /**
     * Generate a complete response (no streaming). Use only for tests or
     * non-interactive batch flows — for end-user APIs prefer streaming.
     */
    suspend fun generateResponse(
        conversation: Conversation,
        userMessage: String,
    ): String {
        // StringBuilder is preallocated large enough to fit a typical
        // long response without growing the backing char[] more than once.
        val sb = StringBuilder(2048)
        streamResponse(conversation, userMessage).collect { token -> sb.append(token) }
        return sb.toString()
    }
}

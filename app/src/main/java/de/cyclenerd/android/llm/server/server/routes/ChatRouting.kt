package de.cyclenerd.android.llm.server.server.routes

import androidx.tracing.trace
import de.cyclenerd.android.llm.server.inference.ConversationManager
import de.cyclenerd.android.llm.server.inference.MetricsCollector
import de.cyclenerd.android.llm.server.inference.PerformanceMetrics
import de.cyclenerd.android.llm.server.inference.StreamingHandler
import de.cyclenerd.android.llm.server.inference.withMetrics
import de.cyclenerd.android.llm.server.server.MetricsAttributeKey
import de.cyclenerd.android.llm.server.server.models.ChatCompletionChunk
import de.cyclenerd.android.llm.server.server.models.ChatCompletionRequest
import de.cyclenerd.android.llm.server.server.models.ChatCompletionResponse
import de.cyclenerd.android.llm.server.server.models.ChatMessage
import de.cyclenerd.android.llm.server.server.models.Choice
import de.cyclenerd.android.llm.server.server.models.ChoiceDelta
import de.cyclenerd.android.llm.server.server.models.ErrorDetail
import de.cyclenerd.android.llm.server.server.models.ErrorResponse
import de.cyclenerd.android.llm.server.server.models.FinishReason
import de.cyclenerd.android.llm.server.server.models.MessageDelta
import de.cyclenerd.android.llm.server.server.models.Usage
import de.cyclenerd.android.llm.server.utils.Logger
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class HealthResponse(
    val status: String,
    val timestamp: Long,
)

/**
 * A single, shared, compact JSON encoder for the streaming hot path.
 *
 * Why module-private and shared?
 *  - Each [Json] instance has heavy internal state (descriptors, caches).
 *  - Allocating one per request/token would burn GC and CPU.
 *  - Compact (`prettyPrint = false`) saves both bytes on the wire and
 *    encode CPU per emission.
 */
private val sseJson =
    Json {
        prettyPrint = false
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
    }

/**
 * Configures chat completion routes.
 */
fun Application.configureChatRoutes(
    conversationManager: ConversationManager,
    onMetrics: ((PerformanceMetrics) -> Unit)? = null,
) {
    routing {
        post("/v1/chat/completions") {
            handleChatCompletion(conversationManager, call, onMetrics)
        }

        get("/health") {
            call.respond(HttpStatusCode.OK, HealthResponse("healthy", System.currentTimeMillis()))
        }

        get("/") {
            call.respondText("Local LLM Server", ContentType.Text.Plain)
        }
    }
}

private suspend fun handleChatCompletion(
    conversationManager: ConversationManager,
    call: ApplicationCall,
    onMetrics: ((PerformanceMetrics) -> Unit)?,
) {
    trace("handleChatCompletion") {
        try {
            val request = call.receive<ChatCompletionRequest>()
            Logger.i(TAG, "Received chat completion request: stream=${request.stream}")

            if (request.stream) {
                handleStreamingRequest(conversationManager, request, call, onMetrics)
            } else {
                handleNonStreamingRequest(conversationManager, request, call, onMetrics)
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Error handling chat completion", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(
                    ErrorDetail(
                        message = e.message ?: "Internal server error",
                        type = "internal_error",
                    ),
                ),
            )
        }
    }
}

/**
 * Non-streaming completion. Uses a [StringBuilder] preallocated to a
 * realistic response size to avoid char[] regrowth.
 */
private suspend fun handleNonStreamingRequest(
    conversationManager: ConversationManager,
    request: ChatCompletionRequest,
    call: ApplicationCall,
    onMetrics: ((PerformanceMetrics) -> Unit)?,
) {
    Logger.d(TAG) { "Handling non-streaming request with ${request.messages.size} messages" }

    val metricsCollector = MetricsCollector()

    conversationManager.withConversation(request.messages) { conversation ->
        val userMessage =
            request.messages.lastOrNull { it.role == "user" }?.content
                ?: throw IllegalArgumentException("No user message found")

        Logger.d(TAG) { "Generating non-streaming response for: ${userMessage.take(50)}…" }

        val promptTokens = estimateTokenCount(userMessage)

        // Pre-sized StringBuilder reduces char[] regrowth allocations.
        val sb = StringBuilder(2048)
        StreamingHandler
            .streamResponse(conversation, userMessage)
            .withMetrics(metricsCollector)
            .toList()
            .forEach { sb.append(it) }
        val responseText = sb.toString()

        val metrics = metricsCollector.onComplete(promptTokens)
        call.attributes.put(MetricsAttributeKey, metrics)
        onMetrics?.invoke(metrics)

        val completionTokens = metrics.totalTokensGenerated

        val response =
            ChatCompletionResponse(
                id = "chatcmpl-${UUID.randomUUID()}",
                created = System.currentTimeMillis() / 1000,
                model = request.model,
                choices =
                    listOf(
                        Choice(
                            index = 0,
                            message = ChatMessage(role = "assistant", content = responseText),
                            finishReason = FinishReason.STOP,
                        ),
                    ),
                usage =
                    Usage(
                        promptTokens = promptTokens,
                        completionTokens = completionTokens,
                        totalTokens = promptTokens + completionTokens,
                    ),
            )

        call.respond(HttpStatusCode.OK, response)
        Logger.i(TAG, "Non-streaming response sent: $completionTokens completion tokens, ${promptTokens + completionTokens} total tokens")
    }
}

/**
 * Streaming SSE completion.
 *
 * Hot-path notes:
 *  - The [Json] encoder is module-shared (no per-token allocation).
 *  - `flush()` is required after every chunk so the client receives
 *    tokens with sub-millisecond latency.
 *  - `ChoiceDelta`/`MessageDelta`/`ChatCompletionChunk` allocations per
 *    token are unavoidable (each chunk has a unique payload), but kept
 *    small so they fit in the young-gen TLAB and never escape.
 */
private suspend fun handleStreamingRequest(
    conversationManager: ConversationManager,
    request: ChatCompletionRequest,
    call: ApplicationCall,
    onMetrics: ((PerformanceMetrics) -> Unit)?,
) {
    Logger.d(TAG) { "Handling streaming request with ${request.messages.size} messages" }

    val metricsCollector = MetricsCollector()

    conversationManager.withConversation(request.messages) { conversation ->
        val userMessage =
            request.messages.lastOrNull { it.role == "user" }?.content
                ?: throw IllegalArgumentException("No user message found")

        Logger.d(TAG) { "Starting streaming response for: ${userMessage.take(50)}…" }

        val promptTokens = estimateTokenCount(userMessage)
        val requestId = "chatcmpl-${UUID.randomUUID()}"
        val timestamp = System.currentTimeMillis() / 1000

        call.response.header(HttpHeaders.ContentType, ContentType.Text.EventStream.toString())
        call.response.header(HttpHeaders.CacheControl, "no-cache")
        call.response.header(HttpHeaders.Connection, "keep-alive")
        // Disable Nagle's algorithm in any reverse proxy in front of us.
        call.response.header("X-Accel-Buffering", "no")

        call.respondTextWriter(contentType = ContentType.Text.EventStream) {
            var isFirst = true

            StreamingHandler
                .streamResponse(conversation, userMessage)
                .withMetrics(metricsCollector)
                .catch { e ->
                    Logger.e(TAG, "Error during streaming", e)
                    val errorChunk = createErrorChunk(requestId, timestamp, request.model, e.message)
                    write("data: ${sseJson.encodeToString(errorChunk)}\n\n")
                    flush()
                }.collect { token ->
                    val chunk =
                        ChatCompletionChunk(
                            id = requestId,
                            created = timestamp,
                            model = request.model,
                            choices =
                                listOf(
                                    ChoiceDelta(
                                        index = 0,
                                        delta =
                                            MessageDelta(
                                                role = if (isFirst) "assistant" else null,
                                                content = token,
                                            ),
                                        finishReason = null,
                                    ),
                                ),
                        )
                    isFirst = false
                    write("data: ${sseJson.encodeToString(chunk)}\n\n")
                    flush()
                }

            val finalChunk =
                ChatCompletionChunk(
                    id = requestId,
                    created = timestamp,
                    model = request.model,
                    choices =
                        listOf(
                            ChoiceDelta(
                                index = 0,
                                delta = MessageDelta(),
                                finishReason = FinishReason.STOP,
                            ),
                        ),
                )
            write("data: ${sseJson.encodeToString(finalChunk)}\n\n")
            write("data: [DONE]\n\n")
            flush()

            val metrics = metricsCollector.onComplete(promptTokens)
            call.attributes.put(MetricsAttributeKey, metrics)
            onMetrics?.invoke(metrics)
            Logger.i(
                TAG,
                "Streaming completed: ${metrics.totalTokensGenerated} completion tokens, " +
                    "${metrics.totalTokens} total tokens in ${metrics.totalTimeMs} ms",
            )
        }
    }
}

private fun createErrorChunk(
    id: String,
    created: Long,
    model: String,
    errorMessage: String?,
): ChatCompletionChunk =
    ChatCompletionChunk(
        id = id,
        created = created,
        model = model,
        choices =
            listOf(
                ChoiceDelta(
                    index = 0,
                    delta = MessageDelta(content = "[ERROR: $errorMessage]"),
                    finishReason = FinishReason.ERROR,
                ),
            ),
    )

/**
 * Estimates token count using the standard ~4 chars/token heuristic.
 * LiteRT does not expose the tokenizer, so this is the cheapest reasonable
 * approximation.
 */
private fun estimateTokenCount(text: String): Int = (text.length / 4).coerceAtLeast(1)

private const val TAG = "ChatRoutes"

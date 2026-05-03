package de.cyclenerd.android.llm.server.server.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A single chat message in the OpenAI format.
 *
 * The wire format follows the OpenAI Chat Completions API as documented at
 * https://platform.openai.com/docs/api-reference/chat. Only the subset of
 * fields meaningful for a local single-user LLM server is modelled here —
 * sampler tuning fields are accepted but ignored (LiteRT uses our global
 * sampling configuration), and `max_tokens` is also ignored to avoid
 * truncated responses.
 *
 * @property role One of "system", "user", "assistant".
 * @property content The textual content of the message. (Multimodal content
 *  is not yet supported by this server.)
 */
@Serializable
data class ChatMessage(
    val role: String,
    val content: String,
)

/**
 * The full request body for `POST /v1/chat/completions`.
 *
 * @property model Name of the model the client wants to talk to. We use it
 *  only as an echo in the response — the server only loads one model at a
 *  time.
 * @property messages Conversation history including the new user prompt.
 * @property temperature **Ignored.** Provided for OpenAI SDK compatibility.
 * @property topP **Ignored.** Same.
 * @property maxTokens **Ignored.** LiteRT generates complete responses to
 *  avoid truncated sentences. The field is still validated to reject
 *  obviously malformed values (zero / negative) for client diagnostics.
 * @property stream If true, the server responds with Server-Sent Events.
 */
@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Float? = null,
    @SerialName("top_p") val topP: Float? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    val stream: Boolean = false,
)

/**
 * Reason the model stopped generating tokens.
 *
 * Mirrors OpenAI's `finish_reason` enum on the wire (lower-case strings).
 */
@Serializable
enum class FinishReason {
    @SerialName("stop")
    STOP,

    @SerialName("length")
    LENGTH,

    @SerialName("content_filter")
    CONTENT_FILTER,

    @SerialName("error")
    ERROR,
}

/**
 * One generated alternative in a non-streaming response. We always return a
 * single choice (n = 1).
 */
@Serializable
data class Choice(
    val index: Int,
    val message: ChatMessage,
    @SerialName("finish_reason") val finishReason: FinishReason,
)

/**
 * Token-usage accounting block, as expected by every OpenAI client.
 *
 * Counts are estimated using a 4-chars-per-token heuristic since LiteRT does
 * not expose the tokenizer.
 */
@Serializable
data class Usage(
    @SerialName("prompt_tokens") val promptTokens: Int,
    @SerialName("completion_tokens") val completionTokens: Int,
    @SerialName("total_tokens") val totalTokens: Int,
)

/**
 * Full non-streaming chat completion response.
 */
@Serializable
data class ChatCompletionResponse(
    val id: String,
    @SerialName("object") val objectType: String = "chat.completion",
    val created: Long,
    val model: String,
    val choices: List<Choice>,
    val usage: Usage,
)

/**
 * Per-chunk delta payload — only the **new** content for this token.
 *
 * The `role` field is set on the first chunk only (OpenAI convention) so
 * clients can identify the speaker without duplicating the role on every
 * subsequent chunk.
 */
@Serializable
data class MessageDelta(
    val role: String? = null,
    val content: String? = null,
)

/**
 * One choice in a streaming chunk. `finishReason` is null until the final
 * chunk.
 */
@Serializable
data class ChoiceDelta(
    val index: Int,
    val delta: MessageDelta,
    @SerialName("finish_reason") val finishReason: FinishReason? = null,
)

/**
 * One Server-Sent Event chunk in a streaming chat completion.
 *
 * The OpenAI streaming format wraps each chunk as
 * `data: <json>\n\n`, with a final `data: [DONE]\n\n` sentinel.
 */
@Serializable
data class ChatCompletionChunk(
    val id: String,
    @SerialName("object") val objectType: String = "chat.completion.chunk",
    val created: Long,
    val model: String,
    val choices: List<ChoiceDelta>,
)

/**
 * Detail block describing a single error.
 */
@Serializable
data class ErrorDetail(
    val message: String,
    val type: String,
    val code: String? = null,
    val param: String? = null,
)

/**
 * Top-level error envelope returned for any 4xx/5xx response. Wraps a single
 * [ErrorDetail].
 */
@Serializable
data class ErrorResponse(
    val error: ErrorDetail,
)

package de.cyclenerd.android.llm.server.inference

import androidx.tracing.trace
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import de.cyclenerd.android.llm.server.perf.PerformanceManager
import de.cyclenerd.android.llm.server.server.models.ChatMessage
import de.cyclenerd.android.llm.server.utils.Logger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Manages LLM conversations with support for multi-turn context and
 * system messages.
 *
 * LiteRT constraint: only **one** conversation can exist per engine at a
 * time. We therefore serialise requests with a [Mutex] and recreate the
 * conversation per request — recreation is cheap (~10 ms) and gives us
 * a clean KV-cache, which prevents context bleed between unrelated calls.
 *
 * Performance notes:
 *  - The mutex is fair (FIFO) so we never starve a slow client.
 *  - We boost the inference worker thread to URGENT_AUDIO + big-core
 *    affinity inside [withConversation] so the decode loop runs on the
 *    fastest core the SoC has.
 *  - The mutable list used to build `initialMessages` is sized up-front to
 *    avoid the doubling reallocation pattern of [ArrayList].
 *  - Sampler config is shared across requests (immutable, allocated once).
 */
class ConversationManager(
    val engine: LlmEngine,
) {
    private val tag = "LLMServer:ConversationManager"

    /** Serialises access — LiteRT allows only one open conversation. */
    private val conversationMutex = Mutex()

    /** Track the active conversation so we always close it on cleanup. */
    @Volatile
    private var currentConversation: Conversation? = null

    @Volatile
    private var requestCount = 0

    /**
     * Standardised global sampler. Allocated once, reused everywhere.
     * Tuned for "creative but coherent" — same defaults as the Gemma
     * reference implementation.
     */
    private val globalSamplerConfig =
        SamplerConfig(
            temperature = 1.0,
            topP = 0.95,
            topK = 64,
        )

    init {
        Logger.i(tag, "Initialized with global config: temp=1.0, topP=0.95, topK=64")
    }

    /**
     * Execute [block] inside a fresh conversation seeded with the message
     * history in [messages]. The conversation is closed automatically.
     *
     * The mutex guarantees serial access to the engine; on a heavily
     * concurrent server this is the right trade-off because LiteRT's
     * decode kernels saturate the GPU/NPU on a single conversation.
     */
    suspend fun <R> withConversation(
        messages: List<ChatMessage>,
        block: suspend (Conversation) -> R,
    ): R =
        trace("ConversationManager#withConversation") {
            conversationMutex.withLock {
                requestCount++
                val reqId = requestCount
                Logger.d(tag, "Processing request #$reqId with ${messages.size} messages")

                // Boost the worker thread that's about to drive inference.
                // Each request acquires a coroutine on Dispatchers.Default,
                // and that worker can vary, so we re-boost every call.
                PerformanceManager.boostCurrentThread("ConversationManager.req$reqId")

                closeCurrentConversation()
                val conversation = createConversation(messages)
                currentConversation = conversation
                try {
                    block(conversation)
                } finally {
                    closeCurrentConversation()
                }
            }
        }

    /**
     * Build a [Conversation] pre-seeded with system + history.
     *
     * The OpenAI message list we receive is split into:
     *  - The leading system message (becomes `systemInstruction`).
     *  - All assistant + user messages **except** the trailing user one
     *    (become `initialMessages`).
     *  - The trailing user message which is the prompt the caller will
     *    send via `sendMessageAsync`.
     */
    private fun createConversation(messages: List<ChatMessage>): Conversation {
        val systemMessage = messages.firstOrNull { it.role == "system" }?.content

        // Index of the LAST user message — that's the prompt, not history.
        val lastUserIndex = messages.indexOfLast { it.role == "user" }

        // Pre-size the array to avoid the ArrayList growth penalty.
        val initial = ArrayList<Message>(messages.size)
        for (i in messages.indices) {
            val msg = messages[i]
            if (msg.role == "system") continue
            if (msg.role == "user" && i == lastUserIndex) continue
            when (msg.role) {
                "user" -> initial.add(Message.user(msg.content))
                "assistant" -> initial.add(Message.model(msg.content))
                else -> Logger.w(tag, "Unknown role: ${msg.role}, skipping")
            }
        }

        Logger.d(tag, "Creating conversation: systemMsg=${systemMessage != null}, history=${initial.size}")

        val config =
            ConversationConfig(
                systemInstruction = systemMessage?.let { Contents.of(it) },
                initialMessages = initial,
                samplerConfig = globalSamplerConfig,
            )

        return engine.createConversation(config)
    }

    private fun closeCurrentConversation() {
        currentConversation?.let { conv ->
            try {
                conv.close()
                Logger.d(tag, "Conversation closed")
            } catch (e: Exception) {
                Logger.e(tag, "Error closing conversation: ${e.message}")
            }
        }
        currentConversation = null
    }

    fun clear() {
        Logger.i(tag, "Clearing conversation manager")
        closeCurrentConversation()
    }
}

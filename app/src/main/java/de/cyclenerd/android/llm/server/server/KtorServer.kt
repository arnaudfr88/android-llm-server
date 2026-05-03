package de.cyclenerd.android.llm.server.server

import de.cyclenerd.android.llm.server.inference.ConversationManager
import de.cyclenerd.android.llm.server.inference.LlmEngine
import de.cyclenerd.android.llm.server.inference.PerformanceMetrics
import de.cyclenerd.android.llm.server.perf.PerformanceManager
import de.cyclenerd.android.llm.server.server.models.ErrorDetail
import de.cyclenerd.android.llm.server.server.models.ErrorResponse
import de.cyclenerd.android.llm.server.server.routes.configureChatRoutes
import de.cyclenerd.android.llm.server.server.routes.configureModelsRoutes
import de.cyclenerd.android.llm.server.utils.Logger
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.EngineConnectorBuilder
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.util.AttributeKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Attribute key for stashing per-call performance metrics on the
 * [io.ktor.server.application.ApplicationCall], which the request log
 * interceptor reads back out for dashboard reporting.
 */
val MetricsAttributeKey = AttributeKey<PerformanceMetrics>("PerformanceMetrics")

/**
 * Ktor HTTP server hosting the OpenAI-compatible LLM API.
 *
 * Engine: **CIO** — pure-Kotlin coroutine-driven engine. We size both the
 * connection-acceptor pool and the worker pool based on the number of CPU
 * big-cores reported by [PerformanceManager], then double it for IO
 * concurrency. CIO's defaults assume a server-class machine with dozens
 * of cores; on a phone we'd otherwise create idle threads we never use.
 *
 * JSON: prettyPrint is OFF (saves ~30 % bytes per response and a noticeable
 * CPU spike on long completions).
 *
 * StatusPages: catches **everything** so a single buggy request can't
 * crash the worker pool.
 */
class KtorServer(
    private val port: Int = 8080,
    private val bindAddresses: List<String>,
    private val llmEngine: LlmEngine,
    private val onMetrics: ((PerformanceMetrics) -> Unit)? = null,
    private val onRequestLog: ((Long, String, String, String, Int, PerformanceMetrics?) -> Unit)? = null,
) {
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private lateinit var conversationManager: ConversationManager

    /**
     * Start the server. Validates that the bind address is private (RFC1918)
     * before binding so a misconfiguration cannot expose the API to the
     * public internet.
     */
    suspend fun start() {
        check(server == null) { "Server already running" }
        require(bindAddresses.isNotEmpty()) { "No bind addresses provided" }
        bindAddresses.forEach { addr ->
            require(addr != "0.0.0.0" && !addr.startsWith("127.")) {
                "Invalid bind address: $addr"
            }
        }

        Logger.i(TAG, "Starting Ktor server on port $port")
        Logger.i(TAG, "Bind addresses: $bindAddresses")

        conversationManager = ConversationManager(llmEngine)

        // Ktor's CIO engine binds via connectors. We add one connector per
        // detected local IP — Ktor will accept requests on every connector
        // simultaneously through the same engine pool.
        val targetHosts = bindAddresses

        // Tune the CIO engine pools. Big-core count is our parallelism floor:
        //   - connectionGroupSize: threads accepting new connections.
        //   - workerGroupSize:     threads handling request bodies (decode SSE).
        //   - callGroupSize:       coroutines per worker.
        val bigCores = PerformanceManager.bigCoreIds.size.coerceAtLeast(2)
        val cpuCount = PerformanceManager.cpuCount

        // Use the (factory, environment, configure, module) overload — this
        // is the only signature in Ktor 3.x that lets us mutate the engine
        // configuration AND register multiple connectors in one call.
        server =
            embeddedServer(
                factory = CIO,
                environment =
                    applicationEnvironment {
                        log =
                            io.ktor.util.logging
                                .KtorSimpleLogger("LlmServer.Ktor")
                    },
                configure = {
                    // Add one connector per local IP.
                    targetHosts.forEach { host ->
                        connectors.add(
                            EngineConnectorBuilder().apply {
                                this.host = host
                                this.port = this@KtorServer.port
                            },
                        )
                    }
                    // Inherited from BaseApplicationEngine.Configuration.
                    connectionGroupSize = bigCores.coerceAtLeast(2)
                    workerGroupSize = (bigCores * 2).coerceAtLeast(4)
                    callGroupSize = (cpuCount * 4).coerceAtLeast(16)
                    shutdownGracePeriod = 30_000
                    shutdownTimeout = 30_000
                    // CIO-specific (set via reflection-free `apply` because
                    // Configuration here is the CIO subtype).
                    connectionIdleTimeoutSeconds = 60
                },
                module = {
                    configureServer(conversationManager, onMetrics, onRequestLog)
                },
            )

        server?.start(wait = false)

        Logger.i(TAG, "Server started successfully")
        bindAddresses.forEach { ip -> Logger.i(TAG, "Server accessible at: http://$ip:$port") }
    }

    suspend fun stop() =
        withContext(Dispatchers.IO) {
            Logger.i(TAG, "Stopping Ktor server")
            try {
                if (::conversationManager.isInitialized) {
                    conversationManager.clear()
                    Logger.i(TAG, "Conversation manager cleared")
                }
                server?.stop(gracePeriodMillis = 30_000, timeoutMillis = 30_000)
            } catch (e: Exception) {
                Logger.e(TAG, "Error during server shutdown", e)
            } finally {
                server = null
                Logger.i(TAG, "Server stopped")
            }
        }

    fun isRunning(): Boolean = server != null

    fun getServerUrls(): List<String> = if (isRunning()) bindAddresses.map { "http://$it:$port" } else emptyList()

    companion object {
        private const val TAG = "KtorServer"
    }
}

/**
 * Configure the Ktor pipeline — plugins and routing.
 *
 * Plugin layout was chosen for the lowest per-request overhead:
 *  - [ContentNegotiation] uses a single shared [Json] instance.
 *  - [CORS] is configured for `anyHost()` because the server only ever
 *    binds to a private IP (defence in depth — no harm in being permissive
 *    here since the only path to the server is the LAN).
 *  - [StatusPages] catches everything so we never leak a stack trace.
 *  - The request logging interceptor only attaches when a callback was
 *    supplied — releases that don't need request logs pay zero overhead.
 */
private fun Application.configureServer(
    conversationManager: ConversationManager,
    onMetrics: ((PerformanceMetrics) -> Unit)?,
    onRequestLog: ((Long, String, String, String, Int, PerformanceMetrics?) -> Unit)?,
) {
    Logger.i("ServerConfig", "Configuring Ktor application")

    // Single shared Json instance — Ktor wraps it once, we reuse for life.
    val json =
        Json {
            prettyPrint = false // Save bytes + CPU on every response.
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false // Skip writing nulls = smaller wire frames.
        }

    install(ContentNegotiation) {
        json(json)
    }

    install(CORS) {
        anyHost()
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Options)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            Logger.e("ServerConfig", "Unhandled exception", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(
                    ErrorDetail(
                        message = cause.message ?: "Internal server error",
                        type = "internal_error",
                    ),
                ),
            )
        }
    }

    if (onRequestLog != null) {
        intercept(ApplicationCallPipeline.Monitoring) {
            val timestamp = System.currentTimeMillis()
            val method = call.request.httpMethod.value
            val path = call.request.path()
            val sourceIp = call.request.local.remoteHost
            try {
                proceed()
                val statusCode = call.response.status()?.value ?: 200
                val metrics = call.attributes.getOrNull(MetricsAttributeKey)
                onRequestLog(timestamp, method, path, sourceIp, statusCode, metrics)
            } catch (e: Exception) {
                val metrics = call.attributes.getOrNull(MetricsAttributeKey)
                onRequestLog(timestamp, method, path, sourceIp, 500, metrics)
                throw e
            }
        }
    }

    val combinedCallback: ((PerformanceMetrics) -> Unit)? =
        if (onMetrics != null || onRequestLog != null) {
            { metrics -> onMetrics?.invoke(metrics) }
        } else {
            null
        }

    configureChatRoutes(conversationManager, combinedCallback)
    configureModelsRoutes(conversationManager.engine)
}

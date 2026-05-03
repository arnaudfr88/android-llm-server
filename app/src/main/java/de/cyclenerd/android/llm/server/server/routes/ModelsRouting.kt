package de.cyclenerd.android.llm.server.server.routes

import de.cyclenerd.android.llm.server.inference.LlmEngine
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable

@Serializable
data class ModelListResponse(
    val `object`: String = "list",
    val data: List<ModelData>,
)

@Serializable
data class ModelData(
    val id: String,
    val `object`: String = "model",
    val created: Long,
    val owned_by: String = "local",
)

/**
 * Configures the /v1/models endpoint (OpenAI-compatible).
 *
 * Returns the currently loaded model from the LLM engine.
 *
 * @param llmEngine LLM engine that has the current model loaded
 */
fun Application.configureModelsRoutes(llmEngine: LlmEngine) {
    routing {
        get("/v1/models") {
            val modelName = llmEngine.getModelName()

            if (modelName == null) {
                call.respond(
                    HttpStatusCode.OK,
                    ModelListResponse(data = emptyList()),
                )
                return@get
            }

            val modelData =
                ModelData(
                    id = modelName,
                    created = System.currentTimeMillis() / 1000,
                )

            call.respond(
                HttpStatusCode.OK,
                ModelListResponse(data = listOf(modelData)),
            )
        }

        get("/v1/models/{model}") {
            val modelId = call.parameters["model"]
            val modelName = llmEngine.getModelName()

            if (modelName == null || modelId != modelName) {
                call.respond(
                    HttpStatusCode.NotFound,
                    mapOf(
                        "error" to
                            mapOf(
                                "message" to "Model not found",
                                "type" to "invalid_request_error",
                            ),
                    ),
                )
                return@get
            }

            val modelData =
                ModelData(
                    id = modelName,
                    created = System.currentTimeMillis() / 1000,
                )

            call.respond(HttpStatusCode.OK, modelData)
        }
    }
}

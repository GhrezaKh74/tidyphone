package app.tidyphone.data.llm

import app.tidyphone.domain.llm.LlmException
import app.tidyphone.domain.llm.LlmPart
import app.tidyphone.domain.llm.LlmProvider
import app.tidyphone.domain.llm.LlmRequest
import app.tidyphone.domain.llm.LlmResult
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Anthropic Messages API over raw HTTP (POST /v1/messages). */
class AnthropicProvider(
    private val http: HttpClient,
    baseUrl: String,
    private val apiKey: String,
    override val model: String,
) : LlmProvider {
    override val name = "Anthropic"
    private val url = baseUrl.trimSlash().removeSuffix("/v1") + "/v1/messages"

    /** Flips off if the model rejects structured output, so later batches don't pay for a 400. */
    @Volatile private var structuredOutput = true

    override suspend fun complete(request: LlmRequest): LlmResult {
        if (apiKey.isBlank()) throw LlmException("No API key set for Anthropic", 401)
        val useSchema = structuredOutput && request.responseSchema != null
        val (response, body) = http.postJson(url) {
            header("x-api-key", apiKey)
            header("anthropic-version", API_VERSION)
            contentType(ContentType.Application.Json)
            setBody(buildBody(request, useSchema).toString())
        }
        val status = response.status.value
        if (status == 400 && useSchema && body.contains(Regex("output_config|json_schema|format", RegexOption.IGNORE_CASE))) {
            structuredOutput = false
            return complete(request)
        }
        if (status !in 200..299) throw errorFor(status, body)
        return parse(body)
    }

    internal fun buildBody(request: LlmRequest, withSchema: Boolean): JsonObject = buildJsonObject {
        put("model", model)
        put("max_tokens", request.maxOutputTokens)
        put("system", request.system)
        putJsonArray("messages") {
            add(buildJsonObject {
                put("role", "user")
                put("content", buildJsonArray {
                    request.parts.forEach { part ->
                        when (part) {
                            is LlmPart.Text -> add(buildJsonObject { put("type", "text"); put("text", part.text) })
                            is LlmPart.Image -> add(buildJsonObject {
                                put("type", "image")
                                putJsonObject("source") {
                                    put("type", "base64")
                                    put("media_type", part.mediaType)
                                    put("data", part.base64)
                                }
                            })
                        }
                    }
                })
            })
        }
        if (withSchema && request.responseSchema != null) {
            putJsonObject("output_config") {
                putJsonObject("format") {
                    put("type", "json_schema")
                    put("schema", request.responseSchema)
                }
            }
        }
    }

    internal fun parse(body: String): LlmResult {
        val root = LlmJson.parseToJsonElement(body).jsonObject
        val stop = root["stop_reason"]?.jsonPrimitive?.contentOrNull
        if (stop == "refusal") throw LlmException("The model declined this request", 200)
        val text = (root["content"] as? JsonArray).orEmpty()
            .map { it.jsonObject }
            .filter { it["type"]?.jsonPrimitive?.contentOrNull == "text" }
            .joinToString("") { it["text"]?.jsonPrimitive?.contentOrNull.orEmpty() }
        val usage = root["usage"] as? JsonObject
        return LlmResult(
            text = text,
            inputTokens = usage?.get("input_tokens")?.jsonPrimitive?.intOrNull,
            outputTokens = usage?.get("output_tokens")?.jsonPrimitive?.intOrNull,
            truncated = stop == "max_tokens",
        )
    }

    override suspend fun testConnection(): String {
        val start = System.currentTimeMillis()
        val r = complete(LlmRequest("You are a connectivity check.", listOf(LlmPart.Text("Reply with the single word OK.")), 512))
        return "$model · ${System.currentTimeMillis() - start} ms · \"${r.text.trim().take(20)}\""
    }

    companion object { const val API_VERSION = "2026-10-21" }
}

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
import io.ktor.http.HttpHeaders
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

/**
 * Any server that speaks the OpenAI Chat Completions format: OpenAI, OpenRouter, Groq,
 * Gemini's OpenAI endpoint, Ollama, LM Studio, vLLM... The API key is optional for local servers.
 */
class OpenAiCompatProvider(
    private val http: HttpClient,
    baseUrl: String,
    private val apiKey: String,
    override val model: String,
    private val jsonMode: Boolean,
) : LlmProvider {
    override val name = "OpenAI-compatible"
    private val url = baseUrl.trimSlash().removeSuffix("/chat/completions") + "/chat/completions"

    // Servers differ; these adapt after the first 400 that names the parameter.
    @Volatile private var useJsonMode = jsonMode
    @Volatile private var useMaxCompletionTokens = false

    override suspend fun complete(request: LlmRequest): LlmResult {
        val (response, body) = http.postJson(url) {
            if (apiKey.isNotBlank()) header(HttpHeaders.Authorization, "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(buildBody(request).toString())
        }
        val status = response.status.value
        if (status == 400) {
            if (!useMaxCompletionTokens && body.contains("max_completion_tokens")) {
                useMaxCompletionTokens = true
                return complete(request)
            }
            if (useJsonMode && body.contains("response_format")) {
                useJsonMode = false
                return complete(request)
            }
        }
        if (status !in 200..299) throw errorFor(status, body)
        return parse(body)
    }

    internal fun buildBody(request: LlmRequest): JsonObject = buildJsonObject {
        put("model", model)
        put(if (useMaxCompletionTokens) "max_completion_tokens" else "max_tokens", request.maxOutputTokens)
        putJsonArray("messages") {
            add(buildJsonObject { put("role", "system"); put("content", request.system) })
            add(buildJsonObject {
                put("role", "user")
                val hasImages = request.parts.any { it is LlmPart.Image }
                if (!hasImages) {
                    // Plain string content works on every server, including older local ones.
                    put("content", request.parts.filterIsInstance<LlmPart.Text>().joinToString("\n") { it.text })
                } else {
                    put("content", buildJsonArray {
                        request.parts.forEach { part ->
                            when (part) {
                                is LlmPart.Text -> add(buildJsonObject { put("type", "text"); put("text", part.text) })
                                is LlmPart.Image -> add(buildJsonObject {
                                    put("type", "image_url")
                                    putJsonObject("image_url") { put("url", "data:${part.mediaType};base64,${part.base64}") }
                                })
                            }
                        }
                    })
                }
            })
        }
        if (useJsonMode && request.responseSchema != null) {
            putJsonObject("response_format") { put("type", "json_object") }
        }
    }

    internal fun parse(body: String): LlmResult {
        val root = LlmJson.parseToJsonElement(body).jsonObject
        val choice = (root["choices"] as? JsonArray)?.firstOrNull()?.jsonObject
            ?: throw LlmException("Response has no choices")
        val message = choice["message"] as? JsonObject
        val content = message?.get("content")
        val text = when (content) {
            is JsonArray -> content.joinToString("") { (it as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull.orEmpty() }
            null -> ""
            else -> content.jsonPrimitive.contentOrNull.orEmpty()
        }
        val usage = root["usage"] as? JsonObject
        return LlmResult(
            text = text,
            inputTokens = usage?.get("prompt_tokens")?.jsonPrimitive?.intOrNull,
            outputTokens = usage?.get("completion_tokens")?.jsonPrimitive?.intOrNull,
            truncated = choice["finish_reason"]?.jsonPrimitive?.contentOrNull == "length",
        )
    }

    override suspend fun testConnection(): String {
        val start = System.currentTimeMillis()
        val r = complete(LlmRequest("You are a connectivity check.", listOf(LlmPart.Text("Reply with the single word OK.")), 256))
        return "$model · ${System.currentTimeMillis() - start} ms · \"${r.text.trim().take(20)}\""
    }
}

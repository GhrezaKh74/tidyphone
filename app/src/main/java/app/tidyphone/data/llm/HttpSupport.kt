package app.tidyphone.data.llm

import app.tidyphone.domain.llm.LlmException
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException

internal val LlmJson = Json { ignoreUnknownKeys = true; explicitNulls = false }

fun defaultHttpClient(): HttpClient = HttpClient(OkHttp) {
    expectSuccess = false
    install(HttpTimeout) {
        requestTimeoutMillis = 240_000
        connectTimeoutMillis = 20_000
        socketTimeoutMillis = 240_000
    }
    // No logging plugin on purpose: request headers carry API keys.
}

/** POSTs and maps failures to [LlmException] without ever echoing request headers. */
internal suspend fun HttpClient.postJson(url: String, block: HttpRequestBuilder.() -> Unit): Pair<HttpResponse, String> {
    val response = try {
        post(url, block)
    } catch (e: IOException) {
        throw LlmException("Network error: ${e.javaClass.simpleName}${e.message?.let { ": " + it.take(160) } ?: ""}", retryable = true)
    } catch (e: io.ktor.client.plugins.HttpRequestTimeoutException) {
        throw LlmException("Request timed out", retryable = true)
    }
    return response to response.bodyAsText()
}

internal fun errorFor(status: Int, body: String): LlmException {
    val message = runCatching {
        val root = LlmJson.parseToJsonElement(body) as JsonObject
        val err = root["error"]
        when (err) {
            is JsonObject -> err["message"]?.jsonPrimitive?.contentOrNull
            is JsonElement -> err.jsonPrimitive.contentOrNull
            else -> root["message"]?.jsonPrimitive?.contentOrNull
        }
    }.getOrNull() ?: body.take(200)
    val retryable = status == 408 || status == 409 || status == 429 || status >= 500
    val hint = when (status) {
        401 -> " (check the API key)"
        403 -> " (key not allowed for this model or endpoint)"
        404 -> " (check the base URL and model name)"
        else -> ""
    }
    return LlmException("HTTP $status: ${message.take(300)}$hint", status, retryable)
}

internal fun String.trimSlash() = trim().trimEnd('/')

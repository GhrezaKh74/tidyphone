package app.tidyphone.domain.llm

import kotlinx.serialization.json.JsonObject

/** A chat-style completion backend. Implementations: Anthropic Messages API, OpenAI-compatible APIs. */
interface LlmProvider {
    val name: String
    val model: String

    suspend fun complete(request: LlmRequest): LlmResult

    /** Cheap round trip to check URL, key and model. Returns a short success message. */
    suspend fun testConnection(): String
}

data class LlmRequest(
    val system: String,
    val parts: List<LlmPart>,
    val maxOutputTokens: Int,
    /** JSON Schema of the expected answer, used for structured output where the API supports it. */
    val responseSchema: JsonObject? = null,
)

sealed interface LlmPart {
    data class Text(val text: String) : LlmPart
    data class Image(val base64: String, val mediaType: String = "image/jpeg") : LlmPart
}

data class LlmResult(
    val text: String,
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    /** True when the answer stopped at the token limit and is probably incomplete. */
    val truncated: Boolean = false,
)

/** Error from a provider. The message never contains the API key. */
class LlmException(message: String, val status: Int? = null, val retryable: Boolean = false) : Exception(message)

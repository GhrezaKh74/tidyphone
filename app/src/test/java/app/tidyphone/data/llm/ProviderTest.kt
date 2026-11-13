package app.tidyphone.data.llm

import app.tidyphone.domain.llm.LlmException
import app.tidyphone.domain.llm.LlmPart
import app.tidyphone.domain.llm.LlmRequest
import app.tidyphone.domain.llm.SuggestionSchema
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.fail
import org.junit.Test

class ProviderTest {
    private val requests = mutableListOf<HttpRequestData>()
    private fun client(vararg replies: Pair<HttpStatusCode, String>): HttpClient {
        val queue = replies.toMutableList()
        return HttpClient(MockEngine { req ->
            requests += req
            val (status, body) = queue.removeAt(0)
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { expectSuccess = false }
    }
    private fun HttpRequestData.bodyText() = (body as TextContent).text
    private val req = LlmRequest("sys", listOf(LlmPart.Text("hello"), LlmPart.Image("AAAA")), 1000, SuggestionSchema.schema)

    @Test fun `anthropic request has the right url, headers and body`() = runTest {
        val ok = """{"content":[{"type":"thinking","thinking":""},{"type":"text","text":"{\"items\":[]}"}],"stop_reason":"end_turn","usage":{"input_tokens":12,"output_tokens":5}}"""
        val p = AnthropicProvider(client(HttpStatusCode.OK to ok), "https://api.anthropic.com/", "sk-secret", "claude-opus-5-5")
        val r = p.complete(req)
        assertThat(r.text).isEqualTo("{\"items\":[]}")
        assertThat(r.inputTokens).isEqualTo(12)
        val sent = requests.single()
        assertThat(sent.url.toString()).isEqualTo("https://api.anthropic.com/v1/messages")
        assertThat(sent.headers["x-api-key"]).isEqualTo("sk-secret")
        assertThat(sent.headers["anthropic-version"]).isEqualTo("2026-10-21")
        val body = sent.bodyText()
        assertThat(body).contains("\"model\":\"claude-opus-5-5\"")
        assertThat(body).contains("\"output_config\"")
        assertThat(body).contains("\"type\":\"image\"")
        assertThat(body).doesNotContain("temperature")
    }

    @Test fun `anthropic falls back without structured output when the model rejects it`() = runTest {
        val p = AnthropicProvider(client(
            HttpStatusCode.BadRequest to """{"error":{"type":"invalid_request_error","message":"output_config.format is not supported for this model"}}""",
            HttpStatusCode.OK to """{"content":[{"type":"text","text":"{}"}],"stop_reason":"max_tokens"}""",
        ), "https://api.anthropic.com", "k", "old-model")
        val r = p.complete(req)
        assertThat(r.truncated).isTrue()
        assertThat(requests[1].bodyText()).doesNotContain("output_config")
    }

    @Test fun `errors carry status and message but never the key`() = runTest {
        val p = AnthropicProvider(client(HttpStatusCode.Unauthorized to """{"error":{"message":"invalid x-api-key"}}"""), "https://api.anthropic.com", "sk-ant-SECRET", "m")
        try { p.complete(req); fail() } catch (e: LlmException) {
            assertThat(e.status).isEqualTo(401)
            assertThat(e.retryable).isFalse()
            assertThat(e.message).doesNotContain("SECRET")
            assertThat(e.message).contains("check the API key")
        }
        val busy = AnthropicProvider(client(HttpStatusCode.TooManyRequests to "{}"), "https://api.anthropic.com", "k", "m")
        try { busy.complete(req); fail() } catch (e: LlmException) { assertThat(e.retryable).isTrue() }
    }

    @Test fun `openai compatible request and adaptive parameters`() = runTest {
        val ok = """{"choices":[{"message":{"content":"{\"items\":[]}"},"finish_reason":"stop"}],"usage":{"prompt_tokens":7,"completion_tokens":3}}"""
        val p = OpenAiCompatProvider(client(
            HttpStatusCode.BadRequest to """{"error":{"message":"Unsupported parameter: 'max_tokens'. Use 'max_completion_tokens' instead."}}""",
            HttpStatusCode.OK to ok,
        ), "https://openrouter.ai/api/v1/", "or-key", "some/model", jsonMode = true)
        val r = p.complete(req)
        assertThat(r.outputTokens).isEqualTo(3)
        assertThat(requests[0].url.toString()).isEqualTo("https://openrouter.ai/api/v1/chat/completions")
        assertThat(requests[0].headers[HttpHeaders.Authorization]).isEqualTo("Bearer or-key")
        val second = requests[1].bodyText()
        assertThat(second).contains("max_completion_tokens")
        assertThat(second).contains("\"response_format\":{\"type\":\"json_object\"}")
        assertThat(second).contains("data:image/jpeg;base64,AAAA")
    }

    @Test fun `local server without key sends no auth header and plain text content`() = runTest {
        val ok = """{"choices":[{"message":{"content":"OK"},"finish_reason":"stop"}]}"""
        val p = OpenAiCompatProvider(client(HttpStatusCode.OK to ok), "http://192.168.1.10:11434/v1", "", "llama3.2", jsonMode = false)
        p.complete(LlmRequest("sys", listOf(LlmPart.Text("hi")), 100))
        assertThat(requests.single().headers[HttpHeaders.Authorization]).isNull()
        assertThat(requests.single().bodyText()).contains("\"content\":\"hi\"")
    }
}

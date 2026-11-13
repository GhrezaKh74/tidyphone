package app.tidyphone.data.llm

import app.tidyphone.data.settings.AppSettings
import app.tidyphone.data.settings.ProviderType
import app.tidyphone.data.settings.SecureKeyStore
import app.tidyphone.domain.llm.LlmProvider
import io.ktor.client.HttpClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LlmProviderFactory @Inject constructor(
    private val http: HttpClient,
    private val keys: SecureKeyStore,
) {
    fun create(s: AppSettings, apiKeyOverride: String? = null): LlmProvider {
        val key = apiKeyOverride ?: keys.get(s.provider).orEmpty()
        return when (s.provider) {
            ProviderType.ANTHROPIC -> AnthropicProvider(http, s.anthropicBaseUrl, key, s.anthropicModel)
            ProviderType.OPENAI_COMPAT -> OpenAiCompatProvider(http, s.openAiBaseUrl, key, s.openAiModel, s.openAiJsonMode)
        }
    }
}

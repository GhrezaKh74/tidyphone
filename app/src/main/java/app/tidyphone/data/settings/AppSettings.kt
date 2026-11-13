package app.tidyphone.data.settings

import app.tidyphone.ui.theme.ThemeMode

enum class ProviderType { ANTHROPIC, OPENAI_COMPAT }

data class AppSettings(
    val onboardingDone: Boolean = false,
    val provider: ProviderType = ProviderType.ANTHROPIC,
    val anthropicBaseUrl: String = DEFAULT_ANTHROPIC_URL,
    val anthropicModel: String = DEFAULT_ANTHROPIC_MODEL,
    val openAiBaseUrl: String = DEFAULT_OPENAI_URL,
    val openAiModel: String = "gpt-5-mini",
    /** Ask OpenAI-compatible servers for `response_format: json_object`. Some local servers don't support it. */
    val openAiJsonMode: Boolean = true,
    /** Custom prices in USD per million tokens; null means "use the built-in table if the model is known". */
    val priceInputPerM: Double? = null,
    val priceOutputPerM: Double? = null,
    val maxInputTokensPerBatch: Int = 6000,
    val customRules: String = "",
    val excludeFolders: List<String> = emptyList(),
    val largeFileMb: Int = 200,
    val oldDownloadDays: Int = 90,
    val oldScreenshotDays: Int = 60,
    val trashRetentionDays: Int = 30,
    val unusedAppDays: Int = 60,
    val dryRun: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
) {
    val activeModel: String get() = if (provider == ProviderType.ANTHROPIC) anthropicModel else openAiModel
    val activeBaseUrl: String get() = if (provider == ProviderType.ANTHROPIC) anthropicBaseUrl else openAiBaseUrl

    companion object {
        const val DEFAULT_ANTHROPIC_URL = "https://api.anthropic.com"
        const val DEFAULT_ANTHROPIC_MODEL = "claude-opus-5-5"
        const val DEFAULT_OPENAI_URL = "https://api.openai.com/v1"
    }
}

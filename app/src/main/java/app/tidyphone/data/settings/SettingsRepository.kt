package app.tidyphone.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.tidyphone.ui.theme.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")

/** Non-secret settings in DataStore. API keys live in [SecureKeyStore], never here. */
@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private object K {
        val onboarding = booleanPreferencesKey("onboarding_done")
        val provider = stringPreferencesKey("provider")
        val anthropicUrl = stringPreferencesKey("anthropic_url")
        val anthropicModel = stringPreferencesKey("anthropic_model")
        val openAiUrl = stringPreferencesKey("openai_url")
        val openAiModel = stringPreferencesKey("openai_model")
        val openAiJson = booleanPreferencesKey("openai_json")
        val priceIn = doublePreferencesKey("price_in")
        val priceOut = doublePreferencesKey("price_out")
        val batchTokens = intPreferencesKey("batch_tokens")
        val rules = stringPreferencesKey("custom_rules")
        val excludes = stringPreferencesKey("exclude_folders")
        val largeMb = intPreferencesKey("large_mb")
        val oldDl = intPreferencesKey("old_download_days")
        val oldShot = intPreferencesKey("old_screenshot_days")
        val trashDays = intPreferencesKey("trash_days")
        val appDays = intPreferencesKey("unused_app_days")
        val dryRun = booleanPreferencesKey("dry_run")
        val theme = stringPreferencesKey("theme")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { fromPrefs(it) }

    private fun fromPrefs(p: Preferences): AppSettings {
        val d = AppSettings()
        return AppSettings(
            onboardingDone = p[K.onboarding] ?: d.onboardingDone,
            provider = p[K.provider]?.let { runCatching { ProviderType.valueOf(it) }.getOrNull() } ?: d.provider,
            anthropicBaseUrl = p[K.anthropicUrl] ?: d.anthropicBaseUrl,
            anthropicModel = p[K.anthropicModel] ?: d.anthropicModel,
            openAiBaseUrl = p[K.openAiUrl] ?: d.openAiBaseUrl,
            openAiModel = p[K.openAiModel] ?: d.openAiModel,
            openAiJsonMode = p[K.openAiJson] ?: d.openAiJsonMode,
            priceInputPerM = p[K.priceIn],
            priceOutputPerM = p[K.priceOut],
            maxInputTokensPerBatch = p[K.batchTokens] ?: d.maxInputTokensPerBatch,
            customRules = p[K.rules] ?: d.customRules,
            excludeFolders = p[K.excludes]?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: d.excludeFolders,
            largeFileMb = p[K.largeMb] ?: d.largeFileMb,
            oldDownloadDays = p[K.oldDl] ?: d.oldDownloadDays,
            oldScreenshotDays = p[K.oldShot] ?: d.oldScreenshotDays,
            trashRetentionDays = p[K.trashDays] ?: d.trashRetentionDays,
            unusedAppDays = p[K.appDays] ?: d.unusedAppDays,
            dryRun = p[K.dryRun] ?: d.dryRun,
            themeMode = p[K.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.themeMode,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { p ->
            val s = transform(fromPrefs(p))
            p[K.onboarding] = s.onboardingDone
            p[K.provider] = s.provider.name
            p[K.anthropicUrl] = s.anthropicBaseUrl.trim()
            p[K.anthropicModel] = s.anthropicModel.trim()
            p[K.openAiUrl] = s.openAiBaseUrl.trim()
            p[K.openAiModel] = s.openAiModel.trim()
            p[K.openAiJson] = s.openAiJsonMode
            if (s.priceInputPerM != null) p[K.priceIn] = s.priceInputPerM else p.remove(K.priceIn)
            if (s.priceOutputPerM != null) p[K.priceOut] = s.priceOutputPerM else p.remove(K.priceOut)
            p[K.batchTokens] = s.maxInputTokensPerBatch.coerceIn(1000, 200_000)
            p[K.rules] = s.customRules
            p[K.excludes] = s.excludeFolders.joinToString("\n")
            p[K.largeMb] = s.largeFileMb.coerceAtLeast(1)
            p[K.oldDl] = s.oldDownloadDays.coerceAtLeast(1)
            p[K.oldShot] = s.oldScreenshotDays.coerceAtLeast(1)
            p[K.trashDays] = s.trashRetentionDays.coerceIn(1, 365)
            p[K.appDays] = s.unusedAppDays.coerceIn(7, 730)
            p[K.dryRun] = s.dryRun
            p[K.theme] = s.themeMode.name
        }
    }
}

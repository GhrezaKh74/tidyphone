package app.tidyphone.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tidyphone.data.llm.LlmProviderFactory
import app.tidyphone.data.settings.AppSettings
import app.tidyphone.data.settings.ProviderType
import app.tidyphone.data.settings.SecureKeyStore
import app.tidyphone.data.settings.SettingsRepository
import app.tidyphone.domain.llm.LlmException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface TestState {
    data object Idle : TestState
    data object Running : TestState
    data class Ok(val message: String) : TestState
    data class Error(val message: String) : TestState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val keys: SecureKeyStore,
    private val providers: LlmProviderFactory,
    private val skipped: app.tidyphone.data.repo.SkippedRepository,
) : ViewModel() {
    val skippedCount: StateFlow<Int> = skipped.count.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    fun restoreSkipped() = viewModelScope.launch { skipped.clear() }

    val settings: StateFlow<AppSettings?> = repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _keySaved = MutableStateFlow(ProviderType.entries.associateWith { keys.has(it) })
    val keySaved: StateFlow<Map<ProviderType, Boolean>> = _keySaved.asStateFlow()

    private val _test = MutableStateFlow<TestState>(TestState.Idle)
    val test: StateFlow<TestState> = _test.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) = viewModelScope.launch {
        repo.update(transform)
        _test.value = TestState.Idle
    }

    fun saveKey(provider: ProviderType, key: String) {
        keys.set(provider, key)
        _keySaved.value = ProviderType.entries.associateWith { keys.has(it) }
        _test.value = TestState.Idle
    }

    fun clearKey(provider: ProviderType) = saveKey(provider, "")

    fun testConnection() = viewModelScope.launch {
        val s = settings.value ?: return@launch
        _test.value = TestState.Running
        _test.value = try {
            TestState.Ok(withContext(Dispatchers.IO) { providers.create(s).testConnection() })
        } catch (e: LlmException) {
            TestState.Error(e.message ?: "Error")
        } catch (e: Exception) {
            TestState.Error(e.javaClass.simpleName)
        }
    }
}

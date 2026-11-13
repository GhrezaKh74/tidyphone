package app.tidyphone.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tidyphone.data.settings.AppSettings
import app.tidyphone.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {
    /** Null until settings are loaded, so the first screen doesn't flash. */
    val settingsState: StateFlow<AppSettings?> = settings.settings.map<AppSettings, AppSettings?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun completeOnboarding() = viewModelScope.launch { settings.update { it.copy(onboardingDone = true) } }
}

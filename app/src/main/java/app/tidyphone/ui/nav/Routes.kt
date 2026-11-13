package app.tidyphone.ui.nav

import kotlinx.serialization.Serializable

@Serializable data object OnboardingRoute
@Serializable data object HomeRoute
@Serializable data class ReviewRoute(val openAi: Boolean = false)
@Serializable data object HistoryRoute
@Serializable data object TrashRoute
@Serializable data object SettingsRoute
@Serializable data object ScanRoute
@Serializable data object RulesRoute
@Serializable data object AppsRoute

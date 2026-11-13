package app.tidyphone.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoDelete
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.tidyphone.R
import app.tidyphone.ui.dashboard.DashboardScreen
import app.tidyphone.ui.history.HistoryScreen
import app.tidyphone.ui.nav.HistoryRoute
import app.tidyphone.ui.nav.HomeRoute
import app.tidyphone.ui.nav.OnboardingRoute
import app.tidyphone.ui.nav.ReviewRoute
import app.tidyphone.ui.nav.RulesRoute
import app.tidyphone.ui.nav.ScanRoute
import app.tidyphone.ui.nav.SettingsRoute
import app.tidyphone.ui.nav.TrashRoute
import app.tidyphone.ui.onboarding.OnboardingScreen
import app.tidyphone.ui.review.ReviewScreen
import app.tidyphone.ui.scan.ScanScreen
import app.tidyphone.ui.settings.RulesScreen
import app.tidyphone.ui.settings.SettingsScreen
import app.tidyphone.ui.theme.TidyTheme
import app.tidyphone.ui.trash.TrashScreen
import kotlin.reflect.KClass

private data class Tab(val route: Any, val cls: KClass<*>, val icon: ImageVector, val label: Int)

private val tabs = listOf(
    Tab(HomeRoute, HomeRoute::class, Icons.Rounded.Home, R.string.nav_home),
    Tab(ReviewRoute(), ReviewRoute::class, Icons.Rounded.Checklist, R.string.nav_review),
    Tab(HistoryRoute, HistoryRoute::class, Icons.Rounded.History, R.string.nav_history),
    Tab(TrashRoute, TrashRoute::class, Icons.Rounded.AutoDelete, R.string.nav_trash),
    Tab(SettingsRoute, SettingsRoute::class, Icons.Rounded.Tune, R.string.nav_settings),
)

@Composable
fun TidyAppRoot(
    permissionGranted: Boolean,
    destination: String? = null,
    onDestinationHandled: () -> Unit = {},
    vm: AppViewModel = hiltViewModel(),
) {
    val settings by vm.settingsState.collectAsStateWithLifecycle()
    val s = settings ?: return
    TidyTheme(mode = s.themeMode) {
        val nav = rememberNavController()
        val entry by nav.currentBackStackEntryAsState()
        val dest = entry?.destination
        val showBar = tabs.any { t -> dest?.hasRoute(t.cls) == true }
        val start: Any = if (s.onboardingDone && permissionGranted) HomeRoute else OnboardingRoute
        androidx.compose.runtime.LaunchedEffect(destination, start) {
            if (destination == app.tidyphone.data.notify.ProgressNotifier.DEST_REVIEW && start == HomeRoute) {
                nav.navigate(ReviewRoute()) { popUpTo(HomeRoute); launchSingleTop = true }
            }
            if (destination != null) onDestinationHandled()
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                AnimatedVisibility(showBar, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp) {
                        tabs.forEach { t ->
                            val selected = dest?.hasRoute(t.cls) == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    nav.navigate(t.route) {
                                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(t.icon, null) },
                                label = { Text(stringResource(t.label), maxLines = 1) },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = .16f),
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                ),
                            )
                        }
                    }
                }
            },
        ) { inner ->
            Box(Modifier.fillMaxSize().padding(bottom = inner.calculateBottomPadding())) {
                NavHost(
                    navController = nav,
                    startDestination = start,
                    enterTransition = { fadeIn(tween(220)) },
                    exitTransition = { fadeOut(tween(160)) },
                ) {
                    composable<OnboardingRoute> {
                        OnboardingScreen(
                            permissionGranted = permissionGranted,
                            startAtPermission = s.onboardingDone,
                            onDone = {
                                vm.completeOnboarding()
                                nav.navigate(HomeRoute) { popUpTo(OnboardingRoute) { inclusive = true } }
                            },
                        )
                    }
                    composable<HomeRoute> {
                        DashboardScreen(
                            onScan = { nav.navigate(ScanRoute) },
                            onReview = { nav.navigate(ReviewRoute()) { launchSingleTop = true } },
                            onAi = { nav.navigate(ReviewRoute(openAi = true)) { launchSingleTop = true } },
                            onSettings = { nav.navigate(SettingsRoute) { launchSingleTop = true } },
                        )
                    }
                    composable<ScanRoute>(
                        enterTransition = { slideInVertically(tween(320)) { it / 3 } + fadeIn() },
                        exitTransition = { ExitTransition.None },
                        popExitTransition = { slideOutVertically(tween(260)) { it / 3 } + fadeOut() },
                        popEnterTransition = { EnterTransition.None },
                    ) {
                        ScanScreen(
                            onClose = { nav.popBackStack() },
                            onReview = { nav.navigate(ReviewRoute()) { popUpTo(HomeRoute) } },
                        )
                    }
                    composable<ReviewRoute> { e -> ReviewScreen(openAi = e.toRoute<ReviewRoute>().openAi, onOpenSettings = { nav.navigate(SettingsRoute) { launchSingleTop = true } }) }
                    composable<HistoryRoute> { HistoryScreen() }
                    composable<TrashRoute> { TrashScreen() }
                    composable<SettingsRoute> { SettingsScreen(onOpenRules = { nav.navigate(RulesRoute) }) }
                    composable<RulesRoute> { RulesScreen(onBack = { nav.popBackStack() }) }
                }
            }
        }
    }
}

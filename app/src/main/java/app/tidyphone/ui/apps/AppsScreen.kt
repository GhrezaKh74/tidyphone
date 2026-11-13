package app.tidyphone.ui.apps

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.tidyphone.R
import app.tidyphone.data.repo.AppsRepository
import app.tidyphone.data.settings.SettingsRepository
import app.tidyphone.domain.apps.AppUsage
import app.tidyphone.domain.apps.UsageState
import app.tidyphone.ui.components.EmptyState
import app.tidyphone.ui.components.GlowBackground
import app.tidyphone.ui.components.GradientButton
import app.tidyphone.ui.components.Tag
import app.tidyphone.ui.components.TidyCard
import app.tidyphone.ui.components.bytes
import app.tidyphone.ui.components.number
import app.tidyphone.ui.theme.Brand
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class AppsUi(
    val hasAccess: Boolean = true,
    val loading: Boolean = true,
    val apps: List<AppUsage> = emptyList(),
    val thresholdDays: Int = 60,
    val gamesOnly: Boolean = false,
    val showAll: Boolean = false,
    val selected: Set<String> = emptySet(),
    val queue: List<String> = emptyList(),
    val uninstalled: List<AppUsage> = emptyList(),
)

@HiltViewModel
class AppsViewModel @Inject constructor(
    private val repo: AppsRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(AppsUi())
    val ui: StateFlow<AppsUi> = _ui.asStateFlow()

    fun refresh() = viewModelScope.launch {
        val access = repo.hasUsageAccess()
        val days = settings.current().unusedAppDays
        _ui.update { it.copy(hasAccess = access, thresholdDays = days, loading = access) }
        if (!access) return@launch
        val apps = repo.load(days)
        _ui.update { s -> s.copy(apps = apps, loading = false, selected = s.selected.filter { p -> apps.any { it.app.packageName == p } }.toSet()) }
    }

    fun setThreshold(days: Int) = viewModelScope.launch {
        settings.update { it.copy(unusedAppDays = days) }
        _ui.update { it.copy(selected = emptySet()) }
        refresh()
    }

    fun toggleGames() = _ui.update { it.copy(gamesOnly = !it.gamesOnly) }
    fun toggleShowAll() = _ui.update { it.copy(showAll = !it.showAll) }
    fun toggle(pkg: String) = _ui.update { it.copy(selected = if (pkg in it.selected) it.selected - pkg else it.selected + pkg) }
    fun selectAll(pkgs: List<String>, on: Boolean) = _ui.update { it.copy(selected = if (on) it.selected + pkgs else it.selected - pkgs.toSet()) }

    fun usageIntent() = repo.usageAccessIntent()
    fun usageFallbackIntent() = repo.usageAccessFallbackIntent()
    fun uninstallIntent(pkg: String) = repo.uninstallIntent(pkg)

    /** Starts the uninstall queue; each app gets its own system confirmation. */
    fun startUninstall() = _ui.update { it.copy(queue = it.selected.toList(), uninstalled = emptyList()) }

    /** Called when the system dialog for the head of the queue closes. */
    fun onUninstallReturned() = viewModelScope.launch {
        val s = _ui.value
        val pkg = s.queue.firstOrNull() ?: return@launch
        val gone = withContext(Dispatchers.IO) { !repo.isInstalled(pkg) }
        val app = s.apps.firstOrNull { it.app.packageName == pkg }
        _ui.update {
            it.copy(
                queue = it.queue.drop(1),
                uninstalled = if (gone && app != null) it.uninstalled + app else it.uninstalled,
                selected = it.selected - pkg,
                apps = if (gone) it.apps.filterNot { a -> a.app.packageName == pkg } else it.apps,
            )
        }
        if (_ui.value.queue.isEmpty()) refresh()
    }

    fun stopQueue() = _ui.update { it.copy(queue = emptyList()) }
    fun clearResult() = _ui.update { it.copy(uninstalled = emptyList()) }
}

@Composable
fun AppsScreen(onBack: () -> Unit, vm: AppsViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleResumeEffect(Unit) { vm.refresh(); onPauseOrDispose { } }

    val uninstaller = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { vm.onUninstallReturned() }
    // Launch the next system uninstall dialog whenever the queue head changes.
    val head = ui.queue.firstOrNull()
    androidx.compose.runtime.LaunchedEffect(head) {
        if (head != null) {
            try { uninstaller.launch(vm.uninstallIntent(head)) } catch (e: ActivityNotFoundException) { vm.stopQueue() }
        }
    }
    var confirm by remember { mutableStateOf(false) }

    AppsContent(
        ui = ui,
        onBack = onBack,
        onGrant = {
            try { context.startActivity(vm.usageIntent()) } catch (e: ActivityNotFoundException) { context.startActivity(vm.usageFallbackIntent()) }
        },
        onThreshold = vm::setThreshold,
        onGames = vm::toggleGames,
        onShowAll = vm::toggleShowAll,
        onToggle = vm::toggle,
        onSelectAll = vm::selectAll,
        onUninstall = { confirm = true },
    )

    if (confirm) {
        val sel = ui.apps.filter { it.app.packageName in ui.selected }
        AlertDialog(
            onDismissRequest = { confirm = false },
            icon = { Icon(Icons.Rounded.DeleteForever, null, tint = Brand.Coral) },
            title = { Text(pluralStringResource(R.plurals.apps_uninstall_q, sel.size, number(sel.size))) },
            text = { Text(stringResource(R.string.apps_uninstall_body, bytes(sel.sumOf { it.app.sizeBytes ?: 0 }))) },
            confirmButton = { TextButton(onClick = { confirm = false; vm.startUninstall() }) { Text(stringResource(R.string.apps_uninstall), color = Brand.Coral) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (ui.queue.isEmpty() && ui.uninstalled.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = vm::clearResult,
            title = { Text(pluralStringResource(R.plurals.apps_uninstalled, ui.uninstalled.size, number(ui.uninstalled.size))) },
            text = { Text(stringResource(R.string.apps_freed, bytes(ui.uninstalled.sumOf { it.app.sizeBytes ?: 0 }))) },
            confirmButton = { TextButton(onClick = vm::clearResult) { Text(stringResource(R.string.done)) } },
        )
    }
}

private val thresholds = listOf(30, 60, 90, 180, 365)

@Composable
fun AppsContent(
    ui: AppsUi,
    onBack: () -> Unit,
    onGrant: () -> Unit,
    onThreshold: (Int) -> Unit,
    onGames: () -> Unit,
    onShowAll: () -> Unit,
    onToggle: (String) -> Unit,
    onSelectAll: (List<String>, Boolean) -> Unit,
    onUninstall: () -> Unit,
) {
    val visible = ui.apps.filter { (ui.showAll || it.isUnused) && (!ui.gamesOnly || it.app.isGame) }
    val unused = ui.apps.filter { it.isUnused }
    val selectedApps = ui.apps.filter { it.app.packageName in ui.selected }
    Box(Modifier.fillMaxSize()) {
        GlowBackground()
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.padding(start = 4.dp, end = 16.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
                Text(stringResource(R.string.apps_title), style = MaterialTheme.typography.headlineSmall)
            }
            if (!ui.hasAccess) {
                AccessCard(onGrant)
                return@Column
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Brand.heroGradient).padding(20.dp)) {
                        Column {
                            Text(pluralStringResource(R.plurals.apps_unused_count, unused.size, number(unused.size), number(ui.thresholdDays)), style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = .85f))
                            Text(bytes(unused.sumOf { it.app.sizeBytes ?: 0 }), style = MaterialTheme.typography.displaySmall, color = Color.White)
                            Text(stringResource(R.string.apps_can_free), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .85f))
                        }
                    }
                }
                item {
                    Text(stringResource(R.string.apps_threshold), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                        items(thresholds) { d ->
                            FilterChip(selected = ui.thresholdDays == d, onClick = { onThreshold(d) }, label = { Text(pluralStringResource(R.plurals.days_n, d, number(d))) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                        FilterChip(selected = ui.gamesOnly, onClick = onGames, label = { Text(stringResource(R.string.apps_games_only)) }, leadingIcon = { Icon(Icons.Rounded.SportsEsports, null, Modifier.size(18.dp)) })
                        FilterChip(selected = ui.showAll, onClick = onShowAll, label = { Text(stringResource(R.string.apps_show_all)) })
                    }
                }
                if (ui.loading) {
                    item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                } else if (visible.isEmpty()) {
                    item { EmptyState(Icons.Rounded.Apps, stringResource(R.string.apps_none_title), stringResource(R.string.apps_none_body)) }
                } else {
                    item {
                        val pkgs = visible.filter { it.isUnused }.map { it.app.packageName }
                        val all = pkgs.isNotEmpty() && pkgs.all { it in ui.selected }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(pluralStringResource(R.plurals.apps_listed, visible.size, number(visible.size)), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            if (pkgs.isNotEmpty()) TextButton(onClick = { onSelectAll(pkgs, !all) }) {
                                Text(stringResource(if (all) R.string.apps_select_none else R.string.apps_select_unused))
                            }
                        }
                    }
                    items(visible, key = { it.app.packageName }) { a -> AppRow(a, a.app.packageName in ui.selected) { onToggle(a.app.packageName) } }
                }
            }
            if (selectedApps.isNotEmpty()) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(pluralStringResource(R.plurals.apps_selected, selectedApps.size, number(selectedApps.size)), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.apps_frees, bytes(selectedApps.sumOf { it.app.sizeBytes ?: 0 })), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        GradientButton(stringResource(R.string.apps_uninstall), onUninstall, icon = Icons.Rounded.DeleteForever, brush = Brand.dangerGradient)
                    }
                }
            }
        }
    }
}

@Composable
private fun AccessCard(onGrant: () -> Unit) {
    Column(Modifier.padding(20.dp)) {
        TidyCard(Modifier.fillMaxWidth()) {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(Brand.heroGradient), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.QueryStats, null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.apps_access_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.apps_access_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(18.dp))
            GradientButton(stringResource(R.string.apps_access_grant), onGrant, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun AppRow(a: AppUsage, selected: Boolean, onToggle: () -> Unit) {
    TidyCard(Modifier.fillMaxWidth(), onClick = onToggle, padding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(a.app.packageName)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(a.app.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when (a.state) {
                        UsageState.ACTIVE, UsageState.UNUSED -> if (a.idleDays == 0L) stringResource(R.string.apps_used_today) else pluralStringResource(R.plurals.apps_last_used, a.idleDays.toInt(), number(a.idleDays))
                        UsageState.NEVER_OPENED -> pluralStringResource(R.plurals.apps_never_opened, a.idleDays.toInt(), number(a.idleDays))
                        UsageState.NOT_IN_HISTORY -> pluralStringResource(R.plurals.apps_not_in_history, a.idleDays.toInt(), number(a.idleDays))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (a.isUnused) Brand.Coral else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                    a.app.sizeBytes?.let { Tag(bytes(it), MaterialTheme.colorScheme.primary) }
                    if (a.app.isGame) Tag(stringResource(R.string.apps_game), Brand.Mint)
                }
            }
            Checkbox(selected, onCheckedChange = { onToggle() })
        }
    }
}

@Composable
private fun AppIcon(pkg: String) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(null, pkg) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.packageManager.getApplicationIcon(pkg).toBitmap(144, 144).asImageBitmap() }.getOrNull()
        }
    }
    Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        icon?.let { Image(it, null, Modifier.size(48.dp)) } ?: Icon(Icons.Rounded.Apps, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

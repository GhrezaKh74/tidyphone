package app.tidyphone.ui.review

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tidyphone.R
import app.tidyphone.data.repo.AnalysisScope
import app.tidyphone.data.repo.AnalysisState
import app.tidyphone.domain.model.ActionType
import app.tidyphone.domain.model.PlanItem
import app.tidyphone.domain.model.PlanSource
import app.tidyphone.ui.components.EmptyState
import app.tidyphone.ui.components.FileThumb
import app.tidyphone.ui.components.GlowBackground
import app.tidyphone.ui.components.GradientButton
import app.tidyphone.ui.components.PathText
import app.tidyphone.ui.components.ProgressBar
import app.tidyphone.ui.components.Tag
import app.tidyphone.ui.components.TidyCard
import app.tidyphone.ui.components.bytes
import app.tidyphone.ui.components.label
import app.tidyphone.ui.components.llmCategoryLabel
import app.tidyphone.ui.components.number
import app.tidyphone.ui.components.reasonText
import app.tidyphone.ui.theme.Brand

@Composable
fun ReviewScreen(onOpenSettings: () -> Unit, openAi: Boolean = false, albums: Boolean = false, vm: ReviewViewModel = hiltViewModel()) {
    val items by vm.items.collectAsStateWithLifecycle()
    val analysis by vm.analysisState.collectAsStateWithLifecycle()
    val exec by vm.exec.collectAsStateWithLifecycle()
    val sheet by vm.sheet.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(ActionType.DELETE) }
    var preview by remember { mutableStateOf<PlanItem?>(null) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val skip: (List<PlanItem>) -> Unit = { list ->
        vm.skip(list)
        scope.launch {
            snack.currentSnackbarData?.dismiss()
            val r = snack.showSnackbar(
                context.resources.getQuantityString(R.plurals.skipped_n, list.size, list.size),
                actionLabel = context.getString(R.string.undo),
                duration = SnackbarDuration.Short,
            )
            if (r == SnackbarResult.ActionPerformed) vm.unskip(list)
        }
    }
    var confirm by remember { mutableStateOf(false) }
    var needKey by remember { mutableStateOf(false) }
    var aiHandled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(openAi) {
        if (openAi && !aiHandled) {
            aiHandled = true
            vm.openAnalysis(if (albums) AnalysisScope.ALBUMS else AnalysisScope.ALL)
        }
    }

    Box(Modifier.fillMaxSize()) {
        GlowBackground()
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.review_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                AiButton(enabled = !analysis.running) { vm.openAnalysis() }
            }
            ReviewList(
                items = items,
                tab = tab,
                onTab = { tab = it },
                root = vm.root,
                analysis = analysis,
                dryRun = settings.dryRun,
                onToggle = vm::toggle,
                onGroup = vm::setSelected,
                onSkip = skip,
                onPreview = { preview = it },
                onCancelAnalysis = vm::cancelAnalysis,
                onDismissAnalysis = vm::dismissAnalysisResult,
                modifier = Modifier.weight(1f),
            )
            ApplyBar(items, dryRun = settings.dryRun, onApply = { confirm = true })
        }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp, start = 16.dp, end = 16.dp))
    }

    val withNotifications = app.tidyphone.ui.rememberWithNotificationPermission()
    if (sheet.open) AnalysisSheet(
        sheet, settings, vm::setScope, vm::setIncludeContent, { withNotifications { vm.runAnalysis() } }, vm::closeAnalysis,
        hasKey = vm.hasKey(), onOpenSettings = { vm.closeAnalysis(); onOpenSettings() },
    )
    preview?.let { PreviewSheet(it, vm.root, onDismiss = { preview = null }, onRemove = { skip(listOf(it)); preview = null }) }
    if (confirm) ConfirmApplyDialog(items, settings.dryRun, onConfirm = { confirm = false; vm.execute() }, onDismiss = { confirm = false })
    if (exec.running || exec.report != null) ExecutionDialog(exec, onUndo = vm::undoLast, onClose = vm::clearReport)
    if (needKey) NeedKeyDialog(onSettings = { needKey = false; onOpenSettings() }, onDismiss = { needKey = false })
}

@Composable
private fun AiButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.padding(end = 8.dp).clip(CircleShape).background(if (enabled) Brand.heroGradient else Brand.heroGradient)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.review_ai), color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private val deleteOrder = listOf(
    PlanSource.LOCAL_DUPLICATE, PlanSource.LOCAL_TEMP, PlanSource.LOCAL_APK, PlanSource.LOCAL_EMPTY_FOLDER,
    PlanSource.LOCAL_OLD_DOWNLOAD, PlanSource.LOCAL_OLD_SCREENSHOT, PlanSource.LOCAL_LARGE, PlanSource.LLM,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReviewList(
    items: List<PlanItem>,
    tab: ActionType,
    onTab: (ActionType) -> Unit,
    root: String,
    analysis: AnalysisState,
    dryRun: Boolean,
    onToggle: (PlanItem) -> Unit,
    onGroup: (List<PlanItem>, Boolean) -> Unit,
    onPreview: (PlanItem) -> Unit,
    onSkip: (List<PlanItem>) -> Unit = {},
    onCancelAnalysis: () -> Unit,
    onDismissAnalysis: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val byAction = items.groupBy { it.action }
    val current = byAction[tab].orEmpty()
    val groups: List<Pair<String, List<PlanItem>>> = when (tab) {
        ActionType.DELETE -> current.groupBy { it.source }.toList().sortedBy { deleteOrder.indexOf(it.first) }
            .map { (src, list) -> src.name to list }
        ActionType.MOVE -> current.groupBy { it.targetDir!!.removePrefix("$root/") }.toList().sortedByDescending { it.second.size }
        ActionType.RENAME -> if (current.isEmpty()) emptyList() else listOf("rename" to current)
    }
    var collapsed by rememberSaveable { mutableStateOf(setOf<String>()) }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp)) {
        if (analysis.running || analysis.finishedSuggestions != null || analysis.error != null) {
            item { AnalysisStatusCard(analysis, onCancelAnalysis, onDismissAnalysis); Spacer(Modifier.height(14.dp)) }
        }
        item { SummaryCard(items, dryRun); Spacer(Modifier.height(14.dp)) }
        item {
            Tabs(tab, onTab, byAction.mapValues { it.value.size })
            Spacer(Modifier.height(10.dp))
        }
        if (current.isEmpty()) {
            item {
                EmptyState(
                    Icons.Rounded.TaskAlt,
                    stringResource(R.string.review_empty_title),
                    stringResource(if (tab == ActionType.DELETE) R.string.review_empty_delete else R.string.review_empty_move),
                )
            }
        }
        groups.forEach { (key, list) ->
            stickyHeader(key = "h_$tab$key") {
                GroupHeader(
                    title = when (tab) {
                        ActionType.DELETE -> stringResource(PlanSource.valueOf(key).label())
                        ActionType.MOVE -> if (list.all { it.source == PlanSource.LLM_ALBUM }) "📷 " + list.first().reason else key
                        ActionType.RENAME -> stringResource(R.string.tab_rename)
                    },
                    isPath = tab == ActionType.MOVE && list.any { it.source != PlanSource.LLM_ALBUM },
                    list = list,
                    collapsed = key in collapsed,
                    onCollapse = { collapsed = if (key in collapsed) collapsed - key else collapsed + key },
                    onGroup = onGroup,
                    onSkip = onSkip,
                )
            }
            if (key !in collapsed) {
                items(list, key = { it.id }) { item ->
                    SwipeToSkip(onSkip = { onSkip(listOf(item)) }, modifier = Modifier.animateItem()) {
                        ItemRow(item, root, onToggle = { onToggle(item) }, onClick = { onPreview(item) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(items: List<PlanItem>, dryRun: Boolean) {
    val deletes = items.filter { it.selected && it.action == ActionType.DELETE }
    val moves = items.count { it.selected && it.action == ActionType.MOVE }
    val renames = items.count { it.selected && it.action == ActionType.RENAME }
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Brand.heroGradient).padding(20.dp)) {
        Column {
            Text(stringResource(R.string.review_will_free), style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = .85f))
            Text(bytes(deletes.sumOf { it.size }), style = MaterialTheme.typography.displaySmall, color = Color.White)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(Icons.Rounded.DeleteSweep, number(deletes.size))
                Chip(Icons.AutoMirrored.Rounded.DriveFileMove, number(moves))
                Chip(Icons.Rounded.DriveFileRenameOutline, number(renames))
            }
            if (dryRun) {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = .22f)).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Science, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.review_dry_run_on), color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun Chip(icon: ImageVector, text: String) {
    Row(Modifier.clip(CircleShape).background(Color.White.copy(alpha = .18f)).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(text, color = Color.White, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun Tabs(tab: ActionType, onTab: (ActionType) -> Unit, counts: Map<ActionType, Int>) {
    Row(
        Modifier.fillMaxWidth().clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(ActionType.DELETE to R.string.tab_delete, ActionType.MOVE to R.string.tab_move, ActionType.RENAME to R.string.tab_rename).forEach { (t, label) ->
            val selected = t == tab
            val bg by animateColorAsState(if (selected) MaterialTheme.colorScheme.surfaceContainerLowest else Color.Transparent, label = "tab")
            Box(
                Modifier.weight(1f).clip(CircleShape).background(bg).clickable { onTab(t) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${stringResource(label)} · ${number(counts[t] ?: 0)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun GroupHeader(title: String, isPath: Boolean, list: List<PlanItem>, collapsed: Boolean, onCollapse: () -> Unit, onGroup: (List<PlanItem>, Boolean) -> Unit, onSkip: (List<PlanItem>) -> Unit) {
    val sel = list.count { it.selected }
    val state = when (sel) { 0 -> ToggleableState.Off; list.size -> ToggleableState.On; else -> ToggleableState.Indeterminate }
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.clickable(onClick = onCollapse).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (isPath) PathText(title) else Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    pluralStringResource(R.plurals.group_summary, list.size, number(list.size), bytes(list.sumOf { it.size })),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { onSkip(list) }) {
                Icon(Icons.Rounded.DoNotDisturbOn, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.skip_all), style = MaterialTheme.typography.labelMedium)
            }
            TriStateCheckbox(state = state, onClick = { onGroup(list, state != ToggleableState.On) })
        }
    }
}

@Composable
private fun ItemRow(item: PlanItem, root: String, onToggle: () -> Unit, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    TidyCard(modifier.fillMaxWidth().padding(vertical = 4.dp), onClick = onClick, padding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FileThumb(item.path, size = 52.dp, isFolder = item.source == PlanSource.LOCAL_EMPTY_FOLDER)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (item.action == ActionType.DELETE) item.fileName else (item.newName ?: item.fileName),
                    style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (item.action != ActionType.DELETE && item.newName != null) {
                    Text(item.fileName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                PathText(item.path.removePrefix("$root/").substringBeforeLast('/', ""))
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (item.size > 0) Tag(bytes(item.size), MaterialTheme.colorScheme.primary)
                    if (item.source == PlanSource.LLM) {
                        Tag(stringResource(llmCategoryLabel(item.category) ?: R.string.src_ai), Brand.Mint)
                    }
                }
                Text(
                    context.reasonText(item, root),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
                )
            }
            Checkbox(item.selected, onCheckedChange = { onToggle() }, colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary))
        }
    }
}

@Composable
private fun AnalysisStatusCard(a: AnalysisState, onCancel: () -> Unit, onDismiss: () -> Unit) {
    TidyCard(Modifier.fillMaxWidth().animateContentSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (a.running) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
            else Icon(if (a.error != null) Icons.Rounded.Close else Icons.Rounded.AutoAwesome, null, tint = if (a.error != null) MaterialTheme.colorScheme.error else Brand.Mint)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                val p = a.progress
                Text(
                    when {
                        a.running -> stringResource(R.string.ai_running)
                        a.finishedSuggestions != null -> stringResource(R.string.ai_done, number(a.finishedSuggestions))
                        else -> stringResource(R.string.ai_failed)
                    },
                    style = MaterialTheme.typography.titleSmall,
                )
                if (p != null && a.running) {
                    Text(stringResource(R.string.ai_progress, number(p.filesDone), number(p.filesTotal), number(p.batchIndex + 1), number(p.batchCount)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!a.running && a.skippedBatches > 0) {
                    Text(stringResource(R.string.ai_skipped, number(a.skippedBatches)), style = MaterialTheme.typography.labelSmall, color = Brand.Amber)
                }
                a.error?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, maxLines = 3) }
            }
            if (a.running) TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
            else IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, stringResource(R.string.close)) }
        }
        if (a.running && a.progress != null && a.progress.filesTotal > 0) {
            Spacer(Modifier.height(10.dp))
            ProgressBar(a.progress.filesDone.toFloat() / a.progress.filesTotal)
        }
    }
}

@Composable
private fun ApplyBar(items: List<PlanItem>, dryRun: Boolean, onApply: () -> Unit) {
    val selected = items.filter { it.selected }
    AnimatedVisibility(selected.isNotEmpty()) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.apply_selected, number(selected.size)), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.apply_frees, bytes(selected.filter { it.action == ActionType.DELETE }.sumOf { it.size })),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                GradientButton(stringResource(if (dryRun) R.string.apply_simulate else R.string.apply), onApply, icon = if (dryRun) Icons.Rounded.Science else Icons.Rounded.TaskAlt)
            }
        }
    }
}

/** Swipe a suggestion sideways (either direction) to skip it. */
@Composable
private fun SwipeToSkip(onSkip: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        onDismiss = { onSkip() },
        backgroundContent = {
            val active = state.targetValue != SwipeToDismissBoxValue.Settled
            Box(
                Modifier.fillMaxSize().padding(vertical = 4.dp).clip(RoundedCornerShape(24.dp))
                    .background(if (active) Brand.Amber.copy(alpha = .9f) else MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 22.dp),
                contentAlignment = if (state.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.DoNotDisturbOn, null, tint = Color.White)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.skip), color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            }
        },
        content = { content() },
    )
}

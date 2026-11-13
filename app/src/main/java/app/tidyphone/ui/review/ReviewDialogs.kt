package app.tidyphone.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.tidyphone.R
import app.tidyphone.data.repo.AnalysisScope
import app.tidyphone.data.settings.AppSettings
import app.tidyphone.data.settings.ProviderType
import app.tidyphone.domain.exec.ItemStatus
import app.tidyphone.domain.model.ActionType
import app.tidyphone.domain.model.FileCategory
import app.tidyphone.domain.model.PlanItem
import app.tidyphone.ui.components.FileThumb
import app.tidyphone.ui.components.GhostButton
import app.tidyphone.ui.components.GradientButton
import app.tidyphone.ui.components.Hairline
import app.tidyphone.ui.components.IconBadge
import app.tidyphone.ui.components.PathText
import app.tidyphone.ui.components.ProgressBar
import app.tidyphone.ui.components.bytes
import app.tidyphone.ui.components.categoryOfName
import app.tidyphone.ui.components.number
import app.tidyphone.ui.components.reasonText
import app.tidyphone.ui.theme.Brand
import coil3.compose.AsyncImage
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisSheet(
    ui: AnalysisSheetUi,
    settings: AppSettings,
    onScope: (AnalysisScope) -> Unit,
    onContent: (Boolean) -> Unit,
    onRun: () -> Unit,
    onDismiss: () -> Unit,
) {
    var privacyAsk by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.AutoAwesome, Brand.Violet)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(stringResource(R.string.ai_sheet_title), style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${if (settings.provider == ProviderType.ANTHROPIC) "Claude" else stringResource(R.string.provider_openai)} · ${settings.activeModel}",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(stringResource(R.string.ai_scope), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(AnalysisScope.ALL to R.string.scope_all, AnalysisScope.DOWNLOADS to R.string.scope_downloads, AnalysisScope.NON_MEDIA to R.string.scope_non_media).forEach { (s, l) ->
                    FilterChip(
                        selected = ui.scope == s,
                        onClick = { onScope(s) },
                        label = { Text("${stringResource(l)} (${number(ui.counts[s] ?: 0)})", maxLines = 1) },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.ai_content), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.ai_content_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(ui.includeContent, onCheckedChange = { on -> if (on) privacyAsk = true else onContent(false) })
            }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).padding(0.dp)) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).padding(0.dp)) {
                    EstimateBox(ui)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.PrivacyTip, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(if (ui.includeContent) R.string.ai_privacy_content else R.string.ai_privacy_metadata),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(18.dp))
            GradientButton(
                stringResource(R.string.ai_run),
                onClick = onRun,
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.AutoAwesome,
                enabled = !ui.preparing && (ui.plan?.estimate?.files ?: 0) > 0,
            )
        }
    }
    if (privacyAsk) {
        AlertDialog(
            onDismissRequest = { privacyAsk = false },
            icon = { Icon(Icons.Rounded.Warning, null, tint = Brand.Amber) },
            title = { Text(stringResource(R.string.privacy_title)) },
            text = { Text(stringResource(R.string.privacy_body)) },
            confirmButton = { TextButton(onClick = { privacyAsk = false; onContent(true) }) { Text(stringResource(R.string.privacy_accept)) } },
            dismissButton = { TextButton(onClick = { privacyAsk = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun EstimateBox(ui: AnalysisSheetUi) {
    val e = ui.plan?.estimate
    androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(stringResource(R.string.ai_estimate), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(10.dp))
            if (ui.preparing || e == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.ai_estimating), style = MaterialTheme.typography.bodySmall)
                }
            } else {
                EstRow(stringResource(R.string.est_files), number(e.files))
                EstRow(stringResource(R.string.est_batches), number(e.batches))
                EstRow(stringResource(R.string.est_tokens), "${number(e.inputTokens)} / ${number(e.outputTokens)}")
                Spacer(Modifier.height(6.dp)); Hairline(); Spacer(Modifier.height(6.dp))
                EstRow(
                    stringResource(R.string.est_cost),
                    e.costUsd?.let { if (it == 0.0) stringResource(R.string.est_free) else "≈ $" + String.format(Locale.US, if (it < 0.01) "%.4f" else "%.2f", it) }
                        ?: stringResource(R.string.est_unknown),
                    bold = true,
                )
            }
        }
    }
}

@Composable
private fun EstRow(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = if (bold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewSheet(item: PlanItem, root: String, onDismiss: () -> Unit, onRemove: () -> Unit) {
    val context = LocalContext.current
    val cat = categoryOfName(item.fileName)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(horizontal = 22.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            if (cat == FileCategory.IMAGE || cat == FileCategory.VIDEO) {
                AsyncImage(
                    model = File(item.path), contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1.4f).clip(RoundedCornerShape(20.dp)),
                )
                Spacer(Modifier.height(16.dp))
            } else {
                FileThumb(item.path, size = 72.dp)
                Spacer(Modifier.height(12.dp))
            }
            Text(item.fileName, style = MaterialTheme.typography.titleLarge)
            PathText(item.path.removePrefix("$root/"), maxLines = 3)
            Spacer(Modifier.height(12.dp))
            Detail(stringResource(R.string.detail_size), bytes(item.size))
            Detail(stringResource(R.string.detail_action), stringResource(when (item.action) { ActionType.DELETE -> R.string.tab_delete; ActionType.MOVE -> R.string.tab_move; ActionType.RENAME -> R.string.tab_rename }))
            item.destinationPath?.let {
                Text(stringResource(R.string.detail_destination), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                PathText(it.removePrefix("$root/"), maxLines = 3)
            }
            Spacer(Modifier.height(10.dp))
            Text(context.reasonText(item, root), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(18.dp))
            GhostButton(stringResource(R.string.skip_suggestion), onRemove, Modifier.fillMaxWidth())
            Text(stringResource(R.string.skip_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ConfirmApplyDialog(items: List<PlanItem>, dryRun: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val sel = items.filter { it.selected }
    val del = sel.filter { it.action == ActionType.DELETE }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(if (dryRun) Icons.Rounded.Science else Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(if (dryRun) R.string.confirm_title_dry else R.string.confirm_title)) },
        text = {
            Column {
                Text(stringResource(R.string.confirm_counts, number(del.size), number(sel.count { it.action == ActionType.MOVE }), number(sel.count { it.action == ActionType.RENAME })))
                Spacer(Modifier.height(8.dp))
                Text(stringResource(if (dryRun) R.string.confirm_body_dry else R.string.confirm_body, bytes(del.sumOf { it.size })), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(if (dryRun) R.string.apply_simulate else R.string.apply)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun ExecutionDialog(exec: ExecUi, onUndo: () -> Unit, onClose: () -> Unit) {
    val r = exec.report
    AlertDialog(
        onDismissRequest = { if (!exec.running) onClose() },
        title = {
            Text(stringResource(when {
                exec.running -> R.string.exec_running
                r?.dryRun == true -> R.string.exec_dry_done
                exec.undone -> R.string.exec_undone
                else -> R.string.exec_done
            }))
        },
        text = {
            Column {
                if (exec.running) {
                    ProgressBar(if (exec.total == 0) 0f else exec.done.toFloat() / exec.total)
                    Spacer(Modifier.height(8.dp))
                    Text("${number(exec.done)} / ${number(exec.total)}", style = MaterialTheme.typography.bodySmall)
                } else if (r != null) {
                    Text(stringResource(R.string.exec_summary, number(r.done), number(r.skipped), number(r.failed)))
                    if (r.bytesTrashed > 0) Text(stringResource(if (r.dryRun) R.string.exec_would_free else R.string.exec_freed, bytes(r.bytesTrashed)), style = MaterialTheme.typography.titleSmall, color = Brand.Mint)
                    val problems = r.results.filter { it.status == ItemStatus.FAILED || it.status == ItemStatus.SKIPPED }.take(4)
                    if (problems.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        problems.forEach { Text("• ${it.item.fileName}: ${it.message}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    if (!r.dryRun && !exec.undone && r.done > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.exec_trash_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = { if (!exec.running) TextButton(onClick = onClose) { Text(stringResource(R.string.done)) } },
        dismissButton = {
            if (!exec.running && r != null && !r.dryRun && !exec.undone && r.done > 0) {
                TextButton(onClick = onUndo) {
                    Icon(Icons.AutoMirrored.Rounded.Undo, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.undo_all))
                }
            }
        },
    )
}

@Composable
fun NeedKeyDialog(onSettings: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Key, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(R.string.need_key_title)) },
        text = { Text(stringResource(R.string.need_key_body)) },
        confirmButton = { TextButton(onClick = onSettings) { Text(stringResource(R.string.open_settings)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

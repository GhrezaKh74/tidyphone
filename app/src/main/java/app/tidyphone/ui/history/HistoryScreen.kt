package app.tidyphone.ui.history

import android.text.format.DateUtils
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.tidyphone.R
import app.tidyphone.data.repo.HistoryRepository
import app.tidyphone.data.repo.IndexRepository
import app.tidyphone.domain.exec.UndoResult
import app.tidyphone.domain.model.ActionType
import app.tidyphone.domain.model.HistoryRecord
import app.tidyphone.ui.components.EmptyState
import app.tidyphone.ui.components.GlowBackground
import app.tidyphone.ui.components.IconBadge
import app.tidyphone.ui.components.PathText
import app.tidyphone.ui.components.Tag
import app.tidyphone.ui.components.TidyCard
import app.tidyphone.ui.components.bytes
import app.tidyphone.ui.components.number
import app.tidyphone.ui.theme.Brand
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(private val repo: HistoryRepository, index: IndexRepository) : ViewModel() {
    val root = index.root
    val records: StateFlow<List<HistoryRecord>> = repo.records.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    suspend fun undo(id: Long): UndoResult = repo.undo(id)
    suspend fun undoBatch(batch: String): List<UndoResult> = repo.undoBatch(batch)
}

@Composable
fun HistoryScreen(vm: HistoryViewModel = hiltViewModel()) {
    val records by vm.records.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    fun report(results: List<UndoResult>) = scope.launch {
        val ok = results.count { it is UndoResult.Restored }
        val renamed = results.count { it is UndoResult.Restored && it.renamed }
        val failed = results.size - ok - results.count { it is UndoResult.AlreadyUndone }
        snack.showSnackbar(
            buildString {
                append(context.getString(R.string.undo_result, ok))
                if (renamed > 0) append(" · ").append(context.getString(R.string.undo_renamed, renamed))
                if (failed > 0) append(" · ").append(context.getString(R.string.undo_failed, failed))
            }
        )
    }
    HistoryContent(
        records, vm.root,
        onUndo = { id -> scope.launch { report(listOf(vm.undo(id))) } },
        onUndoBatch = { b -> scope.launch { report(vm.undoBatch(b)) } },
        snack = snack,
    )
}

@Composable
fun HistoryContent(records: List<HistoryRecord>, root: String, onUndo: (Long) -> Unit, onUndoBatch: (String) -> Unit, snack: SnackbarHostState = remember { SnackbarHostState() }) {
    val batches = records.groupBy { it.batchId }.toList().sortedByDescending { (_, l) -> l.maxOf { it.timestamp } }
    Box(Modifier.fillMaxSize()) {
        GlowBackground()
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Text(stringResource(R.string.history_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.history_sub), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (records.isEmpty()) item { EmptyState(Icons.Rounded.History, stringResource(R.string.history_empty_title), stringResource(R.string.history_empty_body)) }
            batches.forEach { (batch, list) ->
                item(key = batch) { BatchCard(list, root, onUndo, onUndoBatch) }
            }
        }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}

@Composable
private fun BatchCard(list: List<HistoryRecord>, root: String, onUndo: (Long) -> Unit, onUndoBatch: (String) -> Unit) {
    val context = LocalContext.current
    val ts = list.maxOf { it.timestamp }
    val pending = list.count { !it.undone }
    TidyCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(DateUtils.formatDateTime(context, ts, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.history_batch, number(list.size), bytes(list.filter { it.action == ActionType.DELETE }.sumOf { it.size })), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (pending > 1) TextButton(onClick = { onUndoBatch(list.first().batchId) }) { Text(stringResource(R.string.undo_all)) }
        }
        list.sortedByDescending { it.id }.take(50).forEach { r ->
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (icon, color) = when (r.action) {
                    ActionType.DELETE -> Icons.Rounded.DeleteOutline to Brand.Coral
                    ActionType.MOVE -> Icons.AutoMirrored.Rounded.DriveFileMove to Brand.Cyan
                    ActionType.RENAME -> Icons.Rounded.DriveFileRenameOutline to Brand.Amber
                }
                IconBadge(icon, color, size = 38.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.originalPath.substringAfterLast('/'), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    PathText(
                        if (r.action == ActionType.DELETE) r.originalPath.removePrefix("$root/")
                        else "→ " + r.resultPath.removePrefix("$root/"),
                    )
                }
                if (r.undone) Tag(stringResource(R.string.undone), MaterialTheme.colorScheme.onSurfaceVariant)
                else TextButton(onClick = { onUndo(r.id) }) {
                    androidx.compose.material3.Icon(Icons.AutoMirrored.Rounded.Undo, null, modifier = Modifier.padding(end = 4.dp))
                    Text(stringResource(R.string.undo))
                }
            }
        }
        if (list.size > 50) Text("+${number(list.size - 50)}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))
    }
}

package app.tidyphone.ui.trash

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.tidyphone.R
import app.tidyphone.data.repo.IndexRepository
import app.tidyphone.data.repo.TrashRepository
import app.tidyphone.data.settings.SettingsRepository
import app.tidyphone.domain.model.TrashItem
import app.tidyphone.ui.components.EmptyState
import app.tidyphone.ui.components.FileThumb
import app.tidyphone.ui.components.GhostButton
import app.tidyphone.ui.components.GlowBackground
import app.tidyphone.ui.components.PathText
import app.tidyphone.ui.components.Tag
import app.tidyphone.ui.components.TidyCard
import app.tidyphone.ui.components.bytes
import app.tidyphone.ui.components.number
import app.tidyphone.ui.theme.Brand
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class TrashViewModel @Inject constructor(private val repo: TrashRepository, settings: SettingsRepository, index: IndexRepository) : ViewModel() {
    val root = index.root
    val items: StateFlow<List<TrashItem>> = repo.items.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val retentionDays: StateFlow<Int> = settings.settings.map { it.trashRetentionDays }.stateIn(viewModelScope, SharingStarted.Eagerly, 30)
    fun restore(t: TrashItem) = viewModelScope.launch { repo.restore(t) }
    fun purge(t: TrashItem) = viewModelScope.launch { repo.purge(listOf(t)) }
    fun emptyAll() = viewModelScope.launch { repo.emptyAll() }
}

@Composable
fun TrashScreen(vm: TrashViewModel = hiltViewModel()) {
    val items by vm.items.collectAsStateWithLifecycle()
    val days by vm.retentionDays.collectAsStateWithLifecycle()
    TrashContent(items, days, vm.root, vm::restore, vm::purge, vm::emptyAll)
}

@Composable
fun TrashContent(items: List<TrashItem>, days: Int, root: String, onRestore: (TrashItem) -> Unit, onPurge: (TrashItem) -> Unit, onEmpty: () -> Unit) {
    var confirmEmpty by remember { mutableStateOf(false) }
    var confirmOne by remember { mutableStateOf<TrashItem?>(null) }
    Box(Modifier.fillMaxSize()) {
        GlowBackground()
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text(stringResource(R.string.trash_title), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Brand.dangerGradient).padding(20.dp)) {
                    Column {
                        Text(stringResource(R.string.trash_holding), style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = .85f))
                        Text(bytes(items.sumOf { it.size }), style = MaterialTheme.typography.displaySmall, color = Color.White)
                        Text(pluralStringResource(R.plurals.n_files, items.size, number(items.size)), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .85f))
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Schedule, null, tint = Color.White, modifier = Modifier.padding(end = 6.dp))
                            Text(stringResource(R.string.trash_auto, number(days)), style = MaterialTheme.typography.labelMedium, color = Color.White)
                        }
                    }
                }
                if (items.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    GhostButton(stringResource(R.string.trash_empty_now), { confirmEmpty = true }, Modifier.fillMaxWidth(), icon = Icons.Rounded.DeleteSweep)
                }
                Spacer(Modifier.height(4.dp))
            }
            if (items.isEmpty()) item { EmptyState(Icons.Rounded.DeleteSweep, stringResource(R.string.trash_empty_title), stringResource(R.string.trash_empty_body)) }
            items(items, key = { it.id }) { t ->
                val left = (days - TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - t.trashedAt)).coerceAtLeast(0)
                TidyCard(Modifier.fillMaxWidth(), padding = PaddingValues(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FileThumb(t.trashPath, size = 52.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.originalPath.substringAfterLast('/'), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            PathText(t.originalPath.removePrefix("$root/").substringBeforeLast('/', ""))
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Tag(bytes(t.size), MaterialTheme.colorScheme.primary)
                                Tag(stringResource(R.string.trash_days_left, number(left)), if (left <= 3) Brand.Coral else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        IconButton(onClick = { onRestore(t) }) { Icon(Icons.Rounded.Restore, stringResource(R.string.restore), tint = Brand.Mint) }
                        IconButton(onClick = { confirmOne = t }) { Icon(Icons.Rounded.DeleteForever, stringResource(R.string.delete_forever), tint = Brand.Coral) }
                    }
                }
            }
        }
    }
    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            icon = { Icon(Icons.Rounded.DeleteForever, null, tint = Brand.Coral) },
            title = { Text(stringResource(R.string.trash_empty_confirm_title)) },
            text = { Text(stringResource(R.string.trash_empty_confirm_body, number(items.size), bytes(items.sumOf { it.size }))) },
            confirmButton = { TextButton(onClick = { confirmEmpty = false; onEmpty() }) { Text(stringResource(R.string.delete_forever), color = Brand.Coral) } },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    confirmOne?.let { t ->
        AlertDialog(
            onDismissRequest = { confirmOne = null },
            title = { Text(stringResource(R.string.delete_forever_q)) },
            text = { Text(t.originalPath.substringAfterLast('/')) },
            confirmButton = { TextButton(onClick = { confirmOne = null; onPurge(t) }) { Text(stringResource(R.string.delete_forever), color = Brand.Coral) } },
            dismissButton = {
                TextButton(onClick = { confirmOne = null; onRestore(t) }) {
                    Icon(Icons.AutoMirrored.Rounded.Undo, null, modifier = Modifier.padding(end = 4.dp))
                    Text(stringResource(R.string.restore))
                }
            },
        )
    }
}

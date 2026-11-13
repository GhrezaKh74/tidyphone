package app.tidyphone.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tidyphone.R
import app.tidyphone.ui.components.GlowBackground
import app.tidyphone.ui.components.PathText
import app.tidyphone.ui.theme.Brand
import kotlinx.coroutines.delay

@Composable
fun RulesScreen(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val settings = s ?: return
    var rules by remember { mutableStateOf(settings.customRules) }
    var newFolder by remember { mutableStateOf("") }

    // Save the free-text rules shortly after typing stops.
    LaunchedEffect(rules) {
        delay(500)
        if (rules != settings.customRules) vm.update { it.copy(customRules = rules) }
    }

    Box(Modifier.fillMaxSize()) {
        GlowBackground()
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(start = 12.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
                    Text(stringResource(R.string.rules_title), style = MaterialTheme.typography.headlineSmall)
                }
            }
            item {
                Box(Modifier.padding(start = 8.dp)) {
                    Section(Icons.Rounded.EditNote, stringResource(R.string.rules_custom), Brand.Violet) {
                        Text(stringResource(R.string.rules_custom_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = rules,
                            onValueChange = { rules = it },
                            placeholder = { Text(stringResource(R.string.rules_placeholder)) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.rules_examples), style = MaterialTheme.typography.labelMedium)
                        listOf(R.string.rule_ex1, R.string.rule_ex2, R.string.rule_ex3).forEach { ex ->
                            val text = stringResource(ex)
                            SuggestionChip(onClick = { rules = (rules.trimEnd() + "\n" + text).trim() }, label = { Text(text) }, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
            item {
                Box(Modifier.padding(start = 8.dp)) {
                    Section(Icons.Rounded.Block, stringResource(R.string.rules_excludes), Brand.Coral) {
                        Text(stringResource(R.string.rules_excludes_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(10.dp))
                        settings.excludeFolders.forEach { f ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Folder, null, tint = Brand.Coral)
                                Spacer(Modifier.width(10.dp))
                                PathText(f, Modifier.weight(1f))
                                IconButton(onClick = { vm.update { it.copy(excludeFolders = it.excludeFolders - f) } }) { Icon(Icons.Rounded.Close, stringResource(R.string.remove)) }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = newFolder, onValueChange = { newFolder = it }, singleLine = true,
                                placeholder = { Text("Documents/Work") }, modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(8.dp))
                            TextButton(
                                onClick = {
                                    val f = newFolder.trim().trim('/')
                                    if (f.isNotEmpty() && !f.contains("..")) vm.update { it.copy(excludeFolders = (it.excludeFolders + f).distinct()) }
                                    newFolder = ""
                                },
                                enabled = newFolder.isNotBlank(),
                            ) { Icon(Icons.Rounded.Add, null); Text(stringResource(R.string.add)) }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(R.string.rules_always_protected), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}


package app.tidyphone.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tidyphone.BuildConfig
import app.tidyphone.R
import app.tidyphone.data.settings.AppSettings
import app.tidyphone.data.settings.ProviderType
import app.tidyphone.domain.llm.Pricing
import app.tidyphone.ui.components.GhostButton
import app.tidyphone.ui.components.GlowBackground
import app.tidyphone.ui.components.IconBadge
import app.tidyphone.ui.components.TidyCard
import app.tidyphone.ui.components.number
import app.tidyphone.ui.theme.Brand
import app.tidyphone.ui.theme.ThemeMode
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(onOpenRules: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val keys by vm.keySaved.collectAsStateWithLifecycle()
    val test by vm.test.collectAsStateWithLifecycle()
    val settings = s ?: return
    SettingsContent(settings, keys, test, vm::update, vm::saveKey, vm::clearKey, vm::testConnection, onOpenRules)
}

@Composable
fun SettingsContent(
    s: AppSettings,
    keys: Map<ProviderType, Boolean>,
    test: TestState,
    update: ((AppSettings) -> AppSettings) -> Unit,
    saveKey: (ProviderType, String) -> Unit,
    clearKey: (ProviderType) -> Unit,
    onTest: () -> Unit,
    onOpenRules: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        GlowBackground()
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium) }
            item { ProviderCard(s, keys, test, update, saveKey, clearKey, onTest) }
            item {
                TidyCard(Modifier.fillMaxWidth(), onClick = onOpenRules) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.Gavel, Brand.Amber)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.rules_title), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.rules_summary, number(s.customRules.lines().count { it.isNotBlank() }), number(s.excludeFolders.size)),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
                    }
                }
            }
            item {
                Section(Icons.Rounded.Science, stringResource(R.string.sec_cleanup), Brand.Cyan) {
                    SliderRow(stringResource(R.string.set_large, number(s.largeFileMb)), s.largeFileMb.toFloat(), 25f..2000f) { v -> update { it.copy(largeFileMb = (v / 25).roundToInt() * 25) } }
                    SliderRow(stringResource(R.string.set_old_downloads, number(s.oldDownloadDays)), s.oldDownloadDays.toFloat(), 7f..365f) { v -> update { it.copy(oldDownloadDays = v.roundToInt()) } }
                    SliderRow(stringResource(R.string.set_old_screens, number(s.oldScreenshotDays)), s.oldScreenshotDays.toFloat(), 7f..365f) { v -> update { it.copy(oldScreenshotDays = v.roundToInt()) } }
                    SliderRow(stringResource(R.string.set_trash_days, number(s.trashRetentionDays)), s.trashRetentionDays.toFloat(), 1f..90f) { v -> update { it.copy(trashRetentionDays = v.roundToInt()) } }
                    SwitchRow(stringResource(R.string.set_dry_run), stringResource(R.string.set_dry_run_desc), s.dryRun) { on -> update { it.copy(dryRun = on) } }
                }
            }
            item {
                Section(Icons.Rounded.DarkMode, stringResource(R.string.sec_appearance), Brand.Violet) {
                    Text(stringResource(R.string.set_theme), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    Segmented(listOf(ThemeMode.SYSTEM to R.string.theme_system, ThemeMode.LIGHT to R.string.theme_light, ThemeMode.DARK to R.string.theme_dark), s.themeMode) { m -> update { it.copy(themeMode = m) } }
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Language, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.set_language), style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(Modifier.height(8.dp))
                    val current = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-').ifEmpty { "" }
                    Segmented(listOf("" to R.string.lang_system, "en" to R.string.lang_en, "fa" to R.string.lang_fa), current) { tag ->
                        AppCompatDelegate.setApplicationLocales(if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag))
                    }
                }
            }
            item {
                Section(Icons.Rounded.Lock, stringResource(R.string.sec_privacy), Brand.Mint) {
                    Text(stringResource(R.string.privacy_text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Text(
                    "TidyPhone ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderCard(
    s: AppSettings,
    keys: Map<ProviderType, Boolean>,
    test: TestState,
    update: ((AppSettings) -> AppSettings) -> Unit,
    saveKey: (ProviderType, String) -> Unit,
    clearKey: (ProviderType) -> Unit,
    onTest: () -> Unit,
) {
    Section(Icons.Rounded.Cloud, stringResource(R.string.sec_ai), Brand.Violet) {
        Segmented(listOf(ProviderType.ANTHROPIC to R.string.provider_claude, ProviderType.OPENAI_COMPAT to R.string.provider_openai), s.provider) { p -> update { it.copy(provider = p) } }
        Spacer(Modifier.height(14.dp))
        if (s.provider == ProviderType.ANTHROPIC) {
            Field(stringResource(R.string.base_url), s.anthropicBaseUrl, KeyboardType.Uri) { v -> update { it.copy(anthropicBaseUrl = v) } }
            Spacer(Modifier.height(10.dp))
            var open by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(open, onExpandedChange = { open = it }) {
                OutlinedTextField(
                    value = s.anthropicModel,
                    onValueChange = { v -> update { it.copy(anthropicModel = v) } },
                    label = { Text(stringResource(R.string.model)) },
                    singleLine = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                )
                ExposedDropdownMenu(open, onDismissRequest = { open = false }) {
                    Pricing.anthropicModels.forEach { m ->
                        DropdownMenuItem(text = { Text(m) }, onClick = { update { it.copy(anthropicModel = m) }; open = false })
                    }
                }
            }
        } else {
            Field(stringResource(R.string.base_url), s.openAiBaseUrl, KeyboardType.Uri) { v -> update { it.copy(openAiBaseUrl = v) } }
            Text(stringResource(R.string.base_url_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(10.dp))
            Field(stringResource(R.string.model), s.openAiModel) { v -> update { it.copy(openAiModel = v) } }
            Spacer(Modifier.height(6.dp))
            SwitchRow(stringResource(R.string.json_mode), stringResource(R.string.json_mode_desc), s.openAiJsonMode) { on -> update { it.copy(openAiJsonMode = on) } }
        }
        Spacer(Modifier.height(12.dp))
        KeyField(s.provider, keys[s.provider] == true, saveKey, clearKey)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberField(stringResource(R.string.price_in), s.priceInputPerM, Modifier.weight(1f)) { v -> update { it.copy(priceInputPerM = v) } }
            NumberField(stringResource(R.string.price_out), s.priceOutputPerM, Modifier.weight(1f)) { v -> update { it.copy(priceOutputPerM = v) } }
        }
        Text(stringResource(R.string.price_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(6.dp))
        SliderRow(stringResource(R.string.batch_budget, number(s.maxInputTokensPerBatch)), s.maxInputTokensPerBatch.toFloat(), 2000f..30000f) { v -> update { it.copy(maxInputTokensPerBatch = (v / 500).roundToInt() * 500) } }
        Spacer(Modifier.height(8.dp))
        GhostButton(stringResource(R.string.test_connection), onTest, Modifier.fillMaxWidth(), icon = Icons.Rounded.NetworkCheck, enabled = test != TestState.Running)
        AnimatedVisibility(test != TestState.Idle) {
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                when (test) {
                    TestState.Running -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    is TestState.Ok -> Icon(Icons.Rounded.CheckCircle, null, tint = Brand.Mint)
                    is TestState.Error -> Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                    TestState.Idle -> Unit
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    when (test) {
                        TestState.Running -> stringResource(R.string.testing)
                        is TestState.Ok -> stringResource(R.string.test_ok, test.message)
                        is TestState.Error -> test.message
                        TestState.Idle -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (test is TestState.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun KeyField(provider: ProviderType, saved: Boolean, saveKey: (ProviderType, String) -> Unit, clearKey: (ProviderType) -> Unit) {
    var value by remember(provider) { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = { value = it },
        label = { Text(stringResource(if (provider == ProviderType.OPENAI_COMPAT) R.string.api_key_optional else R.string.api_key)) },
        placeholder = { if (saved) Text(stringResource(R.string.key_saved_placeholder)) },
        leadingIcon = { Icon(Icons.Rounded.Key, null) },
        trailingIcon = { IconButton(onClick = { visible = !visible }) { Icon(if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null) } },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Lock, null, tint = if (saved) Brand.Mint else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            stringResource(if (saved) R.string.key_saved else R.string.key_not_saved),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
        )
        if (saved) TextButton(onClick = { clearKey(provider) }) { Icon(Icons.Rounded.Delete, null, modifier = Modifier.size(16.dp)); Text(stringResource(R.string.remove)) }
        TextButton(onClick = { saveKey(provider, value); value = "" }, enabled = value.isNotBlank()) { Text(stringResource(R.string.save)) }
    }
}

@Composable
fun Section(icon: ImageVector, title: String, tint: androidx.compose.ui.graphics.Color, content: @Composable ColumnScope.() -> Unit) {
    TidyCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, tint, size = 38.dp)
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(16.dp))
        content()
    }
}

@Composable
private fun Field(label: String, value: String, type: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = type, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun NumberField(label: String, value: Double?, modifier: Modifier, onChange: (Double?) -> Unit) {
    var text by remember(value == null) { mutableStateOf(value?.toString().orEmpty()) }
    OutlinedTextField(
        value = text,
        onValueChange = { t -> text = t; onChange(t.replace(',', '.').toDoubleOrNull()) },
        label = { Text(label, maxLines = 1) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    var local by remember(value) { mutableStateOf(value) }
    Text(label, style = MaterialTheme.typography.bodyMedium)
    Slider(
        value = local, onValueChange = { local = it }, onValueChangeFinished = { onChange(local) }, valueRange = range,
        colors = androidx.compose.material3.SliderDefaults.colors(
            inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            thumbColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

@Composable
private fun SwitchRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Segmented(options: List<Pair<T, Int>>, selected: T, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (value, label) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                label = { Text(stringResource(label), maxLines = 1) },
            )
        }
    }
}

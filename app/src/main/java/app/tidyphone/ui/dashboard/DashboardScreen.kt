package app.tidyphone.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tidyphone.R
import app.tidyphone.domain.model.PlanSource
import app.tidyphone.ui.components.GhostButton
import app.tidyphone.ui.components.GlowBackground
import app.tidyphone.ui.components.GradientButton
import app.tidyphone.ui.components.IconBadge
import app.tidyphone.ui.components.ProgressBar
import app.tidyphone.ui.components.RingSegment
import app.tidyphone.ui.components.SectionTitle
import app.tidyphone.ui.components.StorageRing
import app.tidyphone.ui.components.TidyCard
import app.tidyphone.ui.components.bytes
import app.tidyphone.ui.components.color
import app.tidyphone.ui.components.icon
import app.tidyphone.ui.components.label
import app.tidyphone.ui.components.number
import app.tidyphone.ui.components.percent
import app.tidyphone.ui.theme.Brand

@Composable
fun DashboardScreen(onScan: () -> Unit, onReview: () -> Unit, onAi: () -> Unit, onSettings: () -> Unit, onApps: () -> Unit = {}, onAlbums: () -> Unit = {}, vm: DashboardViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) { vm.refreshStorage(); onPauseOrDispose { } }
    val withNotifications = app.tidyphone.ui.rememberWithNotificationPermission()
    DashboardContent(s, onScan = { withNotifications { vm.scan(); onScan() } }, onReview = onReview, onSettings = onSettings, onAi = onAi, onApps = onApps, onAlbums = onAlbums)
}

@Composable
fun DashboardContent(s: DashboardState, onScan: () -> Unit, onReview: () -> Unit, onSettings: () -> Unit, onAi: () -> Unit = onReview, onApps: () -> Unit = {}, onAlbums: () -> Unit = {}) {
    Box(Modifier.fillMaxSize()) {
        GlowBackground()
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.dash_hello), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
                    }
                    IconButton(onClick = onSettings) { Icon(Icons.Rounded.Tune, stringResource(R.string.nav_settings)) }
                }
            }
            item { HeroCard(s) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GradientButton(
                        text = stringResource(if (s.scan.running) R.string.dash_scanning else R.string.dash_scan),
                        icon = Icons.Rounded.Radar,
                        onClick = onScan,
                        modifier = Modifier.weight(1f),
                    )
                    GhostButton(
                        text = stringResource(R.string.dash_ai),
                        icon = Icons.Rounded.AutoAwesome,
                        onClick = onAi,
                        modifier = Modifier.weight(1f),
                        enabled = s.indexedFiles > 0,
                    )
                }
            }
            item {
                AnimatedVisibility(s.pendingActions > 0 || s.reclaimableBytes > 0) { ReadyCard(s, onReview) }
            }
            item { AlbumsCard(enabled = s.indexedFiles > 0, onClick = onAlbums) }
            item { AppsCard(s.apps, onApps) }
            if (s.insights.any { it.count > 0 }) {
                item { SectionTitle(stringResource(R.string.dash_insights)) }
                item { InsightsGrid(s.insights, onReview) }
            }
            if (s.categories.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.dash_by_category)) }
                item { CategoryCard(s) }
            } else if (s.loaded && !s.scan.running) {
                item { FirstScanCard(onScan) }
            }
        }
    }
}

@Composable
private fun HeroCard(s: DashboardState) {
    val total = s.storage.totalBytes.coerceAtLeast(1)
    val segments = s.categories.map { RingSegment(it.bytes.toFloat() / total, it.category.color()) } +
        RingSegment(((s.storage.usedBytes - s.categories.sumOf { it.bytes }).coerceAtLeast(0)).toFloat() / total, Color.White.copy(alpha = .55f))
    val usedPct = (s.storage.usedBytes * 100 / total).toInt()
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(Brand.heroGradient).padding(22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StorageRing(segments, Modifier.size(136.dp), stroke = 14.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(percent(usedPct / 100.0), style = MaterialTheme.typography.headlineMedium, color = Color.White)
                    Text(stringResource(R.string.dash_used), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = .8f))
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.dash_storage), style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = .85f))
                Spacer(Modifier.height(4.dp))
                Text(bytes(s.storage.usedBytes), style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Text(stringResource(R.string.dash_of_total, bytes(s.storage.totalBytes)), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .8f))
                Spacer(Modifier.height(12.dp))
                Box(Modifier.clip(CircleShape).background(Color.White.copy(alpha = .18f)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(stringResource(R.string.dash_free, bytes(s.storage.freeBytes)), style = MaterialTheme.typography.labelMedium, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ReadyCard(s: DashboardState, onReview: () -> Unit) {
    TidyCard(Modifier.fillMaxWidth(), onClick = onReview) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(18.dp)).background(Brand.mintGradient), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.dash_ready_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.dash_ready_body, bytes(s.reclaimableBytes), number(s.pendingActions)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun PlanSource.insightIcon(): ImageVector = when (this) {
    PlanSource.LOCAL_DUPLICATE -> Icons.Rounded.ContentCopy
    PlanSource.LOCAL_TEMP -> Icons.Rounded.HourglassBottom
    PlanSource.LOCAL_LARGE -> Icons.Rounded.Straighten
    PlanSource.LOCAL_APK -> Icons.Rounded.Android
    PlanSource.LOCAL_OLD_DOWNLOAD -> Icons.Rounded.Download
    else -> Icons.Rounded.Screenshot
}

private fun PlanSource.insightColor(): Color = when (this) {
    PlanSource.LOCAL_DUPLICATE -> Brand.Violet
    PlanSource.LOCAL_TEMP -> Brand.Amber
    PlanSource.LOCAL_LARGE -> Brand.Coral
    PlanSource.LOCAL_APK -> Brand.Mint
    PlanSource.LOCAL_OLD_DOWNLOAD -> Brand.Cyan
    else -> Color(0xFFB07CFF)
}

@Composable
private fun InsightsGrid(insights: List<Insight>, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        insights.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { i ->
                    TidyCard(Modifier.weight(1f), onClick = onClick, padding = PaddingValues(16.dp)) {
                        IconBadge(i.source.insightIcon(), i.source.insightColor(), size = 40.dp)
                        Spacer(Modifier.height(12.dp))
                        Text(bytes(i.bytes), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(i.source.label()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        Text(pluralStringResource(R.plurals.n_files, i.count, number(i.count)), style = MaterialTheme.typography.labelSmall, color = i.source.insightColor())
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CategoryCard(s: DashboardState) {
    val max = s.categories.maxOfOrNull { it.bytes }?.coerceAtLeast(1) ?: 1
    TidyCard(Modifier.fillMaxWidth()) {
        s.categories.forEachIndexed { i, c ->
            if (i > 0) Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(c.category.icon(), c.category.color(), size = 38.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(c.category.label()), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(bytes(c.bytes), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(6.dp))
                    ProgressBar(c.bytes.toFloat() / max, brush = androidx.compose.ui.graphics.SolidColor(c.category.color()), height = 6.dp)
                    Spacer(Modifier.height(4.dp))
                    Text(pluralStringResource(R.plurals.n_files, c.count, number(c.count)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun FirstScanCard(onScan: () -> Unit) {
    TidyCard(Modifier.fillMaxWidth(), onClick = onScan) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Radar, Brand.Violet)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.dash_first_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.dash_first_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AppsCard(summary: app.tidyphone.data.repo.AppsSummary?, onClick: () -> Unit) {
    TidyCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.SportsEsports, Brand.Coral)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.apps_card_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    if (summary == null) stringResource(R.string.apps_card_body)
                    else androidx.compose.ui.res.pluralStringResource(R.plurals.apps_card_summary, summary.unusedCount, number(summary.unusedCount), bytes(summary.unusedBytes)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun AlbumsCard(enabled: Boolean, onClick: () -> Unit) {
    TidyCard(Modifier.fillMaxWidth(), onClick = if (enabled) onClick else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Brand.heroGradient), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PhotoLibrary, null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.albums_card_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(if (enabled) R.string.albums_card_body else R.string.dash_first_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

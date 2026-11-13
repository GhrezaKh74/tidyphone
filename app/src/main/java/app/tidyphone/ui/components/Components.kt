package app.tidyphone.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tidyphone.ui.theme.Brand

/** Rounded card with a hairline outline in dark mode. */
@Composable
fun TidyCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val dark = isSystemInDarkThemeCompat()
    val shape = RoundedCornerShape(24.dp)
    Surface(
        modifier = modifier,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = if (dark) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
        shadowElevation = if (dark) 0.dp else 1.dp,
        onClick = onClick ?: {},
        enabled = onClick != null,
    ) {
        Column(Modifier.padding(padding), content = content)
    }
}

@Composable
fun isSystemInDarkThemeCompat(): Boolean = MaterialTheme.colorScheme.background.luminanceApprox() < 0.5f

private fun Color.luminanceApprox(): Float = 0.299f * red + 0.587f * green + 0.114f * blue

/** Pill button filled with a gradient. */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    brush: Brush = Brand.heroGradient,
    enabled: Boolean = true,
    trailingIcon: ImageVector? = null,
) {
    Box(
        modifier
            .heightIn(min = 54.dp)
            .clip(CircleShape)
            .background(if (enabled) brush else Brush.linearGradient(listOf(Color.Gray.copy(alpha = .35f), Color.Gray.copy(alpha = .35f))))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (icon != null) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(text, color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (trailingIcon != null) {
                Spacer(Modifier.width(10.dp))
                Icon(trailingIcon, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** Secondary pill with outline. */
@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 54.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (icon != null) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Rounded icon badge with a soft tinted background. */
@Composable
fun IconBadge(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.5f)) }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable RowScope.() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        action?.invoke(this)
    }
}

/** A path always reads left to right, even inside RTL text. */
@Composable
fun PathText(path: String, modifier: Modifier = Modifier, maxLines: Int = 1) {
    Text(
        path,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = maxLines,
        overflow = TextOverflow.MiddleEllipsis,
    )
}

data class RingSegment(val fraction: Float, val color: Color)

/** Animated donut chart. Segments are fractions of the full circle. */
@Composable
fun StorageRing(
    segments: List<RingSegment>,
    modifier: Modifier = Modifier,
    stroke: Dp = 16.dp,
    track: Color = Color.White.copy(alpha = 0.18f),
    center: @Composable BoxScope.() -> Unit = {},
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(segments) { progress.snapTo(0f); progress.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            val arcSize = Size(size.minDimension - s, size.minDimension - s)
            val topLeft = Offset((size.width - arcSize.width) / 2, (size.height - arcSize.height) / 2)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(s))
            var start = -90f
            val gap = 2.5f
            segments.filter { it.fraction > 0.004f }.forEach { seg ->
                val sweep = (seg.fraction * 360f * progress.value - gap).coerceAtLeast(0.5f)
                drawArc(seg.color, start, sweep, false, topLeft, arcSize, style = Stroke(s, cap = StrokeCap.Round))
                start += seg.fraction * 360f * progress.value
            }
        }
        center()
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(96.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Brand.Violet.copy(alpha = .35f), Color.Transparent))),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp)) }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) { Spacer(Modifier.height(20.dp)); action() }
    }
}

/** Small rounded label. */
@Composable
fun Tag(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 3.dp),
        color = color,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
    )
}

/** Soft glow blobs behind screen headers. */
@Composable
fun GlowBackground(modifier: Modifier = Modifier) {
    val dark = isSystemInDarkThemeCompat()
    Canvas(modifier.fillMaxWidth().height(360.dp)) {
        drawCircle(Brush.radialGradient(listOf(Brand.Violet.copy(alpha = if (dark) .28f else .16f), Color.Transparent), center = Offset(size.width * .15f, size.height * .1f), radius = size.width * .7f), radius = size.width * .7f, center = Offset(size.width * .15f, size.height * .1f))
        drawCircle(Brush.radialGradient(listOf(Brand.Cyan.copy(alpha = if (dark) .18f else .12f), Color.Transparent), center = Offset(size.width * .95f, size.height * .25f), radius = size.width * .55f), radius = size.width * .55f, center = Offset(size.width * .95f, size.height * .25f))
    }
}

@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier, brush: Brush = Brand.heroGradient, height: Dp = 8.dp, track: Color = MaterialTheme.colorScheme.surfaceContainerHighest) {
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(track)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(height).clip(CircleShape).background(brush))
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

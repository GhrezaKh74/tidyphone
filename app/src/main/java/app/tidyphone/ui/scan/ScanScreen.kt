package app.tidyphone.ui.scan

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.tidyphone.R
import app.tidyphone.data.repo.ScanPhase
import app.tidyphone.data.repo.ScanRepository
import app.tidyphone.data.repo.ScanState
import app.tidyphone.ui.components.GhostButton
import app.tidyphone.ui.components.GlowBackground
import app.tidyphone.ui.components.GradientButton
import app.tidyphone.ui.components.PathText
import app.tidyphone.ui.components.ProgressBar
import app.tidyphone.ui.components.TidyCard
import app.tidyphone.ui.components.bytes
import app.tidyphone.ui.components.number
import app.tidyphone.ui.theme.Brand
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ScanViewModel @Inject constructor(private val repo: ScanRepository) : ViewModel() {
    val state: StateFlow<ScanState> = repo.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ScanState(ScanPhase.WALK))
    fun cancel() = repo.cancel()
    fun restart() = repo.startScan()
}

@Composable
fun ScanScreen(onClose: () -> Unit, onReview: () -> Unit, vm: ScanViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    ScanContent(s, onClose = onClose, onCancel = { vm.cancel(); onClose() }, onReview = onReview, onRetry = vm::restart)
}

@Composable
fun ScanContent(s: ScanState, onClose: () -> Unit, onCancel: () -> Unit, onReview: () -> Unit, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        GlowBackground()
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.scan_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, stringResource(R.string.close)) }
            }
            Spacer(Modifier.weight(0.4f))
            Radar(running = s.running, done = s.phase == ScanPhase.DONE, failed = s.phase == ScanPhase.FAILED)
            Spacer(Modifier.height(28.dp))
            AnimatedContent(s.phase == ScanPhase.DONE, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "headline") { done ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (done) stringResource(R.string.scan_done_title) else number(s.filesFound),
                        style = MaterialTheme.typography.displaySmall,
                    )
                    Text(
                        if (done) stringResource(R.string.scan_done_body, number(s.filesFound), bytes(s.bytesFound))
                        else stringResource(R.string.scan_files_found, bytes(s.bytesFound)),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            TidyCard(Modifier.fillMaxWidth()) {
                Step(R.string.scan_step_index, state = stepState(s.phase, ScanPhase.WALK))
                Spacer(Modifier.height(14.dp))
                Step(R.string.scan_step_hash, state = stepState(s.phase, ScanPhase.HASH)) {
                    if (s.phase == ScanPhase.HASH && s.hashTotal > 0) {
                        Spacer(Modifier.height(6.dp))
                        ProgressBar(s.hashDone.toFloat() / s.hashTotal, height = 6.dp)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Step(R.string.scan_step_analyze, state = stepState(s.phase, ScanPhase.ANALYZE))
                if (s.running && s.currentItem.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))
                    PathText(s.currentItem)
                }
            }
            Spacer(Modifier.weight(1f))
            when (s.phase) {
                ScanPhase.DONE -> {
                    if (s.suggestions > 0) {
                        Text(
                            stringResource(R.string.scan_summary, number(s.suggestions), bytes(s.reclaimableBytes)),
                            style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    GradientButton(stringResource(R.string.scan_review), onReview, Modifier.fillMaxWidth(), trailingIcon = Icons.AutoMirrored.Rounded.ArrowForward)
                }
                ScanPhase.FAILED, ScanPhase.IDLE -> GradientButton(stringResource(R.string.scan_retry), onRetry, Modifier.fillMaxWidth())
                else -> {
                    GhostButton(stringResource(R.string.scan_run_background), onClose, Modifier.fillMaxWidth())
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

private enum class StepState { PENDING, ACTIVE, DONE }

private fun stepState(current: ScanPhase, step: ScanPhase): StepState {
    val order = listOf(ScanPhase.WALK, ScanPhase.HASH, ScanPhase.ANALYZE)
    if (current == ScanPhase.DONE) return StepState.DONE
    val ci = order.indexOf(current)
    val si = order.indexOf(step)
    return when {
        ci < 0 -> StepState.PENDING
        si < ci -> StepState.DONE
        si == ci -> StepState.ACTIVE
        else -> StepState.PENDING
    }
}

@Composable
private fun Step(label: Int, state: StepState, extra: @Composable () -> Unit = {}) {
    Row(verticalAlignment = Alignment.Top) {
        val bg = when (state) {
            StepState.DONE -> Brand.mintGradient
            StepState.ACTIVE -> Brand.heroGradient
            StepState.PENDING -> Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.surfaceContainerHighest))
        }
        Box(Modifier.size(26.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
            if (state == StepState.DONE) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
            if (state == StepState.ACTIVE) {
                val t = rememberInfiniteTransition(label = "pulse")
                val a by t.animateFloat(.4f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "a")
                Box(Modifier.size(8.dp).clip(CircleShape).background(Color.White.copy(alpha = a)))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(top = 2.dp)) {
            Text(
                stringResource(label),
                style = MaterialTheme.typography.titleSmall,
                color = if (state == StepState.PENDING) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            extra()
        }
    }
}

@Composable
private fun Radar(running: Boolean, done: Boolean, failed: Boolean) {
    val t = rememberInfiniteTransition(label = "radar")
    val sweep by t.animateFloat(0f, 360f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "sweep")
    val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2000, easing = LinearEasing)), label = "pulse")
    val primary = Brand.Violet
    Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2
            for (i in 1..3) drawCircle(primary.copy(alpha = .14f), radius = r * i / 3f, style = Stroke(1.5.dp.toPx()))
            if (running) {
                for (k in 0..1) {
                    val p = (pulse + k * .5f) % 1f
                    drawCircle(Brand.Cyan.copy(alpha = (1f - p) * .35f), radius = r * p, style = Stroke(2.dp.toPx()))
                }
            }
        }
        if (running) {
            Canvas(Modifier.fillMaxSize().rotate(sweep)) {
                val r = size.minDimension / 2
                drawArc(
                    Brush.sweepGradient(listOf(Color.Transparent, Color.Transparent, primary.copy(alpha = .05f), Brand.Cyan.copy(alpha = .55f)), center = Offset(r, r)),
                    startAngle = 0f, sweepAngle = 360f, useCenter = true,
                )
            }
        }
        Box(
            Modifier.size(92.dp).clip(CircleShape).background(if (failed) Brand.dangerGradient else if (done) Brand.mintGradient else Brand.heroGradient),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                when {
                    failed -> Icons.Rounded.ErrorOutline
                    done -> Icons.Rounded.Check
                    else -> Icons.Rounded.Radar
                },
                null, tint = Color.White, modifier = Modifier.size(44.dp),
            )
        }
    }
}

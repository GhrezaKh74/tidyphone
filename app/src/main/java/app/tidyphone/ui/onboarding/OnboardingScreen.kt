package app.tidyphone.ui.onboarding

import android.content.ActivityNotFoundException
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.tidyphone.R
import app.tidyphone.ui.StoragePermission
import app.tidyphone.ui.components.GlowBackground
import app.tidyphone.ui.components.GradientButton
import app.tidyphone.ui.components.IconBadge
import app.tidyphone.ui.components.TidyCard
import app.tidyphone.ui.theme.Brand
import kotlinx.coroutines.launch

private data class Page(val image: Int, val title: Int, val body: Int)

private val pages = listOf(
    Page(R.drawable.illus_scan, R.string.onb1_title, R.string.onb1_body),
    Page(R.drawable.illus_organize, R.string.onb2_title, R.string.onb2_body),
    Page(R.drawable.illus_safety, R.string.onb3_title, R.string.onb3_body),
)

@Composable
fun OnboardingScreen(permissionGranted: Boolean, onDone: () -> Unit) {
    val pager = rememberPagerState { pages.size + 1 }
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        GlowBackground()
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 12.dp).weight(1f))
                if (pager.currentPage < pages.size) {
                    TextButton(onClick = { scope.launch { pager.animateScrollToPage(pages.size) } }) { Text(stringResource(R.string.skip)) }
                }
            }
            HorizontalPager(pager, Modifier.weight(1f)) { index ->
                if (index < pages.size) IntroPage(pages[index]) else PermissionPage(permissionGranted, onDone)
            }
            Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(pages.size + 1) { i ->
                        val active = i == pager.currentPage
                        val w by animateDpAsState(if (active) 26.dp else 8.dp, label = "dot")
                        val c by animateColorAsState(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, label = "dotc")
                        Box(Modifier.height(8.dp).width(w).clip(CircleShape).background(c))
                    }
                }
                if (pager.currentPage < pages.size) {
                    GradientButton(
                        text = stringResource(R.string.next),
                        trailingIcon = Icons.AutoMirrored.Rounded.ArrowForward,
                        onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun IntroPage(page: Page) {
    val float = rememberInfiniteTransition(label = "float")
    val dy by float.animateFloat(-10f, 10f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "dy")
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Image(
            painterResource(page.image), null,
            modifier = Modifier.fillMaxWidth(0.86f).graphicsLayer { translationY = dy * density },
        )
        Spacer(Modifier.height(28.dp))
        Text(stringResource(page.title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(page.body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PermissionPage(granted: Boolean, onDone: () -> Unit) {
    val context = LocalContext.current
    var denied by remember { mutableStateOf(false) }
    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        denied = !StoragePermission.isGranted(context)
    }
    val legacyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        denied = !StoragePermission.isGranted(context)
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))
        Box(Modifier.size(84.dp).clip(CircleShape).background(Brand.heroGradient), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.FolderOpen, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(stringResource(R.string.perm_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.perm_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        TidyCard(Modifier.fillMaxWidth()) {
            Point(Icons.Rounded.VerifiedUser, R.string.perm_point_review)
            Spacer(Modifier.height(14.dp))
            Point(Icons.Rounded.Lock, R.string.perm_point_protected)
            Spacer(Modifier.height(14.dp))
            Point(Icons.Rounded.CloudOff, R.string.perm_point_metadata)
        }
        Spacer(Modifier.height(24.dp))
        if (granted) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, null, tint = Brand.Mint)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.perm_granted), style = MaterialTheme.typography.titleSmall, color = Brand.Mint)
            }
            Spacer(Modifier.height(16.dp))
            GradientButton(stringResource(R.string.get_started), onDone, Modifier.fillMaxWidth(), trailingIcon = Icons.AutoMirrored.Rounded.ArrowForward)
        } else {
            GradientButton(
                text = stringResource(R.string.perm_grant),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        try { settingsLauncher.launch(StoragePermission.settingsIntent(context)) }
                        catch (e: ActivityNotFoundException) { settingsLauncher.launch(StoragePermission.fallbackIntent()) }
                    } else legacyLauncher.launch(StoragePermission.legacyPermissions)
                },
            )
            if (denied) {
                Spacer(Modifier.height(14.dp))
                TidyCard(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.perm_denied_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.perm_denied_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Point(icon: ImageVector, text: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, Brand.Mint, size = 38.dp)
        Spacer(Modifier.width(14.dp))
        Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
    }
}

package app.tidyphone.screenshot

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import app.tidyphone.data.db.CategoryTotal
import app.tidyphone.data.fs.StorageStats
import app.tidyphone.data.repo.AnalysisState
import app.tidyphone.data.repo.ScanPhase
import app.tidyphone.data.repo.ScanState
import app.tidyphone.data.settings.AppSettings
import app.tidyphone.data.settings.ProviderType
import app.tidyphone.domain.analysis.Reasons
import app.tidyphone.domain.model.ActionType
import app.tidyphone.domain.model.FileCategory
import app.tidyphone.domain.model.HistoryRecord
import app.tidyphone.domain.model.PlanItem
import app.tidyphone.domain.model.PlanSource
import app.tidyphone.domain.model.TrashItem
import app.tidyphone.domain.llm.LlmProgress
import app.tidyphone.ui.dashboard.DashboardContent
import app.tidyphone.ui.dashboard.DashboardState
import app.tidyphone.ui.dashboard.Insight
import app.tidyphone.ui.history.HistoryContent
import app.tidyphone.ui.onboarding.OnboardingScreen
import app.tidyphone.ui.review.ReviewList
import app.tidyphone.ui.scan.ScanContent
import app.tidyphone.ui.settings.SettingsContent
import app.tidyphone.ui.settings.TestState
import app.tidyphone.ui.theme.ThemeMode
import app.tidyphone.ui.theme.TidyTheme
import app.tidyphone.ui.trash.TrashContent
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val ROOT = "/storage/emulated/0"
private const val GB = 1024L * 1024 * 1024
private const val MB = 1024L * 1024

abstract class ScreenshotBase(private val mode: ThemeMode, private val prefix: String) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    protected fun shot(name: String, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            TidyTheme(mode) { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() } }
        }
        compose.mainClock.advanceTimeBy(1600)
        val dir = System.getProperty("roborazzi.output.dir") ?: "build/screenshots"
        compose.onRoot().captureRoboImage("$dir/${prefix}_$name.png")
    }

    private val now = System.currentTimeMillis()

    private val dash = DashboardState(
        storage = StorageStats(128 * GB, 23 * GB),
        categories = listOf(
            CategoryTotal(FileCategory.VIDEO, 38 * GB, 412),
            CategoryTotal(FileCategory.IMAGE, 21 * GB, 9_842),
            CategoryTotal(FileCategory.AUDIO, 6 * GB, 1_204),
            CategoryTotal(FileCategory.DOCUMENT, 3 * GB, 2_311),
            CategoryTotal(FileCategory.ARCHIVE, 2 * GB, 96),
            CategoryTotal(FileCategory.APK, 1 * GB, 38),
        ),
        indexedFiles = 13_903,
        reclaimableBytes = 7 * GB + 340 * MB,
        pendingActions = 486,
        insights = listOf(
            Insight(PlanSource.LOCAL_DUPLICATE, 312, 3 * GB + 200 * MB),
            Insight(PlanSource.LOCAL_TEMP, 57, 820 * MB),
            Insight(PlanSource.LOCAL_LARGE, 14, 9 * GB),
            Insight(PlanSource.LOCAL_APK, 38, 1 * GB + 100 * MB),
            Insight(PlanSource.LOCAL_OLD_DOWNLOAD, 120, 2 * GB),
            Insight(PlanSource.LOCAL_OLD_SCREENSHOT, 640, 900 * MB),
        ),
        loaded = true,
    )

    private val planItems = listOf(
        PlanItem(1, "$ROOT/Download/IMG_2041 (1).jpg", ActionType.DELETE, PlanSource.LOCAL_DUPLICATE, 4 * MB, Reasons.DUPLICATE + "$ROOT/DCIM/Camera/IMG_2041.jpg", selected = true),
        PlanItem(2, "$ROOT/Telegram/Telegram Video/video_2024.mp4", ActionType.DELETE, PlanSource.LOCAL_DUPLICATE, 182 * MB, Reasons.DUPLICATE + "$ROOT/Movies/trip.mp4", selected = true),
        PlanItem(3, "$ROOT/Download/WhatsApp-setup.apk", ActionType.DELETE, PlanSource.LOCAL_APK, 96 * MB, Reasons.APK, selected = true),
        PlanItem(4, "$ROOT/Download/movie.mkv.crdownload", ActionType.DELETE, PlanSource.LOCAL_TEMP, 640 * MB, Reasons.TEMP, selected = true),
        PlanItem(5, "$ROOT/Movies/old_backup.zip", ActionType.DELETE, PlanSource.LOCAL_LARGE, 3 * GB, Reasons.LARGE, selected = false),
        PlanItem(6, "$ROOT/Download/scan_83f2a1.pdf", ActionType.MOVE, PlanSource.LLM, 2 * MB, "Bank statement; belongs with finance documents.", targetDir = "$ROOT/Documents/Finance", newName = "Bank statement 2024-03.pdf", category = "finance"),
    )

    private val settings = AppSettings(onboardingDone = true, provider = ProviderType.ANTHROPIC, customRules = "never touch the Work folder", excludeFolders = listOf("Work"))

    @Test fun onboarding() = shot("1_onboarding") { OnboardingScreen(permissionGranted = false, onDone = {}) }
    @Test fun dashboard() = shot("2_dashboard") { DashboardContent(dash, {}, {}, {}) }
    @Test fun scan() = shot("3_scan") {
        ScanContent(ScanState(ScanPhase.HASH, filesFound = 13_903, bytesFound = 72 * GB, currentItem = "IMG_20240311_101500.jpg", hashDone = 420, hashTotal = 1_100), {}, {}, {}, {})
    }
    @Test fun review() = shot("4_review") {
        ReviewList(
            planItems, ActionType.DELETE, {}, ROOT,
            AnalysisState(running = true, progress = LlmProgress(2, 6, 240, 700, 31, 0, 12000, 3000)),
            dryRun = false, onToggle = {}, onGroup = { _, _ -> }, onPreview = {}, onCancelAnalysis = {}, onDismissAnalysis = {},
        )
    }
    @Test fun reviewMove() = shot("5_review_move") {
        ReviewList(planItems, ActionType.MOVE, {}, ROOT, AnalysisState(), dryRun = true, onToggle = {}, onGroup = { _, _ -> }, onPreview = {}, onCancelAnalysis = {}, onDismissAnalysis = {})
    }
    @Test fun history() = shot("6_history") {
        HistoryContent(
            listOf(
                HistoryRecord(1, "b1", now - 3_600_000, ActionType.DELETE, "$ROOT/Download/WhatsApp-setup.apk", "$ROOT/.TidyPhoneTrash/1__WhatsApp-setup.apk", 96 * MB),
                HistoryRecord(2, "b1", now - 3_600_000, ActionType.MOVE, "$ROOT/Download/scan_83f2a1.pdf", "$ROOT/Documents/Finance/Bank statement 2024-03.pdf", 2 * MB),
                HistoryRecord(3, "b1", now - 3_600_000, ActionType.RENAME, "$ROOT/Documents/doc_77.docx", "$ROOT/Documents/CV.docx", MB, undone = true),
            ),
            ROOT, {}, {},
        )
    }
    @Test fun trash() = shot("7_trash") {
        TrashContent(
            listOf(
                TrashItem(1, "$ROOT/Download/WhatsApp-setup.apk", "$ROOT/.TidyPhoneTrash/1__WhatsApp-setup.apk", now - 2 * 86_400_000L, 96 * MB),
                TrashItem(2, "$ROOT/Download/movie.mkv.crdownload", "$ROOT/.TidyPhoneTrash/2__movie.mkv.crdownload", now - 28 * 86_400_000L, 640 * MB),
            ),
            30, ROOT, {}, {}, {},
        )
    }
    @Test fun apps() = shot("9_apps") {
        fun a(name: String, days: Long, size: Long, game: Boolean = false, state: app.tidyphone.domain.apps.UsageState = app.tidyphone.domain.apps.UsageState.UNUSED) =
            app.tidyphone.domain.apps.AppUsage(app.tidyphone.domain.apps.InstalledApp("pkg.$name", name, game, 0, null, size), state, days)
        app.tidyphone.ui.apps.AppsContent(
            app.tidyphone.ui.apps.AppsUi(
                loading = false, thresholdDays = 60, selected = setOf("pkg.Racing Legends", "pkg.Old Bank"),
                apps = listOf(
                    a("Racing Legends", 210, 3 * GB, game = true),
                    a("Old Bank", 160, 180 * MB),
                    a("Photo Editor Pro", 95, 420 * MB, state = app.tidyphone.domain.apps.UsageState.NEVER_OPENED),
                    a("Puzzle Quest", 75, 1 * GB, game = true),
                ),
            ),
            {}, {}, {}, {}, {}, {}, { _, _ -> }, {},
        )
    }
    @Test fun settings() = shot("8_settings") {
        SettingsContent(settings, mapOf(ProviderType.ANTHROPIC to true), TestState.Ok("claude-opus-5-5 · 840 ms · \"OK\""), {}, { _, _ -> }, {}, {}, {})
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-w411dp-h891dp-notnight-xxhdpi")
class LightEnScreenshotTest : ScreenshotBase(ThemeMode.LIGHT, "en_light")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "fa-ldrtl-w411dp-h891dp-night-xxhdpi")
class DarkFaScreenshotTest : ScreenshotBase(ThemeMode.DARK, "fa_dark")

package app.tidyphone.ui

import android.os.Build
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import app.tidyphone.MainActivity
import app.tidyphone.data.settings.SettingsRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import javax.inject.Inject

/** Drives the real app: every bottom-bar tab must open its own screen, including Home. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = HiltTestApplication::class, qualifiers = "en-w411dp-h891dp")
class NavigationTest {
    private val hilt = HiltAndroidRule(this)
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(hilt).around(PrepareRule()).around(compose)

    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var files: app.tidyphone.data.db.FileDao

    /** Runs before the activity starts: onboarding done, all-files access granted, test WorkManager. */
    inner class PrepareRule : org.junit.rules.ExternalResource() {
        override fun before() {
            WorkManagerTestInitHelper.initializeTestWorkManager(ApplicationProvider.getApplicationContext())
            StoragePermission.grantedForTests = true
            hilt.inject()
            runBlocking {
                settings.update { it.copy(onboardingDone = true) }
                // One indexed file so the dashboard's AI shortcuts are enabled.
                files.upsertAll(listOf(app.tidyphone.data.db.FileEntity(
                    "/storage/emulated/0/Download/a.pdf", "a.pdf", "/storage/emulated/0/Download", 1000, "application/pdf", 0,
                    app.tidyphone.domain.model.FileCategory.DOCUMENT, null, false, 1,
                )))
            }
        }
    }

    private fun tab(label: String) = compose.onAllNodesWithText(label).fetchSemanticsNodes().let {
        compose.onAllNodesWithText(label)[it.size - 1]
    }

    private fun assertShows(text: String) {
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun `every tab opens its screen and home works afterwards`() {
        assertShows("Your storage at a glance")
        tab("Review").performClick(); assertShows("Review plan")
        tab("History").performClick(); assertShows("Every change TidyPhone made. Undo anything.")
        tab("Trash").performClick(); assertShows("Holding in Trash")
        tab("Settings").performClick(); assertShows("AI provider")
        tab("Home").performClick(); assertShows("Your storage at a glance")
        tab("Trash").performClick(); assertShows("Holding in Trash")
        tab("Review").performClick(); assertShows("Review plan")
        tab("Home").performClick(); assertShows("Your storage at a glance")
    }

    @Test fun `home tab works after opening a screen from the dashboard`() {
        assertShows("Your storage at a glance")
        // Dashboard shortcut (gear icon) instead of the bottom bar.
        compose.onNodeWithContentDescription("Settings").performClick()
        assertShows("AI provider")
        tab("History").performClick(); assertShows("Every change TidyPhone made. Undo anything.")
        tab("Home").performClick(); assertShows("Your storage at a glance")
        tab("Settings").performClick(); assertShows("AI provider")
        tab("Home").performClick(); assertShows("Your storage at a glance")
    }

    @Test fun `home tab works after opening the AI sheet from the dashboard`() {
        assertShows("Your storage at a glance")
        compose.onNodeWithText("AI organize").performClick()
        assertShows("Which files?") // the AI analysis sheet
        assertShows("Review plan")
        androidx.test.espresso.Espresso.pressBack() // closes the sheet (focused window), not the screen
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Which files?").fetchSemanticsNodes().isEmpty() }
        assertShows("Review plan")
        tab("Trash").performClick(); assertShows("Holding in Trash")
        tab("Home").performClick(); assertShows("Your storage at a glance")
        tab("Review").performClick(); assertShows("Review plan")
        tab("Home").performClick(); assertShows("Your storage at a glance")
    }

    @Suppress("unused") private val sdk = Build.VERSION.SDK_INT
}

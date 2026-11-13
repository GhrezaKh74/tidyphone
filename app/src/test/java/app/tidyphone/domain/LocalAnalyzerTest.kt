package app.tidyphone.domain

import app.tidyphone.domain.analysis.DuplicateGroup
import app.tidyphone.domain.analysis.LocalAnalysisConfig
import app.tidyphone.domain.analysis.LocalAnalyzer
import app.tidyphone.domain.model.ActionType
import app.tidyphone.domain.model.PlanSource
import app.tidyphone.domain.safety.PathGuard
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LocalAnalyzerTest {
    private val now = 1_000 * DAY
    private val config = LocalAnalysisConfig(largeFileBytes = 1_000_000, oldDownloadDays = 90, oldScreenshotDays = 30, now = now)
    private val analyzer = LocalAnalyzer(PathGuard(ROOT, userExcludes = listOf("Work")))

    private fun sources(items: List<app.tidyphone.domain.model.PlanItem>) = items.associate { it.fileName to it.source }

    @Test fun `detects temp, apk, old downloads, old screenshots, large files and empty folders`() {
        val files = listOf(
            file("Download/movie.mkv.crdownload", modified = now),
            file("Download/app-release.apk", modified = now),
            file("Download/old.pdf", modified = now - 200 * DAY),
            file("Download/new.pdf", modified = now - 5 * DAY),
            file("Pictures/Screenshots/Screenshot_2020.png", modified = now - 100 * DAY),
            file("Pictures/Screenshots/Screenshot_new.png", modified = now - 2 * DAY),
            file("Movies/huge.mp4", size = 5_000_000, modified = now),
            file("Documents/~\$draft.docx", modified = now),
            file("Old/EmptyFolder", size = 0, dir = true),
        )
        val items = analyzer.analyze(files, emptyList(), config)
        val s = sources(items)
        assertThat(s["movie.mkv.crdownload"]).isEqualTo(PlanSource.LOCAL_TEMP)
        assertThat(s["~\$draft.docx"]).isEqualTo(PlanSource.LOCAL_TEMP)
        assertThat(s["app-release.apk"]).isEqualTo(PlanSource.LOCAL_APK)
        assertThat(s["old.pdf"]).isEqualTo(PlanSource.LOCAL_OLD_DOWNLOAD)
        assertThat(s).doesNotContainKey("new.pdf")
        assertThat(s["Screenshot_2020.png"]).isEqualTo(PlanSource.LOCAL_OLD_SCREENSHOT)
        assertThat(s).doesNotContainKey("Screenshot_new.png")
        assertThat(s["huge.mp4"]).isEqualTo(PlanSource.LOCAL_LARGE)
        assertThat(s["EmptyFolder"]).isEqualTo(PlanSource.LOCAL_EMPTY_FOLDER)
        assertThat(items.all { it.action == ActionType.DELETE }).isTrue()
    }

    @Test fun `large files and old downloads are flagged but not pre-selected`() {
        val items = analyzer.analyze(listOf(
            file("Movies/huge.mp4", size = 5_000_000, modified = now),
            file("Download/old.zip", modified = 0),
        ), emptyList(), config)
        assertThat(items.map { it.selected }).containsExactly(false, false)
    }

    @Test fun `duplicates win over other rules and point at the kept copy`() {
        val keep = file("DCIM/a.jpg", modified = 1)
        val dup = file("Download/a.jpg", modified = 0)
        val items = analyzer.analyze(listOf(keep, dup), listOf(DuplicateGroup("H", 1000, keep, listOf(dup))), config)
        assertThat(items).hasSize(1)
        assertThat(items[0].source).isEqualTo(PlanSource.LOCAL_DUPLICATE)
        assertThat(items[0].reason).endsWith(keep.path)
        assertThat(items[0].selected).isTrue()
    }

    @Test fun `excluded and protected paths never produce suggestions`() {
        val items = analyzer.analyze(listOf(
            file("Work/setup.apk"),
            file("Android/data/com.x/cache.tmp"),
            file("Download/ok.apk"),
        ), emptyList(), config)
        assertThat(items.map { it.fileName }).containsExactly("ok.apk")
    }
}

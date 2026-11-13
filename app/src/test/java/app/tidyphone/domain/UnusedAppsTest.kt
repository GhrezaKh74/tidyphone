package app.tidyphone.domain

import app.tidyphone.domain.apps.InstalledApp
import app.tidyphone.domain.apps.UnusedAppsAnalyzer
import app.tidyphone.domain.apps.UsageState
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UnusedAppsTest {
    private val now = 1_000 * DAY
    private val historyStart = now - 700 * DAY
    private fun app(name: String, lastUsedDaysAgo: Long?, installedDaysAgo: Long = 900, size: Long = 0, game: Boolean = false) =
        InstalledApp("pkg.$name", name, game, now - installedDaysAgo * DAY, lastUsedDaysAgo?.let { now - it * DAY }, size)

    @Test fun `classifies by last use, never opened and missing history`() {
        val r = UnusedAppsAnalyzer.analyze(
            listOf(
                app("Daily", 1),
                app("OldGame", 200, game = true),
                app("NeverOpened", null, installedDaysAgo = 120),
                app("NewInstall", null, installedDaysAgo = 3),
                app("Ancient", null, installedDaysAgo = 1500),
            ),
            now, thresholdDays = 60, historyStart = historyStart,
        ).associateBy { it.app.label }
        assertThat(r["Daily"]!!.state).isEqualTo(UsageState.ACTIVE)
        assertThat(r["OldGame"]!!.state).isEqualTo(UsageState.UNUSED)
        assertThat(r["OldGame"]!!.idleDays).isEqualTo(200)
        assertThat(r["NeverOpened"]!!.state).isEqualTo(UsageState.NEVER_OPENED)
        assertThat(r["NewInstall"]!!.state).isEqualTo(UsageState.ACTIVE)
        assertThat(r["Ancient"]!!.state).isEqualTo(UsageState.NOT_IN_HISTORY)
        assertThat(r["Ancient"]!!.idleDays).isEqualTo(700)
    }

    @Test fun `unused apps come first, longest idle and biggest first`() {
        val r = UnusedAppsAnalyzer.analyze(
            listOf(app("A", 1), app("B", 100, size = 10), app("C", 300), app("D", 100, size = 99)),
            now, 60, historyStart,
        )
        assertThat(r.map { it.app.label }).containsExactly("C", "D", "B", "A").inOrder()
    }

    @Test fun `threshold moves apps between active and unused`() {
        val apps = listOf(app("X", 45))
        assertThat(UnusedAppsAnalyzer.analyze(apps, now, 30, historyStart).single().isUnused).isTrue()
        assertThat(UnusedAppsAnalyzer.analyze(apps, now, 60, historyStart).single().isUnused).isFalse()
    }
}

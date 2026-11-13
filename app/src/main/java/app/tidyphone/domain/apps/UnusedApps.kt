package app.tidyphone.domain.apps

import java.util.concurrent.TimeUnit

data class InstalledApp(
    val packageName: String,
    val label: String,
    val isGame: Boolean,
    val installedAt: Long,
    /** Last time the app was in the foreground, if Android still has a record of it. */
    val lastUsed: Long?,
    /** App + data + cache, if known. */
    val sizeBytes: Long?,
)

enum class UsageState {
    /** Used within the threshold. */
    ACTIVE,
    /** Last used longer ago than the threshold. */
    UNUSED,
    /** Installed after usage history begins and never opened since. */
    NEVER_OPENED,
    /** No record at all in the usage history Android keeps (roughly the last 1–2 years). */
    NOT_IN_HISTORY,
}

data class AppUsage(val app: InstalledApp, val state: UsageState, val idleDays: Long) {
    val isUnused: Boolean get() = state != UsageState.ACTIVE
}

object UnusedAppsAnalyzer {
    /**
     * @param historyStart the oldest point Android's usage stats cover; apps installed before it
     * with no record are "not in history" rather than "never opened".
     */
    fun analyze(apps: List<InstalledApp>, now: Long, thresholdDays: Int, historyStart: Long): List<AppUsage> =
        apps.map { app ->
            val days: Long
            val state: UsageState
            when {
                app.lastUsed != null && app.lastUsed > 0 -> {
                    days = daysBetween(app.lastUsed, now)
                    state = if (days >= thresholdDays) UsageState.UNUSED else UsageState.ACTIVE
                }
                app.installedAt >= historyStart -> {
                    days = daysBetween(app.installedAt, now)
                    state = if (days >= thresholdDays) UsageState.NEVER_OPENED else UsageState.ACTIVE
                }
                else -> {
                    days = daysBetween(historyStart, now)
                    state = if (days >= thresholdDays) UsageState.NOT_IN_HISTORY else UsageState.ACTIVE
                }
            }
            AppUsage(app, state, days)
        }.sortedWith(
            compareByDescending<AppUsage> { it.isUnused }
                .thenByDescending { it.idleDays }
                .thenByDescending { it.app.sizeBytes ?: 0 }
                .thenBy { it.app.label.lowercase() }
        )

    private fun daysBetween(from: Long, to: Long) = TimeUnit.MILLISECONDS.toDays((to - from).coerceAtLeast(0))
}

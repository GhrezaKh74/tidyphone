package app.tidyphone.domain.analysis

import app.tidyphone.domain.model.ActionType
import app.tidyphone.domain.model.FileCategory
import app.tidyphone.domain.model.FileEntry
import app.tidyphone.domain.model.PlanItem
import app.tidyphone.domain.model.PlanSource
import app.tidyphone.domain.safety.PathGuard
import java.util.concurrent.TimeUnit

data class LocalAnalysisConfig(
    val largeFileBytes: Long = 200L * 1024 * 1024,
    val oldDownloadDays: Int = 90,
    val oldScreenshotDays: Int = 60,
    val now: Long = System.currentTimeMillis(),
)

/** Reasons are short codes; the UI turns them into localized text. */
object Reasons {
    const val DUPLICATE = "duplicate_of:"
    const val LARGE = "large_file"
    const val OLD_DOWNLOAD = "old_download:"
    const val APK = "leftover_apk"
    const val EMPTY_FOLDER = "empty_folder"
    const val TEMP = "temp_file"
    const val OLD_SCREENSHOT = "old_screenshot:"
}

/**
 * Rule-based analysis that runs fully on the device. Produces DELETE suggestions; large files are
 * flagged but not pre-selected, because size alone doesn't mean a file is unwanted.
 */
class LocalAnalyzer(private val guard: PathGuard) {

    fun analyze(files: List<FileEntry>, duplicates: List<DuplicateGroup>, config: LocalAnalysisConfig): List<PlanItem> {
        val items = LinkedHashMap<String, PlanItem>()
        fun add(item: PlanItem) {
            if (!guard.canModify(item.path)) return
            // First rule wins; rules are ordered from most to least certain.
            items.putIfAbsent(item.path, item)
        }

        for (f in files) if (isTempFile(f)) add(item(f, PlanSource.LOCAL_TEMP, Reasons.TEMP, selected = true))

        for (g in duplicates) for (d in g.duplicates) {
            add(item(d, PlanSource.LOCAL_DUPLICATE, Reasons.DUPLICATE + g.keep.path, selected = true, group = g.hash))
        }

        for (f in files) {
            if (f.isDirectory) continue
            when {
                f.category == FileCategory.APK ->
                    add(item(f, PlanSource.LOCAL_APK, Reasons.APK, selected = true))
                isScreenshot(f) && ageDays(f, config) >= config.oldScreenshotDays ->
                    add(item(f, PlanSource.LOCAL_OLD_SCREENSHOT, Reasons.OLD_SCREENSHOT + ageDays(f, config), selected = false))
                isInDownloads(f) && ageDays(f, config) >= config.oldDownloadDays ->
                    add(item(f, PlanSource.LOCAL_OLD_DOWNLOAD, Reasons.OLD_DOWNLOAD + ageDays(f, config), selected = false))
            }
        }

        for (f in files) if (!f.isDirectory && f.size >= config.largeFileBytes) {
            add(item(f, PlanSource.LOCAL_LARGE, Reasons.LARGE, selected = false))
        }

        for (f in files) if (f.isDirectory) add(item(f, PlanSource.LOCAL_EMPTY_FOLDER, Reasons.EMPTY_FOLDER, selected = true))

        return items.values.toList()
    }

    private fun item(f: FileEntry, source: PlanSource, reason: String, selected: Boolean, group: String? = null) =
        PlanItem(path = f.path, action = ActionType.DELETE, source = source, size = f.size, reason = reason, selected = selected, groupKey = group)

    companion object {
        private val tempExtensions = setOf("tmp", "temp", "part", "partial", "crdownload", "download", "dlpart", "!ut", "bak~")
        private val tempNames = setOf("thumbs.db", ".ds_store", "desktop.ini")
        private val screenshotName = Regex("""^(screenshot|screen_shot|screen-shot|scr_|اسکرین)""", RegexOption.IGNORE_CASE)

        fun ageDays(f: FileEntry, c: LocalAnalysisConfig): Long =
            TimeUnit.MILLISECONDS.toDays((c.now - f.lastModified).coerceAtLeast(0))

        fun isTempFile(f: FileEntry): Boolean {
            if (f.isDirectory) return false
            val n = f.name.lowercase()
            return f.extension in tempExtensions ||
                n in tempNames ||
                n.startsWith(".pending-") ||
                n.startsWith(".trashed-") ||
                n.startsWith("~$") ||
                n.endsWith("~")
        }

        fun isScreenshot(f: FileEntry): Boolean =
            f.category == FileCategory.IMAGE &&
                (f.parent.lowercase().split('/').any { it == "screenshots" || it == "screenshot" } || screenshotName.containsMatchIn(f.name))

        fun isInDownloads(f: FileEntry): Boolean =
            f.parent.lowercase().split('/').any { it == "download" || it == "downloads" }
    }
}

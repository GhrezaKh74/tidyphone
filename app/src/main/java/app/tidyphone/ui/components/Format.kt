package app.tidyphone.ui.components

import android.content.Context
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import app.tidyphone.R
import app.tidyphone.domain.analysis.Reasons
import app.tidyphone.domain.model.FileCategory
import app.tidyphone.domain.model.PlanItem
import app.tidyphone.domain.model.PlanSource
import java.text.NumberFormat

/** Localized size, e.g. "1.2 GB" / "۱٫۲ گیگابایت". */
fun Context.bytes(value: Long): String = Formatter.formatShortFileSize(this, value.coerceAtLeast(0))

@Composable @ReadOnlyComposable
fun bytes(value: Long): String {
    LocalConfiguration.current // recompose on locale change
    return LocalContext.current.bytes(value)
}

@Composable @ReadOnlyComposable
fun number(value: Number): String {
    val locale = LocalConfiguration.current.locales[0]
    return NumberFormat.getInstance(locale).format(value)
}

@Composable @ReadOnlyComposable
fun percent(fraction: Double): String {
    val locale = LocalConfiguration.current.locales[0]
    return NumberFormat.getPercentInstance(locale).format(fraction)
}

@Composable @ReadOnlyComposable
fun relativeTime(ms: Long): String {
    LocalConfiguration.current
    return DateUtils.getRelativeTimeSpanString(ms, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
}

fun FileCategory.label(): Int = when (this) {
    FileCategory.IMAGE -> R.string.cat_images
    FileCategory.VIDEO -> R.string.cat_videos
    FileCategory.AUDIO -> R.string.cat_audio
    FileCategory.DOCUMENT -> R.string.cat_documents
    FileCategory.ARCHIVE -> R.string.cat_archives
    FileCategory.APK -> R.string.cat_apks
    FileCategory.OTHER -> R.string.cat_other
}

fun PlanSource.label(): Int = when (this) {
    PlanSource.LOCAL_DUPLICATE -> R.string.src_duplicates
    PlanSource.LOCAL_LARGE -> R.string.src_large
    PlanSource.LOCAL_OLD_DOWNLOAD -> R.string.src_old_downloads
    PlanSource.LOCAL_APK -> R.string.src_apks
    PlanSource.LOCAL_EMPTY_FOLDER -> R.string.src_empty_folders
    PlanSource.LOCAL_TEMP -> R.string.src_temp
    PlanSource.LOCAL_OLD_SCREENSHOT -> R.string.src_old_screenshots
    PlanSource.LLM -> R.string.src_ai
}

fun llmCategoryLabel(key: String?): Int? = when (key) {
    "photos" -> R.string.llmcat_photos
    "screenshots" -> R.string.llmcat_screenshots
    "videos" -> R.string.llmcat_videos
    "music" -> R.string.llmcat_music
    "voice" -> R.string.llmcat_voice
    "documents" -> R.string.llmcat_documents
    "finance" -> R.string.llmcat_finance
    "work" -> R.string.llmcat_work
    "study" -> R.string.llmcat_study
    "books" -> R.string.llmcat_books
    "archives" -> R.string.llmcat_archives
    "apps" -> R.string.llmcat_apps
    "social_media" -> R.string.llmcat_social
    "memes" -> R.string.llmcat_memes
    "other" -> R.string.llmcat_other
    else -> null
}

/** Turns local reason codes into readable text; LLM reasons are shown as written. */
fun Context.reasonText(item: PlanItem, root: String): String {
    val r = item.reason
    return when {
        r.startsWith(Reasons.DUPLICATE) -> getString(R.string.reason_duplicate, ltr(r.removePrefix(Reasons.DUPLICATE).removePrefix("$root/")))
        r == Reasons.LARGE -> getString(R.string.reason_large)
        r.startsWith(Reasons.OLD_DOWNLOAD) -> getString(R.string.reason_old_download, r.removePrefix(Reasons.OLD_DOWNLOAD).toIntOrNull() ?: 0)
        r == Reasons.APK -> getString(R.string.reason_apk)
        r == Reasons.EMPTY_FOLDER -> getString(R.string.reason_empty_folder)
        r == Reasons.TEMP -> getString(R.string.reason_temp)
        r.startsWith(Reasons.OLD_SCREENSHOT) -> getString(R.string.reason_old_screenshot, r.removePrefix(Reasons.OLD_SCREENSHOT).toIntOrNull() ?: 0)
        else -> r
    }
}

/** Wraps a path in Unicode LTR isolates so it reads correctly inside Persian sentences. */
fun ltr(text: String): String = "\u2066$text\u2069"

package app.tidyphone.domain.model

/** A file (or an empty folder) found during a scan. Paths are absolute. */
data class FileEntry(
    val path: String,
    val name: String,
    val parent: String,
    val size: Long,
    val mimeType: String?,
    val lastModified: Long,
    val category: FileCategory,
    val hash: String? = null,
    val isDirectory: Boolean = false,
) {
    val extension: String get() = name.substringAfterLast('.', "").lowercase()
}

enum class FileCategory { IMAGE, VIDEO, AUDIO, DOCUMENT, ARCHIVE, APK, OTHER;

    companion object {
        private val docs = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp", "txt", "rtf", "csv", "md", "epub", "json", "xml", "html", "htm")
        private val archives = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "tgz")
        private val images = setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "svg", "dng", "raw", "avif")
        private val videos = setOf("mp4", "mkv", "mov", "avi", "webm", "3gp", "m4v", "flv", "wmv", "ts")
        private val audio = setOf("mp3", "m4a", "aac", "ogg", "opus", "wav", "flac", "amr", "wma", "mid")

        fun of(mimeType: String?, extension: String): FileCategory {
            val ext = extension.lowercase()
            return when {
                ext == "apk" || ext == "apks" || ext == "xapk" || mimeType == "application/vnd.android.package-archive" -> APK
                mimeType?.startsWith("image/") == true || ext in images -> IMAGE
                mimeType?.startsWith("video/") == true || ext in videos -> VIDEO
                mimeType?.startsWith("audio/") == true || ext in audio -> AUDIO
                ext in archives -> ARCHIVE
                ext in docs || mimeType?.startsWith("text/") == true -> DOCUMENT
                else -> OTHER
            }
        }
    }
}

enum class ActionType { DELETE, MOVE, RENAME }

/** Why a plan item was suggested. LOCAL_* come from on-device rules, LLM from the model. */
enum class PlanSource {
    LOCAL_DUPLICATE, LOCAL_LARGE, LOCAL_OLD_DOWNLOAD, LOCAL_APK, LOCAL_EMPTY_FOLDER, LOCAL_TEMP, LOCAL_OLD_SCREENSHOT, LLM
}

/**
 * One proposed action. For MOVE, [targetDir] is the absolute destination folder and [newName]
 * is optional (move + rename). For RENAME, [newName] is set and the folder stays the same.
 */
data class PlanItem(
    val id: Long = 0,
    val path: String,
    val action: ActionType,
    val source: PlanSource,
    val size: Long,
    val reason: String,
    val targetDir: String? = null,
    val newName: String? = null,
    val category: String? = null,
    val selected: Boolean = true,
    val groupKey: String? = null,
) {
    val fileName: String get() = path.substringAfterLast('/')

    /** Full destination path for MOVE/RENAME, null for DELETE. */
    val destinationPath: String?
        get() = when (action) {
            ActionType.DELETE -> null
            ActionType.MOVE -> "${targetDir!!.trimEnd('/')}/${newName ?: fileName}"
            ActionType.RENAME -> "${path.substringBeforeLast('/')}/${newName!!}"
        }
}

/** A recorded, executed action, which the undo logic can reverse. */
data class HistoryRecord(
    val id: Long = 0,
    val batchId: String,
    val timestamp: Long,
    val action: ActionType,
    val originalPath: String,
    val resultPath: String,
    val size: Long,
    val undone: Boolean = false,
)

data class TrashItem(
    val id: Long = 0,
    val originalPath: String,
    val trashPath: String,
    val trashedAt: Long,
    val size: Long,
)

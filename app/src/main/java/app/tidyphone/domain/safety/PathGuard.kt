package app.tidyphone.domain.safety

/**
 * The one place that decides which paths TidyPhone may read, change, or use as a destination.
 * Every scan, every plan item from the LLM and every executed action goes through it, so the
 * rules hold even if the prompt is ignored.
 *
 * - Only paths inside [storageRoot] are allowed.
 * - `Android/` is off limits (data, obb, app-private dirs), except `Android/media`, where
 *   WhatsApp and other apps keep shared media on newer Android versions.
 * - Folders in [userExcludes] (absolute, or relative to the root) are never touched.
 * - The app's own [trashDir] can't be scanned, moved into, or modified by plans.
 */
class PathGuard(
    storageRoot: String,
    userExcludes: Collection<String> = emptyList(),
    trashDir: String? = null,
) {
    val root: String = normalize(storageRoot)
        ?: throw IllegalArgumentException("Invalid storage root: $storageRoot")

    private val trash: String? = trashDir?.let { normalize(it) }

    private val excludes: List<String> = userExcludes
        .mapNotNull { resolve(it) }
        .distinct()

    private val blocked: List<String> = listOf(
        "$root/Android/data",
        "$root/Android/obb",
        "$root/Android/obj",
        "$root/.android_secure",
        "$root/LOST.DIR",
    )

    /** Resolves [path] (absolute, or relative to the root) to a normalized absolute path inside the root. */
    fun resolve(path: String): String? {
        val trimmed = path.trim()
        if (trimmed.isEmpty() || trimmed.any { it.code < 0x20 }) return null
        val abs = if (trimmed.startsWith("/")) trimmed else "$root/${trimmed.trimStart('/')}"
        val norm = normalize(abs) ?: return null
        return if (isInside(norm, root)) norm else null
    }

    /** May this path be listed and indexed? */
    fun canScan(path: String): Boolean {
        val p = normalize(path) ?: return false
        if (!isInside(p, root)) return false
        if (p == root) return true
        return !isProtected(p)
    }

    /** May this existing file/folder be moved, renamed or trashed? */
    fun canModify(path: String): Boolean {
        val p = normalize(path) ?: return false
        if (p == root || !isInside(p, root)) return false
        if (isProtected(p)) return false
        // Never modify a top-level standard folder itself (DCIM, Download, ...).
        if (p.substringBeforeLast('/').equals(root, ignoreCase = true) &&
            standardFolders.any { it.equals(p.substringAfterLast('/'), ignoreCase = true) }
        ) return false
        return true
    }

    /** May a file be moved into / created at this destination? */
    fun canWriteTo(path: String): Boolean {
        val p = normalize(path) ?: return false
        if (!isInside(p, root)) return false
        return p == root || !isProtected(p)
    }

    /** True when the folder is the app's trash or inside it. */
    fun isTrash(path: String): Boolean {
        val p = normalize(path) ?: return false
        return trash != null && isInside(p, trash)
    }

    fun isExcluded(path: String): Boolean {
        val p = normalize(path) ?: return true
        return excludes.any { isInside(p, it) }
    }

    /** A valid single file name: no separators, no traversal, no control chars, sane length. */
    fun isValidFileName(name: String): Boolean {
        if (name.isBlank() || name.length > 200) return false
        if (name == "." || name == "..") return false
        if (name.startsWith(".")) return false
        if (name.any { it == '/' || it == '\\' || it.code < 0x20 || it in "<>:\"|?*" }) return false
        return true
    }

    /** A folder name segment: same rules as a file name. */
    fun isValidFolderName(name: String): Boolean = isValidFileName(name)

    private fun isProtected(p: String): Boolean {
        if (blocked.any { isInside(p, it) }) return true
        // Anything else under Android/ except Android/media is app-private too.
        val android = "$root/Android"
        if (isInside(p, android) && p != android && !isInside(p, "$android/media")) return true
        if (trash != null && isInside(p, trash)) return true
        if (excludes.any { isInside(p, it) }) return true
        return false
    }

    companion object {
        val standardFolders = setOf(
            "Download", "Downloads", "DCIM", "Pictures", "Documents", "Movies", "Music",
            "Ringtones", "Alarms", "Notifications", "Podcasts", "Audiobooks", "Recordings", "Android",
        )

        /** Pure string normalization: collapses `//`, `.` and `..`. Returns null if `..` escapes `/`. */
        fun normalize(path: String): String? {
            if (!path.startsWith("/")) return null
            val parts = ArrayDeque<String>()
            for (seg in path.split('/')) {
                when (seg) {
                    "", "." -> Unit
                    ".." -> if (parts.isEmpty()) return null else parts.removeLast()
                    else -> parts.addLast(seg)
                }
            }
            return "/" + parts.joinToString("/")
        }

        /** Case-insensitive containment (shared storage is case-insensitive on Android). */
        fun isInside(path: String, dir: String): Boolean {
            val p = path.lowercase()
            val d = dir.lowercase().trimEnd('/')
            return p == d || p.startsWith("$d/")
        }
    }
}

package app.tidyphone.domain

import app.tidyphone.domain.model.FileCategory
import app.tidyphone.domain.model.FileEntry

const val ROOT = "/storage/emulated/0"
const val DAY = 24L * 60 * 60 * 1000

fun file(
    path: String,
    size: Long = 1000,
    modified: Long = 0,
    hash: String? = null,
    dir: Boolean = false,
): FileEntry {
    val abs = if (path.startsWith("/")) path else "$ROOT/$path"
    val name = abs.substringAfterLast('/')
    val ext = name.substringAfterLast('.', "")
    return FileEntry(
        path = abs, name = name, parent = abs.substringBeforeLast('/'), size = size, mimeType = null,
        lastModified = modified, category = if (dir) FileCategory.OTHER else FileCategory.of(null, ext),
        hash = hash, isDirectory = dir,
    )
}

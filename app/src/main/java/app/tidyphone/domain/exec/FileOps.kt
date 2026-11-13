package app.tidyphone.domain.exec

import app.tidyphone.domain.model.HistoryRecord
import app.tidyphone.domain.model.TrashItem

/** File system operations the executor needs. Implemented with java.nio on the device, and in tests. */
interface FileOps {
    fun exists(path: String): Boolean
    fun isDirectory(path: String): Boolean
    fun isEmptyDirectory(path: String): Boolean
    /** Moves without overwriting. Returns false if the target exists or the move fails. */
    fun move(from: String, to: String): Boolean
    fun mkdirs(path: String): Boolean
    /** Permanently deletes a file, or a folder with everything in it. */
    fun deleteRecursively(path: String): Boolean
}

/** Persists executed actions so they can be undone. */
interface ActionLog {
    suspend fun record(r: HistoryRecord): Long
    suspend fun markUndone(id: Long)
}

/** Index of what is in the app trash. */
interface TrashIndex {
    suspend fun add(item: TrashItem): Long
    suspend fun remove(trashPath: String)
    suspend fun find(trashPath: String): TrashItem?
}

/** "photo.jpg" -> "photo (1).jpg", "photo (2).jpg", ... until a free name is found. */
fun uniquePath(path: String, exists: (String) -> Boolean): String {
    if (!exists(path)) return path
    val dir = path.substringBeforeLast('/')
    val name = path.substringAfterLast('/')
    val dot = name.lastIndexOf('.')
    val (base, ext) = if (dot > 0) name.substring(0, dot) to name.substring(dot) else name to ""
    var i = 1
    while (true) {
        val candidate = "$dir/$base ($i)$ext"
        if (!exists(candidate)) return candidate
        i++
    }
}

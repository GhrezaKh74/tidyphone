package app.tidyphone.data.fs

import app.tidyphone.domain.model.FileCategory
import app.tidyphone.domain.model.FileEntry
import app.tidyphone.domain.safety.PathGuard
import kotlinx.coroutines.ensureActive
import java.io.File
import java.nio.file.Files
import kotlin.coroutines.coroutineContext

/**
 * Walks shared storage without following symlinks. Skips everything [PathGuard.canScan] rejects
 * (Android/data, obb, excludes, trash) and hidden folders such as `.thumbnails`.
 */
class FileScanner(
    private val guard: PathGuard,
    private val mimeOf: (String) -> String?,
) {
    data class Progress(val filesFound: Int, val bytesFound: Long, val currentDir: String)

    suspend fun scan(onBatch: suspend (List<FileEntry>) -> Unit, onProgress: (Progress) -> Unit) {
        val stack = ArrayDeque<File>().apply { add(File(guard.root)) }
        val batch = ArrayList<FileEntry>(BATCH)
        var count = 0
        var bytes = 0L
        var lastReport = 0L

        while (stack.isNotEmpty()) {
            coroutineContext.ensureActive()
            val dir = stack.removeLast()
            val children = dir.listFiles() ?: continue
            val now = System.currentTimeMillis()
            if (now - lastReport > 150) {
                onProgress(Progress(count, bytes, dir.absolutePath))
                lastReport = now
            }
            if (children.isEmpty() && dir.absolutePath != guard.root) {
                batch += entry(dir, isDir = true)
            }
            for (child in children) {
                val path = child.absolutePath
                if (isSymlink(child)) continue
                if (child.isDirectory) {
                    if (child.name.startsWith(".")) continue
                    if (guard.canScan(path)) stack.addLast(child)
                } else if (child.isFile) {
                    if (child.name == ".nomedia") continue
                    if (!guard.canScan(path)) continue
                    batch += entry(child, isDir = false)
                    count++
                    bytes += child.length()
                }
                if (batch.size >= BATCH) {
                    onBatch(batch.toList())
                    batch.clear()
                }
            }
        }
        if (batch.isNotEmpty()) onBatch(batch.toList())
        onProgress(Progress(count, bytes, ""))
    }

    private fun entry(f: File, isDir: Boolean): FileEntry {
        val ext = f.name.substringAfterLast('.', "")
        val mime = if (isDir) null else mimeOf(ext)
        return FileEntry(
            path = f.absolutePath,
            name = f.name,
            parent = f.parent ?: "",
            size = if (isDir) 0 else f.length(),
            mimeType = mime,
            lastModified = f.lastModified(),
            category = if (isDir) FileCategory.OTHER else FileCategory.of(mime, ext),
            isDirectory = isDir,
        )
    }

    private fun isSymlink(f: File): Boolean = runCatching { Files.isSymbolicLink(f.toPath()) }.getOrDefault(false)

    private companion object { const val BATCH = 500 }
}

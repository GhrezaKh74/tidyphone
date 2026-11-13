package app.tidyphone.data.fs

import app.tidyphone.domain.exec.FileOps
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

/** [FileOps] on java.nio. A move is a rename on the same volume, with copy + verify + delete as fallback. */
class JavaFileOps : FileOps {
    override fun exists(path: String) = File(path).exists()
    override fun isDirectory(path: String) = File(path).isDirectory
    override fun isEmptyDirectory(path: String) = File(path).let { it.isDirectory && it.list()?.isEmpty() == true }
    override fun mkdirs(path: String) = File(path).let { it.isDirectory || it.mkdirs() }

    override fun move(from: String, to: String): Boolean {
        val src = Paths.get(from)
        val dst = Paths.get(to)
        if (Files.exists(dst)) return false
        return try {
            Files.move(src, dst)
            true
        } catch (e: Exception) {
            copyThenDelete(File(from), File(to))
        }
    }

    private fun copyThenDelete(src: File, dst: File): Boolean {
        if (!src.isFile) return false
        return try {
            Files.copy(src.toPath(), dst.toPath(), StandardCopyOption.COPY_ATTRIBUTES)
            if (dst.length() != src.length()) { dst.delete(); return false }
            src.delete() || run { dst.delete(); false }
        } catch (e: Exception) {
            dst.delete()
            false
        }
    }

    override fun deleteRecursively(path: String): Boolean = File(path).deleteRecursively()
}

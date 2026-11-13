package app.tidyphone.domain.analysis

import app.tidyphone.domain.model.FileEntry

data class DuplicateGroup(
    val hash: String,
    val size: Long,
    /** The copy we suggest keeping. */
    val keep: FileEntry,
    /** The other copies, suggested for deletion. */
    val duplicates: List<FileEntry>,
) {
    val wastedBytes: Long get() = size * duplicates.size
}

/**
 * Finds exact duplicates. Files are first grouped by size, and only files that share a size
 * with another file get hashed, so most files are never read.
 */
class DuplicateFinder(private val minSize: Long = 1) {

    /**
     * @param hasher returns the SHA-256 of a file, or null if it can't be read. It is called
     * lazily, once per candidate, and may return a cached value.
     */
    suspend fun find(files: List<FileEntry>, hasher: suspend (FileEntry) -> String?): List<DuplicateGroup> {
        val candidates = files
            .asSequence()
            .filter { !it.isDirectory && it.size >= minSize }
            .groupBy { it.size }
            .filterValues { it.size > 1 }

        val groups = mutableListOf<DuplicateGroup>()
        for ((size, sameSize) in candidates) {
            val byHash = sameSize
                .mapNotNull { f -> (f.hash ?: hasher(f))?.let { h -> f.copy(hash = h) } }
                .groupBy { it.hash!! }
            for ((hash, copies) in byHash) {
                if (copies.size < 2) continue
                val keep = chooseKeeper(copies)
                groups += DuplicateGroup(hash, size, keep, copies.filter { it.path != keep.path })
            }
        }
        return groups.sortedByDescending { it.wastedBytes }
    }

    companion object {
        private val copySuffix = Regex("""(\s?\(\d+\)|[-_ ]copy(\s?\d+)?|\s-\s\d+)$""", RegexOption.IGNORE_CASE)
        private val transientDirs = listOf("/download", "/sent", "/.thumbnails", "/cache", "/temp", "/tmp", "/telegram")

        /**
         * Picks the copy to keep: prefer files outside Downloads/"Sent"/cache folders, then names
         * without "(1)"/"copy" suffixes, then the oldest file, then the shortest path.
         */
        fun chooseKeeper(copies: List<FileEntry>): FileEntry = copies.minWith(
            compareBy<FileEntry>(
                { f -> if (transientDirs.any { f.parent.lowercase().contains(it) }) 1 else 0 },
                { f -> if (copySuffix.containsMatchIn(f.name.substringBeforeLast('.'))) 1 else 0 },
                { it.lastModified },
                { it.path.length },
                { it.path },
            )
        )
    }
}

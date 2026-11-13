package app.tidyphone.domain.progress

/**
 * Turns scan phases into one overall percentage for the notification.
 * Indexing is 0–70 %, hashing duplicates 70–98 %, building suggestions 98–100 %.
 * While indexing, the total is unknown on a first scan, so progress is indeterminate (null);
 * on later scans the previous file count is used as the estimate.
 */
object ScanProgress {
    const val WALK_END = 70
    const val HASH_END = 98

    enum class Phase { WALK, HASH, ANALYZE }

    fun percent(phase: Phase, filesFound: Int, previousFileCount: Int, hashDone: Int, hashTotal: Int): Int? = when (phase) {
        Phase.WALK ->
            if (previousFileCount <= 0) null
            else (filesFound.toLong() * WALK_END / previousFileCount).toInt().coerceIn(0, WALK_END - 1)
        Phase.HASH ->
            if (hashTotal <= 0) WALK_END
            else WALK_END + (hashDone.toLong() * (HASH_END - WALK_END) / hashTotal).toInt().coerceIn(0, HASH_END - WALK_END)
        Phase.ANALYZE -> HASH_END
    }
}

/** AI analysis progress by files sent. */
object AnalysisProgress {
    fun percent(filesDone: Int, filesTotal: Int): Int =
        if (filesTotal <= 0) 0 else (filesDone.toLong() * 100 / filesTotal).toInt().coerceIn(0, 100)
}

/** Allows at most one update per [intervalMs], plus every forced update (phase changes, the end). */
class Throttle(private val intervalMs: Long, private val clock: () -> Long = System::currentTimeMillis) {
    private var last = Long.MIN_VALUE / 2
    fun ready(force: Boolean = false): Boolean {
        val now = clock()
        if (!force && now - last < intervalMs) return false
        last = now
        return true
    }
}

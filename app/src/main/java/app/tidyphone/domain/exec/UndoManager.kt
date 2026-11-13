package app.tidyphone.domain.exec

import app.tidyphone.domain.model.ActionType
import app.tidyphone.domain.model.HistoryRecord
import app.tidyphone.domain.safety.PathGuard

sealed interface UndoResult {
    /** Restored to [path]; [renamed] is true if the original name was taken and a free one was used. */
    data class Restored(val path: String, val renamed: Boolean) : UndoResult
    data object AlreadyUndone : UndoResult
    data object Missing : UndoResult
    data class Failed(val reason: String) : UndoResult
}

/**
 * Reverses recorded actions: a move or rename goes back to its original path, a delete is
 * restored from the trash. If something now occupies the original path, the restored file gets
 * a "(1)" style name instead of overwriting it.
 */
class UndoManager(
    private val fs: FileOps,
    private val guard: PathGuard,
    private val log: ActionLog,
    private val trash: TrashIndex,
) {
    suspend fun undo(record: HistoryRecord): UndoResult {
        if (record.undone) return UndoResult.AlreadyUndone
        if (!fs.exists(record.resultPath)) return UndoResult.Missing
        if (!guard.canRestoreTo(record.originalPath)) return UndoResult.Failed("original location is not allowed")

        val target = uniquePath(record.originalPath, fs::exists)
        val parent = target.substringBeforeLast('/')
        if (!fs.exists(parent) && !fs.mkdirs(parent)) return UndoResult.Failed("could not recreate folder")
        if (!fs.move(record.resultPath, target)) return UndoResult.Failed("move failed")

        if (record.action == ActionType.DELETE) trash.remove(record.resultPath)
        log.markUndone(record.id)
        return UndoResult.Restored(target, renamed = target != record.originalPath)
    }

    /** Undoes a whole run, newest action first, so chained moves unwind correctly. */
    suspend fun undoBatch(records: List<HistoryRecord>): List<Pair<HistoryRecord, UndoResult>> =
        records.sortedWith(compareByDescending<HistoryRecord> { it.timestamp }.thenByDescending { it.id })
            .map { it to undo(it) }
}

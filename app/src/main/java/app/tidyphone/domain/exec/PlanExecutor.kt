package app.tidyphone.domain.exec

import app.tidyphone.domain.model.ActionType
import app.tidyphone.domain.model.HistoryRecord
import app.tidyphone.domain.model.PlanItem
import app.tidyphone.domain.model.TrashItem
import app.tidyphone.domain.safety.PathGuard
import java.util.UUID

enum class ItemStatus { DONE, WOULD_DO, SKIPPED, FAILED }

data class ItemResult(val item: PlanItem, val status: ItemStatus, val resultPath: String? = null, val message: String? = null)

data class ExecutionReport(val batchId: String, val dryRun: Boolean, val results: List<ItemResult>) {
    val done: Int get() = results.count { it.status == ItemStatus.DONE || it.status == ItemStatus.WOULD_DO }
    val skipped: Int get() = results.count { it.status == ItemStatus.SKIPPED }
    val failed: Int get() = results.count { it.status == ItemStatus.FAILED }
    val bytesTrashed: Long get() = results.filter { (it.status == ItemStatus.DONE || it.status == ItemStatus.WOULD_DO) && it.item.action == ActionType.DELETE }.sumOf { it.item.size }
}

/**
 * Executes approved plan items. Nothing is ever deleted here: DELETE moves the file into the app
 * trash. Every item is re-checked against [PathGuard] at execution time, and every change is
 * written to the [ActionLog] so it can be undone. In dry-run mode the same checks run but
 * nothing changes on disk and nothing is recorded.
 */
class PlanExecutor(
    private val fs: FileOps,
    private val guard: PathGuard,
    private val trashDir: String,
    private val log: ActionLog,
    private val trash: TrashIndex,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun execute(items: List<PlanItem>, dryRun: Boolean, onProgress: (Int, Int) -> Unit = { _, _ -> }): ExecutionReport {
        val batchId = UUID.randomUUID().toString()
        val handled = HashSet<String>()
        val results = items.mapIndexed { i, item ->
            onProgress(i, items.size)
            val r = if (!handled.add(item.path.lowercase())) {
                ItemResult(item, ItemStatus.SKIPPED, message = "already handled in this run")
            } else runCatching { executeOne(item, batchId, dryRun) }
                .getOrElse { ItemResult(item, ItemStatus.FAILED, message = it.message ?: it.javaClass.simpleName) }
            r
        }
        onProgress(items.size, items.size)
        return ExecutionReport(batchId, dryRun, results)
    }

    private suspend fun executeOne(item: PlanItem, batchId: String, dryRun: Boolean): ItemResult {
        if (!guard.canModify(item.path)) return ItemResult(item, ItemStatus.SKIPPED, message = "protected path")
        if (!fs.exists(item.path)) return ItemResult(item, ItemStatus.SKIPPED, message = "file no longer exists")
        val isDir = fs.isDirectory(item.path)

        val target: String = when (item.action) {
            ActionType.DELETE -> {
                if (isDir && !fs.isEmptyDirectory(item.path)) return ItemResult(item, ItemStatus.SKIPPED, message = "folder is not empty")
                val stamp = clock()
                uniquePath("${trashDir.trimEnd('/')}/${stamp}__${item.fileName}", fs::exists)
            }
            ActionType.MOVE, ActionType.RENAME -> {
                if (isDir) return ItemResult(item, ItemStatus.SKIPPED, message = "folders are not moved")
                val newName = item.newName ?: item.fileName
                if (!guard.isValidFileName(newName)) return ItemResult(item, ItemStatus.SKIPPED, message = "invalid file name")
                val dest = item.destinationPath ?: return ItemResult(item, ItemStatus.SKIPPED, message = "no destination")
                val norm = PathGuard.normalize(dest) ?: return ItemResult(item, ItemStatus.SKIPPED, message = "invalid destination")
                if (!guard.canWriteTo(norm) || !guard.canWriteTo(norm.substringBeforeLast('/'))) {
                    return ItemResult(item, ItemStatus.SKIPPED, message = "destination not allowed")
                }
                if (norm == item.path) return ItemResult(item, ItemStatus.SKIPPED, message = "already there")
                uniquePath(norm, fs::exists)
            }
        }

        if (dryRun) return ItemResult(item, ItemStatus.WOULD_DO, resultPath = target)

        val parent = target.substringBeforeLast('/')
        if (!fs.exists(parent) && !fs.mkdirs(parent)) return ItemResult(item, ItemStatus.FAILED, message = "could not create folder")
        if (!fs.move(item.path, target)) return ItemResult(item, ItemStatus.FAILED, message = "move failed")

        val now = clock()
        if (item.action == ActionType.DELETE) {
            trash.add(TrashItem(originalPath = item.path, trashPath = target, trashedAt = now, size = item.size))
        }
        log.record(HistoryRecord(batchId = batchId, timestamp = now, action = item.action, originalPath = item.path, resultPath = target, size = item.size))
        return ItemResult(item, ItemStatus.DONE, resultPath = target)
    }
}

package app.tidyphone.data.repo

import android.content.Context
import android.media.MediaScannerConnection
import app.tidyphone.data.db.FileDao
import app.tidyphone.data.db.HistoryDao
import app.tidyphone.data.db.HistoryEntity
import app.tidyphone.data.db.PlanDao
import app.tidyphone.data.db.TrashDao
import app.tidyphone.data.db.TrashEntity
import app.tidyphone.data.fs.JavaFileOps
import app.tidyphone.data.fs.StorageLocations
import app.tidyphone.data.settings.SettingsRepository
import app.tidyphone.domain.exec.ActionLog
import app.tidyphone.domain.exec.ExecutionReport
import app.tidyphone.domain.exec.ItemStatus
import app.tidyphone.domain.exec.PlanExecutor
import app.tidyphone.domain.exec.TrashIndex
import app.tidyphone.domain.exec.UndoManager
import app.tidyphone.domain.exec.UndoResult
import app.tidyphone.domain.model.ActionType
import app.tidyphone.domain.model.HistoryRecord
import app.tidyphone.domain.model.PlanItem
import app.tidyphone.domain.model.TrashItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomActionLog @Inject constructor(private val dao: HistoryDao) : ActionLog {
    override suspend fun record(r: HistoryRecord) = dao.insert(HistoryEntity.from(r))
    override suspend fun markUndone(id: Long) = dao.markUndone(id, System.currentTimeMillis())
}

@Singleton
class RoomTrashIndex @Inject constructor(private val dao: TrashDao) : TrashIndex {
    override suspend fun add(item: TrashItem) = dao.insert(TrashEntity.from(item))
    override suspend fun remove(trashPath: String) { dao.byTrashPath(trashPath)?.let { dao.delete(it.id) } }
    override suspend fun find(trashPath: String) = dao.byTrashPath(trashPath)?.toDomain()
}

/** Keeps the file index and the system media index in sync after files move. */
@Singleton
class IndexSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileDao: FileDao,
) {
    suspend fun moved(from: String, to: String, trashed: Boolean) {
        val row = fileDao.get(from)
        fileDao.delete(from)
        if (row != null && !trashed) {
            fileDao.upsertAll(listOf(row.copy(path = to, name = to.substringAfterLast('/'), parent = to.substringBeforeLast('/'))))
        }
    }

    fun notifyMediaStore(paths: Collection<String>) {
        if (paths.isEmpty()) return
        runCatching { MediaScannerConnection.scanFile(context, paths.toTypedArray(), null, null) }
    }
}

@Singleton
class PlanRepository @Inject constructor(
    private val planDao: PlanDao,
    private val settings: SettingsRepository,
    private val guards: GuardProvider,
    private val locations: StorageLocations,
    private val log: RoomActionLog,
    private val trash: RoomTrashIndex,
    private val sync: IndexSync,
    private val skipped: SkippedRepository,
) {
    val items: Flow<List<PlanItem>> = planDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun setSelected(ids: List<Long>, selected: Boolean) = ids.chunked(500).forEach { planDao.setSelected(it, selected) }
    suspend fun dismiss(ids: List<Long>) = ids.chunked(500).forEach { planDao.delete(it) }

    /** Removes suggestions from the plan and keeps them from coming back on later scans. */
    suspend fun skip(items: List<PlanItem>) {
        skipped.add(items)
        dismiss(items.map { it.id })
    }

    /** Undo for [skip]: puts the same suggestions back. */
    suspend fun unskip(items: List<PlanItem>) {
        skipped.remove(items)
        val now = System.currentTimeMillis()
        planDao.insertAll(items.map { app.tidyphone.data.db.PlanItemEntity.from(it.copy(id = 0), now) })
    }
    suspend fun clearAll() = planDao.clear()

    /** Runs the selected items (only after the user approved them in the Review screen). */
    suspend fun executeSelected(onProgress: (Int, Int) -> Unit = { _, _ -> }): ExecutionReport = withContext(Dispatchers.IO) {
        val s = settings.current()
        locations.ensureTrash()
        val items = planDao.selected().map { it.toDomain() }
            // Moves and renames first, so a later delete of a sibling can't empty a folder we target.
            .sortedBy { if (it.action == ActionType.DELETE) 1 else 0 }
        val executor = PlanExecutor(JavaFileOps(), guards.guard(s), locations.trashDir, log, trash)
        val report = executor.execute(items, dryRun = s.dryRun, onProgress = onProgress)
        if (!report.dryRun) {
            val touched = mutableListOf<String>()
            report.results.filter { it.status == ItemStatus.DONE }.forEach { r ->
                sync.moved(r.item.path, r.resultPath!!, trashed = r.item.action == ActionType.DELETE)
                touched += r.item.path
                if (r.item.action != ActionType.DELETE) touched += r.resultPath
            }
            sync.notifyMediaStore(touched)
            val finished = report.results.filter { it.status == ItemStatus.DONE || it.status == ItemStatus.SKIPPED }.map { it.item.path }
            finished.chunked(500).forEach { planDao.deleteByPaths(it) }
        }
        report
    }
}

@Singleton
class HistoryRepository @Inject constructor(
    private val dao: HistoryDao,
    private val guards: GuardProvider,
    private val log: RoomActionLog,
    private val trash: RoomTrashIndex,
    private val sync: IndexSync,
) {
    val records: Flow<List<HistoryRecord>> = dao.observeAll().map { l -> l.map { it.toDomain() } }

    private suspend fun manager() = UndoManager(JavaFileOps(), guards.guard(), log, trash)

    suspend fun undo(id: Long): UndoResult = withContext(Dispatchers.IO) {
        val record = dao.get(id)?.toDomain() ?: return@withContext UndoResult.Missing
        manager().undo(record).also { afterUndo(record, it) }
    }

    suspend fun undoBatch(batchId: String): List<UndoResult> = withContext(Dispatchers.IO) {
        manager().undoBatch(dao.batch(batchId).map { it.toDomain() }).map { (r, result) -> afterUndo(r, result); result }
    }

    private suspend fun afterUndo(record: HistoryRecord, result: UndoResult) {
        if (result is UndoResult.Restored) sync.notifyMediaStore(listOf(record.resultPath, result.path))
    }
}

@Singleton
class TrashRepository @Inject constructor(
    private val dao: TrashDao,
    private val historyDao: HistoryDao,
    private val history: HistoryRepository,
    private val settings: SettingsRepository,
    private val guards: GuardProvider,
) {
    val items: Flow<List<TrashItem>> = dao.observeAll().map { l -> l.map { it.toDomain() } }

    /** Restores through the history record when there is one, so history shows it as undone. */
    suspend fun restore(item: TrashItem): UndoResult = withContext(Dispatchers.IO) {
        val record = historyDao.byResultPath(item.trashPath)
        if (record != null) return@withContext history.undo(record.id)
        val manager = UndoManager(JavaFileOps(), guards.guard(), object : ActionLog {
            override suspend fun record(r: HistoryRecord) = 0L
            override suspend fun markUndone(id: Long) = Unit
        }, RoomTrashIndex(dao))
        manager.undo(HistoryRecord(batchId = "", timestamp = item.trashedAt, action = ActionType.DELETE, originalPath = item.originalPath, resultPath = item.trashPath, size = item.size))
    }

    suspend fun purge(items: List<TrashItem>) = withContext(Dispatchers.IO) {
        val guard = guards.guard()
        val fs = JavaFileOps()
        for (t in items) {
            // Permanent deletion only ever happens inside the app's own trash folder.
            if (!guard.isTrash(t.trashPath)) continue
            if (!fs.exists(t.trashPath) || fs.deleteRecursively(t.trashPath)) dao.delete(t.id)
        }
    }

    suspend fun emptyAll() = purge(dao.all().map { it.toDomain() })

    suspend fun emptyExpired() {
        val days = settings.current().trashRetentionDays
        purge(dao.olderThan(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong())).map { it.toDomain() })
    }
}

@Singleton
class SkippedRepository @Inject constructor(private val dao: app.tidyphone.data.db.IgnoredDao) {
    val count: Flow<Int> = dao.observeCount()

    suspend fun add(items: List<PlanItem>) {
        val now = System.currentTimeMillis()
        dao.upsertAll(items.map { app.tidyphone.data.db.IgnoredEntity(it.path, it.source, it.size, now) })
    }

    suspend fun filter(): app.tidyphone.domain.model.SkipFilter =
        app.tidyphone.domain.model.SkipFilter(dao.all().map { app.tidyphone.domain.model.SkipFilter.Skipped(it.path, it.source, it.size) })

    suspend fun remove(items: List<PlanItem>) =
        dao.delete(items.map { app.tidyphone.data.db.IgnoredEntity(it.path, it.source, it.size, 0) })

    /** Forget all skips; the suggestions come back with the next scan or AI run. */
    suspend fun clear() = dao.clear()
}

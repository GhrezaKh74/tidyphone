package app.tidyphone.data.worker

import android.content.Context
import android.webkit.MimeTypeMap
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.tidyphone.R
import app.tidyphone.data.db.FileDao
import app.tidyphone.data.db.FileEntity
import app.tidyphone.data.db.PlanDao
import app.tidyphone.data.db.PlanItemEntity
import app.tidyphone.data.fs.FileScanner
import app.tidyphone.data.fs.Hasher
import app.tidyphone.data.notify.ProgressNotifier
import app.tidyphone.data.repo.GuardProvider
import app.tidyphone.data.settings.SettingsRepository
import app.tidyphone.domain.analysis.DuplicateFinder
import app.tidyphone.domain.analysis.LocalAnalysisConfig
import app.tidyphone.domain.analysis.LocalAnalyzer
import app.tidyphone.domain.model.PlanSource
import app.tidyphone.domain.progress.ScanProgress
import app.tidyphone.domain.progress.Throttle
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Indexes shared storage, hashes same-size files, and refreshes the on-device suggestions.
 * Runs as a long-running foreground worker with a wake lock, so it keeps going with the screen
 * off or locked, and shows live progress in the status bar.
 */
@HiltWorker
class ScanWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val fileDao: FileDao,
    private val planDao: PlanDao,
    private val settings: SettingsRepository,
    private val guards: GuardProvider,
    private val notifier: ProgressNotifier,
) : CoroutineWorker(context, params) {

    private val ctx get() = applicationContext
    private val cancelIntent get() = WorkManager.getInstance(ctx).createCancelPendingIntent(id)
    private val throttle = Throttle(1000)

    private fun progress(phase: ScanProgress.Phase, found: Int, bytes: Long, previous: Int, hashDone: Int = 0, hashTotal: Int = 0): ProgressNotifier.Progress {
        val title = ctx.getString(when (phase) {
            ScanProgress.Phase.WALK -> R.string.notif_scan_walk
            ScanProgress.Phase.HASH -> R.string.notif_scan_hash
            ScanProgress.Phase.ANALYZE -> R.string.notif_scan_analyze
        })
        val text = if (phase == ScanProgress.Phase.HASH && hashTotal > 0) ctx.getString(R.string.notif_hash_text, ctx.num(hashDone), ctx.num(hashTotal))
        else ctx.getString(R.string.notif_scan_text, ctx.num(found), ctx.size(bytes))
        return ProgressNotifier.Progress(title, text, ScanProgress.percent(phase, found, previous, hashDone, hashTotal), cancelIntent)
    }

    private suspend fun show(p: ProgressNotifier.Progress, force: Boolean = false) {
        if (throttle.ready(force)) runCatching { setForeground(notifier.foregroundInfo(ProgressNotifier.ID_SCAN, p)) }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        notifier.foregroundInfo(ProgressNotifier.ID_SCAN, progress(ScanProgress.Phase.WALK, 0, 0, 0))

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        ctx.withWakeLock("TidyPhone:scan", 3 * 60 * 60 * 1000L) { scan() }
    }

    private suspend fun scan(): Result {
        val previous = fileDao.countFiles()
        show(progress(ScanProgress.Phase.WALK, 0, 0, previous), force = true)

        val s = settings.current()
        val guard = guards.guard(s)
        val scanId = System.currentTimeMillis()
        val known = fileDao.knownHashes().associateBy { it.path }
        val mimeMap = MimeTypeMap.getSingleton()
        val scanner = FileScanner(guard) { ext -> mimeMap.getMimeTypeFromExtension(ext.lowercase()) }

        var found = 0
        var bytes = 0L
        scanner.scan(
            onBatch = { batch ->
                fileDao.upsertAll(batch.map { f ->
                    val k = known[f.path]
                    val hash = if (k != null && k.size == f.size && k.lastModified == f.lastModified) k.hash else null
                    FileEntity(f.path, f.name, f.parent, f.size, f.mimeType, f.lastModified, f.category, hash, f.isDirectory, scanId)
                })
                show(progress(ScanProgress.Phase.WALK, found, bytes, previous))
            },
            onProgress = { p ->
                found = p.filesFound
                bytes = p.bytesFound
                setProgressAsync(workDataOf(KEY_PHASE to PHASE_WALK, KEY_FILES to p.filesFound, KEY_BYTES to p.bytesFound, KEY_DIR to p.currentDir))
            },
        )
        fileDao.deleteStale(scanId)

        // Duplicates: hash only files whose size matches another file.
        val files = fileDao.all().map { it.toDomain() }
        val candidates = files.filter { !it.isDirectory && it.size > 0 }.groupBy { it.size }.values.filter { it.size > 1 }.sumOf { it.size }
        var hashed = 0
        setProgress(workDataOf(KEY_PHASE to PHASE_HASH, KEY_FILES to found, KEY_BYTES to bytes, KEY_DONE to 0, KEY_TOTAL to candidates))
        show(progress(ScanProgress.Phase.HASH, found, bytes, previous, 0, candidates), force = true)
        val groups = DuplicateFinder().find(files) { f ->
            val h = Hasher.sha256(File(f.path))
            if (h != null) fileDao.setHash(f.path, h)
            hashed++
            if (hashed % 10 == 0 || hashed == candidates) {
                setProgressAsync(workDataOf(KEY_PHASE to PHASE_HASH, KEY_FILES to found, KEY_BYTES to bytes, KEY_DONE to hashed, KEY_TOTAL to candidates, KEY_DIR to f.name))
            }
            show(progress(ScanProgress.Phase.HASH, found, bytes, previous, hashed, candidates))
            h
        }

        setProgress(workDataOf(KEY_PHASE to PHASE_ANALYZE, KEY_FILES to found, KEY_BYTES to bytes))
        show(progress(ScanProgress.Phase.ANALYZE, found, bytes, previous), force = true)
        val config = LocalAnalysisConfig(
            largeFileBytes = s.largeFileMb.toLong() * 1024 * 1024,
            oldDownloadDays = s.oldDownloadDays,
            oldScreenshotDays = s.oldScreenshotDays,
        )
        val items = LocalAnalyzer(guard).analyze(files, groups, config)
        val now = System.currentTimeMillis()
        planDao.replaceSources(LOCAL_SOURCES, items.map { PlanItemEntity.from(it, now) })

        val reclaim = items.filter { it.selected }.sumOf { it.size }
        notifier.finished(
            ProgressNotifier.ID_SCAN_DONE,
            ctx.getString(R.string.notif_scan_done_title),
            ctx.getString(R.string.notif_scan_done_text, ctx.num(found), ctx.num(items.size), ctx.size(reclaim)),
            ProgressNotifier.DEST_REVIEW,
        )
        return Result.success(workDataOf(
            KEY_FILES to found, KEY_BYTES to bytes,
            KEY_DUP_GROUPS to groups.size, KEY_SUGGESTIONS to items.size,
            KEY_RECLAIM to reclaim,
        ))
    }

    companion object {
        const val NAME = "tidyphone-scan"
        const val KEY_PHASE = "phase"
        const val KEY_FILES = "files"
        const val KEY_BYTES = "bytes"
        const val KEY_DIR = "dir"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_DUP_GROUPS = "dup_groups"
        const val KEY_SUGGESTIONS = "suggestions"
        const val KEY_RECLAIM = "reclaim"
        const val PHASE_WALK = "walk"
        const val PHASE_HASH = "hash"
        const val PHASE_ANALYZE = "analyze"

        val LOCAL_SOURCES = PlanSource.entries.filter { it != PlanSource.LLM }
    }
}

package app.tidyphone.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.webkit.MimeTypeMap
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.tidyphone.R
import app.tidyphone.data.db.FileDao
import app.tidyphone.data.db.FileEntity
import app.tidyphone.data.db.PlanDao
import app.tidyphone.data.db.PlanItemEntity
import app.tidyphone.data.fs.FileScanner
import app.tidyphone.data.fs.Hasher
import app.tidyphone.data.repo.GuardProvider
import app.tidyphone.data.settings.SettingsRepository
import app.tidyphone.domain.analysis.DuplicateFinder
import app.tidyphone.domain.analysis.LocalAnalysisConfig
import app.tidyphone.domain.analysis.LocalAnalyzer
import app.tidyphone.domain.model.PlanSource
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Indexes shared storage, hashes same-size files, and refreshes the on-device suggestions. */
@HiltWorker
class ScanWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val fileDao: FileDao,
    private val planDao: PlanDao,
    private val settings: SettingsRepository,
    private val guards: GuardProvider,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        runCatching { setForeground(foregroundInfo(0)) }

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
        val groups = DuplicateFinder().find(files) { f ->
            val h = Hasher.sha256(File(f.path))
            if (h != null) fileDao.setHash(f.path, h)
            hashed++
            if (hashed % 10 == 0 || hashed == candidates) {
                setProgressAsync(workDataOf(KEY_PHASE to PHASE_HASH, KEY_FILES to found, KEY_BYTES to bytes, KEY_DONE to hashed, KEY_TOTAL to candidates, KEY_DIR to f.name))
            }
            h
        }

        setProgress(workDataOf(KEY_PHASE to PHASE_ANALYZE, KEY_FILES to found, KEY_BYTES to bytes))
        val config = LocalAnalysisConfig(
            largeFileBytes = s.largeFileMb.toLong() * 1024 * 1024,
            oldDownloadDays = s.oldDownloadDays,
            oldScreenshotDays = s.oldScreenshotDays,
        )
        val items = LocalAnalyzer(guard).analyze(files, groups, config)
        val now = System.currentTimeMillis()
        planDao.replaceSources(LOCAL_SOURCES, items.map { PlanItemEntity.from(it, now) })

        Result.success(workDataOf(
            KEY_FILES to found, KEY_BYTES to bytes,
            KEY_DUP_GROUPS to groups.size, KEY_SUGGESTIONS to items.size,
            KEY_RECLAIM to items.filter { it.selected }.sumOf { it.size },
        ))
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(0)

    private fun foregroundInfo(progress: Int): ForegroundInfo {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, applicationContext.getString(R.string.notif_channel_scan), NotificationManager.IMPORTANCE_LOW))
        }
        val n = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_tidy)
            .setContentTitle(applicationContext.getString(R.string.notif_scanning))
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setSilent(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else ForegroundInfo(NOTIF_ID, n)
    }

    companion object {
        const val NAME = "tidyphone-scan"
        const val CHANNEL = "scan"
        const val NOTIF_ID = 42
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

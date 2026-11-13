package app.tidyphone.data.repo

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.tidyphone.data.worker.ScanWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class ScanPhase { IDLE, WALK, HASH, ANALYZE, DONE, FAILED }

data class ScanState(
    val phase: ScanPhase = ScanPhase.IDLE,
    val filesFound: Int = 0,
    val bytesFound: Long = 0,
    val currentItem: String = "",
    val hashDone: Int = 0,
    val hashTotal: Int = 0,
    val duplicateGroups: Int = 0,
    val suggestions: Int = 0,
    val reclaimableBytes: Long = 0,
) {
    val running: Boolean get() = phase in setOf(ScanPhase.WALK, ScanPhase.HASH, ScanPhase.ANALYZE)
}

@Singleton
class ScanRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private val wm get() = WorkManager.getInstance(context)

    fun startScan() {
        // A regular (not expedited) request: the worker promotes itself to a long-running
        // foreground service, which isn't subject to the expedited-job time quota.
        val request = OneTimeWorkRequestBuilder<ScanWorker>().build()
        wm.enqueueUniqueWork(ScanWorker.NAME, ExistingWorkPolicy.KEEP, request)
    }

    fun cancel() = wm.cancelUniqueWork(ScanWorker.NAME)

    val state: Flow<ScanState> = wm.getWorkInfosForUniqueWorkFlow(ScanWorker.NAME).map { infos ->
        val info = infos.lastOrNull() ?: return@map ScanState()
        when (info.state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> ScanState(ScanPhase.WALK)
            WorkInfo.State.RUNNING -> {
                val p = info.progress
                ScanState(
                    phase = when (p.getString(ScanWorker.KEY_PHASE)) {
                        ScanWorker.PHASE_HASH -> ScanPhase.HASH
                        ScanWorker.PHASE_ANALYZE -> ScanPhase.ANALYZE
                        else -> ScanPhase.WALK
                    },
                    filesFound = p.getInt(ScanWorker.KEY_FILES, 0),
                    bytesFound = p.getLong(ScanWorker.KEY_BYTES, 0),
                    currentItem = p.getString(ScanWorker.KEY_DIR).orEmpty(),
                    hashDone = p.getInt(ScanWorker.KEY_DONE, 0),
                    hashTotal = p.getInt(ScanWorker.KEY_TOTAL, 0),
                )
            }
            WorkInfo.State.SUCCEEDED -> {
                val o = info.outputData
                ScanState(
                    phase = ScanPhase.DONE,
                    filesFound = o.getInt(ScanWorker.KEY_FILES, 0),
                    bytesFound = o.getLong(ScanWorker.KEY_BYTES, 0),
                    duplicateGroups = o.getInt(ScanWorker.KEY_DUP_GROUPS, 0),
                    suggestions = o.getInt(ScanWorker.KEY_SUGGESTIONS, 0),
                    reclaimableBytes = o.getLong(ScanWorker.KEY_RECLAIM, 0),
                )
            }
            WorkInfo.State.FAILED -> ScanState(ScanPhase.FAILED)
            WorkInfo.State.CANCELLED -> ScanState(ScanPhase.IDLE)
        }
    }
}

package app.tidyphone.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.tidyphone.data.repo.TrashRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Daily job: permanently removes trash entries older than the retention period. */
@HiltWorker
class TrashCleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val trash: TrashRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        trash.emptyExpired()
        return Result.success()
    }

    companion object { const val NAME = "tidyphone-trash-cleanup" }
}

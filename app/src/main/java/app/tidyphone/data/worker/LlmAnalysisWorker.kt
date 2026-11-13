package app.tidyphone.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.tidyphone.R
import app.tidyphone.data.notify.ProgressNotifier
import app.tidyphone.data.repo.AnalysisScope
import app.tidyphone.data.repo.LlmAnalysisRepository
import app.tidyphone.domain.llm.LlmProgress
import app.tidyphone.domain.progress.AnalysisProgress
import app.tidyphone.domain.progress.Throttle
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Runs AI analysis in the foreground with a status bar progress notification, screen on or off. */
@HiltWorker
class LlmAnalysisWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repo: LlmAnalysisRepository,
    private val notifier: ProgressNotifier,
) : CoroutineWorker(context, params) {

    private val ctx get() = applicationContext
    private val throttle = Throttle(1000)

    private fun progress(p: LlmProgress?): ProgressNotifier.Progress = ProgressNotifier.Progress(
        title = ctx.getString(R.string.notif_ai_title),
        text = if (p == null || p.filesTotal == 0) ctx.getString(R.string.notif_ai_preparing)
        else if (p.batchCount == 0) ctx.getString(R.string.ai_reading_meta, ctx.num(p.filesDone), ctx.num(p.filesTotal))
        else ctx.getString(R.string.ai_progress, ctx.num(p.filesDone), ctx.num(p.filesTotal), ctx.num(p.batchIndex + 1), ctx.num(p.batchCount)),
        percent = p?.let { AnalysisProgress.percent(it.filesDone, it.filesTotal) },
        cancelIntent = WorkManager.getInstance(ctx).createCancelPendingIntent(id),
    )

    override suspend fun getForegroundInfo(): ForegroundInfo = notifier.foregroundInfo(ProgressNotifier.ID_AI, progress(null))

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // If the process died mid-run, don't silently start over and pay for the requests again.
        if (runAttemptCount > 0) {
            repo.markInterrupted(ctx.getString(R.string.ai_interrupted))
            return@withContext Result.failure()
        }
        runCatching { setForeground(notifier.foregroundInfo(ProgressNotifier.ID_AI, progress(null))) }
        val scope = runCatching { AnalysisScope.valueOf(inputData.getString(KEY_SCOPE)!!) }.getOrDefault(AnalysisScope.ALL)
        val includeContent = inputData.getBoolean(KEY_CONTENT, false)

        val state = ctx.withWakeLock("TidyPhone:ai", 2 * 60 * 60 * 1000L) {
            repo.execute(scope, includeContent) { p ->
                val last = p.batchIndex >= p.batchCount
                if (throttle.ready(force = last)) runCatching { setForegroundAsync(notifier.foregroundInfo(ProgressNotifier.ID_AI, progress(p))) }
            }
        }
        if (!isStopped) {
            val ok = state.finishedSuggestions != null
            notifier.finished(
                ProgressNotifier.ID_AI_DONE,
                ctx.getString(if (ok) R.string.notif_ai_done_title else R.string.ai_failed),
                if (ok) ctx.getString(R.string.ai_done, ctx.num(state.finishedSuggestions!!)) else state.error.orEmpty(),
                ProgressNotifier.DEST_REVIEW,
            )
        }
        if (state.finishedSuggestions != null) Result.success() else Result.failure()
    }

    companion object {
        const val NAME = "tidyphone-ai"
        const val KEY_SCOPE = "scope"
        const val KEY_CONTENT = "content"
    }
}

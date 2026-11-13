package app.tidyphone

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class TidyApp : Application(), Configuration.Provider, coil3.SingletonImageLoader.Factory {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.WARN)
            .build()

    override fun newImageLoader(context: coil3.PlatformContext): coil3.ImageLoader =
        coil3.ImageLoader.Builder(context)
            .components { add(coil3.video.VideoFrameDecoder.Factory()) }
            .build()

    override fun onCreate() {
        super.onCreate()
        // Daily trash cleanup (respects the retention setting).
        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            app.tidyphone.data.worker.TrashCleanupWorker.NAME,
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            androidx.work.PeriodicWorkRequestBuilder<app.tidyphone.data.worker.TrashCleanupWorker>(1, java.util.concurrent.TimeUnit.DAYS)
                .setConstraints(androidx.work.Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build(),
        )
    }
}

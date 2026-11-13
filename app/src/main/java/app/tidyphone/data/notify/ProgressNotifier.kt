package app.tidyphone.data.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ForegroundInfo
import app.tidyphone.MainActivity
import app.tidyphone.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ongoing progress notifications for background work. On Android 16+ they are requested as
 * promoted "Live Updates", which show a progress chip in the status bar; on older versions they are
 * regular ongoing notifications with a progress bar.
 */
@Singleton
class ProgressNotifier @Inject constructor(@ApplicationContext private val context: Context) {

    data class Progress(
        val title: String,
        val text: String,
        /** 0–100, or null for indeterminate. */
        val percent: Int?,
        val cancelIntent: PendingIntent?,
    )

    init { createChannels() }

    fun foregroundInfo(id: Int, p: Progress): ForegroundInfo {
        val n = build(p)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(id, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else ForegroundInfo(id, n)
    }

    /** Posts a one-off "finished" notification; tapping it opens [destination] in the app. */
    fun finished(id: Int, title: String, text: String, destination: String) {
        if (!canPost()) return
        val n = NotificationCompat.Builder(context, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_stat_tidy)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setColor(BRAND)
            .setAutoCancel(true)
            .setContentIntent(openApp(destination))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, n) }
    }

    fun cancel(id: Int) = NotificationManagerCompat.from(context).cancel(id)

    private fun build(p: Progress) = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
        .setSmallIcon(R.drawable.ic_stat_tidy)
        .setContentTitle(p.title)
        .setContentText(p.text)
        .setSubText(p.percent?.let { "$it%" })
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setShowWhen(false)
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .setContentIntent(openApp(null))
        // Classic progress bar (Android 15 and older).
        .setProgress(100, p.percent ?: 0, p.percent == null)
        // Live Update (Android 16+): status bar chip with the percentage.
        .setStyle(
            NotificationCompat.ProgressStyle()
                .setProgressSegments(listOf(NotificationCompat.ProgressStyle.Segment(100).setColor(BRAND)))
                .setProgress(p.percent ?: 0)
                .setProgressIndeterminate(p.percent == null)
                .setStyledByProgress(true)
        )
        .setRequestPromotedOngoing(true)
        .setShortCriticalText(p.percent?.let { "$it%" } ?: "…")
        .apply {
            p.cancelIntent?.let { addAction(R.drawable.ic_stat_tidy, context.getString(R.string.cancel), it) }
        }
        .build()

    private fun openApp(destination: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            destination?.let { putExtra(EXTRA_DESTINATION, it) }
        }
        return PendingIntent.getActivity(
            context, destination?.hashCode() ?: 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun canPost(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_PROGRESS, context.getString(R.string.notif_channel_progress), NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_DONE, context.getString(R.string.notif_channel_done), NotificationManager.IMPORTANCE_DEFAULT)
        )
        // Channel from earlier versions.
        nm.deleteNotificationChannel("scan")
    }

    companion object {
        const val CHANNEL_PROGRESS = "progress"
        const val CHANNEL_DONE = "done"
        const val ID_SCAN = 42
        const val ID_SCAN_DONE = 43
        const val ID_AI = 44
        const val ID_AI_DONE = 45
        const val EXTRA_DESTINATION = "destination"
        const val DEST_REVIEW = "review"
        private const val BRAND = 0xFF7C6CFF.toInt()

    }
}

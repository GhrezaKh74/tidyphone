package app.tidyphone.data.repo

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.os.storage.StorageManager
import android.provider.Settings
import app.tidyphone.domain.apps.AppUsage
import app.tidyphone.domain.apps.InstalledApp
import app.tidyphone.domain.apps.UnusedAppsAnalyzer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class AppsSummary(val unusedCount: Int, val unusedBytes: Long)

/** Installed user apps with their last use (UsageStats) and size (StorageStats). */
@Singleton
class AppsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private val _summary = MutableStateFlow<AppsSummary?>(null)
    val summary: StateFlow<AppsSummary?> = _summary.asStateFlow()

    fun hasUsageAccess(): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun usageAccessIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${context.packageName}"))

    fun usageAccessFallbackIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    fun uninstallIntent(packageName: String): Intent =
        Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))

    fun isInstalled(packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0); true
    } catch (e: PackageManager.NameNotFoundException) { false }

    suspend fun load(thresholdDays: Int): List<AppUsage> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val now = System.currentTimeMillis()
        val usage = context.getSystemService(UsageStatsManager::class.java)
        val stats = runCatching { usage.queryAndAggregateUsageStats(now - TimeUnit.DAYS.toMillis(730), now) }.getOrDefault(emptyMap())
        val historyStart = stats.values.minOfOrNull { it.firstTimeStamp }?.takeIf { it > 0 } ?: (now - TimeUnit.DAYS.toMillis(730))
        val storage = context.getSystemService(StorageStatsManager::class.java)

        @Suppress("DEPRECATION")
        val apps = pm.getInstalledApplications(0)
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 && it.packageName != context.packageName && it.enabled }
            .mapNotNull { info ->
                val pkg = runCatching { pm.getPackageInfo(info.packageName, 0) }.getOrNull() ?: return@mapNotNull null
                val s = stats[info.packageName]
                val lastUsed = listOfNotNull(
                    s?.lastTimeUsed,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) s?.lastTimeVisible else null,
                ).maxOrNull()?.takeIf { it > 0 }
                val size = runCatching {
                    storage.queryStatsForPackage(StorageManager.UUID_DEFAULT, info.packageName, Process.myUserHandle())
                        .let { it.appBytes + it.dataBytes }
                }.getOrNull()
                InstalledApp(
                    packageName = info.packageName,
                    label = info.loadLabel(pm).toString(),
                    isGame = isGame(info),
                    installedAt = pkg.firstInstallTime,
                    lastUsed = lastUsed,
                    sizeBytes = size,
                )
            }
        UnusedAppsAnalyzer.analyze(apps, now, thresholdDays, historyStart).also { list ->
            val unused = list.filter { it.isUnused }
            _summary.value = AppsSummary(unused.size, unused.sumOf { it.app.sizeBytes ?: 0 })
        }
    }

    @Suppress("DEPRECATION")
    private fun isGame(info: ApplicationInfo): Boolean =
        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && info.category == ApplicationInfo.CATEGORY_GAME) ||
            info.flags and ApplicationInfo.FLAG_IS_GAME != 0
}

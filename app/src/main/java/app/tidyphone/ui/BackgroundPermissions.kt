package app.tidyphone.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

object BackgroundPermissions {
    fun notificationsAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun batteryUnrestricted(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    @SuppressLint("BatteryLife")
    fun batteryIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
}

/**
 * Returns a function that asks for the notification permission (Android 13+) the first time it's
 * needed, then runs the action whatever the answer is: background work runs either way, the
 * permission only decides whether progress shows in the status bar.
 */
@Composable
fun rememberWithNotificationPermission(): (() -> Unit) -> Unit {
    val context = LocalContext.current
    val pending = remember { arrayOfNulls<() -> Unit>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pending[0]?.invoke(); pending[0] = null
    }
    return remember(launcher) {
        { action ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !BackgroundPermissions.notificationsAllowed(context)) {
                pending[0] = action
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else action()
        }
    }
}

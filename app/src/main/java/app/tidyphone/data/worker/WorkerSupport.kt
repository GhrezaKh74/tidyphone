package app.tidyphone.data.worker

import android.content.Context
import android.os.PowerManager
import android.text.format.Formatter
import java.text.NumberFormat

/** Keeps the CPU awake while the screen is off. Always released, with a safety timeout. */
internal suspend fun <T> Context.withWakeLock(tag: String, timeoutMs: Long, block: suspend () -> T): T {
    val lock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag)
        .apply { setReferenceCounted(false); acquire(timeoutMs) }
    return try { block() } finally { if (lock.isHeld) lock.release() }
}

internal fun Context.num(n: Number): String = NumberFormat.getInstance(resources.configuration.locales[0]).format(n)
internal fun Context.size(b: Long): String = Formatter.formatShortFileSize(this, b.coerceAtLeast(0))

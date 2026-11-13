package app.tidyphone.data.fs

import android.os.Environment
import android.os.StatFs
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class StorageStats(val totalBytes: Long, val freeBytes: Long) {
    val usedBytes: Long get() = totalBytes - freeBytes
}

@Singleton
class StorageLocations @Inject constructor() {
    /** Primary shared storage, e.g. /storage/emulated/0. */
    val root: String get() = Environment.getExternalStorageDirectory().absolutePath

    /** App-managed trash. Same volume as the files, so "deleting" is an instant rename. */
    val trashDir: String get() = "$root/$TRASH_FOLDER"

    fun ensureTrash(): File = File(trashDir).apply {
        if (!exists()) mkdirs()
        File(this, ".nomedia").let { if (!it.exists()) it.createNewFile() }
    }

    fun stats(): StorageStats = runCatching {
        val s = StatFs(root)
        StorageStats(s.totalBytes, s.availableBytes)
    }.getOrDefault(StorageStats(0, 0))

    companion object {
        const val TRASH_FOLDER = ".TidyPhoneTrash"
    }
}

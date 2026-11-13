package app.tidyphone.data.llm

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.os.ParcelFileDescriptor
import android.util.Base64
import app.tidyphone.domain.model.FileCategory
import app.tidyphone.domain.model.FileEntry
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/** Small content samples for opt-in content analysis: 256 px JPEG thumbnails or the first lines of text. */
@Singleton
class ContentSampler @Inject constructor() {

    enum class Kind { IMAGE, TEXT, NONE }

    fun kindOf(f: FileEntry): Kind = when {
        f.category == FileCategory.IMAGE && f.extension != "svg" -> Kind.IMAGE
        f.category == FileCategory.VIDEO -> Kind.IMAGE
        f.extension == "pdf" -> Kind.IMAGE
        f.extension in textExtensions || f.mimeType?.startsWith("text/") == true -> Kind.TEXT
        else -> Kind.NONE
    }

    fun thumbnail(f: FileEntry): String? = runCatching {
        val bmp = when {
            f.category == FileCategory.VIDEO -> videoFrame(f.path)
            f.extension == "pdf" -> pdfPage(f.path)
            else -> decodeScaled(f.path)
        } ?: return null
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 70, out)
        bmp.recycle()
        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }.getOrNull()

    fun textSnippet(f: FileEntry, maxChars: Int = 600): String? = runCatching {
        File(f.path).bufferedReader().use { r ->
            val buf = CharArray(maxChars)
            val n = r.read(buf)
            if (n <= 0) null else String(buf, 0, n).lines().take(15).joinToString("\n").filter { it == '\n' || it >= ' ' }
        }
    }.getOrNull()

    private fun decodeScaled(path: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= SIZE) sample *= 2
        val bmp = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        return scale(bmp)
    }

    private fun videoFrame(path: String): Bitmap? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(path)
            r.getFrameAtTime(1_000_000)?.let { scale(it) }
        } finally {
            r.release()
        }
    }

    private fun pdfPage(path: String): Bitmap? {
        ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                if (renderer.pageCount == 0) return null
                renderer.openPage(0).use { page ->
                    val ratio = SIZE.toFloat() / max(page.width, page.height)
                    val bmp = Bitmap.createBitmap((page.width * ratio).toInt().coerceAtLeast(1), (page.height * ratio).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    return bmp
                }
            }
        }
    }

    private fun scale(bmp: Bitmap): Bitmap {
        val ratio = SIZE.toFloat() / max(bmp.width, bmp.height)
        if (ratio >= 1f) return bmp
        val scaled = Bitmap.createScaledBitmap(bmp, (bmp.width * ratio).toInt().coerceAtLeast(1), (bmp.height * ratio).toInt().coerceAtLeast(1), true)
        if (scaled != bmp) bmp.recycle()
        return scaled
    }

    private companion object {
        const val SIZE = 256
        val textExtensions = setOf("txt", "md", "csv", "json", "xml", "html", "htm", "log", "srt", "ini", "yaml", "yml")
    }
}

package app.tidyphone.data.media

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import app.tidyphone.data.db.MediaMetaDao
import app.tidyphone.data.db.MediaMetaEntity
import app.tidyphone.domain.model.FileCategory
import app.tidyphone.domain.model.FileEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** Reads capture date, GPS and camera from photos (EXIF) and videos (container metadata), with a DB cache. */
@Singleton
class MediaMetaReader @Inject constructor(private val dao: MediaMetaDao) {

    suspend fun read(files: List<FileEntry>, onProgress: (Int, Int) -> Unit = { _, _ -> }): Map<String, MediaMetaEntity> {
        val result = HashMap<String, MediaMetaEntity>(files.size)
        var done = 0
        for (chunk in files.chunked(200)) {
            val cached = dao.get(chunk.map { it.path }).associateBy { it.path }
            val fresh = mutableListOf<MediaMetaEntity>()
            for (f in chunk) {
                val c = cached[f.path]
                val meta = if (c != null && c.size == f.size && c.lastModified == f.lastModified) c else readOne(f).also { fresh += it }
                result[f.path] = meta
                done++
            }
            if (fresh.isNotEmpty()) dao.upsertAll(fresh)
            onProgress(done, files.size)
        }
        return result
    }

    private fun readOne(f: FileEntry): MediaMetaEntity = runCatching {
        if (f.category == FileCategory.VIDEO) readVideo(f) else readImage(f)
    }.getOrElse { MediaMetaEntity(f.path, f.size, f.lastModified, null, null, null, null) }

    private fun readImage(f: FileEntry): MediaMetaEntity {
        val exif = ExifInterface(f.path)
        val taken = exif.dateTimeOriginal ?: exif.dateTimeDigitized ?: exif.dateTime
        val ll = exif.latLong?.takeIf { valid(it[0], it[1]) }
        val camera = listOfNotNull(exif.getAttribute(ExifInterface.TAG_MAKE), exif.getAttribute(ExifInterface.TAG_MODEL))
            .map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(" ").ifEmpty { null }
        return MediaMetaEntity(f.path, f.size, f.lastModified, taken, ll?.get(0), ll?.get(1), camera)
    }

    private fun readVideo(f: FileEntry): MediaMetaEntity {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(f.path)
            val taken = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)?.let(::parseVideoDate)
            val loc = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)?.let(::parseIso6709)?.takeIf { valid(it.first, it.second) }
            return MediaMetaEntity(f.path, f.size, f.lastModified, taken, loc?.first, loc?.second, null)
        } finally {
            r.release()
        }
    }

    private fun valid(lat: Double, lon: Double) = !(lat == 0.0 && lon == 0.0) && lat in -90.0..90.0 && lon in -180.0..180.0

    companion object {
        /** "20260811T201910.000Z" → epoch ms. Placeholder dates (1904/1970) are ignored. */
        fun parseVideoDate(s: String): Long? = listOf("yyyyMMdd'T'HHmmss.SSS'Z'", "yyyyMMdd'T'HHmmss'Z'", "yyyyMMdd'T'HHmmss").firstNotNullOfOrNull { pattern ->
            runCatching { SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(s)?.time }.getOrNull()
        }?.takeIf { it > 946_684_800_000L } // after 2000-01-01

        /** ISO 6709 location as stored in MP4: "+26.5307+053.9806/" or "+26.5307+053.9806+012.000/". */
        fun parseIso6709(s: String): Pair<Double, Double>? =
            Regex("""([+-]\d+(?:\.\d+)?)([+-]\d+(?:\.\d+)?)""").find(s)?.let { m ->
                val lat = m.groupValues[1].toDoubleOrNull()
                val lon = m.groupValues[2].toDoubleOrNull()
                if (lat != null && lon != null) lat to lon else null
            }
    }
}

/**
 * City/region names for coordinates via the system Geocoder. Coordinates are rounded to ~11 km
 * first, and results are cached per area, so few lookups are made.
 */
@Singleton
class PlaceNamer @Inject constructor(@ApplicationContext private val context: Context) {
    private val cache = HashMap<String, String?>()

    suspend fun name(lat: Double, lon: Double, locale: Locale): String? {
        if (!Geocoder.isPresent()) return null
        val rLat = Math.round(lat * 10) / 10.0
        val rLon = Math.round(lon * 10) / 10.0
        val key = "$rLat,$rLon,${locale.language}"
        if (key in cache) return cache[key]
        val geocoder = Geocoder(context, locale)
        val address: Address? = withTimeoutOrNull(6000) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(rLat, rLon, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) { if (cont.isActive) cont.resume(addresses.firstOrNull()) }
                        override fun onError(errorMessage: String?) { if (cont.isActive) cont.resume(null) }
                    })
                }
            } else {
                @Suppress("DEPRECATION")
                runCatching { geocoder.getFromLocation(rLat, rLon, 1)?.firstOrNull() }.getOrNull()
            }
        }
        val name = address?.let { a ->
            listOfNotNull(a.locality ?: a.subAdminArea, a.adminArea, a.countryName).map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(", ")
        }?.ifEmpty { null }
        cache[key] = name
        return name
    }
}

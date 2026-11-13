package app.tidyphone.domain.media

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** What we know about one photo or video from its metadata. */
data class MediaInfo(
    val path: String,
    val name: String,
    val size: Long,
    val isVideo: Boolean,
    /** Capture time (EXIF / video container), or the file time when [fromMetadata] is false. */
    val takenAt: Long,
    val fromMetadata: Boolean,
    val lat: Double? = null,
    val lon: Double? = null,
    val camera: String? = null,
) {
    val located: Boolean get() = lat != null && lon != null
}

data class MediaCluster(val id: Int, val kind: Kind, val items: List<MediaInfo>) {
    /** TRIP: photos taken away from the usual area in one stretch. MONTH: everyday photos of one month. */
    enum class Kind { TRIP, MONTH }

    val start: Long get() = items.minOf { it.takenAt }
    val end: Long get() = items.maxOf { it.takenAt }
    val photos: Int get() = items.count { !it.isVideo }
    val videos: Int get() = items.count { it.isVideo }

    /** Average position of the located items, or null. */
    val center: Pair<Double, Double>?
        get() = items.filter { it.located }.takeIf { it.isNotEmpty() }?.let { l -> l.sumOf { it.lat!! } / l.size to l.sumOf { it.lon!! } / l.size }
}

/**
 * Groups photos and videos into albums from metadata only.
 * "Home" is the most common ~11 km grid cell. Shots far from home form trips, split when there is a
 * long gap in time or a big jump in location; un-located shots taken during a trip join it.
 * Everything else is grouped by calendar month.
 */
object MediaClusterer {
    private const val HOUR = 3_600_000L

    fun cluster(
        items: List<MediaInfo>,
        zone: ZoneId = ZoneId.systemDefault(),
        homeRadiusKm: Double = 40.0,
        tripGapHours: Long = 36,
        jumpKm: Double = 150.0,
        minTripSize: Int = 3,
    ): List<MediaCluster> {
        val sorted = items.sortedBy { it.takenAt }
        val home = home(sorted)
        fun away(m: MediaInfo) = m.located && (home == null || distanceKm(m.lat!!, m.lon!!, home.first, home.second) > homeRadiusKm)

        val trips = mutableListOf<MutableList<MediaInfo>>()
        val everyday = mutableListOf<MediaInfo>()
        var trip: MutableList<MediaInfo>? = null
        var lastLocated: MediaInfo? = null

        for (m in sorted) {
            val current = trip
            when {
                away(m) -> {
                    val last = current?.last()
                    val continues = current != null && last != null && m.takenAt - last.takenAt <= tripGapHours * HOUR &&
                        (lastLocated == null || distanceKm(m.lat!!, m.lon!!, lastLocated.lat!!, lastLocated.lon!!) <= jumpKm)
                    if (continues) current!!.add(m) else trip = mutableListOf(m).also { trips += it }
                    lastLocated = m
                }
                !m.located && current != null && m.takenAt - current.last().takenAt <= 12 * HOUR -> current.add(m)
                else -> {
                    everyday += m
                    if (m.located) { trip = null; lastLocated = null }
                }
            }
        }

        // Tiny "trips" aren't worth an album of their own.
        val (realTrips, tooSmall) = trips.partition { it.size >= minTripSize }
        everyday += tooSmall.flatten()

        val clusters = realTrips.map { MediaCluster(0, MediaCluster.Kind.TRIP, it) } +
            everyday.groupBy { YearMonth.from(Instant.ofEpochMilli(it.takenAt).atZone(zone)) }.values
                .map { MediaCluster(0, MediaCluster.Kind.MONTH, it.sortedBy { m -> m.takenAt }) }
        return clusters.sortedBy { it.start }.mapIndexed { i, c -> c.copy(id = i) }
    }

    /** Centre of the most common 0.1° cell, if it holds at least a quarter of located shots (and 5+). */
    fun home(items: List<MediaInfo>): Pair<Double, Double>? {
        val located = items.filter { it.located }
        if (located.size < 5) return null
        val (cell, members) = located.groupBy { cell(it.lat!!, it.lon!!) }.maxByOrNull { it.value.size } ?: return null
        if (members.size * 4 < located.size || members.size < 5) return null
        return cell.first / 10.0 + 0.05 to cell.second / 10.0 + 0.05
    }

    private fun cell(lat: Double, lon: Double) = kotlin.math.floor(lat * 10).toInt() to kotlin.math.floor(lon * 10).toInt()

    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(a))
    }

    /** Coordinates rounded to ~11 km before they leave the device. */
    fun coarse(v: Double): Double = (v * 10).roundToInt() / 10.0
}

package app.tidyphone.domain

import app.tidyphone.domain.media.AlbumFallback
import app.tidyphone.domain.media.AlbumInput
import app.tidyphone.domain.media.AlbumMapper
import app.tidyphone.domain.media.AlbumPrompt
import app.tidyphone.domain.media.AlbumSchema
import app.tidyphone.domain.media.Jalali
import app.tidyphone.domain.media.MediaCluster
import app.tidyphone.domain.media.MediaClusterer
import app.tidyphone.domain.media.MediaInfo
import app.tidyphone.domain.model.ActionType
import app.tidyphone.domain.safety.PathGuard
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class AlbumsTest {
    private val zone = ZoneId.of("Asia/Tehran")
    private val tehran = 35.70 to 51.40
    private val kish = 26.53 to 53.98
    private val shiraz = 29.61 to 52.53
    private fun t(y: Int, m: Int, d: Int, h: Int = 12) = LocalDateTime.of(y, m, d, h, 0).atZone(zone).toInstant().toEpochMilli()
    private var n = 0
    private fun shot(time: Long, at: Pair<Double, Double>?, video: Boolean = false) =
        MediaInfo("$ROOT/DCIM/Camera/IMG_${n++}.jpg", "IMG_$n.jpg", 1000, video, time, true, at?.first, at?.second, "Galaxy S24")

    @Test fun `jalali conversion matches known dates`() {
        assertThat(Jalali.fromGregorian(2024, 3, 20)).isEqualTo(Jalali.Date(1403, 1, 1))
        assertThat(Jalali.fromGregorian(2026, 3, 21)).isEqualTo(Jalali.Date(1405, 1, 1))
        assertThat(Jalali.fromGregorian(2026, 8, 11)).isEqualTo(Jalali.Date(1405, 5, 20))
        assertThat(Jalali.fromGregorian(2025, 12, 31)).isEqualTo(Jalali.Date(1404, 10, 10))
    }

    @Test fun `home is the most common area`() {
        val items = (1..10).map { shot(t(2026, 1, it), tehran) } + (1..3).map { shot(t(2026, 2, it), kish) }
        val home = MediaClusterer.home(items)!!
        assertThat(MediaClusterer.distanceKm(home.first, home.second, tehran.first, tehran.second)).isLessThan(15.0)
    }

    @Test fun `trips away from home become albums, everyday shots group by month`() {
        val items = buildList {
            (1..12).forEach { add(shot(t(2026, 7, it), tehran)) }
            // Kish trip over four days, with an un-located video in the middle.
            (11..14).forEach { d -> repeat(3) { add(shot(t(2026, 8, d, 10 + it), kish)) } }
            add(shot(t(2026, 8, 12, 20), null, video = true))
            // Shiraz trip, far from Kish: a separate album.
            (1..2).forEach { d -> repeat(2) { add(shot(t(2026, 9, d, 10 + it), shiraz)) } }
            // A single shot away from home is too small for an album.
            add(shot(t(2026, 10, 5), kish))
            (1..3).forEach { add(shot(t(2026, 10, it + 10), tehran)) }
        }
        val clusters = MediaClusterer.cluster(items, zone)
        val trips = clusters.filter { it.kind == MediaCluster.Kind.TRIP }
        assertThat(trips).hasSize(2)
        assertThat(trips[0].items).hasSize(13) // 12 photos + the video
        assertThat(trips[0].videos).isEqualTo(1)
        assertThat(trips[1].items).hasSize(4)
        val months = clusters.filter { it.kind == MediaCluster.Kind.MONTH }
        assertThat(months.map { it.items.size }).containsExactly(12, 4).inOrder() // July, October (incl. the lone Kish shot)
        assertThat(clusters.map { it.id }).isEqualTo(clusters.indices.toList())
    }

    @Test fun `a long pause splits two visits to the same place`() {
        val items = (1..10).map { shot(t(2026, 1, it), tehran) } +
            (1..3).map { shot(t(2026, 3, 1, 10 + it), kish) } + (1..3).map { shot(t(2026, 3, 20, 10 + it), kish) }
        assertThat(MediaClusterer.cluster(items, zone).count { it.kind == MediaCluster.Kind.TRIP }).isEqualTo(2)
    }

    @Test fun `no gps at all means monthly albums`() {
        val items = listOf(shot(t(2026, 1, 1), null), shot(t(2026, 1, 20), null), shot(t(2026, 2, 3), null))
        val c = MediaClusterer.cluster(items, zone)
        assertThat(c.map { it.kind }.toSet()).containsExactly(MediaCluster.Kind.MONTH)
        assertThat(c).hasSize(2)
    }

    private val guard = PathGuard(ROOT, userExcludes = listOf("Work"))

    @Test fun `prompt carries coarse coordinates and jalali dates, never exact gps`() {
        val c = MediaCluster(3, MediaCluster.Kind.TRIP, listOf(shot(t(2026, 8, 11), 26.534567 to 53.981234), shot(t(2026, 8, 14), kish)))
        val line = AlbumPrompt.line(AlbumInput(c, "Kish, Hormozgan, Iran"), persian = true, zone)
        assertThat(line).contains("#3 | trip")
        assertThat(line).contains("Kish, Hormozgan, Iran 26.5,54.0")
        assertThat(line).contains("1405-05-20 مرداد")
        assertThat(line).doesNotContain("26.5345")
        assertThat(AlbumPrompt.system(true, "")).contains("Persian")
    }

    @Test fun `album answers are validated and folder names cleaned`() {
        val raw = """```json
            {"albums":[
              {"id":0,"name":"سفر کیش · مرداد ۱۴۰۵","folder":"۱۴۰۵-۰۵ سفر کیش"},
              {"id":1,"name":"Bad","folder":"../../etc"},
              {"id":2,"name":"Slash","folder":"2026/08 Trip"},
              {"id":3,"name":"","folder":"x"},
              {"id":4,"folder":"missing name"}
            ]}```"""
        val r = AlbumSchema.parse(raw, guard)!!.associateBy { it.id }
        assertThat(r[0]!!.folder).isEqualTo("۱۴۰۵-۰۵ سفر کیش")
        assertThat(r[2]!!.folder).isEqualTo("2026 08 Trip") // slashes can't create subfolders
        assertThat(r.keys).containsExactly(0, 2) // traversal, empty and incomplete entries are dropped
        assertThat(AlbumSchema.parse("no json", guard)).isNull()
    }

    @Test fun `mapper builds moves into the albums folder and respects the rules`() {
        val items = listOf(shot(t(2026, 8, 11), kish), shot(t(2026, 8, 12), kish, video = true))
        val cluster = MediaCluster(0, MediaCluster.Kind.TRIP, items)
        val moves = AlbumMapper(guard, "Pictures/Albums").map(cluster, app.tidyphone.domain.media.AlbumName(0, "Kish trip", "2026-08 Kish"))
        assertThat(moves).hasSize(2)
        assertThat(moves.all { it.action == ActionType.MOVE && it.targetDir == "$ROOT/Pictures/Albums/2026-08 Kish" && it.selected }).isTrue()
        assertThat(moves.map { it.category }).containsExactly("photos", "videos")
        // Month albums are suggested but not pre-selected; albums can't go into excluded folders.
        val month = MediaCluster(1, MediaCluster.Kind.MONTH, items)
        assertThat(AlbumMapper(guard, "Pictures/Albums").map(month, app.tidyphone.domain.media.AlbumName(1, "Aug", "2026-08")).none { it.selected }).isTrue()
        assertThat(AlbumMapper(guard, "Work/Albums").map(cluster, app.tidyphone.domain.media.AlbumName(0, "x", "y"))).isEmpty()
        assertThat(AlbumMapper(guard, "../outside").map(cluster, app.tidyphone.domain.media.AlbumName(0, "x", "y"))).isEmpty()
    }

    @Test fun `fallback names use place and calendar`() {
        val c = MediaCluster(0, MediaCluster.Kind.TRIP, listOf(shot(t(2026, 8, 11), kish)))
        assertThat(AlbumFallback.name(AlbumInput(c, "Kish, Iran"), false, zone)).isEqualTo(app.tidyphone.domain.media.AlbumName(0, "Kish trip · Aug 2026", "2026-08 Kish"))
        val fa = AlbumFallback.name(AlbumInput(c, "کیش، ایران"), true, zone)
        assertThat(fa.name).isEqualTo("سفر کیش · مرداد ۱۴۰۵")
        assertThat(fa.folder).isEqualTo("۱۴۰۵-۰۵ کیش")
    }
}

class MediaMetaParsingTest {
    @Test fun `video dates and iso6709 locations are parsed`() {
        assertThat(app.tidyphone.data.media.MediaMetaReader.parseVideoDate("20260811T201910.000Z")).isEqualTo(1786479550000L)
        assertThat(app.tidyphone.data.media.MediaMetaReader.parseVideoDate("19040101T000000.000Z")).isNull()
        assertThat(app.tidyphone.data.media.MediaMetaReader.parseIso6709("+26.5307+053.9806/")).isEqualTo(26.5307 to 53.9806)
        assertThat(app.tidyphone.data.media.MediaMetaReader.parseIso6709("-33.8688+151.2093+010.000/")).isEqualTo(-33.8688 to 151.2093)
        assertThat(app.tidyphone.data.media.MediaMetaReader.parseIso6709("garbage")).isNull()
    }
}

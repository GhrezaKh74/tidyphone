package app.tidyphone.domain.media

import app.tidyphone.domain.llm.JsonSchema
import app.tidyphone.domain.llm.LlmPart
import app.tidyphone.domain.llm.SuggestionParser
import app.tidyphone.domain.model.ActionType
import app.tidyphone.domain.model.PlanItem
import app.tidyphone.domain.model.PlanSource
import app.tidyphone.domain.safety.PathGuard
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/** A cluster plus what we resolved for it on the device. */
data class AlbumInput(val cluster: MediaCluster, val place: String?, val thumbnails: List<String> = emptyList())

data class AlbumName(val id: Int, val name: String, val folder: String)

object AlbumPrompt {
    fun system(persian: Boolean, customRules: String): String = buildString {
        appendLine("You name photo albums. Each album is a group of photos/videos from a phone, grouped on the device by date and location metadata.")
        appendLine("For each album id return a short, warm, human name and a folder name.")
        appendLine("- trip albums: name the place and when, e.g. \"Kish trip · Aug 2026\". Use the place given; if only coordinates are given, name the city or region they belong to.")
        appendLine("- month albums: everyday photos of one month, e.g. \"March 2026\". Add a theme only if the thumbnails clearly show one.")
        appendLine("- folder: a filesystem-safe name that sorts by date: start with year-month, then a few words, e.g. \"2026-08 Kish trip\". No slashes, no dots at the start, max 50 characters.")
        if (persian) {
            appendLine("Write names and folders in Persian. Use the Solar Hijri (Jalali) dates provided, e.g. name \"سفر کیش · مرداد ۱۴۰۵\" and folder \"۱۴۰۵-۰۵ سفر کیش\".")
        } else {
            appendLine("Write names and folders in English.")
        }
        appendLine("If thumbnails are attached, use them to describe the occasion (beach, wedding, birthday…), but never guess who the people are.")
        appendLine("Return only JSON: {\"albums\": [{\"id\": 0, \"name\": \"...\", \"folder\": \"...\"}]}")
        val rules = customRules.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (rules.isNotEmpty()) {
            appendLine("The phone's owner set these rules; follow them where they apply to albums:")
            rules.forEach { appendLine("- $it") }
        }
    }

    fun line(input: AlbumInput, persian: Boolean, zone: ZoneId): String {
        val c = input.cluster
        val s = ZonedDateTime.ofInstant(Instant.ofEpochMilli(c.start), zone)
        val e = ZonedDateTime.ofInstant(Instant.ofEpochMilli(c.end), zone)
        val dates = "${s.toLocalDate()} → ${e.toLocalDate()}" + if (persian) {
            val js = Jalali.fromGregorian(s.year, s.monthValue, s.dayOfMonth)
            val je = Jalali.fromGregorian(e.year, e.monthValue, e.dayOfMonth)
            " (Jalali ${js.year}-${"%02d".format(js.month)}-${"%02d".format(js.day)} ${Jalali.monthNamesFa[js.month - 1]} → ${je.year}-${"%02d".format(je.month)}-${"%02d".format(je.day)} ${Jalali.monthNamesFa[je.month - 1]})"
        } else ""
        val where = listOfNotNull(
            input.place,
            c.center?.let { (lat, lon) -> "${MediaClusterer.coarse(lat)},${MediaClusterer.coarse(lon)}" },
        ).joinToString(" ").ifEmpty { "unknown" }
        val cameras = c.items.mapNotNull { it.camera }.distinct().take(2).joinToString(", ").ifEmpty { "?" }
        val samples = c.items.map { it.name }.let { n -> listOf(n.first(), n[n.size / 2], n.last()).distinct() }.joinToString(", ")
        return "#${c.id} | ${c.kind.name.lowercase()} | $dates | ${c.photos} photos, ${c.videos} videos | place: $where | camera: $cameras | e.g. $samples"
    }

    fun userParts(inputs: List<AlbumInput>, persian: Boolean, zone: ZoneId): List<LlmPart> {
        val parts = mutableListOf<LlmPart>(LlmPart.Text("Albums:\n" + inputs.joinToString("\n") { line(it, persian, zone) }))
        inputs.forEach { a -> a.thumbnails.forEach { t -> parts += LlmPart.Text("Thumbnail from album #${a.cluster.id}:"); parts += LlmPart.Image(t) } }
        return parts
    }
}

object AlbumSchema {
    val schema: JsonObject = Json.parseToJsonElement(
        """
        {"type":"object","additionalProperties":false,"required":["albums"],"properties":{"albums":{"type":"array","items":{
          "type":"object","additionalProperties":false,"required":["id","name","folder"],"properties":{
            "id":{"type":"integer","minimum":0},
            "name":{"type":"string","maxLength":80},
            "folder":{"type":"string","maxLength":60}
        }}}}}
        """.trimIndent()
    ).jsonObject
    private val itemSchema = schema["properties"]!!.jsonObject["albums"]!!.jsonObject["items"]!!.jsonObject

    /** Returns valid names, or null if the answer is unusable as a whole. Invalid entries are dropped. */
    fun parse(raw: String, guard: PathGuard): List<AlbumName>? {
        val root = SuggestionParser.extractJson(raw)?.let { runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
        val arr = root?.get("albums") as? JsonArray
            // Cut-off answer: keep the albums that came through complete.
            ?: SuggestionParser.salvageArray(raw, "albums").mapNotNull { runCatching { Json.parseToJsonElement(it) }.getOrNull() }
                .takeIf { it.isNotEmpty() }?.let { JsonArray(it) }
            ?: return null
        return arr.mapNotNull { el ->
            if (JsonSchema.validate(el, itemSchema).isNotEmpty()) return@mapNotNull null
            val o = el.jsonObject
            val folder = cleanFolder(o["folder"]!!.jsonPrimitive.content)
            val name = o["name"]!!.jsonPrimitive.content.trim()
            if (name.isEmpty() || folder == null || !guard.isValidFolderName(folder)) null
            else AlbumName(o["id"]!!.jsonPrimitive.int, name, folder)
        }.distinctBy { it.id }
    }

    fun cleanFolder(raw: String): String? =
        raw.trim().replace(Regex("[/\\\\:*?\"<>|\\p{Cntrl}]"), " ").replace(Regex("\\s+"), " ").trim().trimStart('.').take(50).trim()
            .ifEmpty { null }
}

/** Names albums on the device when the model can't (no key, bad output): "2026-08 Kish" / "۱۴۰۵-۰۵ کیش". */
object AlbumFallback {
    fun name(input: AlbumInput, persian: Boolean, zone: ZoneId): AlbumName {
        val c = input.cluster
        val s = ZonedDateTime.ofInstant(Instant.ofEpochMilli(c.start), zone)
        val place = input.place?.split(',', '،')?.first()?.trim()?.takeIf { it.isNotEmpty() }
        return if (persian) {
            val j = Jalali.fromGregorian(s.year, s.monthValue, s.dayOfMonth)
            val month = Jalali.monthNamesFa[j.month - 1]
            val ym = persianDigits("%d-%02d".format(Locale.US, j.year, j.month))
            val year = persianDigits(j.year.toString())
            val label = if (c.kind == MediaCluster.Kind.TRIP && place != null) "سفر $place · $month $year" else "$month $year"
            AlbumName(c.id, label, AlbumSchema.cleanFolder("$ym ${place ?: month}")!!)
        } else {
            val month = s.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH)
            val ym = "%d-%02d".format(Locale.US, s.year, s.monthValue)
            val label = if (c.kind == MediaCluster.Kind.TRIP && place != null) "$place trip · $month ${s.year}" else "$month ${s.year}"
            AlbumName(c.id, label, AlbumSchema.cleanFolder(if (c.kind == MediaCluster.Kind.TRIP && place != null) "$ym $place" else ym)!!)
        }
    }
}

private fun persianDigits(s: String) = s.map { if (it in '0'..'9') '۰' + (it - '0') else it }.joinToString("")

/** Turns a named album into MOVE suggestions, enforcing the same path rules as everything else. */
class AlbumMapper(private val guard: PathGuard, albumsBase: String) {
    private val base: String? = guard.resolve(albumsBase.trim().trim('/'))?.takeIf { guard.canWriteTo(it) }

    fun map(cluster: MediaCluster, album: AlbumName): List<PlanItem> {
        val b = base ?: return emptyList()
        if (!guard.isValidFolderName(album.folder)) return emptyList()
        val target = "$b/${album.folder}"
        if (!guard.canWriteTo(target)) return emptyList()
        return cluster.items
            .filter { guard.canModify(it.path) && !it.path.substringBeforeLast('/').equals(target, ignoreCase = true) }
            .map {
                PlanItem(
                    path = it.path, action = ActionType.MOVE, source = PlanSource.LLM_ALBUM, size = it.size,
                    reason = album.name, targetDir = target, category = if (it.isVideo) "videos" else "photos",
                    selected = cluster.kind == MediaCluster.Kind.TRIP, groupKey = album.folder,
                )
            }
    }
}

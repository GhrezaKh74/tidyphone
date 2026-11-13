package app.tidyphone.domain.llm

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** What the model sees for one file. Only metadata unless content analysis was opted into. */
data class BatchFile(
    val id: Int,
    val relativePath: String,
    val size: Long,
    val mimeType: String?,
    val modified: Long,
    val textSnippet: String? = null,
    val thumbnailBase64: String? = null,
)

object PromptBuilder {

    fun system(customRules: String): String = buildString {
        appendLine("You organize files on an Android phone's shared storage. You only see metadata (relative path, size, type, modified date) and, sometimes, a short content sample.")
        appendLine()
        appendLine("For every file id in the list, return one entry with:")
        appendLine("- category: one of ${CATEGORY_KEYS.joinToString(", ")}.")
        appendLine("- target_folder: a folder relative to the storage root where the file belongs, e.g. \"Documents/Finance\" or \"Pictures/Screenshots\". Use \"\" if the file is already in a sensible place. Prefer the standard Android folders (DCIM, Pictures, Movies, Music, Documents, Download) and short subfolder names. Never use Android/, hidden folders or absolute paths.")
        appendLine("- safe_to_delete: true only for files that are very likely junk: installers already used, partial downloads, obvious duplicates such as \"name (1).ext\", temporary files, cache-like files. When unsure, false. Personal photos, videos, documents and voice notes are never safe to delete.")
        appendLine("- reason: one short sentence (max 20 words) explaining the suggestion, in English.")
        appendLine("- rename: a clean, human-readable file name keeping the same extension, only when the current name is clearly messy (random hashes, \"IMG-2023...-WA0001\" is fine to keep). Otherwise \"\".")
        appendLine()
        appendLine("Return only JSON of the form {\"items\": [{\"id\": 0, \"category\": \"documents\", \"target_folder\": \"\", \"safe_to_delete\": false, \"reason\": \"...\", \"rename\": \"\"}]}. No Markdown, no commentary.")
        val rules = customRules.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (rules.isNotEmpty()) {
            appendLine()
            appendLine("The phone's owner set these rules. Follow them; they override the guidance above:")
            rules.forEach { appendLine("- $it") }
        }
    }

    /** The user message for a batch: one compact line per file, then optional content samples. */
    fun userParts(files: List<BatchFile>, now: Long = System.currentTimeMillis()): List<LlmPart> {
        val parts = mutableListOf<LlmPart>()
        val text = buildString {
            appendLine("Today is ${date(now)}. Files (id | path | size | type | modified):")
            files.forEach { appendLine(line(it)) }
            val withText = files.filter { !it.textSnippet.isNullOrBlank() }
            if (withText.isNotEmpty()) {
                appendLine()
                appendLine("Content samples (first lines only):")
                withText.forEach { f ->
                    appendLine("--- #${f.id} ---")
                    appendLine(f.textSnippet!!.take(MAX_SNIPPET))
                }
            }
        }
        parts += LlmPart.Text(text)
        files.filter { it.thumbnailBase64 != null }.forEach { f ->
            parts += LlmPart.Text("Thumbnail of #${f.id}:")
            parts += LlmPart.Image(f.thumbnailBase64!!)
        }
        return parts
    }

    fun line(f: BatchFile) = "${f.id} | ${f.relativePath} | ${humanSize(f.size)} | ${f.mimeType ?: "?"} | ${date(f.modified)}"

    const val MAX_SNIPPET = 600

    fun repairMessage(error: String) =
        "Your previous answer could not be used ($error). Reply again with only the JSON object described in the instructions, covering every id."

    private fun date(ms: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(ms))

    private fun humanSize(b: Long): String = when {
        b >= 1L shl 30 -> "%.1fGB".format(Locale.US, b / (1L shl 30).toDouble())
        b >= 1L shl 20 -> "%.1fMB".format(Locale.US, b / (1L shl 20).toDouble())
        b >= 1L shl 10 -> "${b shr 10}KB"
        else -> "${b}B"
    }
}

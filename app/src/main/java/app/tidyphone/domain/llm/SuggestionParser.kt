package app.tidyphone.domain.llm

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

data class Suggestion(
    val id: Int,
    val category: String,
    val targetFolder: String,
    val safeToDelete: Boolean,
    val reason: String,
    val rename: String,
)

data class ParseResult(
    val suggestions: List<Suggestion>,
    /** Problems with individual items; those items were skipped. */
    val itemErrors: List<String>,
    /** Set when the whole answer was unusable (not JSON, wrong shape). Worth a retry. */
    val fatalError: String? = null,
)

object SuggestionParser {
    private val json = Json { isLenient = false }

    fun parse(raw: String): ParseResult {
        val text = extractJson(raw) ?: return ParseResult(emptyList(), emptyList(), "no JSON object found")
        val root: JsonElement = try {
            json.parseToJsonElement(text)
        } catch (e: Exception) {
            return ParseResult(emptyList(), emptyList(), "invalid JSON: ${e.message?.take(120)}")
        }
        if (root !is JsonObject) return ParseResult(emptyList(), emptyList(), "top level is not an object")
        val items = root["items"] as? JsonArray ?: return ParseResult(emptyList(), emptyList(), "missing \"items\" array")

        val ok = mutableListOf<Suggestion>()
        val errors = mutableListOf<String>()
        val seen = HashSet<Int>()
        items.forEachIndexed { i, el ->
            val problems = JsonSchema.validate(el, SuggestionSchema.itemSchema, "items[$i]")
            if (problems.isNotEmpty()) {
                errors += problems
                return@forEachIndexed
            }
            val o = el as JsonObject
            val id = o["id"]!!.jsonPrimitive.int
            if (!seen.add(id)) {
                errors += "items[$i]: duplicate id $id"
                return@forEachIndexed
            }
            ok += Suggestion(
                id = id,
                category = o["category"]!!.jsonPrimitive.content,
                targetFolder = o["target_folder"]!!.jsonPrimitive.content.trim(),
                safeToDelete = o["safe_to_delete"]!!.jsonPrimitive.boolean,
                reason = o["reason"]!!.jsonPrimitive.content.trim(),
                rename = o["rename"]!!.jsonPrimitive.content.trim(),
            )
        }
        return ParseResult(ok, errors)
    }

    /** Strips Markdown fences and chatter around the first top-level JSON object. */
    fun extractJson(raw: String): String? {
        val s = raw.trim().removePrefix("﻿")
        val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)```").find(s)?.groupValues?.get(1)?.trim()
        val candidate = fenced ?: s
        val start = candidate.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escape = false
        for (i in start until candidate.length) {
            val c = candidate[i]
            if (inString) {
                when {
                    escape -> escape = false
                    c == '\\' -> escape = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) return candidate.substring(start, i + 1) }
            }
        }
        return null
    }
}

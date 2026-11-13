package app.tidyphone.domain.llm

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * A small JSON Schema validator for the subset we use: type, properties, required,
 * additionalProperties=false, enum, items, maxItems, maxLength, minimum.
 * The same schema object is sent to the API for structured output.
 */
object JsonSchema {

    fun validate(value: JsonElement, schema: JsonObject, path: String = "$"): List<String> {
        val errors = mutableListOf<String>()
        val type = schema["type"]?.jsonPrimitive?.contentOrNull
        if (type != null && !matchesType(value, type)) {
            return listOf("$path: expected $type")
        }
        schema["enum"]?.jsonArray?.let { allowed ->
            if (allowed.none { it == value }) errors += "$path: not one of ${allowed.joinToString { it.toString() }}"
        }
        when (type) {
            "object" -> {
                val obj = value.jsonObject
                val props = schema["properties"]?.jsonObject ?: JsonObject(emptyMap())
                schema["required"]?.jsonArray?.forEach { r ->
                    val key = r.jsonPrimitive.content
                    if (key !in obj) errors += "$path.$key: missing"
                }
                val noExtra = schema["additionalProperties"]?.jsonPrimitive?.booleanOrNull == false
                for ((k, v) in obj) {
                    val sub = props[k]?.jsonObject
                    if (sub != null) errors += validate(v, sub, "$path.$k")
                    else if (noExtra) errors += "$path.$k: unexpected property"
                }
            }
            "array" -> {
                val arr = value.jsonArray
                schema["maxItems"]?.jsonPrimitive?.intOrNull?.let { if (arr.size > it) errors += "$path: more than $it items" }
                schema["items"]?.jsonObject?.let { itemSchema ->
                    arr.forEachIndexed { i, e -> errors += validate(e, itemSchema, "$path[$i]") }
                }
            }
            "string" -> schema["maxLength"]?.jsonPrimitive?.intOrNull?.let {
                if (value.jsonPrimitive.content.length > it) errors += "$path: longer than $it"
            }
            "integer", "number" -> schema["minimum"]?.jsonPrimitive?.doubleOrNull?.let {
                if ((value.jsonPrimitive.doubleOrNull ?: 0.0) < it) errors += "$path: below $it"
            }
        }
        return errors
    }

    private fun matchesType(v: JsonElement, type: String): Boolean = when (type) {
        "object" -> v is JsonObject
        "array" -> v is JsonArray
        "null" -> v is JsonNull
        "string" -> v is JsonPrimitive && v.isString
        "boolean" -> v is JsonPrimitive && !v.isString && v.booleanOrNull != null
        "integer" -> v is JsonPrimitive && !v.isString && v.longOrNull != null
        "number" -> v is JsonPrimitive && !v.isString && v.doubleOrNull != null
        else -> true
    }

    /**
     * The API-facing copy: drops keywords that some structured-output implementations reject
     * (maxLength, maxItems, minimum). They are still enforced locally by [validate].
     */
    fun forApi(schema: JsonObject): JsonObject = JsonObject(
        schema.filterKeys { it !in setOf("maxLength", "maxItems", "minimum") }.mapValues { (k, v) ->
            when {
                k == "properties" -> JsonObject(v.jsonObject.mapValues { forApi(it.value.jsonObject) })
                k == "items" && v is JsonObject -> forApi(v)
                else -> v
            }
        }
    )
}

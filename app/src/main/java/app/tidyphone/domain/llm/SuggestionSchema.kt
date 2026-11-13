package app.tidyphone.domain.llm

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** Category keys the model may return. The UI localizes them. */
val CATEGORY_KEYS = listOf(
    "photos", "screenshots", "videos", "music", "voice", "documents", "finance", "work",
    "study", "books", "archives", "apps", "social_media", "memes", "other",
)

object SuggestionSchema {
    val schema: JsonObject = Json.parseToJsonElement(
        """
        {
          "type": "object",
          "additionalProperties": false,
          "required": ["items"],
          "properties": {
            "items": {
              "type": "array",
              "maxItems": 500,
              "items": {
                "type": "object",
                "additionalProperties": false,
                "required": ["id", "category", "target_folder", "safe_to_delete", "reason", "rename"],
                "properties": {
                  "id": { "type": "integer", "minimum": 0 },
                  "category": { "type": "string", "enum": [${CATEGORY_KEYS.joinToString { "\"$it\"" }}] },
                  "target_folder": { "type": "string", "maxLength": 160 },
                  "safe_to_delete": { "type": "boolean" },
                  "reason": { "type": "string", "maxLength": 240 },
                  "rename": { "type": "string", "maxLength": 200 }
                }
              }
            }
          }
        }
        """.trimIndent()
    ).jsonObject

    val itemSchema: JsonObject get() =
        schema["properties"]!!.jsonObject["items"]!!.jsonObject["items"]!!.jsonObject
}

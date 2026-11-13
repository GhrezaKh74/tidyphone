package app.tidyphone.domain.llm

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Test

class SuggestionParserTest {
    private fun item(id: Int, extra: String = "") =
        """{"id":$id,"category":"documents","target_folder":"Documents/Finance","safe_to_delete":false,"reason":"Invoice","rename":""$extra}"""

    @Test fun `parses a valid answer`() {
        val r = SuggestionParser.parse("""{"items":[${item(0)},${item(1)}]}""")
        assertThat(r.fatalError).isNull()
        assertThat(r.itemErrors).isEmpty()
        assertThat(r.suggestions.map { it.id }).containsExactly(0, 1)
        assertThat(r.suggestions[0].targetFolder).isEqualTo("Documents/Finance")
    }

    @Test fun `strips markdown fences and surrounding chatter`() {
        val raw = "Sure! Here you go:\n```json\n{\"items\":[${item(3)}]}\n```\nLet me know."
        assertThat(SuggestionParser.parse(raw).suggestions.single().id).isEqualTo(3)
    }

    @Test fun `braces inside strings do not confuse extraction`() {
        val raw = """noise {"items":[{"id":1,"category":"other","target_folder":"","safe_to_delete":false,"reason":"has } and { inside","rename":""}]} tail"""
        assertThat(SuggestionParser.parse(raw).suggestions.single().reason).isEqualTo("has } and { inside")
    }

    @Test fun `invalid json or wrong shape is fatal`() {
        assertThat(SuggestionParser.parse("I cannot help").fatalError).isNotNull()
        assertThat(SuggestionParser.parse("{\"items\": [").fatalError).isNotNull()
        assertThat(SuggestionParser.parse("{\"files\": []}").fatalError).contains("items")
        assertThat(SuggestionParser.parse("{\"items\": {}}").fatalError).isNotNull()
    }

    @Test fun `invalid items are skipped individually`() {
        val raw = """{"items":[
            ${item(0)},
            {"id":"1","category":"documents","target_folder":"","safe_to_delete":false,"reason":"x","rename":""},
            {"id":2,"category":"pizza","target_folder":"","safe_to_delete":false,"reason":"x","rename":""},
            {"id":3,"category":"documents","target_folder":"","reason":"x","rename":""},
            ${item(4, ",\"extra\":1")},
            {"id":5,"category":"documents","target_folder":"","safe_to_delete":"yes","reason":"x","rename":""},
            ${item(0)}
        ]}"""
        val r = SuggestionParser.parse(raw)
        assertThat(r.fatalError).isNull()
        assertThat(r.suggestions.map { it.id }).containsExactly(0)
        assertThat(r.itemErrors.joinToString()).contains("duplicate id 0")
        assertThat(r.itemErrors.size).isAtLeast(6)
    }

    @Test fun `over-long reasons are rejected by the schema`() {
        val long = "a".repeat(300)
        val r = SuggestionParser.parse("""{"items":[{"id":0,"category":"other","target_folder":"","safe_to_delete":false,"reason":"$long","rename":""}]}""")
        assertThat(r.suggestions).isEmpty()
        assertThat(r.itemErrors.single()).contains("longer than 240")
    }

    @Test fun `api schema drops locally enforced keywords`() {
        val api = JsonSchema.forApi(SuggestionSchema.schema).toString()
        assertThat(api).doesNotContain("maxLength")
        assertThat(api).doesNotContain("maxItems")
        assertThat(api).contains("additionalProperties")
        assertThat(api).contains("\"enum\"")
    }

    @Test fun `schema validator checks types and required fields`() {
        val schema = Json.parseToJsonElement("""{"type":"object","required":["a"],"additionalProperties":false,"properties":{"a":{"type":"integer"}}}""").jsonObject
        assertThat(JsonSchema.validate(Json.parseToJsonElement("""{"a":1}"""), schema)).isEmpty()
        assertThat(JsonSchema.validate(Json.parseToJsonElement("""{"a":1.5}"""), schema)).isNotEmpty()
        assertThat(JsonSchema.validate(Json.parseToJsonElement("""{}"""), schema)).isNotEmpty()
        assertThat(JsonSchema.validate(Json.parseToJsonElement("""{"a":1,"b":2}"""), schema)).isNotEmpty()
    }
}

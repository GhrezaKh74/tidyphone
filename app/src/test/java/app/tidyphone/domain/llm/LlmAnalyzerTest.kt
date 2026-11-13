package app.tidyphone.domain.llm

import app.tidyphone.domain.ROOT
import app.tidyphone.domain.file
import app.tidyphone.domain.safety.PathGuard
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test

class LlmAnalyzerTest {
    private val files = mapOf(0 to file("Download/a.pdf"), 1 to file("Download/b.pdf"))
    private val batches = listOf(listOf(BatchFile(0, "Download/a.pdf", 1, null, 0)), listOf(BatchFile(1, "Download/b.pdf", 1, null, 0)))
    private val mapper = SuggestionMapper(PathGuard(ROOT))

    private fun answer(id: Int) = """{"items":[{"id":$id,"category":"documents","target_folder":"Documents","safe_to_delete":false,"reason":"r","rename":""}]}"""

    private class FakeProvider(val replies: MutableList<() -> LlmResult>) : LlmProvider {
        override val name = "fake"
        override val model = "fake"
        val requests = mutableListOf<LlmRequest>()
        override suspend fun complete(request: LlmRequest): LlmResult { requests += request; return replies.removeAt(0)() }
        override suspend fun testConnection() = "ok"
    }

    @Test fun `bad output is retried once with a repair message, then accepted`() = runTest {
        val p = FakeProvider(mutableListOf({ LlmResult("not json") }, { LlmResult(answer(0)) }, { LlmResult(answer(1)) }))
        val r = LlmAnalyzer(p, mapper).run(batches, files, "sys")
        assertThat(r.items.map { it.path }).containsExactly("$ROOT/Download/a.pdf", "$ROOT/Download/b.pdf")
        assertThat(r.skippedBatches).isEqualTo(0)
        assertThat((p.requests[1].parts.last() as LlmPart.Text).text).contains("could not be used")
        assertThat(p.requests[0].responseSchema).isNotNull()
    }

    @Test fun `batch is skipped after a second bad answer and the run continues`() = runTest {
        val p = FakeProvider(mutableListOf({ LlmResult("nope") }, { LlmResult("still nope") }, { LlmResult(answer(1)) }))
        val r = LlmAnalyzer(p, mapper).run(batches, files, "sys")
        assertThat(r.skippedBatches).isEqualTo(1)
        assertThat(r.items.single().path).endsWith("b.pdf")
    }

    @Test fun `ids from another batch are ignored`() = runTest {
        val p = FakeProvider(mutableListOf({ LlmResult(answer(1)) }, { LlmResult(answer(1)) }))
        val r = LlmAnalyzer(p, mapper).run(batches, files, "sys")
        assertThat(r.items).hasSize(1)
    }

    @Test fun `transient errors are retried, auth errors stop the run`() = runTest {
        val p = FakeProvider(mutableListOf({ throw LlmException("busy", 529, retryable = true) }, { LlmResult(answer(0)) }, { LlmResult(answer(1)) }))
        assertThat(LlmAnalyzer(p, mapper).run(batches, files, "sys").items).hasSize(2)

        val auth = FakeProvider(mutableListOf({ throw LlmException("HTTP 401", 401) }))
        assertThrows(LlmException::class.java) { kotlinx.coroutines.runBlocking { LlmAnalyzer(auth, mapper).run(batches, files, "sys") } }
    }

    @Test fun `token usage is summed`() = runTest {
        val p = FakeProvider(mutableListOf({ LlmResult(answer(0), 100, 20) }, { LlmResult(answer(1), 50, 10) }))
        val r = LlmAnalyzer(p, mapper).run(batches, files, "sys")
        assertThat(r.inputTokens).isEqualTo(150)
        assertThat(r.outputTokens).isEqualTo(30)
    }

    @Test fun `custom rules are added to the system prompt`() {
        val sys = PromptBuilder.system("never touch the Work folder\n\nput invoices in Documents/Finance")
        assertThat(sys).contains("- never touch the Work folder")
        assertThat(sys).contains("- put invoices in Documents/Finance")
        assertThat(PromptBuilder.system("")).doesNotContain("owner set these rules")
    }
}

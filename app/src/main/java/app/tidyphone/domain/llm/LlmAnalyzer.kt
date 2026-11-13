package app.tidyphone.domain.llm

import app.tidyphone.domain.model.FileEntry
import app.tidyphone.domain.model.PlanItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

data class LlmProgress(
    val batchIndex: Int,
    val batchCount: Int,
    val filesDone: Int,
    val filesTotal: Int,
    val suggestions: Int,
    val skippedBatches: Int,
    val inputTokens: Int,
    val outputTokens: Int,
    val lastError: String? = null,
)

data class LlmRunResult(
    val items: List<PlanItem>,
    val skippedBatches: Int,
    val droppedSuggestions: List<String>,
    val inputTokens: Int,
    val outputTokens: Int,
    val lastError: String?,
)

/**
 * Runs metadata batches through a provider. Each batch: request, validate JSON, and on unusable
 * output retry once with a repair note; then skip the batch. Transient HTTP errors back off and
 * retry up to [maxAttempts] times.
 */
class LlmAnalyzer(
    private val provider: LlmProvider,
    private val mapper: SuggestionMapper,
    private val maxAttempts: Int = 3,
) {
    suspend fun run(
        batches: List<List<BatchFile>>,
        filesById: Map<Int, FileEntry>,
        system: String,
        enrich: suspend (List<BatchFile>) -> List<BatchFile> = { it },
        onProgress: (LlmProgress) -> Unit = {},
    ): LlmRunResult {
        val items = mutableListOf<PlanItem>()
        val dropped = mutableListOf<String>()
        var skipped = 0
        var inTok = 0
        var outTok = 0
        var done = 0
        var lastError: String? = null
        val total = batches.sumOf { it.size }
        val schema = JsonSchema.forApi(SuggestionSchema.schema)

        batches.forEachIndexed { index, batch ->
            onProgress(LlmProgress(index, batches.size, done, total, items.size, skipped, inTok, outTok, lastError))
            var parts = PromptBuilder.userParts(enrich(batch))
            var parsed: ParseResult? = null
            var repairUsed = false

            while (true) {
                val result = try {
                    withRetry { provider.complete(LlmRequest(system, parts, Batcher.maxOutputTokens(batch.size), schema)) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: LlmException) {
                    lastError = e.message
                    if (e.status == 401 || e.status == 403) throw e // bad key: stop the whole run
                    null
                }
                if (result == null) break
                inTok += result.inputTokens ?: 0
                outTok += result.outputTokens ?: 0
                val p = SuggestionParser.parse(result.text)
                val fatal = p.fatalError ?: if (result.truncated && p.suggestions.isEmpty()) "answer was cut off" else null
                if (fatal == null) { parsed = p; break }
                lastError = fatal
                if (repairUsed) break
                repairUsed = true
                parts = parts + LlmPart.Text(PromptBuilder.repairMessage(fatal))
            }

            if (parsed == null) {
                skipped++
            } else {
                val ids = batch.map { it.id }.toSet()
                val inBatch = parsed.suggestions.filter { it.id in ids }
                val mapped = mapper.map(inBatch, filesById)
                items += mapped.items
                dropped += mapped.dropped + parsed.itemErrors
            }
            done += batch.size
        }
        onProgress(LlmProgress(batches.size, batches.size, done, total, items.size, skipped, inTok, outTok, lastError))
        return LlmRunResult(items, skipped, dropped, inTok, outTok, lastError)
    }

    private suspend fun <T> withRetry(block: suspend () -> T): T {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (e: LlmException) {
                attempt++
                if (!e.retryable || attempt >= maxAttempts) throw e
                delay(1500L * attempt * attempt)
            }
        }
    }
}

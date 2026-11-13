package app.tidyphone.domain.llm

/** Rough token estimates. Good enough for batching and for showing a cost before running. */
object TokenEstimator {
    const val IMAGE_TOKENS = 110
    const val OUTPUT_TOKENS_PER_FILE = 55

    /** ~3 UTF-8 bytes per token: about right for English paths, conservative for Persian names. */
    fun text(s: String): Int = (s.toByteArray(Charsets.UTF_8).size + 2) / 3

    fun file(f: BatchFile): Int =
        text(PromptBuilder.line(f)) + 2 +
            (f.textSnippet?.let { text(it.take(PromptBuilder.MAX_SNIPPET)) + 6 } ?: 0) +
            (if (f.thumbnailBase64 != null) IMAGE_TOKENS + 8 else 0)
}

object Batcher {
    const val MAX_FILES_PER_BATCH = 120

    /** Splits files so each batch's input (system prompt + files) stays under [maxInputTokens]. */
    fun split(files: List<BatchFile>, systemTokens: Int, maxInputTokens: Int, maxFiles: Int = MAX_FILES_PER_BATCH): List<List<BatchFile>> {
        val budget = (maxInputTokens - systemTokens - 40).coerceAtLeast(200)
        val batches = mutableListOf<List<BatchFile>>()
        var current = mutableListOf<BatchFile>()
        var used = 0
        for (f in files) {
            val t = TokenEstimator.file(f)
            if (current.isNotEmpty() && (used + t > budget || current.size >= maxFiles)) {
                batches += current
                current = mutableListOf()
                used = 0
            }
            current += f
            used += t
        }
        if (current.isNotEmpty()) batches += current
        return batches
    }

    fun maxOutputTokens(batchSize: Int): Int = (batchSize * TokenEstimator.OUTPUT_TOKENS_PER_FILE * 2 + 2000).coerceAtMost(16_000)
}

data class Price(val inputPerM: Double, val outputPerM: Double)

object Pricing {
    /** USD per million tokens, first-party Anthropic API list prices. */
    private val anthropic = mapOf(
        "claude-fable-5-2" to Price(8.0, 40.0),
        "claude-fable-5-1" to Price(10.0, 50.0),
        "claude-opus-5-5" to Price(4.0, 20.0),
        "claude-opus-5" to Price(5.0, 25.0),
        "claude-opus-4-8" to Price(5.0, 25.0),
        "claude-opus-4-7" to Price(5.0, 25.0),
        "claude-sonnet-5-5" to Price(2.0, 10.0),
        "claude-sonnet-5" to Price(2.0, 10.0),
        "claude-sonnet-4-6" to Price(3.0, 15.0),
        "claude-haiku-5-5" to Price(0.10, 0.50),
        "claude-haiku-4-5" to Price(1.0, 5.0),
    )

    val anthropicModels: List<String> = listOf("claude-opus-5-5", "claude-sonnet-5-5", "claude-haiku-5-5", "claude-fable-5-2")

    fun isLocal(baseUrl: String): Boolean {
        val host = baseUrl.substringAfter("://").substringBefore('/').substringBefore(':').lowercase()
        return host == "localhost" || host == "127.0.0.1" || host == "10.0.2.2" || host.endsWith(".local") ||
            host.startsWith("192.168.") || host.startsWith("10.") || Regex("""^172\.(1[6-9]|2\d|3[01])\.""").containsMatchIn(host)
    }

    /** Custom prices win; then the built-in table; local servers are free; otherwise unknown (null). */
    fun priceFor(model: String, baseUrl: String, customIn: Double?, customOut: Double?): Price? = when {
        customIn != null && customOut != null -> Price(customIn, customOut)
        isLocal(baseUrl) -> Price(0.0, 0.0)
        else -> anthropic[model.trim().lowercase()]
    }
}

data class Estimate(
    val files: Int,
    val batches: Int,
    val inputTokens: Int,
    val outputTokens: Int,
    val price: Price?,
) {
    val costUsd: Double? get() = price?.let { (inputTokens * it.inputPerM + outputTokens * it.outputPerM) / 1_000_000.0 }

    companion object {
        fun of(batches: List<List<BatchFile>>, systemTokens: Int, price: Price?): Estimate {
            val input = batches.sumOf { b -> systemTokens + 30 + b.sumOf { TokenEstimator.file(it) } }
            val output = batches.sumOf { it.size } * TokenEstimator.OUTPUT_TOKENS_PER_FILE
            return Estimate(batches.sumOf { it.size }, batches.size, input, output, price)
        }
    }
}

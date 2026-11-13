package app.tidyphone.domain.llm

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BatcherTest {
    private fun bf(i: Int, name: String = "Download/file_$i.pdf") = BatchFile(i, name, 1000, "application/pdf", 0)

    @Test fun `batches respect the token budget and keep every file once`() {
        val files = (0 until 300).map { bf(it) }
        val batches = Batcher.split(files, systemTokens = 500, maxInputTokens = 2000)
        assertThat(batches.size).isGreaterThan(1)
        assertThat(batches.flatten().map { it.id }).containsExactlyElementsIn(files.map { it.id }).inOrder()
        for (b in batches) assertThat(500 + b.sumOf { TokenEstimator.file(it) }).isAtMost(2000)
    }

    @Test fun `batches respect the file cap`() {
        val batches = Batcher.split((0 until 250).map { bf(it) }, 100, 1_000_000, maxFiles = 100)
        assertThat(batches.map { it.size }).containsExactly(100, 100, 50).inOrder()
    }

    @Test fun `an oversized single file still gets its own batch`() {
        val big = bf(0, "x".repeat(10_000))
        val batches = Batcher.split(listOf(big, bf(1)), 100, 1000)
        assertThat(batches.map { it.size }).containsExactly(1, 1)
    }

    @Test fun `images and snippets cost more tokens`() {
        val plain = bf(0)
        assertThat(TokenEstimator.file(plain.copy(thumbnailBase64 = ""))).isGreaterThan(TokenEstimator.file(plain) + 100)
        assertThat(TokenEstimator.file(plain.copy(textSnippet = "y".repeat(600)))).isGreaterThan(TokenEstimator.file(plain) + 150)
    }

    @Test fun `estimate uses known prices, custom prices and free local servers`() {
        val batches = Batcher.split((0 until 10).map { bf(it) }, 300, 6000)
        val known = Estimate.of(batches, 300, Pricing.priceFor("claude-opus-5-5", "https://api.anthropic.com", null, null))
        assertThat(known.costUsd).isGreaterThan(0.0)
        assertThat(Estimate.of(batches, 300, Pricing.priceFor("llama3", "http://192.168.1.5:11434/v1", null, null)).costUsd).isEqualTo(0.0)
        assertThat(Pricing.priceFor("mystery-model", "https://api.example.com/v1", null, null)).isNull()
        assertThat(Pricing.priceFor("mystery-model", "https://api.example.com/v1", 1.0, 2.0)).isEqualTo(Price(1.0, 2.0))
    }
}

package app.tidyphone.data.repo

import app.tidyphone.data.db.FileDao
import app.tidyphone.data.db.PlanDao
import app.tidyphone.data.db.PlanItemEntity
import app.tidyphone.data.fs.StorageLocations
import app.tidyphone.data.llm.ContentSampler
import app.tidyphone.data.llm.LlmProviderFactory
import app.tidyphone.data.settings.SettingsRepository
import app.tidyphone.di.ApplicationScope
import app.tidyphone.domain.llm.BatchFile
import app.tidyphone.domain.llm.Batcher
import app.tidyphone.domain.llm.Estimate
import app.tidyphone.domain.llm.LlmAnalyzer
import app.tidyphone.domain.llm.LlmException
import app.tidyphone.domain.llm.LlmProgress
import app.tidyphone.domain.llm.Pricing
import app.tidyphone.domain.llm.PromptBuilder
import app.tidyphone.domain.llm.SuggestionMapper
import app.tidyphone.domain.llm.TokenEstimator
import app.tidyphone.domain.model.FileCategory
import app.tidyphone.domain.model.FileEntry
import app.tidyphone.domain.model.PlanSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

enum class AnalysisScope { ALL, DOWNLOADS, NON_MEDIA }

data class AnalysisPlan(
    val scope: AnalysisScope,
    val includeContent: Boolean,
    val estimate: Estimate,
    internal val batches: List<List<BatchFile>>,
    internal val filesById: Map<Int, FileEntry>,
    internal val system: String,
)

data class AnalysisState(
    val running: Boolean = false,
    val progress: LlmProgress? = null,
    val finishedSuggestions: Int? = null,
    val skippedBatches: Int = 0,
    val dropped: Int = 0,
    val error: String? = null,
)

/** Prepares (with a cost estimate) and runs LLM analysis; results become LLM plan items. */
@Singleton
class LlmAnalysisRepository @Inject constructor(
    private val fileDao: FileDao,
    private val planDao: PlanDao,
    private val settings: SettingsRepository,
    private val guards: GuardProvider,
    private val locations: StorageLocations,
    private val providers: LlmProviderFactory,
    private val sampler: ContentSampler,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(AnalysisState())
    val state: StateFlow<AnalysisState> = _state.asStateFlow()
    private var job: Job? = null

    suspend fun countFor(scope: AnalysisScope): Int = withContext(Dispatchers.IO) { eligible(scope).size }

    suspend fun prepare(scope: AnalysisScope, includeContent: Boolean): AnalysisPlan = withContext(Dispatchers.IO) {
        val s = settings.current()
        val files = eligible(scope)
        val root = locations.root
        val byId = files.withIndex().associate { (i, f) -> i to f }
        val batchFiles = files.mapIndexed { i, f ->
            val kind = if (includeContent) sampler.kindOf(f) else ContentSampler.Kind.NONE
            BatchFile(
                id = i,
                relativePath = f.path.removePrefix("$root/"),
                size = f.size,
                mimeType = f.mimeType,
                modified = f.lastModified,
                // Placeholders sized like real samples, so batching and the estimate account for them.
                textSnippet = if (kind == ContentSampler.Kind.TEXT) PLACEHOLDER_TEXT else null,
                thumbnailBase64 = if (kind == ContentSampler.Kind.IMAGE) "" else null,
            )
        }
        val system = PromptBuilder.system(s.customRules)
        val systemTokens = TokenEstimator.text(system)
        val batches = Batcher.split(batchFiles, systemTokens, s.maxInputTokensPerBatch)
        val price = Pricing.priceFor(s.activeModel, s.activeBaseUrl, s.priceInputPerM, s.priceOutputPerM)
        AnalysisPlan(scope, includeContent, Estimate.of(batches, systemTokens, price), batches, byId, system)
    }

    fun run(plan: AnalysisPlan) {
        if (job?.isActive == true) return
        _state.value = AnalysisState(running = true)
        job = scope.launch(Dispatchers.IO) {
            try {
                val s = settings.current()
                val analyzer = LlmAnalyzer(providers.create(s), SuggestionMapper(guards.guard(s)))
                val result = analyzer.run(
                    batches = plan.batches,
                    filesById = plan.filesById,
                    system = plan.system,
                    enrich = { batch -> if (plan.includeContent) batch.map { sample(it, plan.filesById[it.id]!!) } else batch },
                    onProgress = { p -> _state.update { it.copy(progress = p) } },
                )
                val paths = plan.filesById.values.map { it.path }
                paths.chunked(500).forEach { planDao.deleteBySourceAndPaths(PlanSource.LLM, it) }
                val now = System.currentTimeMillis()
                planDao.insertAll(result.items.map { PlanItemEntity.from(it, now) })
                _state.value = AnalysisState(
                    running = false, progress = _state.value.progress, finishedSuggestions = result.items.size,
                    skippedBatches = result.skippedBatches, dropped = result.droppedSuggestions.size,
                    error = if (result.skippedBatches > 0) result.lastError else null,
                )
            } catch (e: CancellationException) {
                _state.value = AnalysisState(running = false)
                throw e
            } catch (e: LlmException) {
                _state.value = AnalysisState(running = false, error = e.message)
            } catch (e: Exception) {
                _state.value = AnalysisState(running = false, error = e.javaClass.simpleName + (e.message?.let { ": $it" } ?: ""))
            }
        }
    }

    fun cancel() { job?.cancel() }

    fun dismissResult() { if (!_state.value.running) _state.value = AnalysisState() }

    private fun sample(b: BatchFile, f: FileEntry): BatchFile = when {
        b.thumbnailBase64 != null -> b.copy(thumbnailBase64 = sampler.thumbnail(f))
        b.textSnippet != null -> b.copy(textSnippet = sampler.textSnippet(f))
        else -> b
    }

    private suspend fun eligible(scope: AnalysisScope): List<FileEntry> {
        val guard = guards.guard()
        return fileDao.all().asSequence()
            .map { it.toDomain() }
            .filter { !it.isDirectory && guard.canModify(it.path) }
            .filter { f ->
                when (scope) {
                    AnalysisScope.ALL -> true
                    AnalysisScope.DOWNLOADS -> f.parent.lowercase().split('/').any { it == "download" || it == "downloads" }
                    AnalysisScope.NON_MEDIA -> f.category !in setOf(FileCategory.IMAGE, FileCategory.VIDEO, FileCategory.AUDIO)
                }
            }
            .sortedByDescending { it.lastModified }
            .toList()
    }

    private companion object { val PLACEHOLDER_TEXT = "x".repeat(PromptBuilder.MAX_SNIPPET) }
}

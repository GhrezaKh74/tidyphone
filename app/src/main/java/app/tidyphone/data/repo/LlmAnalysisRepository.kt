package app.tidyphone.data.repo

import app.tidyphone.data.db.FileDao
import app.tidyphone.data.db.PlanDao
import app.tidyphone.data.db.PlanItemEntity
import app.tidyphone.data.fs.StorageLocations
import app.tidyphone.data.llm.ContentSampler
import app.tidyphone.data.llm.LlmProviderFactory
import app.tidyphone.data.settings.SettingsRepository
import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.tidyphone.data.worker.LlmAnalysisWorker
import dagger.hilt.android.qualifiers.ApplicationContext
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import androidx.appcompat.app.AppCompatDelegate
import app.tidyphone.data.media.MediaMetaReader
import app.tidyphone.data.media.PlaceNamer
import app.tidyphone.data.settings.AppSettings
import app.tidyphone.data.settings.ProviderType
import app.tidyphone.data.settings.SecureKeyStore
import app.tidyphone.domain.analysis.LocalAnalyzer
import app.tidyphone.domain.llm.JsonSchema
import app.tidyphone.domain.llm.LlmPart
import app.tidyphone.domain.llm.LlmRequest
import app.tidyphone.domain.llm.Price
import app.tidyphone.domain.media.AlbumFallback
import app.tidyphone.domain.media.AlbumInput
import app.tidyphone.domain.media.AlbumMapper
import app.tidyphone.domain.media.AlbumName
import app.tidyphone.domain.media.AlbumPrompt
import app.tidyphone.domain.media.AlbumSchema
import app.tidyphone.domain.media.MediaCluster
import app.tidyphone.domain.media.MediaClusterer
import app.tidyphone.domain.media.MediaInfo
import app.tidyphone.domain.safety.PathGuard
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

enum class AnalysisScope { ALL, DOWNLOADS, NON_MEDIA, ALBUMS }

data class AnalysisPlan(
    val scope: AnalysisScope,
    val includeContent: Boolean,
    val estimate: Estimate,
    internal val batches: List<List<BatchFile>>,
    internal val filesById: Map<Int, FileEntry>,
    internal val system: String,
    /** Smart albums only: clusters to name, in request-sized groups. */
    internal val albumBatches: List<List<MediaCluster>> = emptyList(),
    internal val mediaFiles: Map<String, FileEntry> = emptyMap(),
    /** False when albums will be named on the device (no AI key). */
    val usesAi: Boolean = true,
) {
    val albumCount: Int get() = albumBatches.sumOf { it.size }
}

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
    private val skipped: SkippedRepository,
    private val mediaMeta: MediaMetaReader,
    private val places: PlaceNamer,
    private val keys: SecureKeyStore,
    @ApplicationContext private val context: Context,
) {
    private val _state = MutableStateFlow(AnalysisState())
    val state: StateFlow<AnalysisState> = _state.asStateFlow()

    suspend fun countFor(scope: AnalysisScope): Int = withContext(Dispatchers.IO) {
        if (scope == AnalysisScope.ALBUMS) albumCandidates().size else eligible(scope).size
    }

    /** True when the selected provider can be called (local OpenAI-compatible servers need no key). */
    fun aiConfigured(s: AppSettings): Boolean = s.provider == ProviderType.OPENAI_COMPAT || keys.has(ProviderType.ANTHROPIC)

    suspend fun prepare(scope: AnalysisScope, includeContent: Boolean, onMetaProgress: (Int, Int) -> Unit = { _, _ -> }): AnalysisPlan = withContext(Dispatchers.IO) {
        if (scope == AnalysisScope.ALBUMS) return@withContext prepareAlbums(includeContent, onMetaProgress)
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

    /** Starts the analysis as a background worker (keeps running with the screen off). */
    fun run(plan: AnalysisPlan) {
        if (_state.value.running) return
        _state.value = AnalysisState(running = true)
        val request = OneTimeWorkRequestBuilder<LlmAnalysisWorker>()
            .setInputData(workDataOf(LlmAnalysisWorker.KEY_SCOPE to plan.scope.name, LlmAnalysisWorker.KEY_CONTENT to plan.includeContent))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(LlmAnalysisWorker.NAME, ExistingWorkPolicy.KEEP, request)
    }

    /** Called by [LlmAnalysisWorker]. Prepares the batches again from the same options and runs them. */
    suspend fun execute(scope: AnalysisScope, includeContent: Boolean, onProgress: (LlmProgress) -> Unit): AnalysisState {
        _state.value = AnalysisState(running = true)
        if (scope == AnalysisScope.ALBUMS) return executeAlbums(includeContent, onProgress).also { _state.value = it }
        val result = try {
            val plan = prepare(scope, includeContent)
            val skips = skipped.filter()
            val s = settings.current()
            val analyzer = LlmAnalyzer(providers.create(s), SuggestionMapper(guards.guard(s)))
            val r = analyzer.run(
                batches = plan.batches,
                filesById = plan.filesById,
                system = plan.system,
                enrich = { batch -> if (plan.includeContent) batch.map { sample(it, plan.filesById[it.id]!!) } else batch },
                onBatchItems = { batch, items ->
                    // Results show up in Review as soon as each batch is done.
                    val paths = batch.mapNotNull { plan.filesById[it.id]?.path }
                    paths.chunked(500).forEach { planDao.deleteBySourceAndPaths(PlanSource.LLM, it) }
                    val now = System.currentTimeMillis()
                    planDao.insertAll(skips.filter(items).map { PlanItemEntity.from(it, now) })
                },
                onProgress = { p -> _state.update { it.copy(progress = p) }; onProgress(p) },
            )
            AnalysisState(
                running = false, progress = _state.value.progress, finishedSuggestions = r.items.size,
                skippedBatches = r.skippedBatches, dropped = r.droppedSuggestions.size,
                error = if (r.skippedBatches > 0) r.lastError else null,
            )
        } catch (e: CancellationException) {
            _state.value = AnalysisState(running = false)
            throw e
        } catch (e: LlmException) {
            AnalysisState(running = false, error = e.message)
        } catch (e: Exception) {
            AnalysisState(running = false, error = e.javaClass.simpleName + (e.message?.let { ": $it" } ?: ""))
        }
        _state.value = result
        return result
    }

    fun markInterrupted(message: String) { _state.value = AnalysisState(running = false, error = message) }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(LlmAnalysisWorker.NAME)
        _state.value = AnalysisState(running = false)
    }

    fun dismissResult() { if (!_state.value.running) _state.value = AnalysisState() }

    // ---------------------------------------------------------------- smart albums

    private fun persian(): Boolean =
        (AppCompatDelegate.getApplicationLocales()[0] ?: context.resources.configuration.locales[0]).language == "fa"

    private fun appLocale(): Locale = AppCompatDelegate.getApplicationLocales()[0] ?: context.resources.configuration.locales[0]

    /** Camera photos/videos and other photos that may carry EXIF; messaging media and screenshots are left alone. */
    private suspend fun albumCandidates(): List<FileEntry> {
        val s = settings.current()
        val guard = guards.guard(s)
        val base = guard.resolve(s.albumsFolder.trim().trim('/'))
        val exifCapable = setOf("jpg", "jpeg", "heic", "heif", "dng", "mp4", "mov", "3gp", "m4v")
        return fileDao.all().asSequence().map { it.toDomain() }
            .filter { !it.isDirectory && (it.category == FileCategory.IMAGE || it.category == FileCategory.VIDEO) }
            .filter { guard.canModify(it.path) && !LocalAnalyzer.isScreenshot(it) }
            .filter { f -> listOf("whatsapp", "telegram", "/.").none { f.path.lowercase().contains(it) } }
            .filter { base == null || !PathGuard.isInside(it.path, base) }
            .filter { f -> f.path.contains("/DCIM/", ignoreCase = true) || f.extension in exifCapable }
            .toList()
    }

    private suspend fun prepareAlbums(includeContent: Boolean, onMetaProgress: (Int, Int) -> Unit): AnalysisPlan {
        val s = settings.current()
        val files = albumCandidates()
        val meta = mediaMeta.read(files, onMetaProgress)
        val infos = files.mapNotNull { f ->
            val m = meta[f.path]
            val inCamera = f.path.contains("/DCIM/", ignoreCase = true)
            // Outside the camera folders, only files with real capture metadata are grouped.
            if (m?.takenAt == null && !inCamera) return@mapNotNull null
            MediaInfo(f.path, f.name, f.size, f.category == FileCategory.VIDEO, m?.takenAt ?: f.lastModified, m?.takenAt != null, m?.latitude, m?.longitude, m?.camera)
        }
        val clusters = MediaClusterer.cluster(infos)
        val batches = clusters.chunked(ALBUMS_PER_REQUEST)
        val useAi = aiConfigured(s)
        val system = AlbumPrompt.system(persian(), s.customRules)
        val systemTokens = TokenEstimator.text(system)
        val perAlbum = 70 + if (includeContent) 2 * (TokenEstimator.IMAGE_TOKENS + 10) else 0
        val estimate = if (!useAi) Estimate(infos.size, 0, 0, 0, Price(0.0, 0.0)) else Estimate(
            files = infos.size,
            batches = batches.size,
            inputTokens = batches.sumOf { systemTokens + 20 + it.size * perAlbum },
            outputTokens = clusters.size * 40,
            price = Pricing.priceFor(s.activeModel, s.activeBaseUrl, s.priceInputPerM, s.priceOutputPerM),
        )
        return AnalysisPlan(
            AnalysisScope.ALBUMS, includeContent, estimate, emptyList(), emptyMap(), system,
            albumBatches = batches, mediaFiles = files.associateBy { it.path }, usesAi = useAi,
        )
    }

    private suspend fun executeAlbums(includeContent: Boolean, onProgress: (LlmProgress) -> Unit): AnalysisState = try {
        val total = albumCandidates().size
        val plan = prepareAlbums(includeContent) { done, all ->
            val p = LlmProgress(0, 0, done, all, 0, 0, 0, 0)
            _state.update { it.copy(progress = p) }; onProgress(p)
        }
        val s = settings.current()
        val guard = guards.guard(s)
        val mapper = AlbumMapper(guard, s.albumsFolder)
        val skips = skipped.filter()
        val fa = persian()
        val locale = appLocale()
        val zone = ZoneId.systemDefault()
        val provider = if (plan.usesAi) providers.create(s) else null
        val schema = JsonSchema.forApi(AlbumSchema.schema)
        var suggestions = 0
        var skippedBatches = 0
        var inTok = 0
        var outTok = 0
        var lastError: String? = null
        var covered = 0
        val mediaTotal = plan.albumBatches.sumOf { b -> b.sumOf { it.items.size } }.coerceAtLeast(total.coerceAtMost(1))

        plan.albumBatches.forEachIndexed { index, batch ->
            val p = LlmProgress(index, plan.albumBatches.size, covered, mediaTotal, suggestions, skippedBatches, inTok, outTok, lastError)
            _state.update { it.copy(progress = p) }; onProgress(p)

            val inputs = batch.map { c ->
                val place = if (c.kind == MediaCluster.Kind.TRIP) c.center?.let { (lat, lon) -> places.name(lat, lon, locale) } else null
                val thumbs = if (includeContent && provider != null) {
                    c.items.filter { !it.isVideo }.let { photos -> listOfNotNull(photos.firstOrNull(), photos.getOrNull(photos.size / 2)).distinct() }
                        .mapNotNull { plan.mediaFiles[it.path]?.let(sampler::thumbnail) }
                } else emptyList()
                AlbumInput(c, place, thumbs)
            }

            var names: List<AlbumName>? = null
            if (provider != null) {
                var parts = AlbumPrompt.userParts(inputs, fa, zone)
                for (attempt in 0..1) {
                    val r = try {
                        provider.complete(LlmRequest(plan.system, parts, (batch.size * 80 + 1500).coerceAtMost(8000), schema))
                    } catch (e: LlmException) {
                        lastError = e.message
                        if (e.status == 401 || e.status == 403) throw e
                        null
                    } ?: break
                    inTok += r.inputTokens ?: 0
                    outTok += r.outputTokens ?: 0
                    names = AlbumSchema.parse(r.text, guard)
                    if (names != null) break
                    lastError = "invalid album JSON"
                    parts = parts + LlmPart.Text(PromptBuilder.repairMessage("invalid JSON"))
                }
                if (names == null) skippedBatches++
            }
            // Any album the model didn't name (or every album, without AI) gets an on-device name.
            val byId = names.orEmpty().associateBy { it.id }
            val items = inputs.flatMap { input ->
                val name = byId[input.cluster.id] ?: AlbumFallback.name(input, fa, zone)
                mapper.map(input.cluster, name)
            }
            val paths = batch.flatMap { c -> c.items.map { it.path } }
            paths.chunked(500).forEach { planDao.deleteBySourceAndPaths(PlanSource.LLM_ALBUM, it) }
            val now = System.currentTimeMillis()
            val kept = skips.filter(items)
            planDao.insertAll(kept.map { PlanItemEntity.from(it, now) })
            suggestions += kept.size
            covered += paths.size
        }
        val done = LlmProgress(plan.albumBatches.size, plan.albumBatches.size, covered, mediaTotal, suggestions, skippedBatches, inTok, outTok, lastError)
        onProgress(done)
        AnalysisState(running = false, progress = done, finishedSuggestions = suggestions, skippedBatches = skippedBatches, error = if (skippedBatches > 0) lastError else null)
    } catch (e: CancellationException) {
        _state.value = AnalysisState(running = false)
        throw e
    } catch (e: LlmException) {
        AnalysisState(running = false, error = e.message)
    } catch (e: Exception) {
        AnalysisState(running = false, error = e.javaClass.simpleName + (e.message?.let { ": $it" } ?: ""))
    }

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
                    AnalysisScope.ALBUMS -> false // handled by albumCandidates()
                }
            }
            .sortedByDescending { it.lastModified }
            .toList()
    }

    private companion object {
        const val ALBUMS_PER_REQUEST = 40
        val PLACEHOLDER_TEXT = "x".repeat(PromptBuilder.MAX_SNIPPET)
    }
}

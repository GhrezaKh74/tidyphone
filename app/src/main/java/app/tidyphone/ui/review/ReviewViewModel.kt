package app.tidyphone.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tidyphone.data.repo.AnalysisPlan
import app.tidyphone.data.repo.AnalysisScope
import app.tidyphone.data.repo.AnalysisState
import app.tidyphone.data.repo.HistoryRepository
import app.tidyphone.data.repo.IndexRepository
import app.tidyphone.data.repo.LlmAnalysisRepository
import app.tidyphone.data.repo.PlanRepository
import app.tidyphone.data.settings.AppSettings
import app.tidyphone.data.settings.ProviderType
import app.tidyphone.data.settings.SecureKeyStore
import app.tidyphone.data.settings.SettingsRepository
import app.tidyphone.domain.exec.ExecutionReport
import app.tidyphone.domain.model.ActionType
import app.tidyphone.domain.model.PlanItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExecUi(val running: Boolean = false, val done: Int = 0, val total: Int = 0, val report: ExecutionReport? = null, val undone: Boolean = false)

data class AnalysisSheetUi(
    val open: Boolean = false,
    val scope: AnalysisScope = AnalysisScope.ALL,
    val includeContent: Boolean = false,
    val counts: Map<AnalysisScope, Int> = emptyMap(),
    val plan: AnalysisPlan? = null,
    val preparing: Boolean = false,
)

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val plan: PlanRepository,
    private val analysis: LlmAnalysisRepository,
    private val history: HistoryRepository,
    private val settingsRepo: SettingsRepository,
    private val keys: SecureKeyStore,
    index: IndexRepository,
) : ViewModel() {
    val root: String = index.root
    val items: StateFlow<List<PlanItem>> = plan.items.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val analysisState: StateFlow<AnalysisState> = analysis.state
    val settings: StateFlow<AppSettings> = settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _exec = MutableStateFlow(ExecUi())
    val exec: StateFlow<ExecUi> = _exec.asStateFlow()

    private val _sheet = MutableStateFlow(AnalysisSheetUi())
    val sheet: StateFlow<AnalysisSheetUi> = _sheet.asStateFlow()

    fun hasKey(): Boolean = settings.value.provider == ProviderType.OPENAI_COMPAT || keys.has(ProviderType.ANTHROPIC)

    fun toggle(item: PlanItem) = viewModelScope.launch {
        val select = !item.selected
        plan.setSelected(listOf(item.id), select)
        if (select) {
            // One action per file: selecting a delete unselects a move of the same file, and so on.
            val others = items.value.filter { it.path == item.path && it.id != item.id && it.selected }.map { it.id }
            if (others.isNotEmpty()) plan.setSelected(others, false)
        }
    }

    fun setSelected(group: List<PlanItem>, selected: Boolean) = viewModelScope.launch {
        plan.setSelected(group.map { it.id }, selected)
        if (selected) {
            val ids = group.map { it.id }.toSet()
            val paths = group.map { it.path }.toSet()
            val others = items.value.filter { it.path in paths && it.id !in ids && it.selected }.map { it.id }
            if (others.isNotEmpty()) plan.setSelected(others, false)
        }
    }

    fun dismiss(items: List<PlanItem>) = viewModelScope.launch { plan.dismiss(items.map { it.id }) }

    fun skip(items: List<PlanItem>) = viewModelScope.launch { plan.skip(items) }
    fun unskip(items: List<PlanItem>) = viewModelScope.launch { plan.unskip(items) }

    fun execute() {
        if (_exec.value.running) return
        _exec.value = ExecUi(running = true)
        viewModelScope.launch {
            val report = plan.executeSelected { done, total -> _exec.update { it.copy(done = done, total = total) } }
            _exec.value = ExecUi(running = false, report = report)
        }
    }

    fun undoLast() = viewModelScope.launch {
        val r = _exec.value.report ?: return@launch
        if (r.dryRun) return@launch
        history.undoBatch(r.batchId)
        _exec.update { it.copy(undone = true) }
    }

    fun clearReport() { _exec.value = ExecUi() }

    // --- LLM analysis ---

    fun openAnalysis() = viewModelScope.launch {
        _sheet.value = AnalysisSheetUi(open = true, preparing = true)
        val counts = AnalysisScope.entries.associateWith { analysis.countFor(it) }
        _sheet.update { it.copy(counts = counts) }
        prepare()
    }

    fun setScope(scope: AnalysisScope) { _sheet.update { it.copy(scope = scope) }; viewModelScope.launch { prepare() } }

    fun setIncludeContent(on: Boolean) { _sheet.update { it.copy(includeContent = on) }; viewModelScope.launch { prepare() } }

    private suspend fun prepare() {
        _sheet.update { it.copy(preparing = true) }
        val s = _sheet.value
        val p = analysis.prepare(s.scope, s.includeContent)
        _sheet.update { it.copy(plan = p, preparing = false) }
    }

    fun closeAnalysis() { _sheet.value = AnalysisSheetUi() }

    fun runAnalysis() {
        val p = _sheet.value.plan ?: return
        analysis.run(p)
        closeAnalysis()
    }

    fun cancelAnalysis() = analysis.cancel()
    fun dismissAnalysisResult() = analysis.dismissResult()

    companion object {
        fun selectedDeletes(items: List<PlanItem>) = items.filter { it.selected && it.action == ActionType.DELETE }
    }
}

package app.tidyphone.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tidyphone.data.db.CategoryTotal
import app.tidyphone.data.fs.StorageStats
import app.tidyphone.data.repo.IndexRepository
import app.tidyphone.data.repo.PlanRepository
import app.tidyphone.data.repo.ScanRepository
import app.tidyphone.data.repo.ScanState
import app.tidyphone.domain.model.ActionType
import app.tidyphone.domain.model.PlanSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class Insight(val source: PlanSource, val count: Int, val bytes: Long)

data class DashboardState(
    val storage: StorageStats = StorageStats(0, 0),
    val categories: List<CategoryTotal> = emptyList(),
    val indexedFiles: Int = 0,
    val scan: ScanState = ScanState(),
    val reclaimableBytes: Long = 0,
    val pendingActions: Int = 0,
    val moves: Int = 0,
    val insights: List<Insight> = emptyList(),
    val apps: app.tidyphone.data.repo.AppsSummary? = null,
    val loaded: Boolean = false,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val index: IndexRepository,
    plan: PlanRepository,
    private val scans: ScanRepository,
    private val appsRepo: app.tidyphone.data.repo.AppsRepository,
    private val settingsRepo: app.tidyphone.data.settings.SettingsRepository,
) : ViewModel() {
    private val storage = MutableStateFlow(StorageStats(0, 0))

    private val base = combine(storage, index.categoryTotals, index.fileCount, scans.state, plan.items) { st, cats, count, scan, items ->
        val bySource = items.filter { it.action == ActionType.DELETE }.groupBy { it.source }
        DashboardState(
            storage = st,
            categories = cats,
            indexedFiles = count,
            scan = scan,
            reclaimableBytes = items.filter { it.selected && it.action == ActionType.DELETE }.sumOf { it.size },
            pendingActions = items.count { it.selected },
            moves = items.count { it.action != ActionType.DELETE },
            insights = listOf(PlanSource.LOCAL_DUPLICATE, PlanSource.LOCAL_TEMP, PlanSource.LOCAL_LARGE, PlanSource.LOCAL_APK, PlanSource.LOCAL_OLD_DOWNLOAD, PlanSource.LOCAL_OLD_SCREENSHOT)
                .map { s -> bySource[s].orEmpty().let { Insight(s, it.size, it.sumOf { i -> i.size }) } },
            loaded = true,
        )
    }

    val state: StateFlow<DashboardState> = combine(base, appsRepo.summary) { s, apps -> s.copy(apps = apps) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardState())

    init {
        refreshStorage()
        // Fill the unused-apps card in the background when usage access is already granted.
        viewModelScope.launch(Dispatchers.IO) {
            if (appsRepo.summary.value == null && appsRepo.hasUsageAccess()) runCatching { appsRepo.load(settingsRepo.current().unusedAppDays) }
        }
    }

    fun refreshStorage() = viewModelScope.launch(Dispatchers.IO) { storage.value = index.storage() }

    fun scan() = scans.startScan()
}

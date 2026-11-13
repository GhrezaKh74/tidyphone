package app.tidyphone.data.repo

import app.tidyphone.data.db.CategoryTotal
import app.tidyphone.data.db.FileDao
import app.tidyphone.data.fs.StorageLocations
import app.tidyphone.data.fs.StorageStats
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IndexRepository @Inject constructor(private val dao: FileDao, private val locations: StorageLocations) {
    val categoryTotals: Flow<List<CategoryTotal>> = dao.categoryTotals()
    val fileCount: Flow<Int> = dao.observeCount()
    fun storage(): StorageStats = locations.stats()
    val root: String get() = locations.root
}

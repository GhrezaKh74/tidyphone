package app.tidyphone.data.repo

import app.tidyphone.data.fs.StorageLocations
import app.tidyphone.data.settings.AppSettings
import app.tidyphone.data.settings.SettingsRepository
import app.tidyphone.domain.safety.PathGuard
import javax.inject.Inject
import javax.inject.Singleton

/** Builds a [PathGuard] from the current exclude list, so every operation sees the latest rules. */
@Singleton
class GuardProvider @Inject constructor(
    private val settings: SettingsRepository,
    private val locations: StorageLocations,
) {
    suspend fun guard(): PathGuard = guard(settings.current())

    fun guard(s: AppSettings): PathGuard =
        PathGuard(locations.root, userExcludes = s.excludeFolders, trashDir = locations.trashDir)
}

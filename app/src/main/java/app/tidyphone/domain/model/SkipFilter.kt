package app.tidyphone.domain.model

/** Hides suggestions the user skipped, unless the file changed size since then. */
class SkipFilter(skipped: Collection<Skipped>) {
    data class Skipped(val path: String, val source: PlanSource, val size: Long)

    private val bySourceAndPath = skipped.associateBy { it.source to it.path.lowercase() }

    fun isSkipped(item: PlanItem): Boolean =
        bySourceAndPath[item.source to item.path.lowercase()]?.let { it.size == item.size } == true

    fun filter(items: List<PlanItem>): List<PlanItem> = items.filterNot(::isSkipped)
}

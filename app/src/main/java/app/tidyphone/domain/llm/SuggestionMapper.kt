package app.tidyphone.domain.llm

import app.tidyphone.domain.model.ActionType
import app.tidyphone.domain.model.FileEntry
import app.tidyphone.domain.model.PlanItem
import app.tidyphone.domain.model.PlanSource
import app.tidyphone.domain.safety.PathGuard

/**
 * Turns validated model output into plan items. This is where the safety rules are enforced in
 * code: anything outside allowed paths, into excluded/protected folders, or with an unsafe name
 * is dropped, whatever the model said.
 */
class SuggestionMapper(private val guard: PathGuard) {

    data class Result(val items: List<PlanItem>, val dropped: List<String>)

    fun map(suggestions: List<Suggestion>, filesById: Map<Int, FileEntry>): Result {
        val items = mutableListOf<PlanItem>()
        val dropped = mutableListOf<String>()

        for (s in suggestions) {
            val file = filesById[s.id]
            if (file == null) { dropped += "#${s.id}: unknown id"; continue }
            if (!guard.canModify(file.path)) { dropped += "#${s.id}: file is protected"; continue }
            val reason = s.reason.take(240)

            if (s.safeToDelete) {
                items += PlanItem(
                    path = file.path, action = ActionType.DELETE, source = PlanSource.LLM, size = file.size,
                    reason = reason, category = s.category, selected = false,
                )
            }

            val target = s.targetFolder.takeIf { it.isNotBlank() }?.let { t ->
                val resolved = validateFolder(t)
                if (resolved == null) dropped += "#${s.id}: target folder \"$t\" is not allowed"
                resolved
            }?.takeIf { !it.equals(file.parent, ignoreCase = true) }

            val newName = s.rename.takeIf { it.isNotBlank() }?.let { r ->
                val fixed = keepExtension(r, file.name)
                if (fixed == null) { dropped += "#${s.id}: rename \"$r\" is not a valid name"; null } else fixed
            }?.takeIf { it != file.name }

            when {
                target != null -> items += PlanItem(
                    path = file.path, action = ActionType.MOVE, source = PlanSource.LLM, size = file.size,
                    reason = reason, targetDir = target, newName = newName, category = s.category, selected = !s.safeToDelete,
                )
                newName != null -> items += PlanItem(
                    path = file.path, action = ActionType.RENAME, source = PlanSource.LLM, size = file.size,
                    reason = reason, newName = newName, category = s.category, selected = !s.safeToDelete,
                )
            }
        }
        return Result(items, dropped)
    }

    /** Relative folder inside the root, no hidden segments, not too deep, and writable. */
    fun validateFolder(relative: String): String? {
        val raw = relative.trim()
        val rel = if (raw.startsWith("/")) {
            val norm = PathGuard.normalize(raw) ?: return null
            if (!PathGuard.isInside(norm, guard.root)) return null
            norm.drop(guard.root.length)
        } else raw
        val t = rel.trim('/')
        if (t.isEmpty() || t.startsWith("~")) return null
        val segments = t.split('/').filter { it.isNotEmpty() }
        if (segments.size > 5) return null
        if (segments.any { it.startsWith(".") || !guard.isValidFolderName(it) }) return null
        val resolved = guard.resolve(t) ?: return null
        return if (guard.canWriteTo(resolved)) resolved else null
    }

    /** Validates a new name and makes sure the original extension is kept. */
    fun keepExtension(newName: String, original: String): String? {
        val ext = original.substringAfterLast('.', "")
        var name = newName.trim()
        if (ext.isNotEmpty() && !name.endsWith(".$ext", ignoreCase = true)) {
            name = name.substringBeforeLast('.', name).let { base -> if (base.isBlank()) return null else "$base.$ext" }
        }
        return if (guard.isValidFileName(name)) name else null
    }
}

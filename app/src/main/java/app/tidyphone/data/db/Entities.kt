package app.tidyphone.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.tidyphone.domain.model.ActionType
import app.tidyphone.domain.model.FileCategory
import app.tidyphone.domain.model.FileEntry
import app.tidyphone.domain.model.HistoryRecord
import app.tidyphone.domain.model.PlanItem
import app.tidyphone.domain.model.PlanSource
import app.tidyphone.domain.model.TrashItem

@Entity(tableName = "files", indices = [Index("size"), Index("category"), Index("parent")])
data class FileEntity(
    @PrimaryKey val path: String,
    val name: String,
    val parent: String,
    val size: Long,
    val mimeType: String?,
    val lastModified: Long,
    val category: FileCategory,
    val hash: String?,
    val isDirectory: Boolean,
    val scanId: Long,
) {
    fun toDomain() = FileEntry(path, name, parent, size, mimeType, lastModified, category, hash, isDirectory)
}

@Entity(tableName = "plan_items", indices = [Index("path"), Index("source")])
data class PlanItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val path: String,
    val action: ActionType,
    val source: PlanSource,
    val size: Long,
    val reason: String,
    val targetDir: String?,
    val newName: String?,
    val category: String?,
    val selected: Boolean,
    val groupKey: String?,
    val createdAt: Long,
) {
    fun toDomain() = PlanItem(id, path, action, source, size, reason, targetDir, newName, category, selected, groupKey)

    companion object {
        fun from(p: PlanItem, now: Long) = PlanItemEntity(
            id = p.id, path = p.path, action = p.action, source = p.source, size = p.size, reason = p.reason,
            targetDir = p.targetDir, newName = p.newName, category = p.category, selected = p.selected,
            groupKey = p.groupKey, createdAt = now,
        )
    }
}

@Entity(tableName = "history", indices = [Index("batchId"), Index("timestamp")])
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val batchId: String,
    val timestamp: Long,
    val action: ActionType,
    val originalPath: String,
    val resultPath: String,
    val size: Long,
    val undone: Boolean = false,
    val undoneAt: Long? = null,
) {
    fun toDomain() = HistoryRecord(id, batchId, timestamp, action, originalPath, resultPath, size, undone)

    companion object {
        fun from(r: HistoryRecord) = HistoryEntity(r.id, r.batchId, r.timestamp, r.action, r.originalPath, r.resultPath, r.size, r.undone)
    }
}

@Entity(tableName = "trash", indices = [Index(value = ["trashPath"], unique = true)])
data class TrashEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalPath: String,
    val trashPath: String,
    val trashedAt: Long,
    val size: Long,
) {
    fun toDomain() = TrashItem(id, originalPath, trashPath, trashedAt, size)

    companion object {
        fun from(t: TrashItem) = TrashEntity(t.id, t.originalPath, t.trashPath, t.trashedAt, t.size)
    }
}

data class CategoryTotal(val category: FileCategory, val bytes: Long, val count: Int)

package app.tidyphone.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import app.tidyphone.domain.model.PlanSource
import kotlinx.coroutines.flow.Flow

@Dao
interface FileDao {
    @Upsert suspend fun upsertAll(files: List<FileEntity>)

    @Query("SELECT path, size, lastModified, hash FROM files WHERE hash IS NOT NULL")
    suspend fun knownHashes(): List<KnownHash>

    @Query("SELECT * FROM files") suspend fun all(): List<FileEntity>

    @Query("SELECT * FROM files WHERE path = :path") suspend fun get(path: String): FileEntity?

    @Query("SELECT * FROM files WHERE path IN (:paths)") suspend fun getAll(paths: List<String>): List<FileEntity>

    @Query("SELECT * FROM files WHERE isDirectory = 0 ORDER BY path LIMIT :limit OFFSET :offset")
    suspend fun page(limit: Int, offset: Int): List<FileEntity>

    @Query("SELECT COUNT(*) FROM files WHERE isDirectory = 0") suspend fun countFiles(): Int

    @Query("UPDATE files SET hash = :hash WHERE path = :path") suspend fun setHash(path: String, hash: String)

    @Query("DELETE FROM files WHERE scanId != :scanId") suspend fun deleteStale(scanId: Long)

    @Query("DELETE FROM files WHERE path = :path") suspend fun delete(path: String)

    @Query("SELECT category, SUM(size) AS bytes, COUNT(*) AS count FROM files WHERE isDirectory = 0 GROUP BY category ORDER BY bytes DESC")
    fun categoryTotals(): Flow<List<CategoryTotal>>

    @Query("SELECT COUNT(*) FROM files WHERE isDirectory = 0") fun observeCount(): Flow<Int>
}

data class KnownHash(val path: String, val size: Long, val lastModified: Long, val hash: String)

@Dao
interface PlanDao {
    @Query("SELECT * FROM plan_items ORDER BY size DESC") fun observeAll(): Flow<List<PlanItemEntity>>
    @Query("SELECT * FROM plan_items") suspend fun all(): List<PlanItemEntity>
    @Query("SELECT * FROM plan_items WHERE selected = 1") suspend fun selected(): List<PlanItemEntity>
    @Insert suspend fun insertAll(items: List<PlanItemEntity>)
    @Query("UPDATE plan_items SET selected = :selected WHERE id IN (:ids)") suspend fun setSelected(ids: List<Long>, selected: Boolean)
    @Query("DELETE FROM plan_items WHERE id IN (:ids)") suspend fun delete(ids: List<Long>)
    @Query("DELETE FROM plan_items WHERE source IN (:sources)") suspend fun deleteBySources(sources: List<PlanSource>)
    @Query("DELETE FROM plan_items WHERE path NOT IN (SELECT path FROM files)") suspend fun deleteOrphans()
    @Query("DELETE FROM plan_items") suspend fun clear()

    @Transaction
    suspend fun replaceSources(sources: List<PlanSource>, items: List<PlanItemEntity>) {
        deleteBySources(sources)
        insertAll(items)
        deleteOrphans()
    }
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY timestamp DESC, id DESC") fun observeAll(): Flow<List<HistoryEntity>>
    @Query("SELECT * FROM history WHERE id = :id") suspend fun get(id: Long): HistoryEntity?
    @Query("SELECT * FROM history WHERE batchId = :batchId ORDER BY id DESC") suspend fun batch(batchId: String): List<HistoryEntity>
    @Insert suspend fun insert(entity: HistoryEntity): Long
    @Query("UPDATE history SET undone = 1, undoneAt = :at WHERE id = :id") suspend fun markUndone(id: Long, at: Long)
    @Query("DELETE FROM history WHERE timestamp < :before") suspend fun deleteOlderThan(before: Long)
}

@Dao
interface TrashDao {
    @Query("SELECT * FROM trash ORDER BY trashedAt DESC") fun observeAll(): Flow<List<TrashEntity>>
    @Query("SELECT * FROM trash") suspend fun all(): List<TrashEntity>
    @Query("SELECT * FROM trash WHERE trashPath = :trashPath") suspend fun byTrashPath(trashPath: String): TrashEntity?
    @Query("SELECT * FROM trash WHERE trashedAt < :before") suspend fun olderThan(before: Long): List<TrashEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(entity: TrashEntity): Long
    @Query("DELETE FROM trash WHERE id = :id") suspend fun delete(id: Long)
}

package app.tidyphone.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [FileEntity::class, PlanItemEntity::class, HistoryEntity::class, TrashEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class TidyDatabase : RoomDatabase() {
    abstract fun files(): FileDao
    abstract fun plan(): PlanDao
    abstract fun history(): HistoryDao
    abstract fun trash(): TrashDao
}

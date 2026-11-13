package app.tidyphone.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        FileEntity::class, PlanItemEntity::class, HistoryEntity::class, TrashEntity::class,
        IgnoredEntity::class, MediaMetaEntity::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class TidyDatabase : RoomDatabase() {
    abstract fun files(): FileDao
    abstract fun plan(): PlanDao
    abstract fun history(): HistoryDao
    abstract fun trash(): TrashDao
    abstract fun ignored(): IgnoredDao
    abstract fun mediaMeta(): MediaMetaDao
}

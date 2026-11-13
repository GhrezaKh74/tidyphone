package app.tidyphone.di

import android.content.Context
import androidx.room.Room
import app.tidyphone.data.db.FileDao
import app.tidyphone.data.db.HistoryDao
import app.tidyphone.data.db.PlanDao
import app.tidyphone.data.db.TidyDatabase
import app.tidyphone.data.db.TrashDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): TidyDatabase =
        Room.databaseBuilder(context, TidyDatabase::class.java, "tidyphone.db").build()

    @Provides fun fileDao(db: TidyDatabase): FileDao = db.files()
    @Provides fun planDao(db: TidyDatabase): PlanDao = db.plan()
    @Provides fun historyDao(db: TidyDatabase): HistoryDao = db.history()
    @Provides fun trashDao(db: TidyDatabase): TrashDao = db.trash()
}

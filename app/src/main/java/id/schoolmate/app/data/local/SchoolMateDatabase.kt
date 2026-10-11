package id.schoolmate.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import id.schoolmate.app.data.local.dao.SubjectDao
import id.schoolmate.app.data.local.entity.SubjectEntity

/**
 * Single local source of truth (offline-first). Schemas are exported to app/schemas
 * so future versions can ship tested migrations instead of destructive resets.
 */
@Database(
    entities = [SubjectEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class SchoolMateDatabase : RoomDatabase() {
    abstract fun subjectDao(): SubjectDao

    companion object {
        private const val DATABASE_NAME = "schoolmate.db"

        @Volatile
        private var instance: SchoolMateDatabase? = null

        fun getInstance(context: Context): SchoolMateDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SchoolMateDatabase::class.java,
                    DATABASE_NAME,
                ).build().also { instance = it }
            }
    }
}

package id.schoolmate.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import id.schoolmate.app.data.local.converter.Converters
import id.schoolmate.app.data.local.dao.ExamDao
import id.schoolmate.app.data.local.dao.ScheduleDao
import id.schoolmate.app.data.local.dao.StudySessionDao
import id.schoolmate.app.data.local.dao.SubjectDao
import id.schoolmate.app.data.local.dao.TaskDao
import id.schoolmate.app.data.local.entity.ExamEntity
import id.schoolmate.app.data.local.entity.ScheduleEntryEntity
import id.schoolmate.app.data.local.entity.StudySessionEntity
import id.schoolmate.app.data.local.entity.SubjectEntity
import id.schoolmate.app.data.local.entity.TaskEntity
import id.schoolmate.app.data.local.migration.ALL_MIGRATIONS

/**
 * Single local source of truth (offline-first). Schemas are exported to app/schemas and every
 * version bump ships an explicit, tested migration — never a destructive fallback.
 *
 * v1: subjects
 * v2: + schedule_entries, tasks, exams, study_sessions
 */
@Database(
    entities = [
        SubjectEntity::class,
        ScheduleEntryEntity::class,
        TaskEntity::class,
        ExamEntity::class,
        StudySessionEntity::class,
    ],
    version = SchoolMateDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class SchoolMateDatabase : RoomDatabase() {
    abstract fun subjectDao(): SubjectDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun taskDao(): TaskDao
    abstract fun examDao(): ExamDao
    abstract fun studySessionDao(): StudySessionDao

    companion object {
        const val VERSION = 2
        const val DATABASE_NAME = "schoolmate.db"

        @Volatile
        private var instance: SchoolMateDatabase? = null

        fun getInstance(context: Context): SchoolMateDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context).also { instance = it }
            }

        /** Shared builder config so production and tests open the database identically. */
        fun RoomDatabase.Builder<SchoolMateDatabase>.configure(): RoomDatabase.Builder<SchoolMateDatabase> =
            addMigrations(*ALL_MIGRATIONS)

        private fun build(context: Context): SchoolMateDatabase =
            Room.databaseBuilder(context.applicationContext, SchoolMateDatabase::class.java, DATABASE_NAME)
                .configure()
                .build()
    }
}

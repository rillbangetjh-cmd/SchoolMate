package id.schoolmate.app.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 -> v2: purely additive. Creates the schedule, task, exam, and study-session tables.
 * The existing `subjects` table and its rows are not touched.
 *
 * SQL mirrors app/schemas/.../2.json exactly; MigrationTest validates it against Room's schema.
 * Tables are created parent-first (tasks before study_sessions) because of foreign keys.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `schedule_entries` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `subject_id` INTEGER NOT NULL, " +
                "`day_of_week` INTEGER NOT NULL, `start_time` INTEGER NOT NULL, `end_time` INTEGER NOT NULL, " +
                "`room` TEXT NOT NULL, `note` TEXT NOT NULL, `created_at` INTEGER NOT NULL, " +
                "`updated_at` INTEGER NOT NULL, " +
                "FOREIGN KEY(`subject_id`) REFERENCES `subjects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_schedule_entries_subject_id` ON `schedule_entries` (`subject_id`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_schedule_entries_day_of_week_start_time` " +
                "ON `schedule_entries` (`day_of_week`, `start_time`)",
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `tasks` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
                "`description` TEXT NOT NULL, `subject_id` INTEGER, `due_date` INTEGER NOT NULL, " +
                "`due_time` INTEGER, `priority` INTEGER NOT NULL, `status` TEXT NOT NULL, " +
                "`completed_at` INTEGER, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, " +
                "FOREIGN KEY(`subject_id`) REFERENCES `subjects`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_subject_id` ON `tasks` (`subject_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_status_due_date` ON `tasks` (`status`, `due_date`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `exams` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
                "`subject_id` INTEGER, `exam_date` INTEGER NOT NULL, `start_time` INTEGER, " +
                "`duration_minutes` INTEGER, `location` TEXT NOT NULL, `note` TEXT NOT NULL, " +
                "`created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, " +
                "FOREIGN KEY(`subject_id`) REFERENCES `subjects`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_exams_subject_id` ON `exams` (`subject_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_exams_exam_date` ON `exams` (`exam_date`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `study_sessions` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `subject_id` INTEGER, `task_id` INTEGER, " +
                "`started_at` INTEGER NOT NULL, `session_date` INTEGER NOT NULL, " +
                "`duration_minutes` INTEGER NOT NULL, `note` TEXT NOT NULL, `created_at` INTEGER NOT NULL, " +
                "FOREIGN KEY(`subject_id`) REFERENCES `subjects`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , " +
                "FOREIGN KEY(`task_id`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_sessions_subject_id` ON `study_sessions` (`subject_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_sessions_task_id` ON `study_sessions` (`task_id`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_study_sessions_session_date` ON `study_sessions` (`session_date`)",
        )
    }
}

/** Every migration, in order. Add new ones here; DatabaseMigrationTest checks the full chain. */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)

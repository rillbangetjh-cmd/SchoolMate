package id.schoolmate.app.data.local

import android.content.ContentValues
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.schoolmate.app.data.local.SchoolMateDatabase.Companion.configure
import id.schoolmate.app.data.local.entity.TaskEntity
import id.schoolmate.app.data.local.migration.ALL_MIGRATIONS
import id.schoolmate.app.data.preferences.DataStoreUserPreferencesRepository
import id.schoolmate.app.domain.model.Priority
import id.schoolmate.app.domain.model.TaskStatus
import id.schoolmate.app.domain.model.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/**
 * Upgrading from stage 1 (DB v1) must keep subjects and settings, and the hand-written
 * migration must yield exactly the v2 schema.
 *
 * The v1 file is built from the committed 1.json, then opened through the *production* Room
 * builder. Room runs MIGRATION_1_2 and validates every table, column, index, and foreign key
 * against the compiled v2 schema, throwing if anything differs. On top of that, the resulting
 * sqlite_master is compared with 2.json.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val dbName = "migration-test.db"
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val dbFile: File get() = context.getDatabasePath(dbName)

    @Before @After fun cleanUp() {
        context.deleteDatabase(dbName)
    }

    private fun seedVersion1() {
        RoomSchemaFiles.createDatabaseAt(dbFile, version = 1).use { db ->
            listOf(
                Triple("Matematika", "Bu Sari", 0xFF006A60.toInt()),
                Triple("Bahasa Indonesia", "", 0xFF3F51B5.toInt()),
                Triple("Fisika", "Pak Andi", 0xFFC2185B.toInt()),
            ).forEachIndexed { i, (name, teacher, color) ->
                db.insertOrThrow(
                    "subjects", null,
                    ContentValues().apply {
                        put("id", i + 1L)
                        put("name", name)
                        put("teacher", teacher)
                        put("color_argb", color)
                        put("created_at", 1_000L + i)
                    },
                )
            }
        }
    }

    private fun openWithRoom(): SchoolMateDatabase =
        Room.databaseBuilder(context, SchoolMateDatabase::class.java, dbName)
            .configure()
            .allowMainThreadQueries()
            .build()

    @Test fun migrate1To2_keepsSubjectsAndMatchesExportedSchema() {
        seedVersion1()
        val room = openWithRoom()
        try {
            // Forces open -> migration -> Room schema validation.
            val db = room.openHelper.writableDatabase
            assertEquals(2, db.version)

            db.query("SELECT id, name, teacher, color_argb, created_at FROM subjects ORDER BY id").use { c ->
                assertEquals(3, c.count)
                c.moveToFirst()
                assertEquals(1L, c.getLong(0)); assertEquals("Matematika", c.getString(1)); assertEquals("Bu Sari", c.getString(2))
                assertEquals(0xFF006A60.toInt(), c.getInt(3)); assertEquals(1_000L, c.getLong(4))
                c.moveToLast()
                assertEquals(3L, c.getLong(0)); assertEquals("Fisika", c.getString(1)); assertEquals("Pak Andi", c.getString(2))
            }

            val actual = buildMap {
                db.query("SELECT name, sql FROM sqlite_master WHERE type = 'table'").use { c ->
                    while (c.moveToNext()) put(c.getString(0), RoomSchemaFiles.normalize(c.getString(1)))
                }
            }
            val expected = RoomSchemaFiles.tableSql(2)
            expected.forEach { (table, sql) -> assertEquals("schema of $table", sql, actual[table]) }

            val indices = buildSet {
                db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND name LIKE 'index_%'").use { c ->
                    while (c.moveToNext()) add(c.getString(0))
                }
            }
            val expectedIndices = RoomSchemaFiles.createStatements(2)
                .filter { it.startsWith("CREATE INDEX") || it.startsWith("CREATE UNIQUE INDEX") }
                .map { Regex("`(index_[^`]+)`").find(it)!!.groupValues[1] }
                .toSet()
            assertEquals(expectedIndices, indices)

            listOf("schedule_entries", "tasks", "exams", "study_sessions").forEach { table ->
                db.query("SELECT COUNT(*) FROM $table").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
            }
        } finally {
            room.close()
        }
    }

    @Test fun migratedDatabaseWorksThroughDaosAndForeignKeys(): Unit = runBlocking {
        seedVersion1()
        val db = openWithRoom()
        try {
            assertEquals(
                listOf("Bahasa Indonesia", "Fisika", "Matematika"),
                db.subjectDao().observeAll().first().map { it.name },
            )
            val taskId = db.taskDao().insert(
                TaskEntity(
                    title = "PR Fisika", description = "", subjectId = 3, dueDate = LocalDate.of(2026, 10, 13),
                    dueTime = null, priority = Priority.HIGH, status = TaskStatus.TODO, completedAt = null,
                    createdAt = 1, updatedAt = 1,
                ),
            )
            // Foreign-key actions are enforced on the migrated file.
            db.subjectDao().deleteById(3)
            assertNull(db.taskDao().getById(taskId)!!.subjectId)
            assertEquals(2, db.subjectDao().observeCount().first())
        } finally {
            db.close()
        }
    }

    @Test fun reopeningMigratedDatabaseIsStable() {
        seedVersion1()
        openWithRoom().apply { openHelper.writableDatabase; close() }
        // Second open must not re-run or fail validation, and data must still be there.
        openWithRoom().apply {
            openHelper.readableDatabase.query("SELECT COUNT(*) FROM subjects").use { c ->
                c.moveToFirst(); assertEquals(3, c.getInt(0))
            }
            close()
        }
    }

    @Test fun migrationChainCoversEveryVersion() {
        val steps = ALL_MIGRATIONS.map { it.startVersion to it.endVersion }
        assertEquals((1 until SchoolMateDatabase.VERSION).map { it to it + 1 }, steps)
        // Every version in the chain has an exported schema committed to the repo.
        (1..SchoolMateDatabase.VERSION).forEach { v -> assertTrue(RoomSchemaFiles.createStatements(v).isNotEmpty()) }
    }

    @Test fun userSettingsSurviveDatabaseUpgrade(): Unit = runBlocking {
        val prefsFile = context.preferencesDataStoreFile("user_preferences")
        prefsFile.delete()
        try {
            // A stage-1 user saved settings.
            withPreferences(prefsFile) { repo ->
                repo.setStudentName("Rina")
                repo.setThemeMode(ThemeMode.DARK)
                repo.setUseDynamicColor(true)
            }

            // The app updates and the database migrates v1 -> v2.
            seedVersion1()
            openWithRoom().apply { openHelper.writableDatabase; close() }

            // After restart, a fresh DataStore still reads the same settings.
            withPreferences(prefsFile) { repo ->
                val prefs = repo.userPreferences.first()
                assertEquals("Rina", prefs.studentName)
                assertEquals(ThemeMode.DARK, prefs.themeMode)
                assertEquals(true, prefs.useDynamicColor)
            }
        } finally {
            prefsFile.delete()
        }
    }

    private suspend fun withPreferences(file: File, block: suspend (DataStoreUserPreferencesRepository) -> Unit) {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val store: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        try {
            block(DataStoreUserPreferencesRepository(store))
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
        }
    }
}


package id.schoolmate.app.notifications

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import id.schoolmate.app.data.preferences.DataStoreNotificationPreferencesRepository
import id.schoolmate.app.data.preferences.DataStoreUserPreferencesRepository
import id.schoolmate.app.domain.model.NotificationPreferences
import id.schoolmate.app.domain.model.ReminderKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Real DataStore files; "restart" = close the DataStore and open a new one on the same file. */
class NotificationStorageTest {
    @get:Rule val tmp = TemporaryFolder()
    private val scopes = mutableListOf<CoroutineScope>()

    private fun open(file: File): DataStore<Preferences> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scopes += it }
        return PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
    }

    private fun restart() { scopes.forEach { it.cancel() }; scopes.clear(); Thread.sleep(50) }

    @After fun tearDown() = scopes.forEach { it.cancel() }

    @Test fun preferencesHaveSafeDefaultsAndSurviveRestart(): Unit = runBlocking {
        val file = File(tmp.root, "user_preferences.preferences_pb")
        val repo = DataStoreNotificationPreferencesRepository(open(file))
        assertEquals("opt-in: off until the user enables it", NotificationPreferences(), repo.preferences.first())

        repo.setEnabled(true)
        repo.setKindEnabled(ReminderKind.LESSON, false)
        repo.setLeadMinutes(ReminderKind.TASK, 180)
        repo.setLeadMinutes(ReminderKind.EXAM, 4_320)
        repo.setLeadMinutes(ReminderKind.LESSON, 30)
        repo.markPermissionRequested()

        restart()
        val reopened = DataStoreNotificationPreferencesRepository(open(file)).preferences.first()
        assertEquals(
            NotificationPreferences(
                enabled = true, lessonReminders = false, taskLeadMinutes = 180, examLeadMinutes = 4_320,
                lessonLeadMinutes = 30, permissionRequested = true,
            ),
            reopened,
        )
    }

    @Test fun notificationKeysDoNotTouchOtherSettings(): Unit = runBlocking {
        val store = open(File(tmp.root, "user_preferences.preferences_pb"))
        val user = DataStoreUserPreferencesRepository(store)
        val notif = DataStoreNotificationPreferencesRepository(store)
        user.setStudentName("Rina")
        notif.setEnabled(true)
        notif.setEnabled(false)
        assertEquals("Rina", user.userPreferences.first().studentName)
    }

    @Test fun invalidLeadTimesAreRejectedOrSanitized(): Unit = runBlocking {
        val store = open(File(tmp.root, "p.preferences_pb"))
        val repo = DataStoreNotificationPreferencesRepository(store)
        val failure = runCatching { repo.setLeadMinutes(ReminderKind.LESSON, 7) }
        assertTrue(failure.exceptionOrNull() is IllegalArgumentException)
        // A value written by a future/older version that is not offered falls back to the default.
        store.edit { it[intPreferencesKey("notif_lesson_lead_min")] = 999 }
        assertEquals(NotificationPreferences.DEFAULT_LESSON_LEAD, repo.preferences.first().lessonLeadMinutes)
    }

    @Test fun registryPersistsAndPrunesOldDeliveries(): Unit = runBlocking {
        val file = File(tmp.root, "reminder_registry.preferences_pb")
        val registry = DataStoreReminderRegistry(open(file))
        registry.setScheduled(mapOf("task:1" to 1_000L, "lesson:2" to 2_000L))
        registry.markDelivered("task:1", 1_000L, nowMillis = 1_000L)
        assertTrue(registry.wasDelivered("task:1", 1_000L))
        assertFalse("other occurrence of the same lesson is not a duplicate", registry.wasDelivered("task:1", 9_000L))

        restart()
        val reopened = DataStoreReminderRegistry(open(file))
        assertEquals(mapOf("task:1" to 1_000L, "lesson:2" to 2_000L), reopened.scheduled())
        assertTrue("survives restart → no duplicate after reboot", reopened.wasDelivered("task:1", 1_000L))

        val later = 1_000L + DataStoreReminderRegistry.RETENTION_MILLIS + 1
        reopened.markDelivered("exam:3", 5L, nowMillis = later)
        assertFalse("pruned", reopened.wasDelivered("task:1", 1_000L))
        assertEquals(setOf("exam:3"), reopened.deliveredTokens())
        reopened.forgetDelivered(setOf("exam:3"))
        assertTrue(reopened.deliveredTokens().isEmpty())
        assertEquals("task:1" to 5L, DataStoreReminderRegistry.decode("task:1@5"))
    }
}

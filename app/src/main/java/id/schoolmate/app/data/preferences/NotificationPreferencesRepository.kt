package id.schoolmate.app.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import id.schoolmate.app.domain.model.NotificationPreferences
import id.schoolmate.app.domain.model.ReminderKind
import id.schoolmate.app.domain.model.NotificationPreferences.Companion.DEFAULT_EXAM_LEAD
import id.schoolmate.app.domain.model.NotificationPreferences.Companion.DEFAULT_LESSON_LEAD
import id.schoolmate.app.domain.model.NotificationPreferences.Companion.DEFAULT_TASK_LEAD
import id.schoolmate.app.domain.model.NotificationPreferences.Companion.EXAM_LEAD_OPTIONS
import id.schoolmate.app.domain.model.NotificationPreferences.Companion.LESSON_LEAD_OPTIONS
import id.schoolmate.app.domain.model.NotificationPreferences.Companion.TASK_LEAD_OPTIONS
import id.schoolmate.app.domain.model.NotificationPreferences.Companion.sanitize
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

interface NotificationPreferencesRepository {
    val preferences: Flow<NotificationPreferences>
    suspend fun setEnabled(enabled: Boolean)
    suspend fun setKindEnabled(kind: ReminderKind, enabled: Boolean)
    suspend fun setLeadMinutes(kind: ReminderKind, minutes: Int)
    suspend fun markPermissionRequested()
}

/**
 * Stored in the same DataStore file as the other user preferences (one DataStore per file), under
 * separate keys. Writing these keys never touches Room, so tasks/exams/schedule are unaffected.
 */
class DataStoreNotificationPreferencesRepository(
    private val dataStore: DataStore<Preferences>,
) : NotificationPreferencesRepository {

    private object Keys {
        val ENABLED = booleanPreferencesKey("notif_enabled")
        val TASKS = booleanPreferencesKey("notif_tasks")
        val EXAMS = booleanPreferencesKey("notif_exams")
        val LESSONS = booleanPreferencesKey("notif_lessons")
        val TASK_LEAD = intPreferencesKey("notif_task_lead_min")
        val EXAM_LEAD = intPreferencesKey("notif_exam_lead_min")
        val LESSON_LEAD = intPreferencesKey("notif_lesson_lead_min")
        val PERMISSION_REQUESTED = booleanPreferencesKey("notif_permission_requested")
    }

    override val preferences: Flow<NotificationPreferences> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { p ->
            NotificationPreferences(
                enabled = p[Keys.ENABLED] ?: false,
                taskReminders = p[Keys.TASKS] ?: true,
                examReminders = p[Keys.EXAMS] ?: true,
                lessonReminders = p[Keys.LESSONS] ?: true,
                taskLeadMinutes = sanitize(p[Keys.TASK_LEAD], TASK_LEAD_OPTIONS, DEFAULT_TASK_LEAD),
                examLeadMinutes = sanitize(p[Keys.EXAM_LEAD], EXAM_LEAD_OPTIONS, DEFAULT_EXAM_LEAD),
                lessonLeadMinutes = sanitize(p[Keys.LESSON_LEAD], LESSON_LEAD_OPTIONS, DEFAULT_LESSON_LEAD),
                permissionRequested = p[Keys.PERMISSION_REQUESTED] ?: false,
            )
        }
        .distinctUntilChanged()

    override suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.ENABLED] = enabled }
    }

    override suspend fun setKindEnabled(kind: ReminderKind, enabled: Boolean) {
        dataStore.edit {
            it[
                when (kind) {
                    ReminderKind.TASK -> Keys.TASKS
                    ReminderKind.EXAM -> Keys.EXAMS
                    ReminderKind.LESSON -> Keys.LESSONS
                },
            ] = enabled
        }
    }

    override suspend fun setLeadMinutes(kind: ReminderKind, minutes: Int) {
        val (key, options) = when (kind) {
            ReminderKind.TASK -> Keys.TASK_LEAD to TASK_LEAD_OPTIONS
            ReminderKind.EXAM -> Keys.EXAM_LEAD to EXAM_LEAD_OPTIONS
            ReminderKind.LESSON -> Keys.LESSON_LEAD to LESSON_LEAD_OPTIONS
        }
        require(minutes in options) { "Unsupported lead time $minutes for $kind" }
        dataStore.edit { it[key] = minutes }
    }

    override suspend fun markPermissionRequested() {
        dataStore.edit { it[Keys.PERMISSION_REQUESTED] = true }
    }
}

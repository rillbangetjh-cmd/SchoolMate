package id.schoolmate.app.testing

import id.schoolmate.app.data.preferences.NotificationPreferencesRepository
import id.schoolmate.app.domain.model.NotificationPreferences
import id.schoolmate.app.domain.model.ReminderKind
import id.schoolmate.app.notifications.PlannedReminder
import id.schoolmate.app.notifications.ReminderKey
import id.schoolmate.app.notifications.ReminderNotifier
import id.schoolmate.app.notifications.ReminderRegistry
import id.schoolmate.app.notifications.ReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** Clock whose instant and zone can be changed mid-test (time / zone change simulation). */
class MutableClock(var now: Instant, var tz: ZoneId) : Clock() {
    override fun getZone(): ZoneId = tz
    override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)
    override fun instant(): Instant = now
}

/** Behaves like AlarmManager: one alarm per key, rescheduling replaces, reboot clears everything. */
class FakeScheduler : ReminderScheduler {
    val alarms = linkedMapOf<String, Instant>()
    var scheduleCalls = 0
    var cancelCalls = 0
    var resyncAt: Instant? = null

    override fun schedule(reminder: PlannedReminder) {
        scheduleCalls++
        alarms[reminder.key.token] = reminder.triggerAt
    }

    override fun cancel(key: ReminderKey) {
        cancelCalls++
        alarms.remove(key.token)
    }

    override fun scheduleResync(at: Instant?) { resyncAt = at }

    fun reboot() { alarms.clear(); resyncAt = null }
}

class FakeNotifier(var allowed: Boolean = true) : ReminderNotifier {
    val posted = mutableListOf<PlannedReminder>()
    val dismissed = mutableListOf<ReminderKey>()
    override fun canPost(kind: ReminderKind) = allowed
    override fun post(reminder: PlannedReminder): Boolean {
        if (!allowed) return false
        posted += reminder
        return true
    }
    override fun dismiss(key: ReminderKey) { dismissed += key }
}

class InMemoryRegistry : ReminderRegistry {
    var scheduled = mapOf<String, Long>()
    val delivered = mutableSetOf<Pair<String, Long>>()
    override suspend fun scheduled() = scheduled
    override suspend fun setScheduled(value: Map<String, Long>) { scheduled = value }
    override suspend fun wasDelivered(token: String, triggerAtMillis: Long) = (token to triggerAtMillis) in delivered
    override suspend fun markDelivered(token: String, triggerAtMillis: Long, nowMillis: Long) { delivered += token to triggerAtMillis }
    override suspend fun deliveredTokens() = delivered.map { it.first }.toSet()
    override suspend fun forgetDelivered(tokens: Set<String>) { delivered.removeAll { it.first in tokens } }
}

class FakeNotificationPreferencesRepository(initial: NotificationPreferences = NotificationPreferences(enabled = true)) :
    NotificationPreferencesRepository {
    val state = MutableStateFlow(initial)
    override val preferences = state
    override suspend fun setEnabled(enabled: Boolean) = state.update { it.copy(enabled = enabled) }
    override suspend fun setKindEnabled(kind: ReminderKind, enabled: Boolean) = state.update {
        when (kind) {
            ReminderKind.TASK -> it.copy(taskReminders = enabled)
            ReminderKind.EXAM -> it.copy(examReminders = enabled)
            ReminderKind.LESSON -> it.copy(lessonReminders = enabled)
        }
    }
    override suspend fun setLeadMinutes(kind: ReminderKind, minutes: Int) = state.update {
        when (kind) {
            ReminderKind.TASK -> it.copy(taskLeadMinutes = minutes)
            ReminderKind.EXAM -> it.copy(examLeadMinutes = minutes)
            ReminderKind.LESSON -> it.copy(lessonLeadMinutes = minutes)
        }
    }
    override suspend fun markPermissionRequested() = state.update { it.copy(permissionRequested = true) }
}

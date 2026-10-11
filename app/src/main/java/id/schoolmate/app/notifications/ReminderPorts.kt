package id.schoolmate.app.notifications

import java.time.Instant

/** Schedules wake-ups. Production: [AlarmReminderScheduler] (inexact AlarmManager alarms). */
interface ReminderScheduler {
    /** Replaces any alarm for the same key. */
    fun schedule(reminder: PlannedReminder)
    fun cancel(key: ReminderKey)

    /** One extra alarm that only re-runs the planner (used when more than MAX_ALARMS exist). */
    fun scheduleResync(at: Instant?)
}

/** Shows notifications. Production: [AndroidReminderNotifier]. */
interface ReminderNotifier {
    /** False when the permission is missing or the user turned notifications off. Never throws. */
    fun canPost(kind: id.schoolmate.app.domain.model.ReminderKind): Boolean

    /** @return true if the notification was handed to the system. Never throws. */
    fun post(reminder: PlannedReminder): Boolean

    /** Removes an already shown notification (e.g. the task was completed or deleted). */
    fun dismiss(key: ReminderKey)
}

/**
 * Remembers which alarms are currently registered and which notifications were already shown, so
 * re-syncs don't touch unchanged alarms and a reminder is never shown twice.
 */
interface ReminderRegistry {
    /** token → trigger epoch millis. */
    suspend fun scheduled(): Map<String, Long>
    suspend fun setScheduled(value: Map<String, Long>)

    suspend fun wasDelivered(token: String, triggerAtMillis: Long): Boolean
    suspend fun markDelivered(token: String, triggerAtMillis: Long, nowMillis: Long)

    /** Tokens with at least one delivered notification. */
    suspend fun deliveredTokens(): Set<String>
    suspend fun forgetDelivered(tokens: Set<String>)
}

package id.schoolmate.app.notifications

import id.schoolmate.app.domain.model.ReminderKind
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Stable identity of a reminder: one per entity. Re-scheduling the same entity replaces its alarm
 * and its notification instead of creating a duplicate.
 */
data class ReminderKey(val kind: ReminderKind, val entityId: Long) {
    init { require(entityId > 0) { "entityId must be positive" } }

    /** "task:12" – used in the registry and as alarm extra. */
    val token: String get() = "${kind.slug}:$entityId"

    /**
     * Notification id and PendingIntent request code: top 3 bits = kind, low 28 bits = id. Distinct
     * across kinds for every id below 2^28 (268 juta baris – far beyond a student's data).
     */
    val notificationId: Int get() = (kind.code shl 28) or (entityId and ID_MASK).toInt()

    companion object {
        private const val ID_MASK = 0x0FFF_FFFFL

        fun parse(token: String?): ReminderKey? {
            val (slug, id) = token?.split(':')?.takeIf { it.size == 2 } ?: return null
            val kind = ReminderKind.entries.firstOrNull { it.slug == slug } ?: return null
            return id.toLongOrNull()?.takeIf { it > 0 }?.let { ReminderKey(kind, it) }
        }
    }
}

/** What the notification is about; formatted into Indonesian text by the notifier. */
sealed interface ReminderContent {
    data class TaskDue(val title: String, val subjectName: String?, val dueDate: LocalDate, val dueTime: LocalTime?) : ReminderContent
    data class ExamSoon(val title: String, val subjectName: String?, val date: LocalDate, val startTime: LocalTime?) : ReminderContent
    data class LessonSoon(
        val subjectName: String,
        val start: LocalTime,
        val end: LocalTime,
        val room: String,
        val leadMinutes: Int,
    ) : ReminderContent
}

/** A reminder that should fire at [triggerAt] for an event happening at [eventAt]. */
data class PlannedReminder(
    val key: ReminderKey,
    val triggerAt: Instant,
    val eventAt: Instant,
    val content: ReminderContent,
)

/**
 * The set of alarms to keep. When more reminders exist than [ReminderPlanner.MAX_ALARMS],
 * [resyncAt] asks for a re-plan when the last scheduled one fires.
 */
data class ReminderPlan(val reminders: List<PlannedReminder>, val resyncAt: Instant?)

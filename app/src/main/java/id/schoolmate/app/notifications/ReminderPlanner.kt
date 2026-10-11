package id.schoolmate.app.notifications

import id.schoolmate.app.domain.model.Exam
import id.schoolmate.app.domain.model.NotificationPreferences
import id.schoolmate.app.domain.model.ReminderKind
import id.schoolmate.app.domain.model.ScheduleSlot
import id.schoolmate.app.domain.model.Task
import id.schoolmate.app.domain.model.TaskStatus
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Pure reminder math – no Android types, fully unit-tested. Everything is computed from the
 * device's *current* zone, so a re-plan after a zone change moves every alarm to the new local time.
 */
object ReminderPlanner {
    /** Deadline time used for tasks without a time ("akhir hari"), matching Task.isOverdue. */
    val TASK_END_OF_DAY: LocalTime = LocalTime.of(23, 59)

    /** Exams without a start time are assumed to start in the morning. */
    val EXAM_DEFAULT_START: LocalTime = LocalTime.of(7, 0)

    /** Task/exam reminders falling in 21:00–06:00 move to 19:00 the evening before (never later). */
    val QUIET_START: LocalTime = LocalTime.of(21, 0)
    val QUIET_END: LocalTime = LocalTime.of(6, 0)
    val EVENING: LocalTime = LocalTime.of(19, 0)

    /** Android allows ~500 alarms per app; stay far below it. */
    const val MAX_ALARMS = 100

    data class Input(
        val prefs: NotificationPreferences,
        val tasks: List<Task>,
        val exams: List<Exam>,
        val slots: List<ScheduleSlot>,
        val subjectNames: Map<Long, String>,
    )

    fun plan(now: Instant, zone: ZoneId, input: Input, limit: Int = MAX_ALARMS): ReminderPlan {
        val all = planAll(now, zone, input)
        if (all.size <= limit) return ReminderPlan(all, resyncAt = null)
        val kept = all.take(limit)
        return ReminderPlan(kept, resyncAt = kept.last().triggerAt)
    }

    /** Every upcoming reminder, sorted by trigger time (ties broken by key for determinism). */
    fun planAll(now: Instant, zone: ZoneId, input: Input): List<PlannedReminder> {
        val p = input.prefs
        val out = buildList {
            if (p.tasksActive) input.tasks.forEach { t -> task(t, p.taskLeadMinutes, now, zone, input.subjectNames)?.let(::add) }
            if (p.examsActive) input.exams.forEach { e -> exam(e, p.examLeadMinutes, now, zone, input.subjectNames)?.let(::add) }
            if (p.lessonsActive) input.slots.forEach { s -> lesson(s, p.lessonLeadMinutes, now, zone)?.let(::add) }
        }
        return out.sortedWith(compareBy<PlannedReminder> { it.triggerAt }.thenBy { it.key.kind.code }.thenBy { it.key.entityId })
    }

    fun task(task: Task, leadMinutes: Int, now: Instant, zone: ZoneId, subjects: Map<Long, String> = emptyMap()): PlannedReminder? {
        if (task.id <= 0 || task.status == TaskStatus.DONE) return null
        val event = ZonedDateTime.of(task.dueDate, task.dueTime ?: TASK_END_OF_DAY, zone)
        val trigger = avoidQuietHours(event.minusMinutes(leadMinutes.toLong()), now)
        if (!event.toInstant().isAfter(now) || !trigger.isAfter(now)) return null
        return PlannedReminder(
            ReminderKey(ReminderKind.TASK, task.id), trigger, event.toInstant(),
            ReminderContent.TaskDue(task.title, task.subjectId?.let(subjects::get), task.dueDate, task.dueTime),
        )
    }

    fun exam(exam: Exam, leadMinutes: Int, now: Instant, zone: ZoneId, subjects: Map<Long, String> = emptyMap()): PlannedReminder? {
        if (exam.id <= 0) return null
        val event = ZonedDateTime.of(exam.date, exam.startTime ?: EXAM_DEFAULT_START, zone)
        val trigger = avoidQuietHours(event.minusMinutes(leadMinutes.toLong()), now)
        if (!event.toInstant().isAfter(now) || !trigger.isAfter(now)) return null
        return PlannedReminder(
            ReminderKey(ReminderKind.EXAM, exam.id), trigger, event.toInstant(),
            ReminderContent.ExamSoon(exam.title, exam.subjectId?.let(subjects::get), exam.date, exam.startTime),
        )
    }

    /** Next weekly occurrence whose reminder is still in the future (this week or next). */
    fun lesson(slot: ScheduleSlot, leadMinutes: Int, now: Instant, zone: ZoneId): PlannedReminder? {
        val entry = slot.entry
        if (entry.id <= 0) return null
        val today = now.atZone(zone).toLocalDate()
        for (offset in 0L..7L) {
            val date = today.plusDays(offset)
            if (date.dayOfWeek != entry.dayOfWeek) continue
            val start = ZonedDateTime.of(date, entry.startTime, zone)
            val trigger = start.minusMinutes(leadMinutes.toLong()).toInstant()
            if (trigger.isAfter(now)) {
                return PlannedReminder(
                    ReminderKey(ReminderKind.LESSON, entry.id), trigger, start.toInstant(),
                    ReminderContent.LessonSoon(slot.subject.name, entry.startTime, entry.endTime, entry.room, leadMinutes),
                )
            }
        }
        return null
    }

    /**
     * Moves a trigger inside quiet hours to 19:00 of the evening before, unless that moment has
     * already passed – then the original time is kept so the reminder is not lost.
     */
    fun avoidQuietHours(trigger: ZonedDateTime, now: Instant): Instant {
        val t = trigger.toLocalTime()
        val moved = when {
            !t.isBefore(QUIET_START) -> trigger.with(EVENING)
            t.isBefore(QUIET_END) -> trigger.minusDays(1).with(EVENING)
            else -> return trigger.toInstant()
        }.toInstant()
        return if (moved.isAfter(now)) moved else trigger.toInstant()
    }
}

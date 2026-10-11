package id.schoolmate.app.ui.model

import id.schoolmate.app.domain.model.DeadlineUrgency
import id.schoolmate.app.domain.model.Exam
import id.schoolmate.app.domain.model.ScheduleSlot
import id.schoolmate.app.domain.model.Subject
import id.schoolmate.app.domain.model.Task
import id.schoolmate.app.domain.model.urgency
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

data class TaskItem(
    val task: Task,
    val subject: Subject?,
    val urgency: DeadlineUrgency,
)

data class ExamItem(
    val exam: Exam,
    val subject: Subject?,
    /** Negative for past exams. */
    val daysUntil: Long,
)

data class LessonItem(
    val slot: ScheduleSlot,
    val isOngoing: Boolean,
    val isFinished: Boolean,
)

fun Task.toItem(subjects: Map<Long, Subject>, now: LocalDateTime) =
    TaskItem(task = this, subject = subjectId?.let(subjects::get), urgency = urgency(now))

fun Exam.toItem(subjects: Map<Long, Subject>, today: LocalDate) =
    ExamItem(exam = this, subject = subjectId?.let(subjects::get), daysUntil = ChronoUnit.DAYS.between(today, date))

fun ScheduleSlot.toLessonItem(now: LocalDateTime, isToday: Boolean): LessonItem {
    val t = now.toLocalTime()
    return LessonItem(
        slot = this,
        isOngoing = isToday && !t.isBefore(entry.startTime) && t.isBefore(entry.endTime),
        isFinished = isToday && !t.isBefore(entry.endTime),
    )
}

/** Form-level error for a single input field. */
enum class FieldError { REQUIRED, TOO_LONG, INVALID, NOT_FOUND }

/** One-off events from editors/detail screens. */
sealed interface EditorEvent {
    data object Saved : EditorEvent
    data object Deleted : EditorEvent
}

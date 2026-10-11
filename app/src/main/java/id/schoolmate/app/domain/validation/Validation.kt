package id.schoolmate.app.domain.validation

import id.schoolmate.app.domain.model.Exam
import id.schoolmate.app.domain.model.ScheduleEntry
import id.schoolmate.app.domain.model.StudySession
import id.schoolmate.app.domain.model.Task

/** Shared limits so UI, repositories, and tests agree on what valid data is. */
object Limits {
    const val TITLE_MAX = 100
    const val DESCRIPTION_MAX = 1_000
    const val SHORT_TEXT_MAX = 50
    const val NOTE_MAX = 500
    const val SCHEDULE_MIN_MINUTES = 5
    const val EXAM_DURATION_MAX_MINUTES = 600
    const val STUDY_SESSION_MIN_MINUTES = 1
    /** 12 hours; anything longer is almost certainly a timer left running. */
    const val STUDY_SESSION_MAX_MINUTES = 720
}

enum class ValidationError {
    TITLE_EMPTY,
    TITLE_TOO_LONG,
    DESCRIPTION_TOO_LONG,
    TEXT_TOO_LONG,
    NOTE_TOO_LONG,
    INVALID_SUBJECT,
    /** A referenced subject or task does not exist (foreign key rejected). */
    INVALID_REFERENCE,
    INVALID_TIME_RANGE,
    SCHEDULE_TOO_SHORT,
    SCHEDULE_OVERLAP,
    INVALID_DURATION,
    COMPLETED_AT_MISMATCH,
}

/** Thrown inside a failed [Result] when input is rejected; carries every problem found. */
class ValidationException(val errors: Set<ValidationError>) :
    IllegalArgumentException("Invalid data: ${errors.joinToString()}")

private fun MutableSet<ValidationError>.title(title: String) {
    val t = title.trim()
    if (t.isEmpty()) add(ValidationError.TITLE_EMPTY)
    if (t.length > Limits.TITLE_MAX) add(ValidationError.TITLE_TOO_LONG)
}

private fun MutableSet<ValidationError>.note(note: String) {
    if (note.trim().length > Limits.NOTE_MAX) add(ValidationError.NOTE_TOO_LONG)
}

fun validateScheduleEntry(entry: ScheduleEntry): Set<ValidationError> = buildSet {
    if (entry.subjectId <= 0) add(ValidationError.INVALID_SUBJECT)
    if (!entry.endTime.isAfter(entry.startTime)) {
        add(ValidationError.INVALID_TIME_RANGE)
    } else if (entry.durationMinutes < Limits.SCHEDULE_MIN_MINUTES) {
        add(ValidationError.SCHEDULE_TOO_SHORT)
    }
    if (entry.room.trim().length > Limits.SHORT_TEXT_MAX) add(ValidationError.TEXT_TOO_LONG)
    note(entry.note)
}

fun validateTask(task: Task): Set<ValidationError> = buildSet {
    title(task.title)
    if (task.description.trim().length > Limits.DESCRIPTION_MAX) add(ValidationError.DESCRIPTION_TOO_LONG)
    if (task.subjectId != null && task.subjectId <= 0) add(ValidationError.INVALID_SUBJECT)
    if ((task.status.isActive) != (task.completedAt == null)) add(ValidationError.COMPLETED_AT_MISMATCH)
}

fun validateExam(exam: Exam): Set<ValidationError> = buildSet {
    title(exam.title)
    if (exam.subjectId != null && exam.subjectId <= 0) add(ValidationError.INVALID_SUBJECT)
    exam.durationMinutes?.let { if (it !in 1..Limits.EXAM_DURATION_MAX_MINUTES) add(ValidationError.INVALID_DURATION) }
    if (exam.location.trim().length > Limits.SHORT_TEXT_MAX) add(ValidationError.TEXT_TOO_LONG)
    note(exam.note)
}

fun validateStudySession(session: StudySession): Set<ValidationError> = buildSet {
    if (session.durationMinutes !in Limits.STUDY_SESSION_MIN_MINUTES..Limits.STUDY_SESSION_MAX_MINUTES) {
        add(ValidationError.INVALID_DURATION)
    }
    if (session.subjectId != null && session.subjectId <= 0) add(ValidationError.INVALID_SUBJECT)
    note(session.note)
}

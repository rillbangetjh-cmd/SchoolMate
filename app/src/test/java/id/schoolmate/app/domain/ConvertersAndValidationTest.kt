package id.schoolmate.app.domain

import id.schoolmate.app.data.local.converter.Converters
import id.schoolmate.app.domain.model.Exam
import id.schoolmate.app.domain.model.Priority
import id.schoolmate.app.domain.model.ScheduleEntry
import id.schoolmate.app.domain.model.StudySession
import id.schoolmate.app.domain.model.Task
import id.schoolmate.app.domain.model.TaskStatus
import id.schoolmate.app.domain.validation.Limits
import id.schoolmate.app.domain.validation.ValidationError
import id.schoolmate.app.domain.validation.validateExam
import id.schoolmate.app.domain.validation.validateScheduleEntry
import id.schoolmate.app.domain.validation.validateStudySession
import id.schoolmate.app.domain.validation.validateTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class ConvertersAndValidationTest {
    private val c = Converters()

    @Test fun convertersRoundTripAndUseSortableFormats() {
        val date = LocalDate.of(2026, 10, 12)
        assertEquals(date, c.epochDayToLocalDate(c.localDateToEpochDay(date)))
        assertTrue(c.localDateToEpochDay(date)!! < c.localDateToEpochDay(date.plusDays(1))!!)

        assertEquals(0, c.localTimeToMinuteOfDay(LocalTime.MIDNIGHT))
        assertEquals(1439, c.localTimeToMinuteOfDay(LocalTime.of(23, 59, 59)))
        assertEquals(LocalTime.of(7, 30), c.minuteOfDayToLocalTime(c.localTimeToMinuteOfDay(LocalTime.of(7, 30))))

        val instant = Instant.ofEpochMilli(1_791_000_000_123)
        assertEquals(instant, c.epochMilliToInstant(c.instantToEpochMilli(instant)))

        assertEquals(1, c.dayOfWeekToIso(DayOfWeek.MONDAY))
        assertEquals(DayOfWeek.SUNDAY, c.isoToDayOfWeek(7))

        assertEquals(3, c.priorityToLevel(Priority.HIGH))
        assertEquals(Priority.LOW, c.levelToPriority(1))
        assertEquals(Priority.MEDIUM, c.levelToPriority(99)) // unknown values degrade safely

        assertEquals("IN_PROGRESS", c.taskStatusToName(TaskStatus.IN_PROGRESS))
        assertEquals(TaskStatus.DONE, c.nameToTaskStatus("DONE"))
        assertEquals(TaskStatus.TODO, c.nameToTaskStatus("garbage"))
        assertEquals(null, c.localDateToEpochDay(null))
    }

    @Test fun priorityLevelsAreStableAndOrdered() {
        assertEquals(listOf(1, 2, 3), Priority.entries.map { it.level })
        assertFalse(TaskStatus.DONE.isActive)
        assertTrue(TaskStatus.IN_PROGRESS.isActive)
    }

    @Test fun scheduleValidation() {
        val base = ScheduleEntry(subjectId = 1, dayOfWeek = DayOfWeek.MONDAY, startTime = LocalTime.of(7, 0), endTime = LocalTime.of(8, 0))
        assertTrue(validateScheduleEntry(base).isEmpty())
        assertEquals(setOf(ValidationError.INVALID_TIME_RANGE), validateScheduleEntry(base.copy(endTime = LocalTime.of(7, 0))))
        assertEquals(setOf(ValidationError.SCHEDULE_TOO_SHORT), validateScheduleEntry(base.copy(endTime = LocalTime.of(7, 4))))
        assertEquals(setOf(ValidationError.INVALID_SUBJECT), validateScheduleEntry(base.copy(subjectId = 0)))
        assertEquals(setOf(ValidationError.TEXT_TOO_LONG), validateScheduleEntry(base.copy(room = "x".repeat(Limits.SHORT_TEXT_MAX + 1))))
    }

    @Test fun taskValidation() {
        val base = Task(title = "PR", dueDate = LocalDate.of(2026, 10, 12))
        assertTrue(validateTask(base).isEmpty())
        assertEquals(setOf(ValidationError.TITLE_TOO_LONG), validateTask(base.copy(title = "x".repeat(Limits.TITLE_MAX + 1))))
        assertEquals(setOf(ValidationError.DESCRIPTION_TOO_LONG), validateTask(base.copy(description = "x".repeat(Limits.DESCRIPTION_MAX + 1))))
        assertEquals(setOf(ValidationError.COMPLETED_AT_MISMATCH), validateTask(base.copy(status = TaskStatus.DONE)))
        assertEquals(setOf(ValidationError.COMPLETED_AT_MISMATCH), validateTask(base.copy(completedAt = Instant.EPOCH)))
        assertTrue(validateTask(base.copy(status = TaskStatus.DONE, completedAt = Instant.EPOCH)).isEmpty())
    }

    @Test fun taskOverdueRules() {
        val task = Task(title = "PR", dueDate = LocalDate.of(2026, 10, 12), dueTime = LocalTime.of(12, 0))
        val day = LocalDate.of(2026, 10, 12)
        assertFalse(task.isOverdue(day, LocalTime.of(11, 59)))
        assertTrue(task.isOverdue(day, LocalTime.of(12, 1)))
        assertTrue(task.isOverdue(day.plusDays(1), LocalTime.MIN))
        assertFalse(task.copy(dueTime = null).isOverdue(day, LocalTime.of(23, 59)))
        assertFalse(task.copy(status = TaskStatus.DONE, completedAt = Instant.EPOCH).isOverdue(day.plusDays(5), LocalTime.MIN))
    }

    @Test fun examAndSessionValidation() {
        val exam = Exam(title = "UTS", date = LocalDate.of(2026, 10, 12))
        assertTrue(validateExam(exam).isEmpty())
        assertEquals(setOf(ValidationError.INVALID_DURATION), validateExam(exam.copy(durationMinutes = Limits.EXAM_DURATION_MAX_MINUTES + 1)))
        assertEquals(setOf(ValidationError.TITLE_EMPTY), validateExam(exam.copy(title = "")))

        val session = StudySession(startedAt = Instant.EPOCH, date = LocalDate.of(2026, 10, 12), durationMinutes = 25)
        assertTrue(validateStudySession(session).isEmpty())
        assertEquals(setOf(ValidationError.INVALID_DURATION), validateStudySession(session.copy(durationMinutes = -5)))
        assertEquals(setOf(ValidationError.NOTE_TOO_LONG), validateStudySession(session.copy(note = "x".repeat(Limits.NOTE_MAX + 1))))
    }
}

package id.schoolmate.app.ui

import id.schoolmate.app.domain.model.Exam
import id.schoolmate.app.domain.model.ScheduleEntry
import id.schoolmate.app.domain.model.ScheduleSlot
import id.schoolmate.app.domain.model.Subject
import id.schoolmate.app.domain.validation.ValidationError
import id.schoolmate.app.ui.model.FieldError
import id.schoolmate.app.ui.screens.exams.parseExamDuration
import id.schoolmate.app.ui.screens.exams.split
import id.schoolmate.app.ui.screens.exams.toExamFormErrors
import id.schoolmate.app.ui.screens.schedule.conflictsWith
import id.schoolmate.app.ui.screens.schedule.groupByDay
import id.schoolmate.app.ui.screens.schedule.suggestNextSlot
import id.schoolmate.app.ui.screens.studylog.parseSessionDuration
import id.schoolmate.app.ui.screens.studylog.weekStartOf
import id.schoolmate.app.ui.screens.tasks.toTaskFormErrors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** Pure form validation, filtering and sorting helpers used by the ViewModels. */
class FormLogicTest {
    private val math = Subject(1, "Matematika", colorArgb = 0)
    private fun slot(id: Long, day: DayOfWeek, start: String, end: String) =
        ScheduleSlot(ScheduleEntry(id, 1, day, LocalTime.parse(start), LocalTime.parse(end)), math)

    @Test fun taskValidationErrorsMapToFields() {
        val e = setOf(ValidationError.TITLE_EMPTY, ValidationError.DESCRIPTION_TOO_LONG, ValidationError.INVALID_REFERENCE).toTaskFormErrors()
        assertEquals(FieldError.REQUIRED, e.title)
        assertEquals(FieldError.TOO_LONG, e.description)
        assertEquals(FieldError.NOT_FOUND, e.subject)
        assertEquals(FieldError.TOO_LONG, setOf(ValidationError.TITLE_TOO_LONG).toTaskFormErrors().title)
        assertTrue(!emptySet<ValidationError>().toTaskFormErrors().hasAny)
    }

    @Test fun examDurationParsing() {
        assertEquals(null, parseExamDuration("  ").getOrThrow())
        assertEquals(90, parseExamDuration("90").getOrThrow())
        assertTrue(parseExamDuration("0").isFailure)
        assertTrue(parseExamDuration("601").isFailure)
        assertTrue(parseExamDuration("abc").isFailure)
        assertEquals(FieldError.INVALID, setOf(ValidationError.INVALID_DURATION).toExamFormErrors().duration)
    }

    @Test fun studySessionDurationParsing() {
        assertEquals(30, parseSessionDuration("30"))
        assertNull(parseSessionDuration("0"))
        assertNull(parseSessionDuration("721"))
        assertNull(parseSessionDuration(""))
    }

    @Test fun scheduleConflictsAreHalfOpenAndExcludeSelf() {
        val week = listOf(slot(1, DayOfWeek.MONDAY, "07:00", "08:30"), slot(2, DayOfWeek.MONDAY, "08:30", "10:00"))
        val t: (String) -> LocalTime = { LocalTime.parse(it) }
        assertEquals(listOf(1L), week.conflictsWith(DayOfWeek.MONDAY, t("08:00"), t("08:30"), 0).map { it.entry.id })
        assertEquals(listOf(1L, 2L), week.conflictsWith(DayOfWeek.MONDAY, t("08:00"), t("09:00"), 0).map { it.entry.id })
        assertTrue(week.conflictsWith(DayOfWeek.MONDAY, t("10:00"), t("11:00"), 0).isEmpty()) // back-to-back ok
        assertTrue(week.conflictsWith(DayOfWeek.TUESDAY, t("07:00"), t("08:00"), 0).isEmpty())
        assertEquals(listOf(2L), week.conflictsWith(DayOfWeek.MONDAY, t("07:00"), t("09:00"), excludeId = 1).map { it.entry.id })
    }

    @Test fun nextSlotSuggestionFollowsLastLesson() {
        val week = listOf(slot(1, DayOfWeek.MONDAY, "07:00", "08:30"), slot(2, DayOfWeek.MONDAY, "10:00", "11:30"))
        assertEquals(LocalTime.of(11, 30) to LocalTime.of(13, 0), suggestNextSlot(week, DayOfWeek.MONDAY))
        assertEquals(LocalTime.of(7, 0) to LocalTime.of(8, 30), suggestNextSlot(week, DayOfWeek.FRIDAY))
        val late = listOf(slot(3, DayOfWeek.MONDAY, "22:00", "23:30"))
        val (s, e) = suggestNextSlot(late, DayOfWeek.MONDAY)
        assertEquals(LocalTime.of(23, 30), s)
        assertEquals(LocalTime.of(23, 59), e)
    }

    @Test fun weekGroupingCoversAllDaysInOrder() {
        val grouped = listOf(
            slot(2, DayOfWeek.MONDAY, "10:00", "11:00"),
            slot(1, DayOfWeek.MONDAY, "07:00", "08:00"),
            slot(3, DayOfWeek.SATURDAY, "07:00", "08:00"),
        ).groupByDay()
        assertEquals(DayOfWeek.entries.toList(), grouped.keys.toList())
        assertEquals(listOf(1L, 2L), grouped.getValue(DayOfWeek.MONDAY).map { it.entry.id })
        assertTrue(grouped.getValue(DayOfWeek.SUNDAY).isEmpty())
    }

    @Test fun examsSplitIntoUpcomingAndPast() {
        val today = LocalDate.of(2026, 10, 12)
        fun exam(id: Long, d: LocalDate, t: String? = null) = Exam(id, "E$id", date = d, startTime = t?.let { LocalTime.parse(it) })
        val (up, past) = listOf(
            exam(1, today.plusDays(5)), exam(2, today, "10:00"), exam(3, today, "07:30"),
            exam(4, today.minusDays(1)), exam(5, today.minusDays(10)),
        ).split(today)
        assertEquals(listOf(3L, 2L, 1L), up.map { it.id })
        assertEquals(listOf(4L, 5L), past.map { it.id })
    }

    @Test fun weeksStartOnMonday() {
        assertEquals(LocalDate.of(2026, 10, 12), weekStartOf(LocalDate.of(2026, 10, 18))) // Sunday
        assertEquals(LocalDate.of(2026, 10, 12), weekStartOf(LocalDate.of(2026, 10, 12)))
    }
}

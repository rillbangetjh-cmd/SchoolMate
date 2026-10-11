package id.schoolmate.app.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import id.schoolmate.app.data.local.SchoolMateDatabase
import id.schoolmate.app.data.local.inMemoryDatabase
import id.schoolmate.app.data.local.insertSubject
import id.schoolmate.app.domain.model.Exam
import id.schoolmate.app.domain.model.ScheduleEntry
import id.schoolmate.app.domain.model.Task
import id.schoolmate.app.domain.model.TaskStatus
import id.schoolmate.app.domain.usecase.ObserveDashboardSummaryUseCase
import id.schoolmate.app.domain.validation.ValidationError
import id.schoolmate.app.domain.validation.ValidationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Repository behaviour on top of a real (in-memory) Room database. */
@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private val jakarta = ZoneId.of("Asia/Jakarta")
    // Monday 12 Oct 2026, 09:00 WIB
    private val clock = Clock.fixed(Instant.parse("2026-10-12T02:00:00Z"), jakarta)
    private val today = LocalDate.of(2026, 10, 12)

    private lateinit var db: SchoolMateDatabase
    private lateinit var schedule: OfflineScheduleRepository
    private lateinit var tasks: OfflineTaskRepository
    private lateinit var exams: OfflineExamRepository
    private lateinit var sessions: OfflineStudySessionRepository

    @Before fun setUp() {
        db = inMemoryDatabase()
        schedule = OfflineScheduleRepository(db.scheduleDao(), clock)
        tasks = OfflineTaskRepository(db.taskDao(), clock)
        exams = OfflineExamRepository(db.examDao(), clock)
        sessions = OfflineStudySessionRepository(db.studySessionDao(), clock)
    }

    @After fun tearDown() = db.close()

    private fun Result<*>.validationErrors(): Set<ValidationError> =
        (exceptionOrNull() as? ValidationException)?.errors.orEmpty()

    // --- schedule ---

    @Test fun scheduleRejectsOverlapButAllowsBackToBack() = runTest {
        val math = db.insertSubject("Matematika")
        val t = { s: String -> LocalTime.parse(s) }
        val first = schedule.addEntry(ScheduleEntry(subjectId = math, dayOfWeek = DayOfWeek.MONDAY, startTime = t("07:00"), endTime = t("08:30")))
        assertTrue(first.isSuccess)

        val overlap = schedule.addEntry(ScheduleEntry(subjectId = math, dayOfWeek = DayOfWeek.MONDAY, startTime = t("08:00"), endTime = t("09:00")))
        assertEquals(setOf(ValidationError.SCHEDULE_OVERLAP), overlap.validationErrors())

        val next = schedule.addEntry(ScheduleEntry(subjectId = math, dayOfWeek = DayOfWeek.MONDAY, startTime = t("08:30"), endTime = t("10:00")))
        assertTrue(next.isSuccess)

        // Editing a slot must not count itself as an overlap.
        val moved = ScheduleEntry(id = first.getOrThrow(), subjectId = math, dayOfWeek = DayOfWeek.MONDAY, startTime = t("06:45"), endTime = t("08:30"))
        assertTrue(schedule.updateEntry(moved).isSuccess)
        assertEquals(listOf("06:45", "08:30"), schedule.observeDay(DayOfWeek.MONDAY).first().map { it.entry.startTime.toString() })
    }

    @Test fun scheduleValidatesRangeAndSubject() = runTest {
        val t = { s: String -> LocalTime.parse(s) }
        val reversed = schedule.addEntry(ScheduleEntry(subjectId = 1, dayOfWeek = DayOfWeek.MONDAY, startTime = t("09:00"), endTime = t("08:00")))
        assertTrue(ValidationError.INVALID_TIME_RANGE in reversed.validationErrors())

        val unknownSubject = schedule.addEntry(ScheduleEntry(subjectId = 999, dayOfWeek = DayOfWeek.MONDAY, startTime = t("07:00"), endTime = t("08:00")))
        assertEquals(setOf(ValidationError.INVALID_REFERENCE), unknownSubject.validationErrors())

        val missing = schedule.updateEntry(ScheduleEntry(id = 42, subjectId = 1, dayOfWeek = DayOfWeek.MONDAY, startTime = t("07:00"), endTime = t("08:00")))
        assertTrue(missing.exceptionOrNull() is RecordNotFoundException)
    }

    // --- tasks ---

    @Test fun taskCompletionTimestampFollowsStatus() = runTest {
        val id = tasks.addTask(Task(title = "  Ringkasan sejarah  ", dueDate = today)).getOrThrow()
        assertEquals("Ringkasan sejarah", tasks.observeTask(id).first()!!.title)
        assertNull(tasks.observeTask(id).first()!!.completedAt)

        tasks.setStatus(id, TaskStatus.DONE).getOrThrow()
        val done = tasks.observeTask(id).first()!!
        assertEquals(TaskStatus.DONE, done.status)
        assertEquals(clock.instant(), done.completedAt)

        // Editing a finished task keeps its original completion time.
        tasks.updateTask(done.copy(title = "Ringkasan sejarah bab 2", completedAt = null)).getOrThrow()
        assertEquals(clock.instant(), tasks.observeTask(id).first()!!.completedAt)

        tasks.setStatus(id, TaskStatus.TODO).getOrThrow()
        assertNull(tasks.observeTask(id).first()!!.completedAt)
    }

    @Test fun taskValidation() = runTest {
        assertEquals(setOf(ValidationError.TITLE_EMPTY), tasks.addTask(Task(title = "  ", dueDate = today)).validationErrors())
        assertEquals(
            setOf(ValidationError.INVALID_REFERENCE),
            tasks.addTask(Task(title = "PR", dueDate = today, subjectId = 77)).validationErrors(),
        )
        assertTrue(tasks.setStatus(123, TaskStatus.DONE).exceptionOrNull() is RecordNotFoundException)
        assertEquals(0, tasks.observeActiveCount().first())
    }

    // --- exams ---

    @Test fun examValidationAndUpcoming() = runTest {
        assertEquals(
            setOf(ValidationError.INVALID_DURATION),
            exams.addExam(Exam(title = "UTS", date = today, durationMinutes = 0)).validationErrors(),
        )
        exams.addExam(Exam(title = "UTS Fisika", date = today.plusDays(3), startTime = LocalTime.of(7, 30, 45))).getOrThrow()
        val upcoming = exams.observeUpcoming(today).first().single()
        assertEquals(LocalTime.of(7, 30), upcoming.startTime) // seconds are not stored
    }

    // --- study sessions ---

    @Test fun sessionDateUsesLocalZone() = runTest {
        // 23:30 WIB on 12 Oct is still 16:30 UTC on 12 Oct; 00:30 WIB on 13 Oct is 17:30 UTC on 12 Oct.
        sessions.logSession(Instant.parse("2026-10-12T16:30:00Z"), durationMinutes = 25).getOrThrow()
        sessions.logSession(Instant.parse("2026-10-12T17:30:00Z"), durationMinutes = 50).getOrThrow()
        assertEquals(25, sessions.observeTotalMinutes(today, today).first())
        assertEquals(50, sessions.observeTotalMinutes(today.plusDays(1), today.plusDays(1)).first())
    }

    @Test fun sessionDurationIsValidated() = runTest {
        assertEquals(setOf(ValidationError.INVALID_DURATION), sessions.logSession(clock.instant(), 0).validationErrors())
        assertEquals(setOf(ValidationError.INVALID_DURATION), sessions.logSession(clock.instant(), 721).validationErrors())
        assertEquals(setOf(ValidationError.INVALID_REFERENCE), sessions.logSession(clock.instant(), 25, taskId = 9).validationErrors())
        assertTrue(sessions.logSession(clock.instant(), 720).isSuccess)
    }

    // --- dashboard ---

    @Test fun dashboardSummaryAggregatesAllSources() = runTest {
        val math = db.insertSubject("Matematika")
        val t = { s: String -> LocalTime.parse(s) }
        schedule.addEntry(ScheduleEntry(subjectId = math, dayOfWeek = DayOfWeek.MONDAY, startTime = t("07:00"), endTime = t("08:00"))).getOrThrow()
        schedule.addEntry(ScheduleEntry(subjectId = math, dayOfWeek = DayOfWeek.TUESDAY, startTime = t("07:00"), endTime = t("08:00"))).getOrThrow()
        tasks.addTask(Task(title = "Terlambat", dueDate = today.minusDays(1))).getOrThrow()
        tasks.addTask(Task(title = "Besok", dueDate = today.plusDays(1))).getOrThrow()
        tasks.addTask(Task(title = "Selesai", dueDate = today, status = TaskStatus.DONE)).getOrThrow()
        exams.addExam(Exam(title = "Kuis", date = today.plusDays(7))).getOrThrow()
        exams.addExam(Exam(title = "UAS", date = today.plusDays(8))).getOrThrow()
        sessions.logSession(clock.instant(), 40).getOrThrow()
        // Previous week (Sunday 11 Oct) must not count.
        sessions.logSession(Instant.parse("2026-10-11T02:00:00Z"), 30).getOrThrow()

        val summary = ObserveDashboardSummaryUseCase(schedule, tasks, exams, sessions, clock)().first()
        assertEquals(1, summary.lessonsToday)
        assertEquals(2, summary.activeTasks)
        assertEquals(1, summary.overdueTasks)
        assertEquals(1, summary.examsNext7Days)
        assertEquals(40, summary.studyMinutesThisWeek)
        assertNotNull(summary)
    }
}

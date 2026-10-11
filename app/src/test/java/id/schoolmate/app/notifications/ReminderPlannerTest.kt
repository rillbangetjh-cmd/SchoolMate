package id.schoolmate.app.notifications

import id.schoolmate.app.domain.model.Exam
import id.schoolmate.app.domain.model.NotificationPreferences
import id.schoolmate.app.domain.model.ReminderKind
import id.schoolmate.app.domain.model.ScheduleEntry
import id.schoolmate.app.domain.model.ScheduleSlot
import id.schoolmate.app.domain.model.Subject
import id.schoolmate.app.domain.model.Task
import id.schoolmate.app.domain.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Planner math. "Now" is Senin 12 Oktober 2026, 09:00 WIB unless stated otherwise. */
class ReminderPlannerTest {
    private val wib = ZoneId.of("Asia/Jakarta")
    private val now = at(2026, 10, 12, 9, 0)
    private val math = Subject(id = 1, name = "Matematika", colorArgb = 0)

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int, zone: ZoneId = wib): Instant =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant()

    private fun local(i: Instant, zone: ZoneId = wib): LocalDateTime = LocalDateTime.ofInstant(i, zone)

    private fun task(id: Long, date: LocalDate, time: LocalTime? = null, status: TaskStatus = TaskStatus.TODO) =
        Task(id = id, title = "Tugas $id", dueDate = date, dueTime = time, status = status, subjectId = 1)

    private fun slot(id: Long, day: DayOfWeek, start: String, end: String = "23:00") =
        ScheduleSlot(ScheduleEntry(id = id, subjectId = 1, dayOfWeek = day, startTime = LocalTime.parse(start), endTime = LocalTime.parse(end)), math)

    @Test fun timedTaskFiresLeadBeforeDeadline() {
        val r = ReminderPlanner.task(task(1, LocalDate.of(2026, 10, 13), LocalTime.of(14, 0)), 60, now, wib, mapOf(1L to "Matematika"))!!
        assertEquals(LocalDateTime.of(2026, 10, 13, 13, 0), local(r.triggerAt))
        assertEquals(LocalDateTime.of(2026, 10, 13, 14, 0), local(r.eventAt))
        assertEquals(ReminderContent.TaskDue("Tugas 1", "Matematika", LocalDate.of(2026, 10, 13), LocalTime.of(14, 0)), r.content)
    }

    @Test fun dateOnlyTaskUsesEndOfDayAndAvoidsQuietHours() {
        // 23:59 − 1 jam = 22:59 → quiet hours → 19:00 the same evening.
        val oneHour = ReminderPlanner.task(task(1, LocalDate.of(2026, 10, 13)), 60, now, wib)!!
        assertEquals(LocalDateTime.of(2026, 10, 13, 19, 0), local(oneHour.triggerAt))
        assertEquals(LocalDateTime.of(2026, 10, 13, 23, 59), local(oneHour.eventAt))
        // 23:59 − 1 hari = 12 Okt 23:59 → 12 Okt 19:00 (still after now).
        val oneDay = ReminderPlanner.task(task(1, LocalDate.of(2026, 10, 13)), 1_440, now, wib)!!
        assertEquals(LocalDateTime.of(2026, 10, 12, 19, 0), local(oneDay.triggerAt))
    }

    @Test fun earlyMorningTriggerMovesToPreviousEvening() {
        val r = ReminderPlanner.task(task(1, LocalDate.of(2026, 10, 14), LocalTime.of(6, 30)), 60, now, wib)!!
        assertEquals(LocalDateTime.of(2026, 10, 13, 19, 0), local(r.triggerAt))
        // Daytime triggers are untouched.
        val day = ReminderPlanner.task(task(2, LocalDate.of(2026, 10, 14), LocalTime.of(7, 0)), 1_440, now, wib)!!
        assertEquals(LocalDateTime.of(2026, 10, 13, 7, 0), local(day.triggerAt))
    }

    @Test fun quietHoursNeverDropAReminder() {
        val evening = at(2026, 10, 12, 20, 0)
        val r = ReminderPlanner.task(task(1, LocalDate.of(2026, 10, 12), LocalTime.of(23, 30)), 60, evening, wib)!!
        assertEquals("19:00 already passed → keep 22:30", LocalDateTime.of(2026, 10, 12, 22, 30), local(r.triggerAt))
    }

    @Test fun doneOverdueOrTooLateTasksGetNoReminder() {
        assertNull(ReminderPlanner.task(task(1, LocalDate.of(2026, 10, 13), status = TaskStatus.DONE), 60, now, wib))
        assertNull("overdue", ReminderPlanner.task(task(2, LocalDate.of(2026, 10, 11)), 60, now, wib))
        assertNull("deadline passed earlier today", ReminderPlanner.task(task(3, LocalDate.of(2026, 10, 12), LocalTime.of(8, 0)), 60, now, wib))
        assertNull("reminder time already passed", ReminderPlanner.task(task(4, LocalDate.of(2026, 10, 12), LocalTime.of(9, 30)), 60, now, wib))
        // In-progress tasks still remind.
        assertTrue(ReminderPlanner.task(task(5, LocalDate.of(2026, 10, 14), status = TaskStatus.IN_PROGRESS), 60, now, wib) != null)
    }

    @Test fun examRemindsBeforeStartAndSkipsPastExams() {
        val exam = Exam(id = 7, title = "PTS", subjectId = 1, date = LocalDate.of(2026, 10, 15), startTime = LocalTime.of(10, 0))
        val r = ReminderPlanner.exam(exam, 1_440, now, wib, mapOf(1L to "Matematika"))!!
        assertEquals(LocalDateTime.of(2026, 10, 14, 10, 0), local(r.triggerAt))
        assertEquals(ReminderContent.ExamSoon("PTS", "Matematika", exam.date, exam.startTime), r.content)

        val noTime = ReminderPlanner.exam(exam.copy(startTime = null), 60, now, wib)!!
        assertEquals("no start time → 07:00 − 1 jam", LocalDateTime.of(2026, 10, 15, 6, 0), local(noTime.triggerAt))

        assertNull("past", ReminderPlanner.exam(exam.copy(date = LocalDate.of(2026, 10, 10)), 60, now, wib))
        assertNull("starts today 09:30, 1 day lead already passed", ReminderPlanner.exam(exam.copy(date = LocalDate.of(2026, 10, 12), startTime = LocalTime.of(9, 30)), 1_440, now, wib))
    }

    @Test fun lessonUsesNextWeeklyOccurrence() {
        // Monday 10:00 → today 09:50.
        assertEquals(LocalDateTime.of(2026, 10, 12, 9, 50), local(ReminderPlanner.lesson(slot(1, DayOfWeek.MONDAY, "10:00"), 10, now, wib)!!.triggerAt))
        // Monday 07:00 is over → next Monday.
        assertEquals(LocalDateTime.of(2026, 10, 19, 6, 50), local(ReminderPlanner.lesson(slot(2, DayOfWeek.MONDAY, "07:00"), 10, now, wib)!!.triggerAt))
        // Monday 09:05 has not started but its 10-minute reminder (08:55) passed → next week.
        assertEquals(LocalDateTime.of(2026, 10, 19, 8, 55), local(ReminderPlanner.lesson(slot(3, DayOfWeek.MONDAY, "09:05"), 10, now, wib)!!.triggerAt))
        // Sunday → end of this week.
        assertEquals(LocalDateTime.of(2026, 10, 18, 6, 45), local(ReminderPlanner.lesson(slot(4, DayOfWeek.SUNDAY, "07:00"), 15, now, wib)!!.triggerAt))
        val content = ReminderPlanner.lesson(slot(1, DayOfWeek.MONDAY, "10:00", "11:30"), 10, now, wib)!!.content
        assertEquals(ReminderContent.LessonSoon("Matematika", LocalTime.of(10, 0), LocalTime.of(11, 30), "", 10), content)
    }

    @Test fun lessonRepeatsEveryWeekAcrossDayRollover() {
        val s = slot(1, DayOfWeek.TUESDAY, "07:00")
        val first = ReminderPlanner.lesson(s, 10, now, wib)!!
        assertEquals(LocalDateTime.of(2026, 10, 13, 6, 50), local(first.triggerAt))
        // Just after it fired (next day, past midnight) the following Tuesday is planned.
        val next = ReminderPlanner.lesson(s, 10, first.triggerAt.plusSeconds(1), wib)!!
        assertEquals(Duration.ofDays(7), Duration.between(first.triggerAt, next.triggerAt))
    }

    @Test fun deviceZoneDecidesTheInstant() {
        val s = slot(1, DayOfWeek.MONDAY, "11:00") // still ahead in both zones (09:00 WIB = 10:00 WITA)
        val wita = ZoneId.of("Asia/Makassar")
        val inWib = ReminderPlanner.lesson(s, 10, now, wib)!!
        val inWita = ReminderPlanner.lesson(s, 10, now, wita)!!
        assertEquals("same wall-clock time in both zones", local(inWib.triggerAt, wib), local(inWita.triggerAt, wita))
        assertEquals("WITA is one hour ahead", Duration.ofHours(1), Duration.between(inWita.triggerAt, inWib.triggerAt))
    }

    @Test fun weeklyLessonKeepsLocalTimeOverDaylightSavingChange() {
        val berlin = ZoneId.of("Europe/Berlin") // DST ends Sunday 25 Oct 2026
        val s = slot(1, DayOfWeek.SUNDAY, "08:00")
        val before = ReminderPlanner.lesson(s, 10, at(2026, 10, 17, 12, 0, berlin), berlin)!!
        val after = ReminderPlanner.lesson(s, 10, before.triggerAt.plusSeconds(60), berlin)!!
        assertEquals(LocalTime.of(7, 50), local(before.triggerAt, berlin).toLocalTime())
        assertEquals(LocalTime.of(7, 50), local(after.triggerAt, berlin).toLocalTime())
        assertEquals(Duration.ofDays(7).plusHours(1), Duration.between(before.triggerAt, after.triggerAt))
    }

    @Test fun preferencesGateEachKind() {
        val input = ReminderPlanner.Input(
            prefs = NotificationPreferences(enabled = true),
            tasks = listOf(task(1, LocalDate.of(2026, 10, 14))),
            exams = listOf(Exam(id = 1, title = "PTS", date = LocalDate.of(2026, 10, 15))),
            slots = listOf(slot(1, DayOfWeek.TUESDAY, "07:00")),
            subjectNames = emptyMap(),
        )
        fun kinds(p: NotificationPreferences) = ReminderPlanner.planAll(now, wib, input.copy(prefs = p)).map { it.key.kind }.toSet()
        assertEquals(ReminderKind.entries.toSet(), kinds(NotificationPreferences(enabled = true)))
        assertEquals(emptySet<ReminderKind>(), kinds(NotificationPreferences(enabled = false)))
        assertEquals(setOf(ReminderKind.EXAM, ReminderKind.LESSON), kinds(NotificationPreferences(enabled = true, taskReminders = false)))
        assertEquals(setOf(ReminderKind.TASK), kinds(NotificationPreferences(enabled = true, examReminders = false, lessonReminders = false)))
    }

    @Test fun planIsSortedAndCappedWithResync() {
        val input = ReminderPlanner.Input(
            prefs = NotificationPreferences(enabled = true, taskLeadMinutes = 60),
            tasks = (1L..5L).map { task(it, LocalDate.of(2026, 10, 13).plusDays(5 - it), LocalTime.of(12, 0)) },
            exams = emptyList(), slots = emptyList(), subjectNames = emptyMap(),
        )
        val plan = ReminderPlanner.plan(now, wib, input, limit = 3)
        assertEquals(listOf(5L, 4L, 3L), plan.reminders.map { it.key.entityId })
        assertEquals(plan.reminders.last().triggerAt, plan.resyncAt)
        assertNull(ReminderPlanner.plan(now, wib, input, limit = 5).resyncAt)
    }
}

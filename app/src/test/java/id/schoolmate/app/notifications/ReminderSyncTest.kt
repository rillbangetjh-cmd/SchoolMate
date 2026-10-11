package id.schoolmate.app.notifications

import androidx.test.ext.junit.runners.AndroidJUnit4
import id.schoolmate.app.domain.model.Exam
import id.schoolmate.app.domain.model.NotificationPreferences
import id.schoolmate.app.domain.model.ReminderKind
import id.schoolmate.app.domain.model.ScheduleEntry
import id.schoolmate.app.domain.model.Task
import id.schoolmate.app.domain.model.TaskStatus
import id.schoolmate.app.testing.FakeNotificationPreferencesRepository
import id.schoolmate.app.testing.FakeNotifier
import id.schoolmate.app.testing.FakeScheduler
import id.schoolmate.app.testing.InMemoryRegistry
import id.schoolmate.app.testing.MutableClock
import id.schoolmate.app.testing.ViewModelTestEnv
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Sync engine against a real (in-memory) Room database. Now = Senin 12 Okt 2026 09:00 WIB. */
@RunWith(AndroidJUnit4::class)
class ReminderSyncTest {
    private val env = ViewModelTestEnv()
    private val clock = MutableClock(env.clock.instant(), env.zone)
    private val prefs = FakeNotificationPreferencesRepository(NotificationPreferences(enabled = true, taskLeadMinutes = 60, examLeadMinutes = 1_440, lessonLeadMinutes = 10))
    private val scheduler = FakeScheduler()
    private val notifier = FakeNotifier()
    private val registry = InMemoryRegistry()
    private val sync = ReminderSync(prefs, env.tasks, env.exams, env.schedule, env.subjects, scheduler, notifier, registry, clock)

    @After fun tearDown() = env.close()

    private fun local(token: String): LocalDateTime? = scheduler.alarms[token]?.let { LocalDateTime.ofInstant(it, clock.zone) }
    private fun d(day: Int) = LocalDate.of(2026, 10, day)

    private suspend fun addTask(day: Int, time: String? = "14:00", title: String = "PR") =
        env.tasks.addTask(Task(title = title, dueDate = d(day), dueTime = time?.let(LocalTime::parse))).getOrThrow()

    @Test fun schedulesOnceAndSkipsUnchangedAlarms(): Unit = runBlocking {
        val id = addTask(13)
        val first = sync.sync()
        assertEquals(1, first.scheduled)
        assertEquals(LocalDateTime.of(2026, 10, 13, 13, 0), local("task:$id"))
        // No change → AlarmManager is not touched again (no duplicate registration).
        val second = sync.sync()
        assertEquals(ReminderSync.Result(scheduled = 0, cancelled = 0, unchanged = 1), second)
        assertEquals(1, scheduler.scheduleCalls)
        assertEquals(1, scheduler.alarms.size)
    }

    @Test fun editingTheDeadlineMovesTheSameAlarm(): Unit = runBlocking {
        val id = addTask(13)
        sync.sync()
        val task = env.tasks.observeTask(id).awaitFirst()
        env.tasks.updateTask(task.copy(dueDate = d(15), dueTime = LocalTime.of(10, 0))).getOrThrow()
        sync.sync()
        assertEquals(LocalDateTime.of(2026, 10, 15, 9, 0), local("task:$id"))
        assertEquals("still a single alarm for the task", 1, scheduler.alarms.size)
    }

    @Test fun completingCancelsAndReopeningReschedules(): Unit = runBlocking {
        val id = addTask(13)
        sync.sync()
        env.tasks.setStatus(id, TaskStatus.DONE).getOrThrow()
        assertEquals(1, sync.sync().cancelled)
        assertNull(local("task:$id"))

        env.tasks.setStatus(id, TaskStatus.TODO).getOrThrow()
        sync.sync()
        assertEquals(LocalDateTime.of(2026, 10, 13, 13, 0), local("task:$id"))

        // Reopened after its deadline: nothing to remind any more.
        env.tasks.setStatus(id, TaskStatus.DONE).getOrThrow(); sync.sync()
        clock.now = clock.now.plus(Duration.ofDays(2))
        env.tasks.setStatus(id, TaskStatus.TODO).getOrThrow(); sync.sync()
        assertNull(local("task:$id"))
    }

    @Test fun deletingCancelsAndDismissesShownNotification(): Unit = runBlocking {
        val id = addTask(13)
        sync.sync()
        val trigger = scheduler.alarms.getValue("task:$id")
        clock.now = trigger
        assertTrue(sync.onAlarm("task:$id", trigger.toEpochMilli()))
        env.tasks.deleteTask(id)
        sync.sync()
        assertNull(local("task:$id"))
        assertEquals(listOf(id), notifier.dismissed.map { it.entityId })
    }

    @Test fun examTimeChangeAndPastExams(): Unit = runBlocking {
        val id = env.exams.addExam(Exam(title = "PTS", date = d(15), startTime = LocalTime.of(10, 0))).getOrThrow()
        val past = env.exams.addExam(Exam(title = "Kuis", date = d(9), startTime = LocalTime.of(8, 0))).getOrThrow()
        sync.sync()
        assertEquals(LocalDateTime.of(2026, 10, 14, 10, 0), local("exam:$id"))
        assertNull("past exam gets no alarm", local("exam:$past"))

        val exam = env.exams.observeExam(id).awaitFirst()
        env.exams.updateExam(exam.copy(date = d(16), startTime = LocalTime.of(7, 30))).getOrThrow()
        sync.sync()
        assertEquals(LocalDateTime.of(2026, 10, 15, 7, 30), local("exam:$id"))

        env.exams.deleteExam(id)
        sync.sync()
        assertTrue(scheduler.alarms.isEmpty())
    }

    @Test fun lessonsFollowTheWeeklyScheduleAndRepeat(): Unit = runBlocking {
        val math = env.subject("Matematika")
        val id = env.schedule.addEntry(ScheduleEntry(subjectId = math, dayOfWeek = DayOfWeek.MONDAY, startTime = LocalTime.of(10, 0), endTime = LocalTime.of(11, 0))).getOrThrow()
        sync.sync()
        assertEquals(LocalDateTime.of(2026, 10, 12, 9, 50), local("lesson:$id"))

        // The alarm fires: notification shown, next Monday planned automatically.
        val trigger = scheduler.alarms.getValue("lesson:$id")
        clock.now = trigger
        assertTrue(sync.onAlarm("lesson:$id", trigger.toEpochMilli()))
        assertEquals(LocalDateTime.of(2026, 10, 19, 9, 50), local("lesson:$id"))

        // Editing the slot moves it; deleting it removes it.
        val entry = env.schedule.observeWeek().awaitFirst().single().entry
        env.schedule.updateEntry(entry.copy(dayOfWeek = DayOfWeek.WEDNESDAY, startTime = LocalTime.of(13, 0), endTime = LocalTime.of(14, 0))).getOrThrow()
        sync.sync()
        assertEquals(LocalDateTime.of(2026, 10, 14, 12, 50), local("lesson:$id"))
        env.schedule.deleteEntry(id)
        sync.sync()
        assertNull(local("lesson:$id"))
    }

    @Test fun duplicateOrStaleAlarmsNeverShowTwice(): Unit = runBlocking {
        val id = addTask(13)
        sync.sync()
        val trigger = scheduler.alarms.getValue("task:$id").toEpochMilli()
        clock.now = java.time.Instant.ofEpochMilli(trigger)
        assertTrue(sync.onAlarm("task:$id", trigger))
        assertFalse("same alarm delivered twice", sync.onAlarm("task:$id", trigger))
        assertEquals(1, notifier.posted.size)

        // An alarm left over from before an edit stays silent.
        val other = addTask(20, title = "Lama")
        sync.sync()
        val oldTrigger = scheduler.alarms.getValue("task:$other").toEpochMilli()
        val t = env.tasks.observeTask(other).awaitFirst()
        env.tasks.updateTask(t.copy(dueDate = d(22))).getOrThrow()
        clock.now = java.time.Instant.ofEpochMilli(oldTrigger)
        assertFalse(sync.onAlarm("task:$other", oldTrigger))

        // Delivered after the deadline (device was asleep) → not shown.
        clock.now = env.clock.instant()
        val late = addTask(14, "08:00", "Telat")
        sync.sync()
        val lateTrigger = scheduler.alarms.getValue("task:$late").toEpochMilli()
        clock.now = java.time.Instant.parse("2026-10-14T02:00:00Z") // 09:00 WIB, after 08:00
        assertFalse(sync.onAlarm("task:$late", lateTrigger))
        assertEquals(1, notifier.posted.size)
    }

    @Test fun deniedPermissionShowsNothingAndDoesNotCrash(): Unit = runBlocking {
        notifier.allowed = false
        val id = addTask(13)
        sync.sync()
        val trigger = scheduler.alarms.getValue("task:$id").toEpochMilli()
        clock.now = java.time.Instant.ofEpochMilli(trigger)
        assertFalse(sync.onAlarm("task:$id", trigger))
        assertTrue(notifier.posted.isEmpty())
        // Granting later works for the next reminder; nothing was marked as delivered.
        assertFalse(registry.wasDelivered("task:$id", trigger))
    }

    @Test fun turningRemindersOffCancelsEverythingWithoutTouchingData(): Unit = runBlocking {
        addTask(13)
        env.exams.addExam(Exam(title = "PTS", date = d(15))).getOrThrow()
        sync.sync()
        assertEquals(2, scheduler.alarms.size)
        prefs.setEnabled(false)
        assertEquals(2, sync.sync().cancelled)
        assertTrue(scheduler.alarms.isEmpty())
        assertEquals(1, env.tasks.observeAll().awaitFirst().size)
        assertEquals(1, env.exams.observeAll().awaitFirst().size)

        prefs.setEnabled(true)
        prefs.setKindEnabled(ReminderKind.EXAM, false)
        sync.sync()
        assertEquals(listOf("task"), scheduler.alarms.keys.map { it.substringBefore(':') })
    }

    @Test fun changingLeadTimeReschedules(): Unit = runBlocking {
        val id = addTask(14)
        sync.sync()
        prefs.setLeadMinutes(ReminderKind.TASK, 1_440)
        sync.sync()
        assertEquals(LocalDateTime.of(2026, 10, 13, 14, 0), local("task:$id"))
    }

    @Test fun timeZoneChangeMovesAlarmsToTheNewLocalTime(): Unit = runBlocking {
        val math = env.subject("Matematika")
        val id = env.schedule.addEntry(ScheduleEntry(subjectId = math, dayOfWeek = DayOfWeek.TUESDAY, startTime = LocalTime.of(7, 0), endTime = LocalTime.of(8, 0))).getOrThrow()
        sync.sync()
        val before = scheduler.alarms.getValue("lesson:$id")
        clock.tz = ZoneId.of("Asia/Makassar") // user flies to Makassar (WITA, +1 h)
        assertEquals(1, sync.sync(force = true).scheduled)
        val after = scheduler.alarms.getValue("lesson:$id")
        assertEquals(LocalDateTime.of(2026, 10, 13, 6, 50), local("lesson:$id"))
        assertEquals(Duration.ofHours(1), Duration.between(after, before))
    }

    @Test fun rebootRestoresAllAlarmsFromTheDatabase(): Unit = runBlocking {
        val task = addTask(13)
        val exam = env.exams.addExam(Exam(title = "PTS", date = d(15))).getOrThrow()
        sync.sync()
        val expected = scheduler.alarms.toMap()
        scheduler.reboot() // the system dropped every alarm, the registry still lists them
        assertEquals("a normal sync would trust the registry", 0, sync.sync().scheduled)
        assertTrue(scheduler.alarms.isEmpty())
        // BOOT_COMPLETED → force sync re-registers everything.
        assertEquals(2, sync.sync(force = true).scheduled)
        assertEquals(expected, scheduler.alarms)
        assertEquals(setOf("task:$task", "exam:$exam"), scheduler.alarms.keys)
    }

    @Test fun manyRemindersAreCappedWithResyncAlarm(): Unit = runBlocking {
        repeat(ReminderPlanner.MAX_ALARMS + 5) { i -> addTask(13 + i % 15, String.format("%02d:%02d", 10 + i % 8, i % 60)) }
        sync.sync()
        assertEquals(ReminderPlanner.MAX_ALARMS, scheduler.alarms.size)
        assertEquals(scheduler.alarms.values.max(), scheduler.resyncAt)
    }
}

private suspend fun <T : Any> kotlinx.coroutines.flow.Flow<T?>.awaitFirst(): T = filterNotNull().first()

package id.schoolmate.app.ui.home

import androidx.test.ext.junit.runners.AndroidJUnit4
import id.schoolmate.app.domain.model.Exam
import id.schoolmate.app.domain.model.ScheduleEntry
import id.schoolmate.app.domain.model.Task
import id.schoolmate.app.domain.model.TaskStatus
import id.schoolmate.app.domain.model.UserPreferences
import id.schoolmate.app.testing.FakeUserPreferencesRepository
import id.schoolmate.app.testing.MainDispatcherRule
import id.schoolmate.app.testing.ViewModelTestEnv
import id.schoolmate.app.testing.awaitValue
import id.schoolmate.app.ui.screens.home.HomeViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class HomeViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val env = ViewModelTestEnv()

    @After fun tearDown() = env.close()

    private fun vm() = HomeViewModel(
        FakeUserPreferencesRepository(UserPreferences(studentName = "Rina")),
        env.subjects, env.schedule, env.tasks, env.exams, env.dashboard::invoke, env.now,
    )

    @Test fun emptyDatabaseGivesEmptyAgenda(): Unit = runBlocking {
        val state = vm().uiState.awaitValue { !it.isLoading }
        assertEquals("Selamat pagi, Rina", state.greeting)
        assertEquals("Senin, 12 Oktober 2026", state.dateLabel)
        assertEquals(0, state.subjectCount)
        assertTrue(state.lessonsToday.isEmpty() && state.upcomingTasks.isEmpty() && state.upcomingExams.isEmpty())
    }

    @Test fun agendaComesFromAllTables(): Unit = runBlocking {
        val math = env.subject("Matematika")
        env.schedule.addEntry(ScheduleEntry(subjectId = math, dayOfWeek = DayOfWeek.MONDAY, startTime = LocalTime.of(7, 0), endTime = LocalTime.of(8, 30))).getOrThrow()
        env.schedule.addEntry(ScheduleEntry(subjectId = math, dayOfWeek = DayOfWeek.TUESDAY, startTime = LocalTime.of(7, 0), endTime = LocalTime.of(8, 30))).getOrThrow()
        env.tasks.addTask(Task(title = "Terlambat", dueDate = env.today.minusDays(1))).getOrThrow()
        repeat(6) { i -> env.tasks.addTask(Task(title = "Tugas $i", dueDate = env.today.plusDays(i + 1L), subjectId = math)).getOrThrow() }
        env.tasks.addTask(Task(title = "Selesai", dueDate = env.today, status = TaskStatus.DONE)).getOrThrow()
        repeat(4) { i -> env.exams.addExam(Exam(title = "Ujian $i", date = env.today.plusDays(i * 2L))).getOrThrow() }
        env.exams.addExam(Exam(title = "Lalu", date = env.today.minusDays(1))).getOrThrow()
        env.sessions.logSession(env.clock.instant().minusSeconds(3_600), 45, subjectId = math).getOrThrow()

        val vm = vm()
        val s = vm.uiState.awaitValue {
            !it.isLoading && it.subjectCount == 1 && it.lessonsToday.size == 1 && it.overdueTasks.size == 1 &&
                it.upcomingTasks.size == 5 && it.upcomingExams.size == 3 &&
                it.summary.studyMinutesThisWeek == 45 && it.summary.activeTasks == 7
        }
        assertEquals(1, s.subjectCount)
        assertEquals(1, s.lessonsToday.size)
        assertTrue("07:00–08:30 is over at 09:00", s.lessonsToday.single().isFinished)
        assertEquals(listOf("Terlambat"), s.overdueTasks.map { it.task.title })
        assertEquals((0 until 5).map { "Tugas $it" }, s.upcomingTasks.map { it.task.title })
        assertEquals(listOf("Ujian 0", "Ujian 1", "Ujian 2"), s.upcomingExams.map { it.exam.title })
        assertEquals(7, s.summary.activeTasks)
        assertEquals(1, s.summary.overdueTasks)

        // Completing from Home removes the task from the agenda.
        vm.toggleDone(s.upcomingTasks.first().task)
        assertEquals("Tugas 1", vm.uiState.awaitValue {
            it.summary.activeTasks == 6 && it.upcomingTasks.none { t -> t.task.title == "Tugas 0" }
        }.upcomingTasks.first().task.title)

        // Midnight rollover: Tuesday's lesson replaces Monday's without re-creating the ViewModel.
        env.now.value = env.now.value.plusDays(1).withHour(7).withMinute(30)
        val tue = vm.uiState.awaitValue { it.dateLabel.startsWith("Selasa") && it.lessonsToday.singleOrNull()?.isOngoing == true }
        assertTrue(tue.lessonsToday.single().isOngoing)
        assertEquals("Selamat pagi, Rina", tue.greeting)
    }
}

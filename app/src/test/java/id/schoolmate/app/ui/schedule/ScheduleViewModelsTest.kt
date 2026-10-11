package id.schoolmate.app.ui.schedule

import androidx.test.ext.junit.runners.AndroidJUnit4
import id.schoolmate.app.domain.model.ScheduleEntry
import id.schoolmate.app.testing.MainDispatcherRule
import id.schoolmate.app.testing.ViewModelTestEnv
import id.schoolmate.app.testing.awaitValue
import id.schoolmate.app.ui.model.EditorEvent
import id.schoolmate.app.ui.model.FieldError
import id.schoolmate.app.ui.screens.schedule.ScheduleEditorViewModel
import id.schoolmate.app.ui.screens.schedule.ScheduleViewModel
import id.schoolmate.app.ui.screens.schedule.TimeRangeError
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class ScheduleViewModelsTest {
    @get:Rule val main = MainDispatcherRule()
    private val env = ViewModelTestEnv()
    private val t: (String) -> LocalTime = { LocalTime.parse(it) }

    @After fun tearDown() = env.close()

    private suspend fun lesson(subject: Long, day: DayOfWeek, start: String, end: String, room: String = "") =
        env.schedule.addEntry(ScheduleEntry(subjectId = subject, dayOfWeek = day, startTime = t(start), endTime = t(end), room = room)).getOrThrow()

    @Test fun weekViewGroupsByDayAndStartsOnToday(): Unit = runBlocking {
        val math = env.subject("Matematika")
        val bio = env.subject("Biologi")
        lesson(bio, DayOfWeek.MONDAY, "10:00", "11:30")
        lesson(math, DayOfWeek.MONDAY, "08:30", "10:00", room = "X-1")
        lesson(math, DayOfWeek.WEDNESDAY, "07:00", "08:30")
        val vm = ScheduleViewModel(env.schedule, env.subjects, env.now, env.clock)

        val state = vm.uiState.awaitValue { !it.isLoading && it.totalLessons == 3 }
        assertEquals(DayOfWeek.MONDAY, state.selectedDay) // fixed clock = Monday
        assertTrue(state.hasSubjects)
        assertEquals(listOf("Matematika", "Biologi"), state.lessons.map { it.slot.subject.name })
        assertTrue("09:00 is inside 08:30–10:00", state.lessons.first().isOngoing)
        assertFalse(state.lessons.last().isFinished)
        val monday = state.days.first { it.day == DayOfWeek.MONDAY }
        assertEquals(2, monday.lessonCount)
        assertEquals(180, monday.totalMinutes)
        assertTrue(monday.isToday)
        assertEquals(7, state.days.size)

        vm.selectDay(DayOfWeek.WEDNESDAY)
        val wed = vm.uiState.awaitValue { it.selectedDay == DayOfWeek.WEDNESDAY }
        assertEquals(1, wed.lessons.size)
        assertFalse("not today, so never 'ongoing'", wed.lessons.single().isOngoing)
    }

    @Test fun editorSuggestsNextFreeSlotAndSaves(): Unit = runBlocking {
        val math = env.subject("Matematika")
        lesson(math, DayOfWeek.TUESDAY, "07:00", "08:30")
        val vm = ScheduleEditorViewModel(0, DayOfWeek.TUESDAY, env.schedule, env.subjects)
        val job = launch { vm.uiState.collect {} }
        val ready = vm.uiState.awaitValue { !it.isLoading }
        assertEquals(t("08:30"), ready.form.start)
        assertEquals(t("10:00"), ready.form.end)
        assertEquals("only subject is preselected", math, ready.form.subjectId)

        vm.onRoomChange("Lab")
        vm.save()
        assertEquals(EditorEvent.Saved, withTimeout(5_000) { vm.eventFlow.first() })
        val saved = env.schedule.observeDay(DayOfWeek.TUESDAY).first()
        assertEquals(listOf(t("07:00"), t("08:30")), saved.map { it.entry.startTime })
        assertEquals("Lab", saved.last().entry.room)
        job.cancel()
    }

    @Test fun editorShowsConflictLiveAndBlocksSave(): Unit = runBlocking {
        val math = env.subject("Matematika")
        val bio = env.subject("Biologi")
        lesson(math, DayOfWeek.MONDAY, "07:00", "08:30")
        val vm = ScheduleEditorViewModel(0, DayOfWeek.MONDAY, env.schedule, env.subjects)
        val job = launch { vm.uiState.collect {} }
        vm.uiState.awaitValue { !it.isLoading }
        vm.onSubjectChange(bio)
        vm.onStartChange(t("08:00")) // keeps 90 min length → 08:00–09:30
        val conflict = vm.uiState.awaitValue { it.conflicts.isNotEmpty() }
        assertEquals(t("09:30"), conflict.form.end)
        assertEquals("Matematika", conflict.conflicts.single().subject.name)

        vm.save()
        assertTrue(vm.uiState.awaitValue { it.errors.overlapRejected }.errors.overlapRejected)
        assertEquals(1, env.schedule.observeWeek().first().size)

        vm.onStartChange(t("08:30")) // back-to-back is fine
        vm.uiState.awaitValue { it.conflicts.isEmpty() && !it.errors.overlapRejected }
        vm.save()
        assertEquals(EditorEvent.Saved, withTimeout(5_000) { vm.eventFlow.first() })
        assertEquals(2, env.schedule.observeWeek().first().size)
        job.cancel()
    }

    @Test fun editorValidatesTimeAndSubject(): Unit = runBlocking {
        env.subject("A"); env.subject("B") // two subjects → nothing preselected
        val vm = ScheduleEditorViewModel(0, DayOfWeek.FRIDAY, env.schedule, env.subjects)
        val job = launch { vm.uiState.collect {} }
        vm.uiState.awaitValue { !it.isLoading }
        vm.onEndChange(t("06:00"))
        vm.save()
        val errors = vm.uiState.awaitValue { it.errors.time != null && !it.isSaving }.errors
        assertEquals(TimeRangeError.END_BEFORE_START, errors.time)
        assertEquals(FieldError.REQUIRED, errors.subject)
        vm.onEndChange(t("07:03"))
        vm.save()
        assertEquals(TimeRangeError.TOO_SHORT, vm.uiState.awaitValue { it.errors.time == TimeRangeError.TOO_SHORT }.errors.time)
        assertTrue(env.schedule.observeWeek().first().isEmpty())
        job.cancel()
    }

    @Test fun editingIgnoresItselfAndCanDelete(): Unit = runBlocking {
        val math = env.subject("Matematika")
        val id = lesson(math, DayOfWeek.THURSDAY, "07:00", "08:30")
        val vm = ScheduleEditorViewModel(id, DayOfWeek.MONDAY, env.schedule, env.subjects)
        val job = launch { vm.uiState.collect {} }
        val loaded = vm.uiState.awaitValue { !it.isLoading }
        assertEquals(DayOfWeek.THURSDAY, loaded.form.day)
        assertTrue("an entry never conflicts with itself", loaded.conflicts.isEmpty())
        vm.onEndChange(t("09:00"))
        vm.save()
        assertEquals(EditorEvent.Saved, withTimeout(5_000) { vm.eventFlow.first() })
        assertEquals(t("09:00"), env.schedule.observeWeek().first().single().entry.endTime)

        vm.delete()
        assertEquals(EditorEvent.Deleted, withTimeout(5_000) { vm.eventFlow.first() })
        assertTrue(env.schedule.observeWeek().first().isEmpty())
        job.cancel()
    }
}

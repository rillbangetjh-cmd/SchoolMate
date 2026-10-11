package id.schoolmate.app.ui.studylog

import androidx.test.ext.junit.runners.AndroidJUnit4
import id.schoolmate.app.testing.MainDispatcherRule
import id.schoolmate.app.testing.ViewModelTestEnv
import id.schoolmate.app.testing.awaitValue
import id.schoolmate.app.ui.model.FieldError
import id.schoolmate.app.ui.screens.studylog.StudyLogViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class StudyLogViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val env = ViewModelTestEnv()

    @After fun tearDown() = env.close()

    @Test fun logsSessionsAndSummarisesWeek(): Unit = runBlocking {
        val math = env.subject("Matematika")
        val vm = env.track(StudyLogViewModel(env.sessions, env.subjects, env.clock))
        assertEquals(env.today, vm.uiState.awaitValue { !it.isLoading }.weekStart)

        vm.openEditor()
        val form = vm.editor.value!!.form
        assertEquals(env.today, form.date)
        assertEquals(LocalTime.of(8, 30), form.start) // 30 minutes before "now"

        vm.onFormChange(form.copy(durationText = "0"))
        vm.saveSession()
        assertEquals(FieldError.INVALID, vm.editor.value!!.durationError)

        vm.onFormChange(form.copy(durationText = "50", subjectId = math, note = "Latihan soal"))
        vm.saveSession()
        val state = vm.uiState.awaitValue { it.totalMinutes == 50 }
        // The dialog closes right after the insert returns; Room may emit the new total first.
        assertNull("dialog closes after saving", vm.editor.awaitValue { it == null })
        assertEquals("Matematika", state.perSubject.single().subject?.name)
        assertEquals(env.today, state.sessions.single().first.date)

        vm.delete(state.sessions.single().first.id)
        vm.uiState.awaitValue { it.sessions.isEmpty() && it.totalMinutes == 0 }
    }

    @Test fun weekNavigationStopsAtCurrentWeek(): Unit = runBlocking {
        val vm = env.track(StudyLogViewModel(env.sessions, env.subjects, env.clock))
        vm.uiState.awaitValue { !it.isLoading }
        vm.nextWeek()
        assertTrue(vm.uiState.awaitValue { !it.isLoading }.isCurrentWeek)
        vm.previousWeek()
        val prev = vm.uiState.awaitValue { !it.isCurrentWeek }
        assertEquals(env.today.minusWeeks(1), prev.weekStart)

        // Logging a session in an older week jumps to that week.
        vm.openEditor()
        vm.onFormChange(vm.editor.value!!.form.copy(date = env.today.minusDays(9), durationText = "20"))
        vm.saveSession()
        val shown = vm.uiState.awaitValue { it.totalMinutes == 20 }
        assertEquals(env.today.minusDays(14), shown.weekStart)
        assertFalse(shown.isCurrentWeek)
        assertEquals(1, env.sessions.observeBetween(env.today.minusDays(14), env.today).first().size)
    }
}

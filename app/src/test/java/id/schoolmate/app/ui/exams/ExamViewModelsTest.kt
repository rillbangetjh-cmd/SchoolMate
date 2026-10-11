package id.schoolmate.app.ui.exams

import androidx.test.ext.junit.runners.AndroidJUnit4
import id.schoolmate.app.domain.model.Exam
import id.schoolmate.app.testing.MainDispatcherRule
import id.schoolmate.app.testing.ViewModelTestEnv
import id.schoolmate.app.testing.awaitValue
import id.schoolmate.app.ui.model.EditorEvent
import id.schoolmate.app.ui.model.FieldError
import id.schoolmate.app.ui.screens.exams.ExamDetailUiState
import id.schoolmate.app.ui.screens.exams.ExamDetailViewModel
import id.schoolmate.app.ui.screens.exams.ExamEditorViewModel
import id.schoolmate.app.ui.screens.exams.ExamTab
import id.schoolmate.app.ui.screens.exams.ExamsViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class ExamViewModelsTest {
    @get:Rule val main = MainDispatcherRule()
    private val env = ViewModelTestEnv()

    @After fun tearDown() = env.close()

    private suspend fun exam(title: String, days: Long, subjectId: Long? = null) =
        env.exams.addExam(Exam(title = title, date = env.today.plusDays(days), subjectId = subjectId)).getOrThrow()

    @Test fun listSplitsUpcomingAndPast(): Unit = runBlocking {
        val kimia = env.subject("Kimia")
        exam("PTS Kimia", 10, kimia)
        exam("Kuis hari ini", 0)
        exam("Ulangan lalu", -3)
        val vm = ExamsViewModel(env.exams, env.subjects, env.now)

        val upcoming = vm.uiState.awaitValue { !it.isLoading && it.items.size == 2 }
        assertEquals(ExamTab.UPCOMING, upcoming.tab)
        assertEquals(listOf("Kuis hari ini", "PTS Kimia"), upcoming.items.map { it.exam.title })
        assertEquals(listOf(0L, 10L), upcoming.items.map { it.daysUntil })
        assertEquals("Kimia", upcoming.items.last().subject?.name)
        assertEquals(1, upcoming.pastCount)

        vm.setTab(ExamTab.PAST)
        val past = vm.uiState.awaitValue { it.tab == ExamTab.PAST }
        assertEquals(listOf(-3L), past.items.map { it.daysUntil })

        // Next day: today's quiz moves to "past".
        env.now.value = env.now.value.plusDays(1)
        assertEquals(2, vm.uiState.awaitValue { it.pastCount == 2 }.items.size)
    }

    @Test fun editorValidatesDurationAndTitle(): Unit = runBlocking {
        val vm = ExamEditorViewModel(0, env.exams, env.subjects, env.clock)
        assertEquals(env.today.plusDays(7), vm.uiState.value.form.date)
        vm.onDurationChange("abc") // rejected at input level
        assertEquals("90", vm.uiState.value.form.durationText)
        vm.onDurationChange("700")
        vm.save()
        val errors = vm.uiState.value.errors
        assertEquals(FieldError.REQUIRED, errors.title)
        assertEquals(FieldError.INVALID, errors.duration)
        assertTrue(env.exams.observeAll().first().isEmpty())
    }

    @Test fun editorCreatesEditsAndDeletes(): Unit = runBlocking {
        val fis = env.subject("Fisika")
        val create = ExamEditorViewModel(0, env.exams, env.subjects, env.clock)
        create.onTitleChange("PAS Fisika")
        create.onSubjectChange(fis)
        create.onDateChange(env.today.plusDays(3))
        create.onTimeChange(LocalTime.of(8, 0))
        create.onDurationChange("120")
        create.onLocationChange("Aula")
        create.onNoteChange("Bab 1–4")
        create.save()
        assertEquals(EditorEvent.Saved, withTimeout(5_000) { create.eventFlow.first() })
        val saved = env.exams.observeAll().first().single()
        assertEquals(120, saved.durationMinutes)
        assertEquals("Aula", saved.location)

        val edit = ExamEditorViewModel(saved.id, env.exams, env.subjects, env.clock)
        assertEquals("120", edit.uiState.awaitValue { !it.isLoading }.form.durationText)
        edit.onDurationChange("")
        edit.onTimeChange(null)
        edit.save()
        assertEquals(EditorEvent.Saved, withTimeout(5_000) { edit.eventFlow.first() })
        val updated = env.exams.observeExam(saved.id).first()!!
        assertNull(updated.durationMinutes)
        assertNull(updated.startTime)

        val detail = ExamDetailViewModel(saved.id, env.exams, env.subjects, env.now)
        val ready = detail.uiState.awaitValue { it is ExamDetailUiState.Ready } as ExamDetailUiState.Ready
        assertEquals(3L, ready.item.daysUntil)
        assertEquals("Fisika", ready.item.subject?.name)
        detail.delete()
        assertEquals(EditorEvent.Deleted, withTimeout(5_000) { detail.eventFlow.first() })
        assertTrue(env.exams.observeAll().first().isEmpty())
    }
}

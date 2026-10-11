package id.schoolmate.app.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.schoolmate.app.data.local.SchoolMateDatabase
import id.schoolmate.app.data.local.SchoolMateDatabase.Companion.configure
import id.schoolmate.app.data.local.insertSubject
import id.schoolmate.app.domain.model.TaskFilter
import id.schoolmate.app.testing.MainDispatcherRule
import id.schoolmate.app.testing.ViewModelTestEnv
import id.schoolmate.app.testing.awaitValue
import id.schoolmate.app.ui.model.EditorEvent
import id.schoolmate.app.ui.screens.exams.ExamEditorViewModel
import id.schoolmate.app.ui.screens.exams.ExamsViewModel
import id.schoolmate.app.ui.screens.schedule.ScheduleEditorViewModel
import id.schoolmate.app.ui.screens.schedule.ScheduleViewModel
import id.schoolmate.app.ui.screens.tasks.TaskEditorViewModel
import id.schoolmate.app.ui.screens.tasks.TasksViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek

/**
 * Simulates closing and reopening the app: data is written through the editor ViewModels into a
 * file-backed database built like production, the database is closed, then a fresh instance and
 * fresh ViewModels must see everything.
 */
@RunWith(AndroidJUnit4::class)
class PersistenceAcrossRestartTest {
    @get:Rule val main = MainDispatcherRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "persistence-test.db"

    private fun open(): SchoolMateDatabase =
        Room.databaseBuilder(context, SchoolMateDatabase::class.java, name).configure().allowMainThreadQueries().build()

    @After fun cleanUp() {
        context.deleteDatabase(name)
    }

    @Test fun editsSurviveDatabaseReopen(): Unit = runBlocking {
        context.deleteDatabase(name)
        val first = ViewModelTestEnv(open())
        val math = first.db.insertSubject("Matematika")

        val task = TaskEditorViewModel(0, first.tasks, first.subjects, first.clock)
        task.onTitleChange("PR Aljabar")
        task.onSubjectChange(math)
        task.save()
        assertEquals(EditorEvent.Saved, withTimeout(5_000) { task.eventFlow.first() })

        val exam = ExamEditorViewModel(0, first.exams, first.subjects, first.clock)
        exam.onTitleChange("PTS Matematika")
        exam.save()
        assertEquals(EditorEvent.Saved, withTimeout(5_000) { exam.eventFlow.first() })

        val lesson = ScheduleEditorViewModel(0, DayOfWeek.MONDAY, first.schedule, first.subjects)
        val job = launch { lesson.uiState.collect {} }
        lesson.uiState.awaitValue { !it.isLoading && it.form.subjectId == math }
        lesson.save()
        assertEquals(EditorEvent.Saved, withTimeout(5_000) { lesson.eventFlow.first() })
        job.cancel()
        first.close()

        // "App restart"
        val second = ViewModelTestEnv(open())
        val tasks = TasksViewModel(second.tasks, second.subjects, second.now)
        tasks.setFilter(TaskFilter.ALL)
        val t = tasks.uiState.awaitValue { !it.isLoading && it.items.isNotEmpty() }.items.single()
        assertEquals("PR Aljabar", t.task.title)
        assertEquals("Matematika", t.subject?.name)

        val exams = ExamsViewModel(second.exams, second.subjects, second.now)
        assertEquals("PTS Matematika", exams.uiState.awaitValue { it.items.isNotEmpty() }.items.single().exam.title)

        val schedule = ScheduleViewModel(second.schedule, second.subjects, second.now, second.clock)
        assertEquals("Matematika", schedule.uiState.awaitValue { it.totalLessons == 1 }.lessons.single().slot.subject.name)
        second.close()
    }
}

package id.schoolmate.app.ui.tasks

import androidx.test.ext.junit.runners.AndroidJUnit4
import id.schoolmate.app.domain.model.Priority
import id.schoolmate.app.domain.model.Task
import id.schoolmate.app.domain.model.TaskFilter
import id.schoolmate.app.domain.model.TaskStatus
import id.schoolmate.app.testing.MainDispatcherRule
import id.schoolmate.app.testing.ViewModelTestEnv
import id.schoolmate.app.testing.awaitValue
import id.schoolmate.app.ui.model.EditorEvent
import id.schoolmate.app.ui.model.FieldError
import id.schoolmate.app.ui.screens.tasks.TaskDetailUiState
import id.schoolmate.app.ui.screens.tasks.TaskDetailViewModel
import id.schoolmate.app.ui.screens.tasks.TaskEditorViewModel
import id.schoolmate.app.ui.screens.tasks.TasksEvent
import id.schoolmate.app.ui.screens.tasks.TasksViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class TaskViewModelsTest {
    @get:Rule val main = MainDispatcherRule()
    private val env = ViewModelTestEnv()

    @After fun tearDown() = env.close()

    private suspend fun add(title: String, daysFromToday: Long, time: LocalTime? = null, status: TaskStatus = TaskStatus.TODO, subjectId: Long? = null) =
        env.tasks.addTask(Task(title = title, dueDate = env.today.plusDays(daysFromToday), dueTime = time, status = status, subjectId = subjectId)).getOrThrow()

    @Test fun listFiltersAndSortsFromDatabase(): Unit = runBlocking {
        val math = env.subject("Matematika")
        add("Besok", 1, subjectId = math)
        add("Terlambat", -1)
        add("Nanti sore", 0, LocalTime.of(15, 0))
        add("Sudah", 2, status = TaskStatus.DONE)
        val vm = env.track(TasksViewModel(env.tasks, env.subjects, env.now))

        val active = vm.uiState.awaitValue { !it.isLoading && it.items.size == 3 }
        assertEquals(TaskFilter.ACTIVE, active.filter)
        assertEquals(listOf("Terlambat", "Nanti sore", "Besok"), active.items.map { it.task.title })
        assertEquals("Matematika", active.items.last().subject?.name)
        assertEquals(mapOf(TaskFilter.ALL to 4, TaskFilter.ACTIVE to 3, TaskFilter.OVERDUE to 1, TaskFilter.DONE to 1), active.counts)

        vm.setFilter(TaskFilter.OVERDUE)
        assertEquals(listOf("Terlambat"), vm.uiState.awaitValue { it.filter == TaskFilter.OVERDUE }.items.map { it.task.title })
        vm.setFilter(TaskFilter.DONE)
        assertEquals(listOf("Sudah"), vm.uiState.awaitValue { it.filter == TaskFilter.DONE }.items.map { it.task.title })
        vm.setFilter(TaskFilter.ALL)
        assertEquals(4, vm.uiState.awaitValue { it.filter == TaskFilter.ALL }.items.size)

        // Time passing makes "Nanti sore" overdue without any database change.
        env.now.value = env.now.value.withHour(16)
        vm.setFilter(TaskFilter.OVERDUE)
        assertEquals(2, vm.uiState.awaitValue { it.filter == TaskFilter.OVERDUE && it.items.size == 2 }.counts[TaskFilter.OVERDUE])
    }

    @Test fun quickCompleteCanBeUndone(): Unit = runBlocking {
        val id = add("PR Fisika", 1, status = TaskStatus.IN_PROGRESS)
        val vm = env.track(TasksViewModel(env.tasks, env.subjects, env.now))
        val item = vm.uiState.awaitValue { it.items.isNotEmpty() }.items.single()

        vm.toggleDone(item.task)
        val event = withTimeout(5_000) { vm.eventFlow.first() } as TasksEvent.Completed
        assertEquals(TaskStatus.IN_PROGRESS, event.previous)
        val done = env.tasks.observeTask(id).awaitValue { it?.status == TaskStatus.DONE }!!
        assertNotNull(done.completedAt)
        vm.uiState.awaitValue { it.items.isEmpty() && it.counts[TaskFilter.DONE] == 1 }

        vm.undoComplete(event)
        val restored = env.tasks.observeTask(id).awaitValue { it?.status == TaskStatus.IN_PROGRESS }!!
        assertNull(restored.completedAt)
    }

    @Test fun deleteRemovesTask(): Unit = runBlocking {
        val id = add("Hapus aku", 1)
        val vm = env.track(TasksViewModel(env.tasks, env.subjects, env.now))
        vm.uiState.awaitValue { it.items.size == 1 }
        vm.delete(id)
        vm.uiState.awaitValue { !it.isLoading && it.totalTasks == 0 }
    }

    @Test fun editorValidatesBeforeSaving(): Unit = runBlocking {
        val vm = env.track(TaskEditorViewModel(0, env.tasks, env.subjects, env.clock))
        assertTrue(vm.uiState.value.isNew)
        assertEquals(env.today.plusDays(1), vm.uiState.value.form.dueDate) // default: tomorrow
        vm.onTitleChange("   ")
        vm.save()
        assertEquals(FieldError.REQUIRED, vm.uiState.value.errors.title)
        vm.onTitleChange("x".repeat(101))
        assertNull("typing clears the error", vm.uiState.value.errors.title)
        vm.save()
        assertEquals(FieldError.TOO_LONG, vm.uiState.value.errors.title)
        assertTrue(env.tasks.observeAll().first().isEmpty())
    }

    @Test fun editorCreatesAndEditsTask(): Unit = runBlocking {
        val bio = env.subject("Biologi")
        val create = env.track(TaskEditorViewModel(0, env.tasks, env.subjects, env.clock))
        create.onTitleChange("  Laporan praktikum ")
        create.onDescriptionChange("Bab 3")
        create.onSubjectChange(bio)
        create.onDueDateChange(env.today.plusDays(4))
        create.onDueTimeChange(LocalTime.of(20, 0))
        create.onPriorityChange(Priority.HIGH)
        create.save()
        assertEquals(EditorEvent.Saved, withTimeout(5_000) { create.eventFlow.first() })

        val saved = env.tasks.observeAll().first().single()
        assertEquals("Laporan praktikum", saved.title)
        assertEquals(bio, saved.subjectId)
        assertEquals(LocalTime.of(20, 0), saved.dueTime)
        assertEquals(Priority.HIGH, saved.priority)

        val edit = env.track(TaskEditorViewModel(saved.id, env.tasks, env.subjects, env.clock))
        val loaded = edit.uiState.awaitValue { !it.isLoading }
        assertEquals("Laporan praktikum", loaded.form.title)
        edit.onStatusChange(TaskStatus.DONE)
        edit.onDueTimeChange(null)
        edit.save()
        assertEquals(EditorEvent.Saved, withTimeout(5_000) { edit.eventFlow.first() })
        val updated = env.tasks.observeTask(saved.id).first()!!
        assertEquals(TaskStatus.DONE, updated.status)
        assertNull(updated.dueTime)
        assertNotNull(updated.completedAt)
    }

    @Test fun editorHandlesMissingTaskAndDelete(): Unit = runBlocking {
        val missing = env.track(TaskEditorViewModel(999, env.tasks, env.subjects, env.clock))
        assertTrue(missing.uiState.awaitValue { !it.isLoading }.notFound)

        val id = add("Hapus", 1)
        val edit = env.track(TaskEditorViewModel(id, env.tasks, env.subjects, env.clock))
        edit.uiState.awaitValue { !it.isLoading }
        edit.delete()
        assertEquals(EditorEvent.Deleted, withTimeout(5_000) { edit.eventFlow.first() })
        assertNull(env.tasks.observeTask(id).first())
    }

    @Test fun detailShowsTaskAndChangesStatus(): Unit = runBlocking {
        val id = add("Ringkasan bab", 0, LocalTime.of(10, 0))
        val vm = env.track(TaskDetailViewModel(id, env.tasks, env.subjects, env.now))
        val ready = vm.uiState.awaitValue { it is TaskDetailUiState.Ready } as TaskDetailUiState.Ready
        assertEquals("Ringkasan bab", ready.item.task.title)
        vm.setStatus(TaskStatus.IN_PROGRESS)
        vm.uiState.awaitValue { (it as? TaskDetailUiState.Ready)?.item?.task?.status == TaskStatus.IN_PROGRESS }
        vm.delete()
        assertEquals(EditorEvent.Deleted, withTimeout(5_000) { vm.eventFlow.first() })
        vm.uiState.awaitValue { it == TaskDetailUiState.NotFound }
    }
}

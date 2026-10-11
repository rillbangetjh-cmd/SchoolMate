package id.schoolmate.app.ui.screens.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.schoolmate.app.data.repository.SubjectRepository
import id.schoolmate.app.data.repository.TaskRepository
import id.schoolmate.app.domain.model.Task
import id.schoolmate.app.domain.model.TaskFilter
import id.schoolmate.app.domain.model.TaskStatus
import id.schoolmate.app.domain.model.countFor
import id.schoolmate.app.domain.model.filterAndSort
import id.schoolmate.app.ui.model.TaskItem
import id.schoolmate.app.ui.model.toItem
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime

data class TasksUiState(
    val isLoading: Boolean = true,
    val filter: TaskFilter = TaskFilter.ACTIVE,
    val items: List<TaskItem> = emptyList(),
    val counts: Map<TaskFilter, Int> = emptyMap(),
    val totalTasks: Int = 0,
)

sealed interface TasksEvent {
    /** A task was completed from the list; offer undo. */
    data class Completed(val taskId: Long, val previous: TaskStatus) : TasksEvent
}

class TasksViewModel(
    private val taskRepository: TaskRepository,
    subjectRepository: SubjectRepository,
    now: Flow<LocalDateTime>,
) : ViewModel() {

    private val filter = MutableStateFlow(TaskFilter.ACTIVE)
    private val events = Channel<TasksEvent>(Channel.BUFFERED)
    val eventFlow: Flow<TasksEvent> = events.receiveAsFlow()

    val uiState: StateFlow<TasksUiState> = combine(
        taskRepository.observeAll(),
        subjectRepository.observeSubjects(),
        filter,
        now,
    ) { tasks, subjects, filter, now ->
        val byId = subjects.associateBy { it.id }
        TasksUiState(
            isLoading = false,
            filter = filter,
            items = tasks.filterAndSort(filter, now).map { it.toItem(byId, now) },
            counts = TaskFilter.entries.associateWith { tasks.countFor(it, now) },
            totalTasks = tasks.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksUiState())

    fun setFilter(value: TaskFilter) {
        filter.value = value
    }

    /** Quick action: checkbox toggles between DONE and TODO. */
    fun toggleDone(task: Task) {
        viewModelScope.launch {
            if (task.status == TaskStatus.DONE) {
                taskRepository.setStatus(task.id, TaskStatus.TODO)
            } else {
                taskRepository.setStatus(task.id, TaskStatus.DONE).onSuccess {
                    events.send(TasksEvent.Completed(task.id, task.status))
                }
            }
        }
    }

    fun undoComplete(event: TasksEvent.Completed) {
        viewModelScope.launch { taskRepository.setStatus(event.taskId, event.previous) }
    }

    fun delete(taskId: Long) {
        viewModelScope.launch { taskRepository.deleteTask(taskId) }
    }
}

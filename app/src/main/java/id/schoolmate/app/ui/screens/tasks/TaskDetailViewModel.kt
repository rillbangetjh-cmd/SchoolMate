package id.schoolmate.app.ui.screens.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.schoolmate.app.data.repository.SubjectRepository
import id.schoolmate.app.data.repository.TaskRepository
import id.schoolmate.app.domain.model.TaskStatus
import id.schoolmate.app.ui.model.EditorEvent
import id.schoolmate.app.ui.model.TaskItem
import id.schoolmate.app.ui.model.toItem
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime

sealed interface TaskDetailUiState {
    data object Loading : TaskDetailUiState
    data object NotFound : TaskDetailUiState
    data class Ready(val item: TaskItem) : TaskDetailUiState
}

class TaskDetailViewModel(
    private val taskId: Long,
    private val taskRepository: TaskRepository,
    subjectRepository: SubjectRepository,
    now: Flow<LocalDateTime>,
) : ViewModel() {

    private val events = Channel<EditorEvent>(Channel.BUFFERED)
    val eventFlow: Flow<EditorEvent> = events.receiveAsFlow()

    val uiState: StateFlow<TaskDetailUiState> = combine(
        taskRepository.observeTask(taskId),
        subjectRepository.observeSubjects(),
        now,
    ) { task, subjects, now ->
        if (task == null) TaskDetailUiState.NotFound
        else TaskDetailUiState.Ready(task.toItem(subjects.associateBy { it.id }, now))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskDetailUiState.Loading)

    fun setStatus(status: TaskStatus) {
        viewModelScope.launch { taskRepository.setStatus(taskId, status) }
    }

    fun delete() {
        viewModelScope.launch {
            taskRepository.deleteTask(taskId)
            events.send(EditorEvent.Deleted)
        }
    }
}

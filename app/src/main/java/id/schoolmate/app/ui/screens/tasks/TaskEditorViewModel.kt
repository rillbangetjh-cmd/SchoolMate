package id.schoolmate.app.ui.screens.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.schoolmate.app.data.repository.SubjectRepository
import id.schoolmate.app.data.repository.TaskRepository
import id.schoolmate.app.domain.model.Priority
import id.schoolmate.app.domain.model.Subject
import id.schoolmate.app.domain.model.Task
import id.schoolmate.app.domain.model.TaskStatus
import id.schoolmate.app.domain.validation.ValidationError
import id.schoolmate.app.domain.validation.ValidationException
import id.schoolmate.app.domain.validation.validateTask
import id.schoolmate.app.ui.model.EditorEvent
import id.schoolmate.app.ui.model.FieldError
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

data class TaskForm(
    val title: String = "",
    val description: String = "",
    val subjectId: Long? = null,
    val dueDate: LocalDate,
    val dueTime: LocalTime? = null,
    val priority: Priority = Priority.MEDIUM,
    val status: TaskStatus = TaskStatus.TODO,
)

data class TaskFormErrors(
    val title: FieldError? = null,
    val description: FieldError? = null,
    val subject: FieldError? = null,
) {
    val hasAny: Boolean get() = title != null || description != null || subject != null
}

data class TaskEditorUiState(
    val isNew: Boolean,
    val isLoading: Boolean,
    val form: TaskForm,
    val errors: TaskFormErrors = TaskFormErrors(),
    val isSaving: Boolean = false,
    val notFound: Boolean = false,
)

/** Maps domain validation errors to per-field form errors. */
fun Set<ValidationError>.toTaskFormErrors() = TaskFormErrors(
    title = when {
        ValidationError.TITLE_EMPTY in this -> FieldError.REQUIRED
        ValidationError.TITLE_TOO_LONG in this -> FieldError.TOO_LONG
        else -> null
    },
    description = if (ValidationError.DESCRIPTION_TOO_LONG in this) FieldError.TOO_LONG else null,
    subject = if (ValidationError.INVALID_SUBJECT in this || ValidationError.INVALID_REFERENCE in this) {
        FieldError.NOT_FOUND
    } else {
        null
    },
)

/** Create (taskId == 0) or edit an existing task. */
class TaskEditorViewModel(
    private val taskId: Long,
    private val taskRepository: TaskRepository,
    subjectRepository: SubjectRepository,
    clock: Clock,
) : ViewModel() {

    private var original: Task? = null

    private val _state = MutableStateFlow(
        TaskEditorUiState(
            isNew = taskId == 0L,
            isLoading = taskId != 0L,
            form = TaskForm(dueDate = LocalDate.now(clock).plusDays(1)),
        ),
    )
    val uiState: StateFlow<TaskEditorUiState> = _state.asStateFlow()

    val subjects: StateFlow<List<Subject>> = subjectRepository.observeSubjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val events = Channel<EditorEvent>(Channel.BUFFERED)
    val eventFlow: Flow<EditorEvent> = events.receiveAsFlow()

    init {
        if (taskId != 0L) {
            viewModelScope.launch {
                val task = taskRepository.observeTask(taskId).first()
                original = task
                _state.update {
                    if (task == null) it.copy(isLoading = false, notFound = true)
                    else it.copy(
                        isLoading = false,
                        form = TaskForm(
                            title = task.title,
                            description = task.description,
                            subjectId = task.subjectId,
                            dueDate = task.dueDate,
                            dueTime = task.dueTime,
                            priority = task.priority,
                            status = task.status,
                        ),
                    )
                }
            }
        }
    }

    private fun edit(block: (TaskForm) -> TaskForm, clear: (TaskFormErrors) -> TaskFormErrors = { it }) =
        _state.update { it.copy(form = block(it.form), errors = clear(it.errors)) }

    fun onTitleChange(v: String) = edit({ it.copy(title = v) }, { it.copy(title = null) })
    fun onDescriptionChange(v: String) = edit({ it.copy(description = v) }, { it.copy(description = null) })
    fun onSubjectChange(id: Long?) = edit({ it.copy(subjectId = id) }, { it.copy(subject = null) })
    fun onDueDateChange(d: LocalDate) = edit({ it.copy(dueDate = d) })
    fun onDueTimeChange(t: LocalTime?) = edit({ it.copy(dueTime = t) })
    fun onPriorityChange(p: Priority) = edit({ it.copy(priority = p) })
    fun onStatusChange(s: TaskStatus) = edit({ it.copy(status = s) })

    private fun TaskForm.toTask() = Task(
        id = taskId,
        title = title.trim(),
        description = description.trim(),
        subjectId = subjectId,
        dueDate = dueDate,
        dueTime = dueTime,
        priority = priority,
        status = status,
        // Only used for local validation; the repository sets the real completion time.
        completedAt = if (status.isActive) null else original?.completedAt ?: Instant.EPOCH,
    )

    fun save() {
        val state = _state.value
        if (state.isSaving || state.isLoading || state.notFound) return
        val task = state.form.toTask()
        val localErrors = validateTask(task).toTaskFormErrors()
        if (localErrors.hasAny) {
            _state.update { it.copy(errors = localErrors) }
            return
        }
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            // completedAt is derived by the repository (kept when a task was already done).
            val toSave = task.copy(completedAt = null)
            val result = if (state.isNew) taskRepository.addTask(toSave).map { } else taskRepository.updateTask(toSave)
            result.onSuccess {
                _state.update { it.copy(isSaving = false) }
                events.send(EditorEvent.Saved)
            }.onFailure { e ->
                val errors = (e as? ValidationException)?.errors?.toTaskFormErrors() ?: TaskFormErrors()
                _state.update { it.copy(isSaving = false, errors = errors, notFound = e is NoSuchElementException) }
            }
        }
    }

    fun delete() {
        if (taskId == 0L) return
        viewModelScope.launch {
            taskRepository.deleteTask(taskId)
            events.send(EditorEvent.Deleted)
        }
    }
}

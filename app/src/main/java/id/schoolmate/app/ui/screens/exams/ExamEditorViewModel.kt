package id.schoolmate.app.ui.screens.exams

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.schoolmate.app.data.repository.ExamRepository
import id.schoolmate.app.data.repository.SubjectRepository
import id.schoolmate.app.domain.model.Exam
import id.schoolmate.app.domain.model.Subject
import id.schoolmate.app.domain.validation.Limits
import id.schoolmate.app.domain.validation.ValidationError
import id.schoolmate.app.domain.validation.ValidationException
import id.schoolmate.app.domain.validation.validateExam
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
import java.time.LocalDate
import java.time.LocalTime

data class ExamForm(
    val title: String = "",
    val subjectId: Long? = null,
    val date: LocalDate,
    val startTime: LocalTime? = LocalTime.of(7, 30),
    /** Raw text so the user can type freely; blank = unknown duration. */
    val durationText: String = "90",
    val location: String = "",
    val note: String = "",
)

data class ExamFormErrors(
    val title: FieldError? = null,
    val subject: FieldError? = null,
    val duration: FieldError? = null,
    val location: FieldError? = null,
    val note: FieldError? = null,
) {
    val hasAny get() = listOf(title, subject, duration, location, note).any { it != null }
}

data class ExamEditorUiState(
    val isNew: Boolean,
    val isLoading: Boolean,
    val form: ExamForm,
    val errors: ExamFormErrors = ExamFormErrors(),
    val isSaving: Boolean = false,
    val notFound: Boolean = false,
)

/** Parses the duration field: blank → null (ok), digits within limits → value, else error. */
fun parseExamDuration(text: String): Result<Int?> {
    val t = text.trim()
    if (t.isEmpty()) return Result.success(null)
    val n = t.toIntOrNull()
    return if (n != null && n in 1..Limits.EXAM_DURATION_MAX_MINUTES) Result.success(n)
    else Result.failure(IllegalArgumentException("invalid duration"))
}

fun Set<ValidationError>.toExamFormErrors() = ExamFormErrors(
    title = when {
        ValidationError.TITLE_EMPTY in this -> FieldError.REQUIRED
        ValidationError.TITLE_TOO_LONG in this -> FieldError.TOO_LONG
        else -> null
    },
    subject = if (ValidationError.INVALID_SUBJECT in this || ValidationError.INVALID_REFERENCE in this) FieldError.NOT_FOUND else null,
    duration = if (ValidationError.INVALID_DURATION in this) FieldError.INVALID else null,
    location = if (ValidationError.TEXT_TOO_LONG in this) FieldError.TOO_LONG else null,
    note = if (ValidationError.NOTE_TOO_LONG in this) FieldError.TOO_LONG else null,
)

class ExamEditorViewModel(
    private val examId: Long,
    private val examRepository: ExamRepository,
    subjectRepository: SubjectRepository,
    clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ExamEditorUiState(
            isNew = examId == 0L,
            isLoading = examId != 0L,
            form = ExamForm(date = LocalDate.now(clock).plusDays(7)),
        ),
    )
    val uiState: StateFlow<ExamEditorUiState> = _state.asStateFlow()

    val subjects: StateFlow<List<Subject>> = subjectRepository.observeSubjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val events = Channel<EditorEvent>(Channel.BUFFERED)
    val eventFlow: Flow<EditorEvent> = events.receiveAsFlow()

    init {
        if (examId != 0L) {
            viewModelScope.launch {
                val exam = examRepository.observeExam(examId).first()
                _state.update {
                    if (exam == null) it.copy(isLoading = false, notFound = true)
                    else it.copy(
                        isLoading = false,
                        form = ExamForm(
                            title = exam.title,
                            subjectId = exam.subjectId,
                            date = exam.date,
                            startTime = exam.startTime,
                            durationText = exam.durationMinutes?.toString().orEmpty(),
                            location = exam.location,
                            note = exam.note,
                        ),
                    )
                }
            }
        }
    }

    private fun edit(block: (ExamForm) -> ExamForm, clear: (ExamFormErrors) -> ExamFormErrors = { it }) =
        _state.update { it.copy(form = block(it.form), errors = clear(it.errors)) }

    fun onTitleChange(v: String) = edit({ it.copy(title = v) }, { it.copy(title = null) })
    fun onSubjectChange(id: Long?) = edit({ it.copy(subjectId = id) }, { it.copy(subject = null) })
    fun onDateChange(d: LocalDate) = edit({ it.copy(date = d) })
    fun onTimeChange(t: LocalTime?) = edit({ it.copy(startTime = t) })
    fun onDurationChange(v: String) {
        if (v.length <= 3 && v.all(Char::isDigit)) edit({ it.copy(durationText = v) }, { it.copy(duration = null) })
    }
    fun onLocationChange(v: String) = edit({ it.copy(location = v) }, { it.copy(location = null) })
    fun onNoteChange(v: String) = edit({ it.copy(note = v) }, { it.copy(note = null) })

    fun save() {
        val state = _state.value
        if (state.isSaving || state.isLoading || state.notFound) return
        val f = state.form
        val duration = parseExamDuration(f.durationText)
        val exam = Exam(
            id = examId, title = f.title.trim(), subjectId = f.subjectId, date = f.date, startTime = f.startTime,
            durationMinutes = duration.getOrNull(), location = f.location.trim(), note = f.note.trim(),
        )
        val errors = validateExam(exam).toExamFormErrors().let {
            if (duration.isFailure) it.copy(duration = FieldError.INVALID) else it
        }
        if (errors.hasAny) {
            _state.update { it.copy(errors = errors) }
            return
        }
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = if (examId == 0L) examRepository.addExam(exam).map { } else examRepository.updateExam(exam)
            result.onSuccess {
                _state.update { it.copy(isSaving = false) }
                events.send(EditorEvent.Saved)
            }.onFailure { e ->
                val mapped = (e as? ValidationException)?.errors?.toExamFormErrors() ?: ExamFormErrors()
                _state.update { it.copy(isSaving = false, errors = mapped, notFound = e is NoSuchElementException) }
            }
        }
    }

    fun delete() {
        if (examId == 0L) return
        viewModelScope.launch {
            examRepository.deleteExam(examId)
            events.send(EditorEvent.Deleted)
        }
    }
}

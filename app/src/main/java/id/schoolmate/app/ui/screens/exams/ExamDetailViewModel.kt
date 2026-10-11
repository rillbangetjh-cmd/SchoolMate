package id.schoolmate.app.ui.screens.exams

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.schoolmate.app.data.repository.ExamRepository
import id.schoolmate.app.data.repository.SubjectRepository
import id.schoolmate.app.ui.model.EditorEvent
import id.schoolmate.app.ui.model.ExamItem
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

sealed interface ExamDetailUiState {
    data object Loading : ExamDetailUiState
    data object NotFound : ExamDetailUiState
    data class Ready(val item: ExamItem) : ExamDetailUiState
}

class ExamDetailViewModel(
    private val examId: Long,
    private val examRepository: ExamRepository,
    subjectRepository: SubjectRepository,
    now: Flow<LocalDateTime>,
) : ViewModel() {

    private val events = Channel<EditorEvent>(Channel.BUFFERED)
    val eventFlow: Flow<EditorEvent> = events.receiveAsFlow()

    val uiState: StateFlow<ExamDetailUiState> = combine(
        examRepository.observeExam(examId),
        subjectRepository.observeSubjects(),
        now,
    ) { exam, subjects, now ->
        if (exam == null) ExamDetailUiState.NotFound
        else ExamDetailUiState.Ready(exam.toItem(subjects.associateBy { it.id }, now.toLocalDate()))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExamDetailUiState.Loading)

    fun delete() {
        viewModelScope.launch {
            examRepository.deleteExam(examId)
            events.send(EditorEvent.Deleted)
        }
    }
}

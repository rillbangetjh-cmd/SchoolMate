package id.schoolmate.app.ui.screens.exams

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.schoolmate.app.data.repository.ExamRepository
import id.schoolmate.app.data.repository.SubjectRepository
import id.schoolmate.app.domain.model.Exam
import id.schoolmate.app.ui.model.ExamItem
import id.schoolmate.app.ui.model.toItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class ExamTab { UPCOMING, PAST }

data class ExamsUiState(
    val isLoading: Boolean = true,
    val tab: ExamTab = ExamTab.UPCOMING,
    val items: List<ExamItem> = emptyList(),
    val upcomingCount: Int = 0,
    val pastCount: Int = 0,
)

private val examChronological = compareBy<Exam> { it.date }.thenBy { it.startTime ?: LocalTime.MIN }.thenBy { it.id }

/** Upcoming = today or later (soonest first); past = before today (most recent first). */
fun List<Exam>.split(today: LocalDate): Pair<List<Exam>, List<Exam>> {
    val (upcoming, past) = partition { !it.date.isBefore(today) }
    return upcoming.sortedWith(examChronological) to past.sortedWith(examChronological.reversed())
}

class ExamsViewModel(
    private val examRepository: ExamRepository,
    subjectRepository: SubjectRepository,
    now: Flow<LocalDateTime>,
) : ViewModel() {

    private val tab = MutableStateFlow(ExamTab.UPCOMING)

    val uiState: StateFlow<ExamsUiState> = combine(
        examRepository.observeAll(),
        subjectRepository.observeSubjects(),
        tab,
        now.map { it.toLocalDate() }.distinctUntilChanged(),
    ) { exams, subjects, tab, today ->
        val byId = subjects.associateBy { it.id }
        val (upcoming, past) = exams.split(today)
        ExamsUiState(
            isLoading = false,
            tab = tab,
            items = (if (tab == ExamTab.UPCOMING) upcoming else past).map { it.toItem(byId, today) },
            upcomingCount = upcoming.size,
            pastCount = past.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExamsUiState())

    fun setTab(value: ExamTab) {
        tab.value = value
    }

    fun delete(id: Long) {
        viewModelScope.launch { examRepository.deleteExam(id) }
    }
}

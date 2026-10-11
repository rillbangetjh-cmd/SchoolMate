package id.schoolmate.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.schoolmate.app.data.preferences.UserPreferencesRepository
import id.schoolmate.app.data.repository.ExamRepository
import id.schoolmate.app.data.repository.ScheduleRepository
import id.schoolmate.app.data.repository.SubjectRepository
import id.schoolmate.app.data.repository.TaskRepository
import id.schoolmate.app.domain.model.TaskDeadlineComparator
import id.schoolmate.app.domain.usecase.DashboardSummary
import id.schoolmate.app.ui.model.ExamItem
import id.schoolmate.app.ui.model.LessonItem
import id.schoolmate.app.ui.model.TaskItem
import id.schoolmate.app.ui.model.toItem
import id.schoolmate.app.ui.model.toLessonItem
import id.schoolmate.app.util.Formatters
import id.schoolmate.app.util.dayPeriodOf
import id.schoolmate.app.util.greetingFor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import id.schoolmate.app.domain.model.Task
import id.schoolmate.app.domain.model.TaskStatus
import java.time.LocalDate
import java.time.LocalDateTime

data class HomeUiState(
    val isLoading: Boolean = true,
    val greeting: String = "",
    val dateLabel: String = "",
    val hasName: Boolean = false,
    val subjectCount: Int = 0,
    val lessonsToday: List<LessonItem> = emptyList(),
    val overdueTasks: List<TaskItem> = emptyList(),
    val upcomingTasks: List<TaskItem> = emptyList(),
    val upcomingExams: List<ExamItem> = emptyList(),
    val summary: DashboardSummary = DashboardSummary(),
)

private data class DayData(
    val date: LocalDate,
    val lessons: List<id.schoolmate.app.domain.model.ScheduleSlot>,
    val exams: List<id.schoolmate.app.domain.model.Exam>,
    val summary: DashboardSummary,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    preferencesRepository: UserPreferencesRepository,
    subjectRepository: SubjectRepository,
    scheduleRepository: ScheduleRepository,
    private val taskRepo: TaskRepository,
    examRepository: ExamRepository,
    observeDashboardSummary: (LocalDate) -> Flow<DashboardSummary>,
    now: Flow<LocalDateTime>,
) : ViewModel() {

    private val dayData: Flow<DayData> = now.map { it.toLocalDate() }.distinctUntilChanged().flatMapLatest { date ->
        combine(
            scheduleRepository.observeDay(date.dayOfWeek),
            examRepository.observeUpcoming(date),
            observeDashboardSummary(date),
        ) { lessons, exams, summary -> DayData(date, lessons, exams, summary) }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        now,
        preferencesRepository.userPreferences,
        subjectRepository.observeSubjects(),
        taskRepo.observeActive(),
        dayData,
    ) { now, prefs, subjects, activeTasks, day ->
        val subjectsById = subjects.associateBy { it.id }
        val tasks = activeTasks.sortedWith(TaskDeadlineComparator).map { it.toItem(subjectsById, now) }
        val (overdue, upcoming) = tasks.partition { it.task.isOverdue(now.toLocalDate(), now.toLocalTime()) }
        HomeUiState(
            isLoading = false,
            greeting = greetingFor(dayPeriodOf(now.toLocalTime()), prefs.studentName),
            dateLabel = Formatters.dateLong(now.toLocalDate()),
            hasName = prefs.studentName.isNotBlank(),
            subjectCount = subjects.size,
            lessonsToday = day.lessons.map { it.toLessonItem(now, isToday = day.date == now.toLocalDate()) },
            overdueTasks = overdue.take(MAX_OVERDUE),
            upcomingTasks = upcoming.take(MAX_UPCOMING_TASKS),
            upcomingExams = day.exams.take(MAX_EXAMS).map { it.toItem(subjectsById, day.date) },
            summary = day.summary,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Quick action from the home list; completed tasks simply disappear from it. */
    fun toggleDone(task: Task) {
        viewModelScope.launch {
            taskRepo.setStatus(task.id, if (task.status == TaskStatus.DONE) TaskStatus.TODO else TaskStatus.DONE)
        }
    }

    companion object {
        const val MAX_OVERDUE = 3
        const val MAX_UPCOMING_TASKS = 5
        const val MAX_EXAMS = 3
    }
}

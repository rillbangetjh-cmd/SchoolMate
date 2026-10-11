package id.schoolmate.app.ui.screens.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.schoolmate.app.data.repository.ScheduleRepository
import id.schoolmate.app.data.repository.SubjectRepository
import id.schoolmate.app.domain.model.ScheduleSlot
import id.schoolmate.app.ui.model.LessonItem
import id.schoolmate.app.ui.model.toLessonItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

data class DayOverview(
    val day: DayOfWeek,
    val lessonCount: Int,
    val totalMinutes: Long,
    val isToday: Boolean,
)

data class ScheduleUiState(
    val isLoading: Boolean = true,
    val selectedDay: DayOfWeek = DayOfWeek.MONDAY,
    val days: List<DayOverview> = DayOfWeek.entries.map { DayOverview(it, 0, 0, false) },
    val lessonsByDay: Map<DayOfWeek, List<LessonItem>> = emptyMap(),
    val hasSubjects: Boolean = false,
    val totalLessons: Int = 0,
) {
    val lessons: List<LessonItem> get() = lessonsByDay[selectedDay].orEmpty()
}

/** Groups slots by ISO day (Monday..Sunday), each day ordered by start time. */
fun List<ScheduleSlot>.groupByDay(): Map<DayOfWeek, List<ScheduleSlot>> {
    val grouped = groupBy { it.entry.dayOfWeek }
    return DayOfWeek.entries.associateWith { day -> grouped[day].orEmpty().sortedBy { it.entry.startTime } }
}

class ScheduleViewModel(
    scheduleRepository: ScheduleRepository,
    subjectRepository: SubjectRepository,
    now: Flow<LocalDateTime>,
    clock: Clock,
) : ViewModel() {

    private val selectedDay = MutableStateFlow(LocalDate.now(clock).dayOfWeek)

    val uiState: StateFlow<ScheduleUiState> = combine(
        scheduleRepository.observeWeek(),
        subjectRepository.observeSubjectCount(),
        selectedDay,
        now,
    ) { slots, subjectCount, selected, now ->
        val byDay = slots.groupByDay()
        val today = now.dayOfWeek
        ScheduleUiState(
            isLoading = false,
            selectedDay = selected,
            days = DayOfWeek.entries.map { day ->
                val list = byDay.getValue(day)
                DayOverview(day, list.size, list.sumOf { it.entry.durationMinutes }, day == today)
            },
            lessonsByDay = byDay.mapValues { (day, list) -> list.map { it.toLessonItem(now, isToday = day == today) } },
            hasSubjects = subjectCount > 0,
            totalLessons = slots.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScheduleUiState(selectedDay = selectedDay.value))

    fun selectDay(day: DayOfWeek) {
        selectedDay.value = day
    }
}

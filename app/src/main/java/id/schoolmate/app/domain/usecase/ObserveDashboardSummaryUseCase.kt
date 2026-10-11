package id.schoolmate.app.domain.usecase

import id.schoolmate.app.data.repository.ExamRepository
import id.schoolmate.app.data.repository.ScheduleRepository
import id.schoolmate.app.data.repository.StudySessionRepository
import id.schoolmate.app.data.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class DashboardSummary(
    val lessonsToday: Int = 0,
    val activeTasks: Int = 0,
    val overdueTasks: Int = 0,
    val examsNext7Days: Int = 0,
    val studyMinutesThisWeek: Int = 0,
)

/** Aggregates the home-screen numbers from every local repository. Weeks start on Monday. */
class ObserveDashboardSummaryUseCase(
    private val scheduleRepository: ScheduleRepository,
    private val taskRepository: TaskRepository,
    private val examRepository: ExamRepository,
    private val studySessionRepository: StudySessionRepository,
    private val clock: Clock,
) {
    operator fun invoke(): Flow<DashboardSummary> {
        val today = LocalDate.now(clock)
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekEnd = weekStart.plusDays(6)
        return combine(
            scheduleRepository.observeLessonCount(today.dayOfWeek),
            taskRepository.observeActiveCount(),
            taskRepository.observeOverdueCount(today),
            examRepository.observeCountBetween(today, today.plusDays(7)),
            studySessionRepository.observeTotalMinutes(weekStart, weekEnd),
        ) { lessons, active, overdue, exams, minutes ->
            DashboardSummary(lessons, active, overdue, exams, minutes)
        }
    }
}

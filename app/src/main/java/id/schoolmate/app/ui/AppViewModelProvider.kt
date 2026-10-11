package id.schoolmate.app.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import id.schoolmate.app.SchoolMateApplication
import id.schoolmate.app.di.AppContainer
import id.schoolmate.app.ui.screens.exams.ExamDetailViewModel
import id.schoolmate.app.ui.screens.exams.ExamEditorViewModel
import id.schoolmate.app.ui.screens.exams.ExamsViewModel
import id.schoolmate.app.ui.screens.home.HomeViewModel
import id.schoolmate.app.ui.screens.schedule.ScheduleEditorViewModel
import id.schoolmate.app.ui.screens.schedule.ScheduleViewModel
import id.schoolmate.app.ui.screens.settings.SettingsViewModel
import id.schoolmate.app.ui.screens.settings.NotificationSettingsViewModel
import id.schoolmate.app.ui.screens.studylog.StudyLogViewModel
import id.schoolmate.app.ui.screens.subjects.SubjectsViewModel
import id.schoolmate.app.ui.screens.tasks.TaskDetailViewModel
import id.schoolmate.app.ui.screens.tasks.TaskEditorViewModel
import id.schoolmate.app.ui.screens.tasks.TasksViewModel
import id.schoolmate.app.util.minuteTicker
import java.time.DayOfWeek
import java.time.LocalDate

/** Factories for every ViewModel; dependencies come from the [AppContainer]. */
object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer { MainViewModel(container().userPreferencesRepository) }
        initializer {
            val c = container()
            HomeViewModel(
                preferencesRepository = c.userPreferencesRepository,
                subjectRepository = c.subjectRepository,
                scheduleRepository = c.scheduleRepository,
                taskRepo = c.taskRepository,
                examRepository = c.examRepository,
                observeDashboardSummary = c.observeDashboardSummary::invoke,
                now = minuteTicker(c.clock),
            )
        }
        initializer { SubjectsViewModel(container().subjectRepository) }
        initializer { SettingsViewModel(container().userPreferencesRepository) }
        initializer { NotificationSettingsViewModel(container().notificationPreferencesRepository) }
        initializer {
            val c = container()
            ScheduleViewModel(c.scheduleRepository, c.subjectRepository, minuteTicker(c.clock), c.clock)
        }
        initializer { val c = container(); TasksViewModel(c.taskRepository, c.subjectRepository, minuteTicker(c.clock)) }
        initializer { val c = container(); ExamsViewModel(c.examRepository, c.subjectRepository, minuteTicker(c.clock)) }
        initializer { val c = container(); StudyLogViewModel(c.studySessionRepository, c.subjectRepository, c.clock) }
    }

    /** [isoDay] 1..7 prefills the day for new entries; 0 = today. */
    fun scheduleEditor(entryId: Long, isoDay: Int): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            val c = container()
            val day = if (isoDay in 1..7) DayOfWeek.of(isoDay) else LocalDate.now(c.clock).dayOfWeek
            ScheduleEditorViewModel(entryId, day, c.scheduleRepository, c.subjectRepository)
        }
    }

    fun taskDetail(taskId: Long): ViewModelProvider.Factory = viewModelFactory {
        initializer { val c = container(); TaskDetailViewModel(taskId, c.taskRepository, c.subjectRepository, minuteTicker(c.clock)) }
    }

    fun taskEditor(taskId: Long): ViewModelProvider.Factory = viewModelFactory {
        initializer { val c = container(); TaskEditorViewModel(taskId, c.taskRepository, c.subjectRepository, c.clock) }
    }

    fun examDetail(examId: Long): ViewModelProvider.Factory = viewModelFactory {
        initializer { val c = container(); ExamDetailViewModel(examId, c.examRepository, c.subjectRepository, minuteTicker(c.clock)) }
    }

    fun examEditor(examId: Long): ViewModelProvider.Factory = viewModelFactory {
        initializer { val c = container(); ExamEditorViewModel(examId, c.examRepository, c.subjectRepository, c.clock) }
    }
}

private fun CreationExtras.container(): AppContainer =
    (this[APPLICATION_KEY] as SchoolMateApplication).container

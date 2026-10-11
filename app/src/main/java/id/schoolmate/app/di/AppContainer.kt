package id.schoolmate.app.di

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import id.schoolmate.app.data.preferences.DataStoreNotificationPreferencesRepository
import id.schoolmate.app.data.preferences.NotificationPreferencesRepository
import id.schoolmate.app.notifications.AlarmReminderScheduler
import id.schoolmate.app.notifications.AndroidReminderNotifier
import id.schoolmate.app.notifications.DataStoreReminderRegistry
import id.schoolmate.app.notifications.ReminderSync
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

import android.content.Context
import id.schoolmate.app.data.local.SchoolMateDatabase
import id.schoolmate.app.data.preferences.DataStoreUserPreferencesRepository
import id.schoolmate.app.data.preferences.UserPreferencesRepository
import id.schoolmate.app.data.preferences.userPreferencesDataStore
import id.schoolmate.app.data.repository.ExamRepository
import id.schoolmate.app.data.repository.OfflineExamRepository
import id.schoolmate.app.data.repository.OfflineScheduleRepository
import id.schoolmate.app.data.repository.OfflineStudySessionRepository
import id.schoolmate.app.data.repository.OfflineSubjectRepository
import id.schoolmate.app.data.repository.OfflineTaskRepository
import id.schoolmate.app.data.repository.ScheduleRepository
import id.schoolmate.app.data.repository.StudySessionRepository
import id.schoolmate.app.data.repository.SubjectRepository
import id.schoolmate.app.data.repository.TaskRepository
import id.schoolmate.app.domain.usecase.ObserveDashboardSummaryUseCase
import id.schoolmate.app.util.DeviceClock
import java.time.Clock

/** Manual dependency container. Keeps the dependency graph explicit and free of codegen. */
interface AppContainer {
    val clock: Clock
    val userPreferencesRepository: UserPreferencesRepository
    val subjectRepository: SubjectRepository
    val scheduleRepository: ScheduleRepository
    val taskRepository: TaskRepository
    val examRepository: ExamRepository
    val studySessionRepository: StudySessionRepository
    val observeDashboardSummary: ObserveDashboardSummaryUseCase

    /** Lives as long as the process; used by receivers (goAsync) and the reminder observer. */
    val applicationScope: CoroutineScope
    val notificationPreferencesRepository: NotificationPreferencesRepository
    val reminderNotifier: AndroidReminderNotifier
    val reminderSync: ReminderSync
}

class DefaultAppContainer(context: Context) : AppContainer {
    private val appContext = context.applicationContext

    private val database: SchoolMateDatabase by lazy { SchoolMateDatabase.getInstance(appContext) }

    /** Device clock that follows zone changes; tests use a fixed clock instead. */
    override val clock: Clock = DeviceClock

    override val userPreferencesRepository: UserPreferencesRepository by lazy {
        DataStoreUserPreferencesRepository(appContext.userPreferencesDataStore)
    }

    override val subjectRepository: SubjectRepository by lazy {
        OfflineSubjectRepository(database.subjectDao(), clock::millis)
    }

    override val scheduleRepository: ScheduleRepository by lazy {
        OfflineScheduleRepository(database.scheduleDao(), clock)
    }

    override val taskRepository: TaskRepository by lazy { OfflineTaskRepository(database.taskDao(), clock) }

    override val examRepository: ExamRepository by lazy { OfflineExamRepository(database.examDao(), clock) }

    override val studySessionRepository: StudySessionRepository by lazy {
        OfflineStudySessionRepository(database.studySessionDao(), clock)
    }

    override val observeDashboardSummary: ObserveDashboardSummaryUseCase by lazy {
        ObserveDashboardSummaryUseCase(scheduleRepository, taskRepository, examRepository, studySessionRepository, clock)
    }

    override val applicationScope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> Log.w("SchoolMate", "Background work failed", e) },
    )

    override val notificationPreferencesRepository: NotificationPreferencesRepository by lazy {
        DataStoreNotificationPreferencesRepository(appContext.userPreferencesDataStore)
    }

    override val reminderNotifier: AndroidReminderNotifier by lazy { AndroidReminderNotifier(appContext, clock) }

    override val reminderSync: ReminderSync by lazy {
        ReminderSync(
            prefs = notificationPreferencesRepository,
            tasks = taskRepository,
            exams = examRepository,
            schedule = scheduleRepository,
            subjects = subjectRepository,
            scheduler = AlarmReminderScheduler(appContext),
            notifier = reminderNotifier,
            registry = DataStoreReminderRegistry(appContext.reminderRegistryDataStore),
            clock = clock,
        )
    }
}

/** Bookkeeping for scheduled alarms; kept apart from user data and excluded from backups. */
val Context.reminderRegistryDataStore: DataStore<Preferences> by preferencesDataStore(name = "reminder_registry")

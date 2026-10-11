package id.schoolmate.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.ui.graphics.vector.ImageVector
import id.schoolmate.app.R
import kotlinx.serialization.Serializable

// Type-safe Navigation Compose routes. An id of 0 means "create new".
@Serializable data object HomeRoute
@Serializable data object ScheduleRoute
@Serializable data object TasksRoute
@Serializable data object ExamsRoute
@Serializable data object SettingsRoute
@Serializable data object SubjectsRoute
@Serializable data object StudyLogRoute

/** [day] is an ISO day-of-week (1 = Senin) used to prefill new entries; 0 = today. */
@Serializable data class ScheduleEditRoute(val entryId: Long = 0L, val day: Int = 0)
@Serializable data class TaskDetailRoute(val taskId: Long)
@Serializable data class TaskEditRoute(val taskId: Long = 0L)
@Serializable data class ExamDetailRoute(val examId: Long)
@Serializable data class ExamEditRoute(val examId: Long = 0L)

enum class TopLevelDestination(
    val route: Any,
    @param:StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    HOME(HomeRoute, R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home),
    SCHEDULE(ScheduleRoute, R.string.nav_schedule, Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth),
    TASKS(TasksRoute, R.string.nav_tasks, Icons.Filled.TaskAlt, Icons.Outlined.TaskAlt),
    EXAMS(ExamsRoute, R.string.nav_exams, Icons.Filled.Event, Icons.Outlined.Event),
    SETTINGS(SettingsRoute, R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings),
}

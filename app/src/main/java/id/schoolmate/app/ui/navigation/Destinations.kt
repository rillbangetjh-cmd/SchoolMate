package id.schoolmate.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.ui.graphics.vector.ImageVector
import id.schoolmate.app.R
import kotlinx.serialization.Serializable

// Type-safe Navigation Compose routes.
@Serializable data object HomeRoute
@Serializable data object SubjectsRoute
@Serializable data object TasksRoute
@Serializable data object FocusRoute
@Serializable data object SettingsRoute

enum class TopLevelDestination(
    val route: Any,
    @param:StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    HOME(HomeRoute, R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home),
    SUBJECTS(SubjectsRoute, R.string.nav_subjects, Icons.AutoMirrored.Filled.MenuBook, Icons.AutoMirrored.Outlined.MenuBook),
    TASKS(TasksRoute, R.string.nav_tasks, Icons.Filled.TaskAlt, Icons.Outlined.TaskAlt),
    FOCUS(FocusRoute, R.string.nav_focus, Icons.Filled.Timer, Icons.Outlined.Timer),
    SETTINGS(SettingsRoute, R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings),
}

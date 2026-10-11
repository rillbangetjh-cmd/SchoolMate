package id.schoolmate.app.ui.screens.tasks

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import id.schoolmate.app.R
import id.schoolmate.app.ui.components.EmptyState

@Composable
fun TasksScreen() {
    EmptyState(
        icon = Icons.Outlined.TaskAlt,
        title = stringResource(R.string.tasks_title),
        message = stringResource(R.string.tasks_coming_soon),
    )
}

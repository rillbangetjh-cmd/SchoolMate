package id.schoolmate.app.ui.screens.focus

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import id.schoolmate.app.R
import id.schoolmate.app.ui.components.EmptyState

@Composable
fun FocusScreen() {
    EmptyState(
        icon = Icons.Outlined.Timer,
        title = stringResource(R.string.focus_title),
        message = stringResource(R.string.focus_coming_soon),
    )
}

package id.schoolmate.app.ui.screens.exams

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import id.schoolmate.app.R
import id.schoolmate.app.ui.AppViewModelProvider
import id.schoolmate.app.ui.components.EmptyState
import id.schoolmate.app.ui.components.ExamRow
import id.schoolmate.app.ui.components.LoadingBox

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamsScreen(
    onOpenExam: (Long) -> Unit,
    onAddExam: () -> Unit,
    viewModel: ExamsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.exams_title)) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddExam,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.exams_add)) },
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = state.tab.ordinal) {
                ExamTab.entries.forEach { tab ->
                    val count = if (tab == ExamTab.UPCOMING) state.upcomingCount else state.pastCount
                    val label = stringResource(if (tab == ExamTab.UPCOMING) R.string.exams_tab_upcoming else R.string.exams_tab_past)
                    Tab(
                        selected = state.tab == tab,
                        onClick = { viewModel.setTab(tab) },
                        text = { Text(if (count > 0) "$label ($count)" else label) },
                    )
                }
            }
            when {
                state.isLoading -> LoadingBox()
                state.items.isEmpty() -> EmptyState(
                    icon = Icons.Outlined.Event,
                    title = stringResource(R.string.exams_empty_title),
                    message = stringResource(if (state.tab == ExamTab.UPCOMING) R.string.exams_empty_body else R.string.exams_empty_past),
                    actionLabel = if (state.tab == ExamTab.UPCOMING) stringResource(R.string.exams_add) else null,
                    onAction = onAddExam,
                )
                else -> LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)) {
                    items(state.items, key = { it.exam.id }) { item ->
                        ExamRow(item, onClick = { onOpenExam(item.exam.id) }, modifier = Modifier.animateItem())
                    }
                }
            }
        }
    }
}

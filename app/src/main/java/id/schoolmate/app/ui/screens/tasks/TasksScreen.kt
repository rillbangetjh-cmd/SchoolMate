package id.schoolmate.app.ui.screens.tasks

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import id.schoolmate.app.R
import id.schoolmate.app.domain.model.TaskFilter
import id.schoolmate.app.ui.AppViewModelProvider
import id.schoolmate.app.ui.components.EmptyState
import id.schoolmate.app.ui.components.LoadingBox
import id.schoolmate.app.ui.components.TaskRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    onOpenTask: (Long) -> Unit,
    onAddTask: () -> Unit,
    viewModel: TasksViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val completedMsg = stringResource(R.string.tasks_completed_snackbar)
    val undoLabel = stringResource(R.string.action_undo)

    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is TasksEvent.Completed -> {
                    val result = snackbar.showSnackbar(completedMsg, actionLabel = undoLabel, duration = SnackbarDuration.Short)
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoComplete(event)
                }
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tasks_title)) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddTask,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.tasks_add)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            FilterRow(state.filter, state.counts, viewModel::setFilter)
            HorizontalDivider()
            when {
                state.isLoading -> LoadingBox()
                state.items.isEmpty() -> EmptyState(
                    icon = Icons.Outlined.TaskAlt,
                    title = if (state.totalTasks == 0) stringResource(R.string.tasks_empty_title) else filterLabel(state.filter),
                    message = when {
                        state.totalTasks == 0 -> stringResource(R.string.tasks_empty_body)
                        state.filter == TaskFilter.ACTIVE -> stringResource(R.string.tasks_empty_active)
                        state.filter == TaskFilter.OVERDUE -> stringResource(R.string.tasks_empty_overdue)
                        else -> stringResource(R.string.tasks_empty_done)
                    },
                    actionLabel = if (state.totalTasks == 0) stringResource(R.string.tasks_add) else null,
                    onAction = onAddTask,
                )
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(state.items, key = { it.task.id }) { item ->
                        TaskRow(
                            item = item,
                            onClick = { onOpenTask(item.task.id) },
                            onToggleDone = { viewModel.toggleDone(item.task) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun filterLabel(filter: TaskFilter) = when (filter) {
    TaskFilter.ALL -> stringResource(R.string.tasks_filter_all)
    TaskFilter.ACTIVE -> stringResource(R.string.tasks_filter_active)
    TaskFilter.OVERDUE -> stringResource(R.string.tasks_filter_overdue)
    TaskFilter.DONE -> stringResource(R.string.tasks_filter_done)
}

@Composable
private fun FilterRow(selected: TaskFilter, counts: Map<TaskFilter, Int>, onSelect: (TaskFilter) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(TaskFilter.ACTIVE, TaskFilter.OVERDUE, TaskFilter.DONE, TaskFilter.ALL).forEach { filter ->
            val count = counts[filter] ?: 0
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = { Text(if (count > 0) "${filterLabel(filter)} $count" else filterLabel(filter)) },
            )
        }
    }
}

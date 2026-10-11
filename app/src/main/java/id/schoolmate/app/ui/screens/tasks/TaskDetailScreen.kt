package id.schoolmate.app.ui.screens.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.schoolmate.app.R
import id.schoolmate.app.domain.model.TaskStatus
import id.schoolmate.app.ui.components.DeadlinePill
import id.schoolmate.app.ui.components.DeleteConfirmDialog
import id.schoolmate.app.ui.components.EmptyState
import id.schoolmate.app.ui.components.LoadingBox
import id.schoolmate.app.ui.components.SubjectDot
import id.schoolmate.app.ui.components.priorityLabel
import id.schoolmate.app.ui.components.statusLabel
import id.schoolmate.app.ui.model.EditorEvent
import id.schoolmate.app.util.Formatters
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    viewModel: TaskDetailViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(viewModel) { viewModel.eventFlow.collect { if (it == EditorEvent.Deleted) onBack() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                },
                actions = {
                    if (state is TaskDetailUiState.Ready) {
                        IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, stringResource(R.string.action_edit)) }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete)) }
                    }
                },
            )
        },
    ) { padding ->
        when (val s = state) {
            TaskDetailUiState.Loading -> LoadingBox(Modifier.padding(padding))
            TaskDetailUiState.NotFound -> EmptyState(
                Icons.Outlined.SearchOff, stringResource(R.string.not_found_title), stringResource(R.string.not_found_body),
                Modifier.padding(padding),
            )
            is TaskDetailUiState.Ready -> {
                val task = s.item.task
                Column(
                    Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SubjectDot(s.item.subject?.let { Color(it.colorArgb) })
                        Text(
                            s.item.subject?.name ?: stringResource(R.string.no_subject),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(task.title, style = MaterialTheme.typography.headlineSmall)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DeadlinePill(s.item.urgency)
                        Text(
                            stringResource(R.string.task_field_priority) + ": " + priorityLabel(task.priority),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DetailLine(
                        stringResource(R.string.task_detail_deadline),
                        Formatters.dateLong(task.dueDate) + " · " +
                            (task.dueTime?.let(Formatters::time) ?: stringResource(R.string.task_field_due_time_none)),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.task_field_status), style = MaterialTheme.typography.labelLarge)
                        StatusSelector(task.status, viewModel::setStatus)
                        task.completedAt?.let {
                            val local = it.atZone(ZoneId.systemDefault())
                            Text(
                                stringResource(R.string.task_detail_completed_at, Formatters.dateShort(local.toLocalDate()) + " " + Formatters.time(local.toLocalTime())),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    HorizontalDivider()
                    Text(
                        task.description.ifBlank { stringResource(R.string.task_detail_no_description) },
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (task.description.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (confirmDelete) {
                    DeleteConfirmDialog(task.title, onConfirm = viewModel::delete, onDismiss = { confirmDelete = false })
                }
            }
        }
    }
}

@Composable
internal fun DetailLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StatusSelector(selected: TaskStatus, onSelect: (TaskStatus) -> Unit) {
    val options = TaskStatus.entries
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, status ->
            SegmentedButton(
                selected = status == selected,
                onClick = { onSelect(status) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) { Text(statusLabel(status), maxLines = 1) }
        }
    }
}

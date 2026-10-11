package id.schoolmate.app.ui.screens.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.schoolmate.app.R
import id.schoolmate.app.domain.model.Priority
import id.schoolmate.app.domain.validation.Limits
import id.schoolmate.app.ui.components.DateField
import id.schoolmate.app.ui.components.DeleteConfirmDialog
import id.schoolmate.app.ui.components.EmptyState
import id.schoolmate.app.ui.components.LoadingBox
import id.schoolmate.app.ui.components.SubjectField
import id.schoolmate.app.ui.components.TimeField
import id.schoolmate.app.ui.components.fieldErrorText
import id.schoolmate.app.ui.components.priorityLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorScreen(viewModel: TaskEditorViewModel, onClose: () -> Unit, onDeleted: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val subjects by viewModel.subjects.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                id.schoolmate.app.ui.model.EditorEvent.Saved -> onClose()
                id.schoolmate.app.ui.model.EditorEvent.Deleted -> onDeleted()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.isNew) R.string.tasks_add else R.string.tasks_edit)) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, stringResource(R.string.action_cancel)) } },
                actions = {
                    if (!state.isNew && !state.notFound) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete)) }
                    }
                    TextButton(onClick = viewModel::save, enabled = !state.isSaving && !state.isLoading && !state.notFound) {
                        Text(stringResource(R.string.action_save))
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> LoadingBox(Modifier.padding(padding))
            state.notFound -> EmptyState(Icons.Outlined.SearchOff, stringResource(R.string.not_found_title), stringResource(R.string.not_found_body), Modifier.padding(padding))
            else -> {
                val f = state.form
                Column(
                    Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = f.title,
                        onValueChange = viewModel::onTitleChange,
                        label = { Text(stringResource(R.string.task_field_title)) },
                        singleLine = true,
                        isError = state.errors.title != null,
                        supportingText = { Text(fieldErrorText(state.errors.title, Limits.TITLE_MAX) ?: "${f.title.trim().length}/${Limits.TITLE_MAX}") },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SubjectField(
                        subjects = subjects,
                        selectedId = f.subjectId,
                        label = stringResource(R.string.task_field_subject),
                        onSelect = viewModel::onSubjectChange,
                        error = fieldErrorText(state.errors.subject),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DateField(f.dueDate, stringResource(R.string.task_field_due_date), viewModel::onDueDateChange, Modifier.weight(1.4f))
                        TimeField(
                            time = f.dueTime,
                            label = stringResource(R.string.task_field_due_time),
                            onTimeChange = viewModel::onDueTimeChange,
                            emptyLabel = stringResource(R.string.task_field_due_time_none),
                            onClear = { viewModel.onDueTimeChange(null) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(stringResource(R.string.task_field_priority), style = MaterialTheme.typography.labelLarge)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        Priority.entries.forEachIndexed { i, p ->
                            SegmentedButton(
                                selected = f.priority == p,
                                onClick = { viewModel.onPriorityChange(p) },
                                shape = SegmentedButtonDefaults.itemShape(i, Priority.entries.size),
                            ) { Text(priorityLabel(p)) }
                        }
                    }
                    Text(stringResource(R.string.task_field_status), style = MaterialTheme.typography.labelLarge)
                    StatusSelector(f.status, viewModel::onStatusChange)
                    OutlinedTextField(
                        value = f.description,
                        onValueChange = viewModel::onDescriptionChange,
                        label = { Text(stringResource(R.string.task_field_description)) },
                        minLines = 3,
                        isError = state.errors.description != null,
                        supportingText = state.errors.description?.let { { Text(fieldErrorText(it, Limits.DESCRIPTION_MAX).orEmpty()) } },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = viewModel::save,
                        enabled = !state.isSaving,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    ) { Text(stringResource(R.string.action_save)) }
                }
            }
        }
    }
    if (confirmDelete) {
        DeleteConfirmDialog(state.form.title, onConfirm = viewModel::delete, onDismiss = { confirmDelete = false })
    }
}

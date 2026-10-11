package id.schoolmate.app.ui.screens.schedule

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.schoolmate.app.R
import id.schoolmate.app.domain.validation.Limits
import id.schoolmate.app.ui.components.DeleteConfirmDialog
import id.schoolmate.app.ui.components.EmptyState
import id.schoolmate.app.ui.components.LoadingBox
import id.schoolmate.app.ui.components.SubjectField
import id.schoolmate.app.ui.components.TimeField
import id.schoolmate.app.ui.components.fieldErrorText
import id.schoolmate.app.ui.model.EditorEvent
import id.schoolmate.app.util.Formatters
import java.time.DayOfWeek
import java.time.Duration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleEditorScreen(viewModel: ScheduleEditorViewModel, onClose: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(viewModel) { viewModel.eventFlow.collect { if (it == EditorEvent.Saved || it == EditorEvent.Deleted) onClose() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.isNew) R.string.schedule_add else R.string.schedule_edit)) },
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
                    Text(stringResource(R.string.schedule_field_day), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DayOfWeek.entries.forEach { day ->
                            FilterChip(selected = f.day == day, onClick = { viewModel.onDayChange(day) }, label = { Text(Formatters.dayShort(day)) })
                        }
                    }
                    SubjectField(
                        subjects = state.subjects,
                        selectedId = f.subjectId,
                        label = stringResource(R.string.schedule_field_subject),
                        onSelect = { id -> id?.let(viewModel::onSubjectChange) },
                        allowNone = false,
                        error = if (state.errors.subject != null) stringResource(R.string.schedule_error_subject) else null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TimeField(f.start, stringResource(R.string.schedule_field_start), viewModel::onStartChange, Modifier.weight(1f),
                            isError = state.errors.time != null || state.conflicts.isNotEmpty())
                        TimeField(f.end, stringResource(R.string.schedule_field_end), viewModel::onEndChange, Modifier.weight(1f),
                            isError = state.errors.time != null || state.conflicts.isNotEmpty())
                    }
                    val minutes = Duration.between(f.start, f.end).toMinutes()
                    val timeError = when (state.errors.time ?: if (minutes <= 0) TimeRangeError.END_BEFORE_START else null) {
                        TimeRangeError.END_BEFORE_START -> stringResource(R.string.schedule_error_end_before_start)
                        TimeRangeError.TOO_SHORT -> stringResource(R.string.schedule_error_too_short, Limits.SCHEDULE_MIN_MINUTES)
                        null -> null
                    }
                    Text(
                        timeError ?: stringResource(R.string.schedule_duration, Formatters.duration(minutes)),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (timeError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AnimatedVisibility(
                        visible = state.conflicts.isNotEmpty(),
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        val c = state.conflicts.firstOrNull()
                        if (c != null) {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                                    Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                                    Column {
                                        Text(stringResource(R.string.schedule_conflict_title), style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer)
                                        Text(
                                            stringResource(R.string.schedule_conflict_body, c.subject.name, Formatters.timeRange(c.entry.startTime, c.entry.endTime)),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = f.room, onValueChange = viewModel::onRoomChange,
                        label = { Text(stringResource(R.string.schedule_field_room)) }, singleLine = true,
                        isError = state.errors.room != null,
                        supportingText = state.errors.room?.let { { Text(fieldErrorText(it, Limits.SHORT_TEXT_MAX).orEmpty()) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = f.note, onValueChange = viewModel::onNoteChange,
                        label = { Text(stringResource(R.string.schedule_field_note)) }, minLines = 2,
                        isError = state.errors.note != null,
                        supportingText = state.errors.note?.let { { Text(fieldErrorText(it, Limits.NOTE_MAX).orEmpty()) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = viewModel::save,
                        enabled = !state.isSaving && state.conflicts.isEmpty(),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    ) { Text(stringResource(R.string.action_save)) }
                }
            }
        }
    }
    if (confirmDelete) {
        val name = state.subjects.firstOrNull { it.id == state.form.subjectId }?.name.orEmpty()
        DeleteConfirmDialog(
            "$name (${Formatters.dayName(state.form.day)} ${Formatters.timeRange(state.form.start, state.form.end)})",
            onConfirm = viewModel::delete,
            onDismiss = { confirmDelete = false },
        )
    }
}

package id.schoolmate.app.ui.screens.studylog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import id.schoolmate.app.R
import id.schoolmate.app.domain.validation.Limits
import id.schoolmate.app.ui.AppViewModelProvider
import id.schoolmate.app.ui.components.DateField
import id.schoolmate.app.ui.components.EmptyState
import id.schoolmate.app.ui.components.LoadingBox
import id.schoolmate.app.ui.components.SectionHeader
import id.schoolmate.app.ui.components.SubjectDot
import id.schoolmate.app.ui.components.SubjectField
import id.schoolmate.app.ui.components.TimeField
import id.schoolmate.app.ui.components.fieldErrorText
import id.schoolmate.app.util.Formatters
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyLogScreen(
    onBack: () -> Unit,
    viewModel: StudyLogViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val editor by viewModel.editor.collectAsStateWithLifecycle()
    val subjectList by viewModel.subjects.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.study_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = viewModel::openEditor,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.study_add)) },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
            item(key = "week") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = viewModel::previousWeek) { Icon(Icons.Outlined.ChevronLeft, stringResource(R.string.study_previous_week)) }
                    Text(
                        stringResource(R.string.study_week_range, Formatters.dateShort(state.weekStart), Formatters.dateShort(state.weekEnd)),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = viewModel::nextWeek, enabled = !state.isCurrentWeek) {
                        Icon(Icons.Outlined.ChevronRight, stringResource(R.string.study_next_week))
                    }
                }
            }
            item(key = "total") {
                Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            stringResource(if (state.isCurrentWeek) R.string.study_week_total else R.string.study_week_total_other),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(Formatters.duration(state.totalMinutes.toLong()), style = MaterialTheme.typography.headlineMedium)
                    }
                }
            }
            if (state.sessions.isEmpty()) {
                item(key = "empty") {
                    EmptyState(Icons.Outlined.Timer, stringResource(R.string.study_empty_title), stringResource(R.string.study_empty_body), Modifier.padding(top = 24.dp))
                }
            } else {
                if (state.perSubject.isNotEmpty()) {
                    item(key = "per_subject_header") { SectionHeader(stringResource(R.string.study_per_subject)) }
                    items(state.perSubject, key = { "ps-${it.subject?.id ?: 0}" }) { row ->
                        val fraction = if (state.totalMinutes > 0) row.minutes / state.totalMinutes.toFloat() else 0f
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp).animateItem()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SubjectDot(row.subject?.let { Color(it.colorArgb) })
                                Text(row.subject?.name ?: stringResource(R.string.no_subject), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Text(Formatters.durationShort(row.minutes.toLong()), style = MaterialTheme.typography.labelLarge)
                            }
                            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                        }
                    }
                }
                item(key = "sessions_header") { SectionHeader(stringResource(R.string.study_sessions)) }
                items(state.sessions, key = { it.first.id }) { (session, subject) ->
                    val start = session.startedAt.atZone(ZoneId.systemDefault()).toLocalTime()
                    ListItem(
                        modifier = Modifier.animateItem(),
                        leadingContent = { SubjectDot(subject?.let { Color(it.colorArgb) }, size = 12.dp) },
                        headlineContent = { Text(subject?.name ?: stringResource(R.string.no_subject)) },
                        supportingContent = {
                            Text(
                                listOfNotNull(
                                    Formatters.dateShort(session.date) + " · " + Formatters.time(start),
                                    session.note.takeIf { it.isNotBlank() },
                                ).joinToString(" · "),
                                maxLines = 2,
                            )
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(Formatters.durationShort(session.durationMinutes.toLong()), style = MaterialTheme.typography.labelLarge)
                                IconButton(onClick = { viewModel.delete(session.id) }) {
                                    Icon(Icons.Outlined.Delete, stringResource(R.string.study_delete_cd))
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    editor?.let { formState ->
        val f = formState.form
        AlertDialog(
            onDismissRequest = viewModel::dismissEditor,
            title = { Text(stringResource(R.string.study_add)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SubjectField(subjectList, f.subjectId, stringResource(R.string.study_field_subject), { viewModel.onFormChange(f.copy(subjectId = it)) }, Modifier.fillMaxWidth())
                    DateField(f.date, stringResource(R.string.study_field_date), { viewModel.onFormChange(f.copy(date = it)) }, Modifier.fillMaxWidth())
                    TimeField(f.start, stringResource(R.string.study_field_start), { viewModel.onFormChange(f.copy(start = it)) }, Modifier.fillMaxWidth())
                    OutlinedTextField(
                        value = f.durationText,
                        onValueChange = { v -> if (v.length <= 3 && v.all(Char::isDigit)) viewModel.onFormChange(f.copy(durationText = v)) },
                        label = { Text(stringResource(R.string.study_field_duration)) },
                        singleLine = true,
                        isError = formState.durationError != null,
                        supportingText = formState.durationError?.let {
                            { Text(stringResource(R.string.study_error_duration, Limits.STUDY_SESSION_MIN_MINUTES, Limits.STUDY_SESSION_MAX_MINUTES)) }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = f.note,
                        onValueChange = { viewModel.onFormChange(f.copy(note = it)) },
                        label = { Text(stringResource(R.string.study_field_note)) },
                        isError = formState.noteError != null,
                        supportingText = formState.noteError?.let { { Text(fieldErrorText(it, Limits.NOTE_MAX).orEmpty()) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = { TextButton(onClick = viewModel::saveSession) { Text(stringResource(R.string.action_save)) } },
            dismissButton = { TextButton(onClick = viewModel::dismissEditor) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

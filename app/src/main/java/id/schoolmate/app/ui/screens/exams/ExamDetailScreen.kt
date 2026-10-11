package id.schoolmate.app.ui.screens.exams

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import id.schoolmate.app.ui.components.DeleteConfirmDialog
import id.schoolmate.app.ui.components.EmptyState
import id.schoolmate.app.ui.components.LoadingBox
import id.schoolmate.app.ui.components.Pill
import id.schoolmate.app.ui.components.SubjectDot
import id.schoolmate.app.ui.components.examCountdown
import id.schoolmate.app.ui.model.EditorEvent
import id.schoolmate.app.ui.screens.tasks.DetailLine
import id.schoolmate.app.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamDetailScreen(viewModel: ExamDetailViewModel, onBack: () -> Unit, onEdit: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(viewModel) { viewModel.eventFlow.collect { if (it == EditorEvent.Deleted) onBack() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
                actions = {
                    if (state is ExamDetailUiState.Ready) {
                        IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, stringResource(R.string.action_edit)) }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete)) }
                    }
                },
            )
        },
    ) { padding ->
        when (val s = state) {
            ExamDetailUiState.Loading -> LoadingBox(Modifier.padding(padding))
            ExamDetailUiState.NotFound -> EmptyState(Icons.Outlined.SearchOff, stringResource(R.string.not_found_title), stringResource(R.string.not_found_body), Modifier.padding(padding))
            is ExamDetailUiState.Ready -> {
                val exam = s.item.exam
                Column(
                    Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SubjectDot(s.item.subject?.let { Color(it.colorArgb) })
                        Text(s.item.subject?.name ?: stringResource(R.string.no_subject), style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(exam.title, style = MaterialTheme.typography.headlineSmall)
                    Pill(examCountdown(s.item.daysUntil), MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                    DetailLine(
                        stringResource(R.string.exam_detail_when),
                        Formatters.dateLong(exam.date) + (exam.startTime?.let { " · " + Formatters.time(it) } ?: ""),
                    )
                    exam.durationMinutes?.let { DetailLine(stringResource(R.string.exam_detail_duration), Formatters.duration(it.toLong())) }
                    if (exam.location.isNotBlank()) DetailLine(stringResource(R.string.exam_detail_location), exam.location)
                    if (exam.note.isNotBlank()) DetailLine(stringResource(R.string.exam_detail_note), exam.note)
                }
                if (confirmDelete) DeleteConfirmDialog(exam.title, onConfirm = viewModel::delete, onDismiss = { confirmDelete = false })
            }
        }
    }
}

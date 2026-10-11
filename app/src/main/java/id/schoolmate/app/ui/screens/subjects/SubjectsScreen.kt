package id.schoolmate.app.ui.screens.subjects

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import id.schoolmate.app.R
import id.schoolmate.app.domain.model.Subject
import id.schoolmate.app.ui.AppViewModelProvider
import id.schoolmate.app.ui.components.EmptyState
import id.schoolmate.app.ui.theme.SubjectPalette
import id.schoolmate.app.util.MAX_SUBJECT_NAME_LENGTH

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectsScreen(
    onBack: () -> Unit,
    viewModel: SubjectsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val subjects by viewModel.subjects.collectAsStateWithLifecycle()
    val editor by viewModel.editor.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<Subject?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.subjects_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = viewModel::openEditor,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.subjects_add)) },
            )
        },
    ) { padding ->
        val list = subjects
        when {
            list == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            list.isEmpty() -> EmptyState(
                icon = Icons.AutoMirrored.Outlined.MenuBook,
                title = stringResource(R.string.subjects_empty_title),
                message = stringResource(R.string.subjects_empty_body),
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp),
            ) {
                items(list, key = { it.id }) { subject ->
                    ListItem(
                        modifier = Modifier.animateItem(),
                        leadingContent = {
                            Box(Modifier.size(16.dp).clip(CircleShape).background(Color(subject.colorArgb)))
                        },
                        headlineContent = { Text(subject.name, fontWeight = FontWeight.Medium) },
                        supportingContent = if (subject.teacher.isNotBlank()) {
                            { Text(subject.teacher) }
                        } else null,
                        trailingContent = {
                            IconButton(onClick = { pendingDelete = subject }) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = stringResource(R.string.subjects_delete_cd, subject.name),
                                )
                            }
                        },
                    )
                    HorizontalDivider(Modifier.animateItem())
                }
            }
        }
    }

    editor?.let { state ->
        SubjectEditorDialog(
            state = state,
            onNameChange = viewModel::onNameChange,
            onTeacherChange = viewModel::onTeacherChange,
            onColorChange = viewModel::onColorChange,
            onSave = viewModel::save,
            onDismiss = viewModel::dismissEditor,
        )
    }

    pendingDelete?.let { subject ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.subjects_delete_title)) },
            text = { Text(stringResource(R.string.subjects_delete_body, subject.name)) },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(subject.id); pendingDelete = null }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun SubjectEditorDialog(
    state: SubjectEditorState,
    onNameChange: (String) -> Unit,
    onTeacherChange: (String) -> Unit,
    onColorChange: (Int) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.subjects_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(R.string.subjects_field_name)) },
                    singleLine = true,
                    isError = state.error != null,
                    supportingText = state.error?.let { error ->
                        {
                            Text(
                                when (error) {
                                    SubjectEditorError.EMPTY -> stringResource(R.string.subjects_error_empty)
                                    SubjectEditorError.TOO_LONG -> stringResource(R.string.subjects_error_too_long, MAX_SUBJECT_NAME_LENGTH)
                                    SubjectEditorError.DUPLICATE -> stringResource(R.string.subjects_error_duplicate)
                                },
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.teacher,
                    onValueChange = onTeacherChange,
                    label = { Text(stringResource(R.string.subjects_field_teacher)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.subjects_field_color), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SubjectPalette.forEachIndexed { index, color ->
                        val selected = index == state.colorIndex
                        val label = stringResource(R.string.subjects_color_cd, index + 1)
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color)
                                .then(
                                    if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    else Modifier,
                                )
                                .clickable { onColorChange(index) }
                                .semantics { contentDescription = label },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = !state.isSaving) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

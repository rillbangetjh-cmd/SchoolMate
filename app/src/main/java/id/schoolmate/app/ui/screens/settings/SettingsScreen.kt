package id.schoolmate.app.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import id.schoolmate.app.BuildConfig
import id.schoolmate.app.R
import id.schoolmate.app.domain.model.ThemeMode
import id.schoolmate.app.domain.model.UserPreferences
import id.schoolmate.app.ui.AppViewModelProvider

@Composable
fun SettingsScreen(
    onOpenSubjects: () -> Unit,
    onOpenStudyLog: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when (val s = state) {
        SettingsUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is SettingsUiState.Ready -> SettingsContent(
            preferences = s.preferences,
            onSaveName = viewModel::saveStudentName,
            onThemeChange = viewModel::setThemeMode,
            onDynamicColorChange = viewModel::setUseDynamicColor,
            onOpenSubjects = onOpenSubjects,
            onOpenStudyLog = onOpenStudyLog,
        )
    }
}

@Composable
private fun SettingsContent(
    preferences: UserPreferences,
    onSaveName: (String) -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onOpenSubjects: () -> Unit = {},
    onOpenStudyLog: () -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    var name by rememberSaveable(preferences.studentName) { mutableStateOf(preferences.studentName) }
    val nameChanged = name.trim() != preferences.studentName

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .verticalScroll(rememberScrollState())
            .padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        SectionLabel(stringResource(R.string.settings_profile))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= MAX_STUDENT_NAME_LENGTH) name = it },
                label = { Text(stringResource(R.string.settings_name_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSaveName(name); focusManager.clearFocus() }),
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { onSaveName(name); focusManager.clearFocus() }, enabled = nameChanged) {
                Text(stringResource(R.string.action_save))
            }
        }

        HorizontalDivider(Modifier.padding(top = 16.dp))
        SectionLabel(stringResource(R.string.settings_data))
        ListItem(
            modifier = Modifier.clickable(onClick = onOpenSubjects),
            leadingContent = { Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null) },
            headlineContent = { Text(stringResource(R.string.home_manage_subjects)) },
            supportingContent = { Text(stringResource(R.string.settings_subjects_desc)) },
            trailingContent = { Icon(Icons.Outlined.ChevronRight, contentDescription = null) },
        )
        ListItem(
            modifier = Modifier.clickable(onClick = onOpenStudyLog),
            leadingContent = { Icon(Icons.Outlined.Timer, contentDescription = null) },
            headlineContent = { Text(stringResource(R.string.study_title)) },
            supportingContent = { Text(stringResource(R.string.settings_study_desc)) },
            trailingContent = { Icon(Icons.Outlined.ChevronRight, contentDescription = null) },
        )

        HorizontalDivider(Modifier.padding(top = 16.dp))
        SectionLabel(stringResource(R.string.settings_appearance))
        Column(Modifier.selectableGroup()) {
            ThemeMode.entries.forEach { mode ->
                val label = when (mode) {
                    ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
                    ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
                    ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
                }
                ListItem(
                    modifier = Modifier.selectable(
                        selected = preferences.themeMode == mode,
                        onClick = { onThemeChange(mode) },
                        role = Role.RadioButton,
                    ),
                    headlineContent = { Text(label) },
                    leadingContent = { RadioButton(selected = preferences.themeMode == mode, onClick = null) },
                )
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_dynamic_color)) },
                supportingContent = { Text(stringResource(R.string.settings_dynamic_color_desc)) },
                trailingContent = {
                    Switch(checked = preferences.useDynamicColor, onCheckedChange = onDynamicColorChange)
                },
            )
        }

        HorizontalDivider(Modifier.padding(top = 16.dp))
        SectionLabel(stringResource(R.string.settings_about))
        ListItem(
            headlineContent = { Text(stringResource(R.string.app_name)) },
            supportingContent = {
                Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
            },
        )
        Text(
            stringResource(R.string.settings_offline_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
    )
}

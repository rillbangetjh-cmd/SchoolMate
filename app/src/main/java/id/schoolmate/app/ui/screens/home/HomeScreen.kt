package id.schoolmate.app.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import id.schoolmate.app.R
import id.schoolmate.app.domain.usecase.DashboardSummary
import id.schoolmate.app.ui.AppViewModelProvider
import id.schoolmate.app.ui.theme.SchoolMateTheme

@Composable
fun HomeScreen(
    onOpenSubjects: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(state = state, onOpenSubjects = onOpenSubjects)
}

@Composable
fun HomeContent(state: HomeUiState, onOpenSubjects: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(state.dateLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(state.greeting, style = MaterialTheme.typography.headlineMedium)
        if (!state.hasName && !state.isLoading) {
            Text(
                stringResource(R.string.home_set_name_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.home_subjects_title)) },
                supportingContent = {
                    Text(
                        if (state.subjectCount == 0) stringResource(R.string.home_subjects_empty)
                        else pluralStringResource(R.plurals.home_subjects_count, state.subjectCount, state.subjectCount),
                    )
                },
            )
            FilledTonalButton(
                onClick = onOpenSubjects,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            ) { Text(stringResource(R.string.home_manage_subjects)) }
        }

        SummaryCard(state.summary)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.home_roadmap_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.home_roadmap_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(summary: DashboardSummary) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.home_summary_title), style = MaterialTheme.typography.titleMedium)
            SummaryRow(Icons.Outlined.Schedule, stringResource(R.string.home_summary_lessons_today), summary.lessonsToday.toString())
            SummaryRow(Icons.Outlined.TaskAlt, stringResource(R.string.home_summary_active_tasks), summary.activeTasks.toString())
            SummaryRow(
                Icons.Outlined.WarningAmber,
                stringResource(R.string.home_summary_overdue_tasks),
                summary.overdueTasks.toString(),
                highlight = summary.overdueTasks > 0,
            )
            SummaryRow(Icons.Outlined.Event, stringResource(R.string.home_summary_exams_week), summary.examsNext7Days.toString())
            SummaryRow(
                Icons.Outlined.Timer,
                stringResource(R.string.home_summary_study_week),
                stringResource(R.string.home_summary_minutes, summary.studyMinutesThisWeek),
            )
        }
    }
}

@Composable
private fun SummaryRow(icon: ImageVector, label: String, value: String, highlight: Boolean = false) {
    val color = if (highlight) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, tint = if (highlight) color else MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleSmall, color = color)
    }
}

@Preview(showBackground = true)
@Composable
private fun HomePreview() {
    SchoolMateTheme {
        HomeContent(
            state = HomeUiState(
                greeting = "Selamat pagi, Rina",
                dateLabel = "Senin, 12 Oktober 2026",
                subjectCount = 4,
                hasName = true,
                summary = DashboardSummary(lessonsToday = 6, activeTasks = 3, overdueTasks = 1, examsNext7Days = 2, studyMinutesThisWeek = 135),
                isLoading = false,
            ),
            onOpenSubjects = {},
        )
    }
}

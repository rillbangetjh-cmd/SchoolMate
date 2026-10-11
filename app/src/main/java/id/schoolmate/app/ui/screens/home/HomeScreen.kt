package id.schoolmate.app.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import id.schoolmate.app.R
import id.schoolmate.app.domain.model.Task
import id.schoolmate.app.domain.usecase.DashboardSummary
import id.schoolmate.app.ui.AppViewModelProvider
import id.schoolmate.app.ui.components.ExamRow
import id.schoolmate.app.ui.components.LessonRow
import id.schoolmate.app.ui.components.SectionHeader
import id.schoolmate.app.ui.components.TaskRow
import id.schoolmate.app.ui.theme.SchoolMateTheme
import id.schoolmate.app.util.Formatters

data class HomeActions(
    val openLesson: (Long) -> Unit = {},
    val openTask: (Long) -> Unit = {},
    val toggleTask: (Task) -> Unit = {},
    val openExam: (Long) -> Unit = {},
    val openSchedule: () -> Unit = {},
    val openTasks: () -> Unit = {},
    val openExams: () -> Unit = {},
    val openStudyLog: () -> Unit = {},
    val openSubjects: () -> Unit = {},
    val addTask: () -> Unit = {},
    val addExam: () -> Unit = {},
    val addLesson: () -> Unit = {},
)

@Composable
fun HomeScreen(
    actions: HomeActions,
    viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(state = state, actions = actions.copy(toggleTask = viewModel::toggleDone))
}

@Composable
fun HomeContent(state: HomeUiState, actions: HomeActions, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item(key = "header") {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(state.dateLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(state.greeting, style = MaterialTheme.typography.headlineMedium)
                if (!state.hasName && !state.isLoading) {
                    Text(
                        stringResource(R.string.home_set_name_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item(key = "stats") { StatsRow(state.summary, actions) }
        item(key = "quick") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AssistChip(onClick = actions.addTask, label = { Text(stringResource(R.string.home_add_task)) },
                    leadingIcon = { Icon(Icons.Outlined.TaskAlt, null) })
                AssistChip(onClick = actions.addExam, label = { Text(stringResource(R.string.home_add_exam)) },
                    leadingIcon = { Icon(Icons.Outlined.Event, null) })
                AssistChip(onClick = actions.addLesson, label = { Text(stringResource(R.string.home_add_lesson)) },
                    leadingIcon = { Icon(Icons.Outlined.CalendarMonth, null) })
            }
        }

        if (!state.isLoading && state.subjectCount == 0) {
            item(key = "onboarding") { OnboardingCard(actions.openSubjects) }
        }

        item(key = "today_header") {
            SectionHeader(stringResource(R.string.home_today_title), actionLabel = stringResource(R.string.action_see_all), onAction = actions.openSchedule)
        }
        if (state.lessonsToday.isEmpty()) {
            item(key = "today_empty") {
                MutedLine(stringResource(R.string.home_today_empty), stringResource(R.string.home_today_setup), actions.addLesson)
            }
        } else {
            items(state.lessonsToday, key = { "lesson-${it.slot.entry.id}" }) { lesson ->
                LessonRow(lesson, onClick = { actions.openLesson(lesson.slot.entry.id) }, modifier = Modifier.animateItem())
            }
        }

        if (state.overdueTasks.isNotEmpty()) {
            item(key = "overdue_header") {
                Text(
                    stringResource(R.string.home_overdue_title) + " (${state.summary.overdueTasks})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 4.dp),
                )
            }
            items(state.overdueTasks, key = { "overdue-${it.task.id}" }) { item ->
                TaskRow(item, onClick = { actions.openTask(item.task.id) }, onToggleDone = { actions.toggleTask(item.task) }, modifier = Modifier.animateItem())
            }
        }

        item(key = "tasks_header") {
            SectionHeader(stringResource(R.string.home_tasks_title), actionLabel = stringResource(R.string.action_see_all), onAction = actions.openTasks)
        }
        if (state.upcomingTasks.isEmpty()) {
            item(key = "tasks_empty") { MutedLine(stringResource(R.string.home_tasks_empty), stringResource(R.string.tasks_add), actions.addTask) }
        } else {
            items(state.upcomingTasks, key = { "task-${it.task.id}" }) { item ->
                TaskRow(item, onClick = { actions.openTask(item.task.id) }, onToggleDone = { actions.toggleTask(item.task) }, modifier = Modifier.animateItem())
            }
        }

        item(key = "exams_header") {
            SectionHeader(stringResource(R.string.home_exams_title), actionLabel = stringResource(R.string.action_see_all), onAction = actions.openExams)
        }
        if (state.upcomingExams.isEmpty()) {
            item(key = "exams_empty") { MutedLine(stringResource(R.string.home_exams_empty), stringResource(R.string.exams_add), actions.addExam) }
        } else {
            items(state.upcomingExams, key = { "exam-${it.exam.id}" }) { item ->
                ExamRow(item, onClick = { actions.openExam(item.exam.id) }, modifier = Modifier.animateItem())
            }
        }

        item(key = "study") { StudyCard(state.summary.studyMinutesThisWeek, actions.openStudyLog) }
    }
}

@Composable
private fun StatsRow(summary: DashboardSummary, actions: HomeActions) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatTile(summary.lessonsToday.toString(), stringResource(R.string.home_summary_lessons_today), actions.openSchedule, Modifier.weight(1f))
        StatTile(
            summary.activeTasks.toString(),
            stringResource(R.string.home_summary_active_tasks),
            actions.openTasks,
            Modifier.weight(1f),
            highlight = summary.overdueTasks > 0,
        )
        StatTile(summary.examsNext7Days.toString(), stringResource(R.string.home_summary_exams_week), actions.openExams, Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(value: String, label: String, onClick: () -> Unit, modifier: Modifier, highlight: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = if (highlight) scheme.errorContainer else scheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold,
                color = if (highlight) scheme.onErrorContainer else scheme.onSurface)
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 2,
                color = if (highlight) scheme.onErrorContainer else scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MutedLine(text: String, actionLabel: String, onAction: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onAction).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(actionLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun OnboardingCard(onOpenSubjects: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null)
                Text(stringResource(R.string.home_subjects_title), style = MaterialTheme.typography.titleMedium)
            }
            Text(stringResource(R.string.home_subjects_empty), style = MaterialTheme.typography.bodyMedium)
            FilledTonalButton(onClick = onOpenSubjects) { Text(stringResource(R.string.home_manage_subjects)) }
        }
    }
}

@Composable
private fun StudyCard(minutes: Int, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StudyIcon(Icons.Outlined.Timer)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.home_study_title), style = MaterialTheme.typography.titleSmall)
                Text(Formatters.duration(minutes.toLong()), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.home_study_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
private fun StudyIcon(icon: ImageVector) {
    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
}

@Preview(showBackground = true)
@Composable
private fun HomePreview() {
    SchoolMateTheme {
        HomeContent(
            state = HomeUiState(
                isLoading = false,
                greeting = "Selamat pagi, Rina",
                dateLabel = "Senin, 12 Oktober 2026",
                subjectCount = 4,
                hasName = true,
                summary = DashboardSummary(lessonsToday = 6, activeTasks = 3, overdueTasks = 1, examsNext7Days = 2, studyMinutesThisWeek = 135),
            ),
            actions = HomeActions(),
        )
    }
}

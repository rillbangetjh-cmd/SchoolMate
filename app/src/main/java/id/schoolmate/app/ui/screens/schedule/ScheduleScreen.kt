package id.schoolmate.app.ui.screens.schedule

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import id.schoolmate.app.R
import id.schoolmate.app.ui.AppViewModelProvider
import id.schoolmate.app.ui.components.EmptyState
import id.schoolmate.app.ui.components.LessonRow
import id.schoolmate.app.ui.components.LoadingBox
import id.schoolmate.app.util.Formatters
import java.time.DayOfWeek

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    onAddLesson: (DayOfWeek) -> Unit,
    onOpenLesson: (Long) -> Unit,
    onOpenSubjects: () -> Unit,
    viewModel: ScheduleViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.schedule_title)) },
                actions = {
                    IconButton(onClick = onOpenSubjects) {
                        Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = stringResource(R.string.subjects_manage_cd))
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.hasSubjects) {
                ExtendedFloatingActionButton(
                    onClick = { onAddLesson(state.selectedDay) },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.schedule_add)) },
                )
            }
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            DayTabs(state, viewModel::selectDay)
            when {
                state.isLoading -> LoadingBox()
                !state.hasSubjects && state.totalLessons == 0 -> EmptyState(
                    icon = Icons.AutoMirrored.Outlined.MenuBook,
                    title = stringResource(R.string.schedule_no_subjects_title),
                    message = stringResource(R.string.schedule_no_subjects_body),
                    actionLabel = stringResource(R.string.subjects_add),
                    onAction = onOpenSubjects,
                )
                else -> AnimatedContent(
                    targetState = state.selectedDay,
                    transitionSpec = {
                        val dir = if (targetState > initialState) 1 else -1
                        (slideInHorizontally(tween(220)) { it / 6 * dir } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(tween(180)) { -it / 6 * dir } + fadeOut(tween(180)))
                    },
                    label = "scheduleDay",
                ) { day ->
                    val lessons = state.lessonsByDay[day].orEmpty()
                    val overview = state.days.first { it.day == day }
                    if (lessons.isEmpty()) {
                        EmptyState(
                            icon = Icons.Outlined.CalendarMonth,
                            title = stringResource(R.string.schedule_day_empty, Formatters.dayName(day)),
                            message = stringResource(R.string.schedule_day_empty_action),
                            actionLabel = if (state.hasSubjects) stringResource(R.string.schedule_add) else null,
                            onAction = { onAddLesson(day) },
                        )
                    } else {
                        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                            item(key = "summary") {
                                Text(
                                    stringResource(R.string.schedule_day_summary, overview.lessonCount, Formatters.duration(overview.totalMinutes)),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
                                )
                            }
                            items(lessons, key = { it.slot.entry.id }) { lesson ->
                                LessonRow(lesson, onClick = { onOpenLesson(lesson.slot.entry.id) }, modifier = Modifier.animateItem())
                                HorizontalDivider(Modifier.padding(start = 86.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayTabs(state: ScheduleUiState, onSelect: (DayOfWeek) -> Unit) {
    val index = state.days.indexOfFirst { it.day == state.selectedDay }.coerceAtLeast(0)
    PrimaryTabRow(selectedTabIndex = index) {
        state.days.forEach { day ->
            Tab(
                selected = day.day == state.selectedDay,
                onClick = { onSelect(day.day) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Content-only Tab: no 16dp text padding, so "Kam"/"Jum" fit 7 tabs on a phone.
                Column(Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(Formatters.dayShort(day.day), maxLines = 1)
                        // Dot marks days that have lessons; filled dot = today.
                        Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.Center) {
                            Box(
                                Modifier.size(5.dp).clip(CircleShape).background(
                                    when {
                                        day.isToday -> MaterialTheme.colorScheme.primary
                                        day.lessonCount > 0 -> MaterialTheme.colorScheme.outline
                                        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                                    },
                                ),
                            )
                        }
                }
            }
        }
    }
}

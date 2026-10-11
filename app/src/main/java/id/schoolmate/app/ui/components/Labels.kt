package id.schoolmate.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import id.schoolmate.app.R
import id.schoolmate.app.domain.model.DeadlineUrgency
import id.schoolmate.app.domain.model.Priority
import id.schoolmate.app.domain.model.TaskStatus
import id.schoolmate.app.util.Formatters

@Composable
fun deadlineLabel(urgency: DeadlineUrgency): String = when (urgency) {
    DeadlineUrgency.Done -> stringResource(R.string.deadline_done)
    is DeadlineUrgency.Overdue ->
        if (urgency.days == 0L) stringResource(R.string.deadline_overdue_today)
        else stringResource(R.string.deadline_overdue_days, urgency.days.toInt())
    is DeadlineUrgency.Today -> urgency.time?.let { stringResource(R.string.deadline_today_at, Formatters.time(it)) }
        ?: stringResource(R.string.deadline_today)
    is DeadlineUrgency.Tomorrow -> urgency.time?.let { stringResource(R.string.deadline_tomorrow_at, Formatters.time(it)) }
        ?: stringResource(R.string.deadline_tomorrow)
    is DeadlineUrgency.Soon -> stringResource(R.string.deadline_in_days, urgency.days.toInt())
    is DeadlineUrgency.Later -> Formatters.dateShort(urgency.date)
}

/** Only overdue/today/tomorrow get a colored pill; everything else stays quiet text. */
@Composable
fun DeadlinePill(urgency: DeadlineUrgency, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (urgency) {
        is DeadlineUrgency.Overdue -> scheme.errorContainer to scheme.onErrorContainer
        is DeadlineUrgency.Today -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        is DeadlineUrgency.Tomorrow -> scheme.secondaryContainer to scheme.onSecondaryContainer
        else -> Color.Transparent to scheme.onSurfaceVariant
    }
    Pill(deadlineLabel(urgency), container, content, modifier)
}

@Composable
fun priorityLabel(priority: Priority): String = when (priority) {
    Priority.LOW -> stringResource(R.string.priority_low)
    Priority.MEDIUM -> stringResource(R.string.priority_medium)
    Priority.HIGH -> stringResource(R.string.priority_high)
}

@Composable
fun statusLabel(status: TaskStatus, long: Boolean = false): String = when (status) {
    TaskStatus.TODO -> stringResource(if (long) R.string.status_todo_long else R.string.status_todo)
    TaskStatus.IN_PROGRESS -> stringResource(if (long) R.string.status_in_progress_long else R.string.status_in_progress)
    TaskStatus.DONE -> stringResource(if (long) R.string.status_done_long else R.string.status_done)
}

@Composable
fun examCountdown(daysUntil: Long): String = when {
    daysUntil == 0L -> stringResource(R.string.exam_countdown_today)
    daysUntil == 1L -> stringResource(R.string.exam_countdown_tomorrow)
    daysUntil > 1L -> stringResource(R.string.exam_countdown_days, daysUntil.toInt())
    else -> stringResource(R.string.exam_countdown_past, (-daysUntil).toInt())
}

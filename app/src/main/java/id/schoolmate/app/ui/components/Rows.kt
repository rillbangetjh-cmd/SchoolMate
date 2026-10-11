package id.schoolmate.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.schoolmate.app.R
import id.schoolmate.app.domain.model.Priority
import id.schoolmate.app.domain.model.TaskStatus
import id.schoolmate.app.ui.model.ExamItem
import id.schoolmate.app.ui.model.LessonItem
import id.schoolmate.app.ui.model.TaskItem
import id.schoolmate.app.util.Formatters

@Composable
fun TaskRow(
    item: TaskItem,
    onClick: () -> Unit,
    onToggleDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val task = item.task
    val done = task.status == TaskStatus.DONE
    val contentAlpha by animateFloatAsState(if (done) 0.55f else 1f, label = "taskAlpha")
    val name = task.title
    val toggleCd = if (done) stringResource(R.string.tasks_mark_undone_cd, name) else stringResource(R.string.tasks_mark_done_cd, name)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = done,
            onCheckedChange = { onToggleDone() },
            modifier = Modifier.semantics { contentDescription = toggleCd },
        )
        Column(Modifier.weight(1f).alpha(contentAlpha).padding(start = 4.dp)) {
            Text(
                name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                textDecoration = if (done) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 2.dp),
            ) {
                SubjectDot(item.subject?.let { Color(it.colorArgb) }, size = 8.dp)
                Text(
                    item.subject?.name ?: stringResource(R.string.no_subject),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (task.status == TaskStatus.IN_PROGRESS && !done) {
                    Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        statusLabel(task.status),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
            DeadlinePill(item.urgency)
            if (task.priority == Priority.HIGH && !done) {
                val tint by animateColorAsState(MaterialTheme.colorScheme.error, label = "flag")
                Icon(
                    Icons.Filled.Flag,
                    contentDescription = priorityLabel(Priority.HIGH),
                    tint = tint,
                    modifier = Modifier.padding(top = 4.dp).size(16.dp),
                )
            }
        }
    }
}

@Composable
fun ExamRow(item: ExamItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val exam = item.exam
    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SubjectBar(item.subject?.let { Color(it.colorArgb) })
        Column(Modifier.weight(1f)) {
            Text(exam.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val time = exam.startTime?.let { " · ${Formatters.time(it)}" }.orEmpty()
            Text(
                (item.subject?.name ?: stringResource(R.string.no_subject)) + " · " + Formatters.dateShort(exam.date) + time,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val urgent = item.daysUntil in 0..2
        val scheme = MaterialTheme.colorScheme
        Pill(
            examCountdown(item.daysUntil),
            container = if (urgent) scheme.tertiaryContainer else Color.Transparent,
            content = if (urgent) scheme.onTertiaryContainer else scheme.onSurfaceVariant,
        )
    }
}

@Composable
fun LessonRow(item: LessonItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val entry = item.slot.entry
    val scheme = MaterialTheme.colorScheme
    val alpha by animateFloatAsState(if (item.isFinished) 0.55f else 1f, label = "lessonAlpha")
    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp).alpha(alpha),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.width(52.dp)) {
            Text(Formatters.time(entry.startTime), style = MaterialTheme.typography.labelLarge)
            Text(Formatters.time(entry.endTime), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
        }
        SubjectBar(Color(item.slot.subject.colorArgb))
        Column(Modifier.weight(1f)) {
            Text(item.slot.subject.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val details = buildList {
                add(Formatters.duration(entry.durationMinutes))
                if (entry.room.isNotBlank()) add(entry.room)
            }.joinToString(" · ")
            Text(details, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        when {
            item.isOngoing -> Pill(stringResource(R.string.lesson_now), scheme.primaryContainer, scheme.onPrimaryContainer)
            item.isFinished -> Text(stringResource(R.string.lesson_done), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
        }
    }
}

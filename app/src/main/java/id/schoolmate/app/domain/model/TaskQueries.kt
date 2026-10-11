package id.schoolmate.app.domain.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** Filters offered on the task list. */
enum class TaskFilter { ALL, ACTIVE, OVERDUE, DONE }

/** How urgent a task's deadline is relative to "now". */
sealed interface DeadlineUrgency {
    data object Done : DeadlineUrgency
    data class Overdue(val days: Long) : DeadlineUrgency
    data class Today(val time: LocalTime?) : DeadlineUrgency
    data class Tomorrow(val time: LocalTime?) : DeadlineUrgency
    /** Due within the next 2–6 days. */
    data class Soon(val days: Long) : DeadlineUrgency
    data class Later(val date: LocalDate) : DeadlineUrgency

    /** Overdue or due today/tomorrow — worth highlighting in the UI. */
    val isUrgent: Boolean get() = this is Overdue || this is Today || this is Tomorrow
}

fun Task.urgency(now: LocalDateTime): DeadlineUrgency {
    if (!status.isActive) return DeadlineUrgency.Done
    val today = now.toLocalDate()
    if (isOverdue(today, now.toLocalTime())) {
        return DeadlineUrgency.Overdue(ChronoUnit.DAYS.between(dueDate, today).coerceAtLeast(0))
    }
    val days = ChronoUnit.DAYS.between(today, dueDate)
    return when {
        days == 0L -> DeadlineUrgency.Today(dueTime)
        days == 1L -> DeadlineUrgency.Tomorrow(dueTime)
        days < 7L -> DeadlineUrgency.Soon(days)
        else -> DeadlineUrgency.Later(dueDate)
    }
}

/** Deadline order: date, then timed before end-of-day, then higher priority, then id. */
val TaskDeadlineComparator: Comparator<Task> =
    compareBy<Task> { it.dueDate }
        .thenBy { it.dueTime ?: LocalTime.MAX }
        .thenByDescending { it.priority.level }
        .thenBy { it.id }

/**
 * Applies a [TaskFilter]. Active views are sorted by deadline; DONE is sorted by most recently
 * completed; ALL shows active tasks (by deadline) followed by completed ones.
 */
fun List<Task>.filterAndSort(filter: TaskFilter, now: LocalDateTime): List<Task> {
    val today = now.toLocalDate()
    val time = now.toLocalTime()
    val active = filter { it.status.isActive }.sortedWith(TaskDeadlineComparator)
    val done = filter { !it.status.isActive }.sortedWith(compareByDescending<Task> { it.completedAt }.thenByDescending { it.id })
    return when (filter) {
        TaskFilter.ALL -> active + done
        TaskFilter.ACTIVE -> active
        TaskFilter.OVERDUE -> active.filter { it.isOverdue(today, time) }
        TaskFilter.DONE -> done
    }
}

fun List<Task>.countFor(filter: TaskFilter, now: LocalDateTime): Int {
    val today = now.toLocalDate()
    val time = now.toLocalTime()
    return when (filter) {
        TaskFilter.ALL -> size
        TaskFilter.ACTIVE -> count { it.status.isActive }
        TaskFilter.OVERDUE -> count { it.isOverdue(today, time) }
        TaskFilter.DONE -> count { !it.status.isActive }
    }
}

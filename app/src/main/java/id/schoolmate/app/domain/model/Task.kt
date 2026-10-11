package id.schoolmate.app.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * A homework / assignment (tugas/PR). The deadline is a local calendar date with an optional
 * time; a task without [dueTime] is due at the end of [dueDate].
 */
data class Task(
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val subjectId: Long? = null,
    val dueDate: LocalDate,
    val dueTime: LocalTime? = null,
    val priority: Priority = Priority.MEDIUM,
    val status: TaskStatus = TaskStatus.TODO,
    val completedAt: Instant? = null,
) {
    fun isOverdue(today: LocalDate, now: LocalTime): Boolean = status.isActive && when {
        dueDate.isBefore(today) -> true
        dueDate.isAfter(today) -> false
        else -> dueTime != null && dueTime.isBefore(now)
    }
}

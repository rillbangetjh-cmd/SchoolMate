package id.schoolmate.app.domain.model

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime

/** One recurring weekly lesson slot (e.g. Senin 07:00–08:30 Matematika). */
data class ScheduleEntry(
    val id: Long = 0,
    val subjectId: Long,
    val dayOfWeek: DayOfWeek,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val room: String = "",
    val note: String = "",
) {
    val durationMinutes: Long get() = Duration.between(startTime, endTime).toMinutes()
}

/** A schedule entry joined with its subject, ready for display. */
data class ScheduleSlot(
    val entry: ScheduleEntry,
    val subject: Subject,
)

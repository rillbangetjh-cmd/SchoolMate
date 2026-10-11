package id.schoolmate.app.domain.model

import java.time.Instant
import java.time.LocalDate

/**
 * A completed block of study time. [date] is the local calendar day the session started on and
 * is what date filters/statistics use, so results don't shift if the device time zone changes.
 */
data class StudySession(
    val id: Long = 0,
    val subjectId: Long? = null,
    val taskId: Long? = null,
    val startedAt: Instant,
    val date: LocalDate,
    val durationMinutes: Int,
    val note: String = "",
)

data class SubjectStudyMinutes(
    val subjectId: Long?,
    val minutes: Int,
)

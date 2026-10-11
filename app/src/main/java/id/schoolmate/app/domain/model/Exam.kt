package id.schoolmate.app.domain.model

import java.time.LocalDate
import java.time.LocalTime

/** An exam or quiz (ujian/ulangan). */
data class Exam(
    val id: Long = 0,
    val title: String,
    val subjectId: Long? = null,
    val date: LocalDate,
    val startTime: LocalTime? = null,
    val durationMinutes: Int? = null,
    val location: String = "",
    val note: String = "",
)

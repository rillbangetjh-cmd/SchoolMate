package id.schoolmate.app.domain.model

/** A school subject (mata pelajaran). */
data class Subject(
    val id: Long = 0,
    val name: String,
    val teacher: String = "",
    val colorArgb: Int,
)

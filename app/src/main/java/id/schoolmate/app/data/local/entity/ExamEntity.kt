package id.schoolmate.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import id.schoolmate.app.domain.model.Exam
import java.time.LocalDate
import java.time.LocalTime

@Entity(
    tableName = "exams",
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subject_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["subject_id"]),
        Index(value = ["exam_date"]),
    ],
)
data class ExamEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    @ColumnInfo(name = "subject_id") val subjectId: Long?,
    @ColumnInfo(name = "exam_date") val date: LocalDate,
    @ColumnInfo(name = "start_time") val startTime: LocalTime?,
    @ColumnInfo(name = "duration_minutes") val durationMinutes: Int?,
    val location: String,
    val note: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

fun ExamEntity.toDomain() = Exam(
    id = id,
    title = title,
    subjectId = subjectId,
    date = date,
    startTime = startTime,
    durationMinutes = durationMinutes,
    location = location,
    note = note,
)

fun Exam.toEntity(createdAt: Long, updatedAt: Long) = ExamEntity(
    id = id,
    title = title.trim(),
    subjectId = subjectId,
    date = date,
    startTime = startTime?.withSecond(0)?.withNano(0),
    durationMinutes = durationMinutes,
    location = location.trim(),
    note = note.trim(),
    createdAt = createdAt,
    updatedAt = updatedAt,
)

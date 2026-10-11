package id.schoolmate.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import id.schoolmate.app.domain.model.StudySession
import id.schoolmate.app.domain.model.SubjectStudyMinutes
import java.time.Instant
import java.time.LocalDate

/** Logged study time. Subject/task links are cleared (not deleted) when the parent goes away. */
@Entity(
    tableName = "study_sessions",
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subject_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["task_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["subject_id"]),
        Index(value = ["task_id"]),
        Index(value = ["session_date"]),
    ],
)
data class StudySessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "subject_id") val subjectId: Long?,
    @ColumnInfo(name = "task_id") val taskId: Long?,
    @ColumnInfo(name = "started_at") val startedAt: Instant,
    @ColumnInfo(name = "session_date") val date: LocalDate,
    @ColumnInfo(name = "duration_minutes") val durationMinutes: Int,
    val note: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

data class SubjectMinutesRow(
    @ColumnInfo(name = "subject_id") val subjectId: Long?,
    val minutes: Int,
)

fun SubjectMinutesRow.toDomain() = SubjectStudyMinutes(subjectId = subjectId, minutes = minutes)

fun StudySessionEntity.toDomain() = StudySession(
    id = id,
    subjectId = subjectId,
    taskId = taskId,
    startedAt = startedAt,
    date = date,
    durationMinutes = durationMinutes,
    note = note,
)

fun StudySession.toEntity(createdAt: Long) = StudySessionEntity(
    id = id,
    subjectId = subjectId,
    taskId = taskId,
    startedAt = startedAt,
    date = date,
    durationMinutes = durationMinutes,
    note = note.trim(),
    createdAt = createdAt,
)

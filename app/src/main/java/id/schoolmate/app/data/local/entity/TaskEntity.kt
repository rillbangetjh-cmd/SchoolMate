package id.schoolmate.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import id.schoolmate.app.domain.model.Priority
import id.schoolmate.app.domain.model.Task
import id.schoolmate.app.domain.model.TaskStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Homework / assignment. Deleting a subject keeps the task but clears the link (SET NULL). */
@Entity(
    tableName = "tasks",
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
        Index(value = ["status", "due_date"]),
    ],
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String,
    @ColumnInfo(name = "subject_id") val subjectId: Long?,
    @ColumnInfo(name = "due_date") val dueDate: LocalDate,
    @ColumnInfo(name = "due_time") val dueTime: LocalTime?,
    val priority: Priority,
    val status: TaskStatus,
    @ColumnInfo(name = "completed_at") val completedAt: Instant?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

fun TaskEntity.toDomain() = Task(
    id = id,
    title = title,
    description = description,
    subjectId = subjectId,
    dueDate = dueDate,
    dueTime = dueTime,
    priority = priority,
    status = status,
    completedAt = completedAt,
)

fun Task.toEntity(createdAt: Long, updatedAt: Long) = TaskEntity(
    id = id,
    title = title.trim(),
    description = description.trim(),
    subjectId = subjectId,
    dueDate = dueDate,
    dueTime = dueTime?.withSecond(0)?.withNano(0),
    priority = priority,
    status = status,
    completedAt = completedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

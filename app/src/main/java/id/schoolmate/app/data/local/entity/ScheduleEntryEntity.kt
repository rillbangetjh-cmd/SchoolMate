package id.schoolmate.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import id.schoolmate.app.domain.model.ScheduleEntry
import id.schoolmate.app.domain.model.ScheduleSlot
import java.time.DayOfWeek
import java.time.LocalTime

/** Weekly lesson slot. Deleting a subject deletes its slots (CASCADE). */
@Entity(
    tableName = "schedule_entries",
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subject_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["subject_id"]),
        Index(value = ["day_of_week", "start_time"]),
    ],
)
data class ScheduleEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "subject_id") val subjectId: Long,
    @ColumnInfo(name = "day_of_week") val dayOfWeek: DayOfWeek,
    @ColumnInfo(name = "start_time") val startTime: LocalTime,
    @ColumnInfo(name = "end_time") val endTime: LocalTime,
    val room: String,
    val note: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

data class ScheduleEntryWithSubject(
    @Embedded val entry: ScheduleEntryEntity,
    @Relation(parentColumn = "subject_id", entityColumn = "id")
    val subject: SubjectEntity,
)

fun ScheduleEntryEntity.toDomain() = ScheduleEntry(
    id = id,
    subjectId = subjectId,
    dayOfWeek = dayOfWeek,
    startTime = startTime,
    endTime = endTime,
    room = room,
    note = note,
)

fun ScheduleEntryWithSubject.toDomain() = ScheduleSlot(entry = entry.toDomain(), subject = subject.toDomain())

fun ScheduleEntry.toEntity(createdAt: Long, updatedAt: Long) = ScheduleEntryEntity(
    id = id,
    subjectId = subjectId,
    dayOfWeek = dayOfWeek,
    startTime = startTime,
    endTime = endTime,
    room = room.trim(),
    note = note.trim(),
    createdAt = createdAt,
    updatedAt = updatedAt,
)

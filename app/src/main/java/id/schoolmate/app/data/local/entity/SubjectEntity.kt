package id.schoolmate.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import id.schoolmate.app.domain.model.Subject

@Entity(
    tableName = "subjects",
    indices = [Index(value = ["name"], unique = true)],
)
data class SubjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val teacher: String,
    @ColumnInfo(name = "color_argb") val colorArgb: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

fun SubjectEntity.toDomain() = Subject(id = id, name = name, teacher = teacher, colorArgb = colorArgb)

fun Subject.toEntity(createdAt: Long) = SubjectEntity(
    id = id,
    name = name,
    teacher = teacher,
    colorArgb = colorArgb,
    createdAt = createdAt,
)

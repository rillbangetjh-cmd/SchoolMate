package id.schoolmate.app.data.repository

import id.schoolmate.app.data.local.dao.ScheduleDao
import id.schoolmate.app.data.local.entity.toDomain
import id.schoolmate.app.data.local.entity.toEntity
import id.schoolmate.app.domain.model.ScheduleEntry
import id.schoolmate.app.domain.model.ScheduleSlot
import id.schoolmate.app.domain.validation.ValidationError
import id.schoolmate.app.domain.validation.ValidationException
import id.schoolmate.app.domain.validation.validateScheduleEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.DayOfWeek

interface ScheduleRepository {
    /** All slots ordered Monday→Sunday, then by start time. */
    fun observeWeek(): Flow<List<ScheduleSlot>>
    fun observeDay(day: DayOfWeek): Flow<List<ScheduleSlot>>
    fun observeLessonCount(day: DayOfWeek): Flow<Int>

    /** Fails with [ValidationException] for invalid ranges, overlaps, or unknown subjects. */
    suspend fun addEntry(entry: ScheduleEntry): Result<Long>
    suspend fun updateEntry(entry: ScheduleEntry): Result<Unit>
    suspend fun deleteEntry(id: Long)
}

class OfflineScheduleRepository(
    private val dao: ScheduleDao,
    private val clock: Clock,
) : ScheduleRepository {

    override fun observeWeek(): Flow<List<ScheduleSlot>> =
        dao.observeAllWithSubject().map { rows -> rows.map { it.toDomain() } }

    override fun observeDay(day: DayOfWeek): Flow<List<ScheduleSlot>> =
        dao.observeForDayWithSubject(day).map { rows -> rows.map { it.toDomain() } }

    override fun observeLessonCount(day: DayOfWeek): Flow<Int> = dao.observeCountForDay(day)

    override suspend fun addEntry(entry: ScheduleEntry): Result<Long> {
        validate(entry, excludeId = 0)?.let { return it }
        val now = clock.millis()
        return guardedWrite { dao.insert(entry.copy(id = 0).toEntity(createdAt = now, updatedAt = now)) }
    }

    override suspend fun updateEntry(entry: ScheduleEntry): Result<Unit> {
        val existing = dao.getById(entry.id)
            ?: return Result.failure(RecordNotFoundException("ScheduleEntry", entry.id))
        validate(entry, excludeId = entry.id)?.let { return it }
        return guardedWrite {
            dao.update(entry.toEntity(createdAt = existing.createdAt, updatedAt = clock.millis()))
        }.map { }
    }

    override suspend fun deleteEntry(id: Long) {
        dao.deleteById(id)
    }

    private suspend fun validate(entry: ScheduleEntry, excludeId: Long): Result<Nothing>? {
        validateScheduleEntry(entry).toFailureOrNull()?.let { return it }
        val overlaps = dao.countOverlapping(entry.dayOfWeek, entry.startTime, entry.endTime, excludeId)
        return if (overlaps > 0) Result.failure(ValidationException(setOf(ValidationError.SCHEDULE_OVERLAP))) else null
    }
}

package id.schoolmate.app.data.repository

import id.schoolmate.app.data.local.dao.StudySessionDao
import id.schoolmate.app.data.local.entity.toDomain
import id.schoolmate.app.data.local.entity.toEntity
import id.schoolmate.app.domain.model.StudySession
import id.schoolmate.app.domain.model.SubjectStudyMinutes
import id.schoolmate.app.domain.validation.validateStudySession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

interface StudySessionRepository {
    fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<StudySession>>
    fun observeTotalMinutes(from: LocalDate, to: LocalDate): Flow<Int>
    fun observeMinutesPerSubject(from: LocalDate, to: LocalDate): Flow<List<SubjectStudyMinutes>>

    /** Logs a finished session; its calendar [StudySession.date] is derived from [startedAt]. */
    suspend fun logSession(
        startedAt: Instant,
        durationMinutes: Int,
        subjectId: Long? = null,
        taskId: Long? = null,
        note: String = "",
    ): Result<Long>

    suspend fun updateSession(session: StudySession): Result<Unit>
    suspend fun deleteSession(id: Long)
}

class OfflineStudySessionRepository(
    private val dao: StudySessionDao,
    private val clock: Clock,
) : StudySessionRepository {

    override fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<StudySession>> =
        dao.observeBetween(from, to).map { list -> list.map { it.toDomain() } }

    override fun observeTotalMinutes(from: LocalDate, to: LocalDate): Flow<Int> =
        dao.observeTotalMinutesBetween(from, to)

    override fun observeMinutesPerSubject(from: LocalDate, to: LocalDate): Flow<List<SubjectStudyMinutes>> =
        dao.observeMinutesPerSubjectBetween(from, to).map { list -> list.map { it.toDomain() } }

    override suspend fun logSession(
        startedAt: Instant,
        durationMinutes: Int,
        subjectId: Long?,
        taskId: Long?,
        note: String,
    ): Result<Long> {
        val session = StudySession(
            subjectId = subjectId,
            taskId = taskId,
            startedAt = startedAt,
            date = startedAt.atZone(clock.zone).toLocalDate(),
            durationMinutes = durationMinutes,
            note = note,
        )
        validateStudySession(session).toFailureOrNull()?.let { return it }
        return guardedWrite { dao.insert(session.toEntity(createdAt = clock.millis())) }
    }

    override suspend fun updateSession(session: StudySession): Result<Unit> {
        val existing = dao.getById(session.id)
            ?: return Result.failure(RecordNotFoundException("StudySession", session.id))
        validateStudySession(session).toFailureOrNull()?.let { return it }
        return guardedWrite {
            dao.update(session.toEntity(createdAt = existing.createdAt))
        }.map { }
    }

    override suspend fun deleteSession(id: Long) {
        dao.deleteById(id)
    }
}

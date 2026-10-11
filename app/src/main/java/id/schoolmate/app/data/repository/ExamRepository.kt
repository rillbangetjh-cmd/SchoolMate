package id.schoolmate.app.data.repository

import id.schoolmate.app.data.local.dao.ExamDao
import id.schoolmate.app.data.local.entity.toDomain
import id.schoolmate.app.data.local.entity.toEntity
import id.schoolmate.app.domain.model.Exam
import id.schoolmate.app.domain.validation.validateExam
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate

interface ExamRepository {
    fun observeAll(): Flow<List<Exam>>
    fun observeUpcoming(from: LocalDate): Flow<List<Exam>>
    fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<Exam>>
    fun observeCountBetween(from: LocalDate, to: LocalDate): Flow<Int>

    suspend fun addExam(exam: Exam): Result<Long>
    suspend fun updateExam(exam: Exam): Result<Unit>
    suspend fun deleteExam(id: Long)
}

class OfflineExamRepository(
    private val dao: ExamDao,
    private val clock: Clock,
) : ExamRepository {

    override fun observeAll(): Flow<List<Exam>> = dao.observeAll().map { list -> list.map { it.toDomain() } }
    override fun observeUpcoming(from: LocalDate): Flow<List<Exam>> =
        dao.observeUpcoming(from).map { list -> list.map { it.toDomain() } }
    override fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<Exam>> =
        dao.observeBetween(from, to).map { list -> list.map { it.toDomain() } }
    override fun observeCountBetween(from: LocalDate, to: LocalDate): Flow<Int> = dao.observeCountBetween(from, to)

    override suspend fun addExam(exam: Exam): Result<Long> {
        validateExam(exam).toFailureOrNull()?.let { return it }
        val now = clock.millis()
        return guardedWrite { dao.insert(exam.copy(id = 0).toEntity(createdAt = now, updatedAt = now)) }
    }

    override suspend fun updateExam(exam: Exam): Result<Unit> {
        val existing = dao.getById(exam.id) ?: return Result.failure(RecordNotFoundException("Exam", exam.id))
        validateExam(exam).toFailureOrNull()?.let { return it }
        return guardedWrite {
            dao.update(exam.toEntity(createdAt = existing.createdAt, updatedAt = clock.millis()))
        }.map { }
    }

    override suspend fun deleteExam(id: Long) {
        dao.deleteById(id)
    }
}

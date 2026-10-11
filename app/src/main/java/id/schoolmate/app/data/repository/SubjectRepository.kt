package id.schoolmate.app.data.repository

import android.database.sqlite.SQLiteConstraintException
import id.schoolmate.app.data.local.dao.SubjectDao
import id.schoolmate.app.data.local.entity.toDomain
import id.schoolmate.app.data.local.entity.toEntity
import id.schoolmate.app.domain.model.Subject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface SubjectRepository {
    fun observeSubjects(): Flow<List<Subject>>
    fun observeSubjectCount(): Flow<Int>

    /** @return failure with [DuplicateSubjectException] when the name already exists. */
    suspend fun addSubject(subject: Subject): Result<Long>
    suspend fun deleteSubject(id: Long)
}

class DuplicateSubjectException(name: String) : IllegalArgumentException("Subject '$name' already exists")

class OfflineSubjectRepository(
    private val dao: SubjectDao,
    private val clock: () -> Long = System::currentTimeMillis,
) : SubjectRepository {

    override fun observeSubjects(): Flow<List<Subject>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeSubjectCount(): Flow<Int> = dao.observeCount()

    override suspend fun addSubject(subject: Subject): Result<Long> =
        try {
            Result.success(dao.insert(subject.copy(name = subject.name.trim()).toEntity(clock())))
        } catch (e: SQLiteConstraintException) {
            Result.failure(DuplicateSubjectException(subject.name))
        }

    override suspend fun deleteSubject(id: Long) = dao.deleteById(id)
}

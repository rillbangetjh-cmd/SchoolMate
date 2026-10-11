package id.schoolmate.app.data.repository

import id.schoolmate.app.data.local.dao.TaskDao
import id.schoolmate.app.data.local.entity.toDomain
import id.schoolmate.app.data.local.entity.toEntity
import id.schoolmate.app.domain.model.Task
import id.schoolmate.app.domain.model.TaskStatus
import id.schoolmate.app.domain.validation.validateTask
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate

interface TaskRepository {
    /** Active tasks by deadline, followed by completed tasks. */
    fun observeAll(): Flow<List<Task>>
    fun observeActive(): Flow<List<Task>>
    fun observeDueBetween(from: LocalDate, to: LocalDate): Flow<List<Task>>
    fun observeBySubject(subjectId: Long): Flow<List<Task>>
    fun observeTask(id: Long): Flow<Task?>
    fun observeActiveCount(): Flow<Int>
    fun observeOverdueCount(today: LocalDate): Flow<Int>

    suspend fun addTask(task: Task): Result<Long>
    suspend fun updateTask(task: Task): Result<Unit>
    suspend fun setStatus(id: Long, status: TaskStatus): Result<Unit>
    suspend fun deleteTask(id: Long)
    suspend fun deleteCompleted(): Int
}

class OfflineTaskRepository(
    private val dao: TaskDao,
    private val clock: Clock,
) : TaskRepository {

    override fun observeAll(): Flow<List<Task>> = dao.observeAll().map { list -> list.map { it.toDomain() } }
    override fun observeActive(): Flow<List<Task>> = dao.observeActive().map { list -> list.map { it.toDomain() } }
    override fun observeDueBetween(from: LocalDate, to: LocalDate): Flow<List<Task>> =
        dao.observeDueBetween(from, to).map { list -> list.map { it.toDomain() } }
    override fun observeBySubject(subjectId: Long): Flow<List<Task>> =
        dao.observeBySubject(subjectId).map { list -> list.map { it.toDomain() } }
    override fun observeTask(id: Long): Flow<Task?> = dao.observeById(id).map { it?.toDomain() }
    override fun observeActiveCount(): Flow<Int> = dao.observeActiveCount()
    override fun observeOverdueCount(today: LocalDate): Flow<Int> = dao.observeOverdueCount(today)

    override suspend fun addTask(task: Task): Result<Long> {
        val normalized = normalizeCompletion(task.copy(id = 0), previous = null)
        validateTask(normalized).toFailureOrNull()?.let { return it }
        val now = clock.millis()
        return guardedWrite { dao.insert(normalized.toEntity(createdAt = now, updatedAt = now)) }
    }

    override suspend fun updateTask(task: Task): Result<Unit> {
        val existing = dao.getById(task.id) ?: return Result.failure(RecordNotFoundException("Task", task.id))
        val normalized = normalizeCompletion(task, previous = existing.toDomain())
        validateTask(normalized).toFailureOrNull()?.let { return it }
        return guardedWrite {
            dao.update(normalized.toEntity(createdAt = existing.createdAt, updatedAt = clock.millis()))
        }.map { }
    }

    override suspend fun setStatus(id: Long, status: TaskStatus): Result<Unit> {
        val existing = dao.getById(id) ?: return Result.failure(RecordNotFoundException("Task", id))
        val updated = normalizeCompletion(existing.toDomain().copy(status = status), previous = existing.toDomain())
        dao.updateStatus(id, updated.status, updated.completedAt, clock.millis())
        return Result.success(Unit)
    }

    override suspend fun deleteTask(id: Long) {
        dao.deleteById(id)
    }

    override suspend fun deleteCompleted(): Int = dao.deleteCompleted()

    /**
     * Keeps `completedAt` consistent with `status`: set when a task becomes DONE (keeping the
     * original timestamp if it was already done), cleared when it is reopened.
     */
    private fun normalizeCompletion(task: Task, previous: Task?): Task = when {
        task.status.isActive -> task.copy(completedAt = null)
        task.completedAt != null -> task
        previous?.status == TaskStatus.DONE && previous.completedAt != null -> task.copy(completedAt = previous.completedAt)
        else -> task.copy(completedAt = clock.instant())
    }
}

package id.schoolmate.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import id.schoolmate.app.data.local.entity.TaskEntity
import id.schoolmate.app.domain.model.TaskStatus
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

/**
 * Deadline order: earliest date first; on the same date, tasks with a time come before
 * end-of-day tasks (NULL time is treated as 24:00); then higher priority; then oldest id.
 */
private const val DEADLINE_ORDER = "due_date ASC, COALESCE(due_time, 1440) ASC, priority DESC, id ASC"

@Dao
interface TaskDao {
    /** Active tasks first (by deadline), then completed tasks (most recently completed first). */
    @Query(
        "SELECT * FROM tasks ORDER BY CASE WHEN status = 'DONE' THEN 1 ELSE 0 END ASC, " +
            "CASE WHEN status = 'DONE' THEN -completed_at END ASC, $DEADLINE_ORDER",
    )
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE status != 'DONE' ORDER BY $DEADLINE_ORDER")
    fun observeActive(): Flow<List<TaskEntity>>

    /** Tasks whose due date is within [from, to] (inclusive), any status. */
    @Query("SELECT * FROM tasks WHERE due_date BETWEEN :from AND :to ORDER BY $DEADLINE_ORDER")
    fun observeDueBetween(from: LocalDate, to: LocalDate): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE subject_id = :subjectId ORDER BY $DEADLINE_ORDER")
    fun observeBySubject(subjectId: Long): Flow<List<TaskEntity>>

    @Query("SELECT COUNT(*) FROM tasks WHERE status != 'DONE'")
    fun observeActiveCount(): Flow<Int>

    /** Active tasks whose due date is strictly before [today]. */
    @Query("SELECT COUNT(*) FROM tasks WHERE status != 'DONE' AND due_date < :today")
    fun observeOverdueCount(today: LocalDate): Flow<Int>

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observeById(id: Long): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity): Int

    @Query(
        "UPDATE tasks SET status = :status, completed_at = :completedAt, updated_at = :updatedAt WHERE id = :id",
    )
    suspend fun updateStatus(id: Long, status: TaskStatus, completedAt: Instant?, updatedAt: Long): Int

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM tasks WHERE status = 'DONE'")
    suspend fun deleteCompleted(): Int
}

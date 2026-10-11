package id.schoolmate.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import id.schoolmate.app.data.local.entity.ExamEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Chronological order; exams without a start time sort first on their day. */
private const val EXAM_ORDER = "exam_date ASC, COALESCE(start_time, -1) ASC, id ASC"

@Dao
interface ExamDao {
    @Query("SELECT * FROM exams ORDER BY $EXAM_ORDER")
    fun observeAll(): Flow<List<ExamEntity>>

    /** Exams on or after [from]. */
    @Query("SELECT * FROM exams WHERE exam_date >= :from ORDER BY $EXAM_ORDER")
    fun observeUpcoming(from: LocalDate): Flow<List<ExamEntity>>

    /** Exams within [from, to] inclusive. */
    @Query("SELECT * FROM exams WHERE exam_date BETWEEN :from AND :to ORDER BY $EXAM_ORDER")
    fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<ExamEntity>>

    @Query("SELECT COUNT(*) FROM exams WHERE exam_date BETWEEN :from AND :to")
    fun observeCountBetween(from: LocalDate, to: LocalDate): Flow<Int>

    @Query("SELECT * FROM exams WHERE id = :id")
    suspend fun getById(id: Long): ExamEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(exam: ExamEntity): Long

    @Update
    suspend fun update(exam: ExamEntity): Int

    @Query("DELETE FROM exams WHERE id = :id")
    suspend fun deleteById(id: Long): Int
}

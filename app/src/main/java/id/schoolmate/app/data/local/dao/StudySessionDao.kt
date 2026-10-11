package id.schoolmate.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import id.schoolmate.app.data.local.entity.StudySessionEntity
import id.schoolmate.app.data.local.entity.SubjectMinutesRow
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface StudySessionDao {
    /** Sessions within [from, to] inclusive, newest first. */
    @Query("SELECT * FROM study_sessions WHERE session_date BETWEEN :from AND :to ORDER BY started_at DESC, id DESC")
    fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<StudySessionEntity>>

    @Query("SELECT COALESCE(SUM(duration_minutes), 0) FROM study_sessions WHERE session_date BETWEEN :from AND :to")
    fun observeTotalMinutesBetween(from: LocalDate, to: LocalDate): Flow<Int>

    @Query(
        """
        SELECT subject_id, SUM(duration_minutes) AS minutes FROM study_sessions
        WHERE session_date BETWEEN :from AND :to
        GROUP BY subject_id ORDER BY minutes DESC
        """,
    )
    fun observeMinutesPerSubjectBetween(from: LocalDate, to: LocalDate): Flow<List<SubjectMinutesRow>>

    @Query("SELECT * FROM study_sessions WHERE id = :id")
    suspend fun getById(id: Long): StudySessionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(session: StudySessionEntity): Long

    @Update
    suspend fun update(session: StudySessionEntity): Int

    @Query("DELETE FROM study_sessions WHERE id = :id")
    suspend fun deleteById(id: Long): Int
}

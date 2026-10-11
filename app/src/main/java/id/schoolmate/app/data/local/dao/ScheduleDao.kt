package id.schoolmate.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import id.schoolmate.app.data.local.entity.ScheduleEntryEntity
import id.schoolmate.app.data.local.entity.ScheduleEntryWithSubject
import kotlinx.coroutines.flow.Flow
import java.time.DayOfWeek
import java.time.LocalTime

@Dao
interface ScheduleDao {
    @Transaction
    @Query("SELECT * FROM schedule_entries ORDER BY day_of_week ASC, start_time ASC, id ASC")
    fun observeAllWithSubject(): Flow<List<ScheduleEntryWithSubject>>

    @Transaction
    @Query("SELECT * FROM schedule_entries WHERE day_of_week = :day ORDER BY start_time ASC, id ASC")
    fun observeForDayWithSubject(day: DayOfWeek): Flow<List<ScheduleEntryWithSubject>>

    @Query("SELECT COUNT(*) FROM schedule_entries WHERE day_of_week = :day")
    fun observeCountForDay(day: DayOfWeek): Flow<Int>

    @Query("SELECT * FROM schedule_entries WHERE id = :id")
    suspend fun getById(id: Long): ScheduleEntryEntity?

    /** Counts slots on [day] that overlap the half-open range [start, end), ignoring [excludeId]. */
    @Query(
        """
        SELECT COUNT(*) FROM schedule_entries
        WHERE day_of_week = :day AND start_time < :end AND end_time > :start AND id != :excludeId
        """,
    )
    suspend fun countOverlapping(day: DayOfWeek, start: LocalTime, end: LocalTime, excludeId: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: ScheduleEntryEntity): Long

    @Update
    suspend fun update(entry: ScheduleEntryEntity): Int

    @Query("DELETE FROM schedule_entries WHERE id = :id")
    suspend fun deleteById(id: Long): Int
}

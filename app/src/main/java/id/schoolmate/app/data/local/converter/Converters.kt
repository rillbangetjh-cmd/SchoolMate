package id.schoolmate.app.data.local.converter

import androidx.room.TypeConverter
import id.schoolmate.app.domain.model.Priority
import id.schoolmate.app.domain.model.TaskStatus
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Storage formats (all chosen so SQL comparisons and ORDER BY behave correctly):
 * - LocalDate  -> epoch day (INTEGER)
 * - LocalTime  -> minute of day 0..1439 (INTEGER); seconds are intentionally dropped
 * - Instant    -> epoch milliseconds (INTEGER)
 * - DayOfWeek  -> ISO value 1 (Monday) .. 7 (Sunday) (INTEGER)
 * - Priority   -> level 1..3 (INTEGER)
 * - TaskStatus -> enum name (TEXT)
 */
class Converters {
    @TypeConverter fun localDateToEpochDay(value: LocalDate?): Long? = value?.toEpochDay()
    @TypeConverter fun epochDayToLocalDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    @TypeConverter fun localTimeToMinuteOfDay(value: LocalTime?): Int? = value?.let { it.hour * 60 + it.minute }
    @TypeConverter fun minuteOfDayToLocalTime(value: Int?): LocalTime? = value?.let { LocalTime.of(it / 60, it % 60) }

    @TypeConverter fun instantToEpochMilli(value: Instant?): Long? = value?.toEpochMilli()
    @TypeConverter fun epochMilliToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter fun dayOfWeekToIso(value: DayOfWeek?): Int? = value?.value
    @TypeConverter fun isoToDayOfWeek(value: Int?): DayOfWeek? = value?.let(DayOfWeek::of)

    @TypeConverter fun priorityToLevel(value: Priority?): Int? = value?.level
    @TypeConverter fun levelToPriority(value: Int?): Priority? = value?.let(Priority::fromLevel)

    @TypeConverter fun taskStatusToName(value: TaskStatus?): String? = value?.name
    @TypeConverter fun nameToTaskStatus(value: String?): TaskStatus? =
        value?.let { name -> TaskStatus.entries.firstOrNull { it.name == name } ?: TaskStatus.TODO }
}

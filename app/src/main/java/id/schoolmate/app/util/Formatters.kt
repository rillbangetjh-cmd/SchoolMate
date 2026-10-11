package id.schoolmate.app.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Indonesian formatting used across the UI. Kept pure so it can be unit-tested. */
object Formatters {
    val locale: Locale = Locale.forLanguageTag("id-ID")

    private val time = DateTimeFormatter.ofPattern("HH:mm", locale)
    private val dateShort = DateTimeFormatter.ofPattern("EEE, d MMM", locale)
    private val dateMedium = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", locale)
    private val dateNoYear = DateTimeFormatter.ofPattern("EEEE, d MMMM", locale)

    fun time(value: LocalTime): String = value.format(time)

    fun timeRange(start: LocalTime, end: LocalTime): String = "${time(start)}–${time(end)}"

    fun dayName(day: DayOfWeek): String = day.getDisplayName(TextStyle.FULL, locale).capitalized()

    fun dayShort(day: DayOfWeek): String = day.getDisplayName(TextStyle.SHORT, locale).capitalized()

    /** "Sen, 12 Okt" (year added when it differs from [today]'s year). */
    fun dateShort(date: LocalDate, today: LocalDate = LocalDate.now()): String =
        if (date.year == today.year) date.format(dateShort).capitalized()
        else "${date.format(dateShort).capitalized()} ${date.year}"

    fun dateLong(date: LocalDate): String = date.format(dateMedium).capitalized()

    fun dateLongNoYear(date: LocalDate): String = date.format(dateNoYear).capitalized()

    /** 45 → "45 menit", 90 → "1 jam 30 menit", 120 → "2 jam". */
    fun duration(minutes: Long): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0L -> "$m menit"
            m == 0L -> "$h jam"
            else -> "$h jam $m menit"
        }
    }

    /** Compact variant for tight rows: "1j 30m", "45m". */
    fun durationShort(minutes: Long): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0L -> "${m}m"
            m == 0L -> "${h}j"
            else -> "${h}j ${m}m"
        }
    }

    private fun String.capitalized() = replaceFirstChar { it.titlecase(locale) }
}

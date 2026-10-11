package id.schoolmate.app.util

import java.time.LocalTime

enum class DayPeriod { PAGI, SIANG, SORE, MALAM }

/** Maps a time of day to the Indonesian greeting period. */
fun dayPeriodOf(time: LocalTime): DayPeriod = when (time.hour) {
    in 4..10 -> DayPeriod.PAGI
    in 11..14 -> DayPeriod.SIANG
    in 15..17 -> DayPeriod.SORE
    else -> DayPeriod.MALAM
}

fun greetingFor(period: DayPeriod, name: String): String {
    val base = when (period) {
        DayPeriod.PAGI -> "Selamat pagi"
        DayPeriod.SIANG -> "Selamat siang"
        DayPeriod.SORE -> "Selamat sore"
        DayPeriod.MALAM -> "Selamat malam"
    }
    val trimmed = name.trim()
    return if (trimmed.isEmpty()) base else "$base, $trimmed"
}

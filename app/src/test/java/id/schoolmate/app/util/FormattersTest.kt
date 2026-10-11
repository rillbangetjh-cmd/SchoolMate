package id.schoolmate.app.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class FormattersTest {
    @Test fun durationsAreReadableInIndonesian() {
        assertEquals("45 menit", Formatters.duration(45))
        assertEquals("1 jam", Formatters.duration(60))
        assertEquals("1 jam 30 menit", Formatters.duration(90))
        assertEquals("0 menit", Formatters.duration(0))
        assertEquals("2j 5m", Formatters.durationShort(125))
        assertEquals("45m", Formatters.durationShort(45))
    }

    @Test fun timesUse24HourClock() {
        assertEquals("07:05", Formatters.time(LocalTime.of(7, 5)))
        assertEquals("13:00–14:30", Formatters.timeRange(LocalTime.of(13, 0), LocalTime.of(14, 30)))
    }

    @Test fun dayAndDateNamesAreIndonesian() {
        assertEquals("Senin", Formatters.dayName(DayOfWeek.MONDAY))
        assertEquals("Minggu", Formatters.dayName(DayOfWeek.SUNDAY))
        assertEquals("Senin, 12 Oktober 2026", Formatters.dateLong(LocalDate.of(2026, 10, 12)))
        val today = LocalDate.of(2026, 10, 12)
        assertEquals(true, Formatters.dateShort(LocalDate.of(2026, 10, 14), today).startsWith("Rab, 14 Okt"))
        assertEquals(true, Formatters.dateShort(LocalDate.of(2027, 1, 4), today).endsWith("2027"))
    }
}

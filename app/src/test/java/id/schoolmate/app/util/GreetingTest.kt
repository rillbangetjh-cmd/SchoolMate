package id.schoolmate.app.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class GreetingTest {
    @Test
    fun `maps hours to Indonesian day periods`() {
        assertEquals(DayPeriod.MALAM, dayPeriodOf(LocalTime.of(3, 59)))
        assertEquals(DayPeriod.PAGI, dayPeriodOf(LocalTime.of(4, 0)))
        assertEquals(DayPeriod.PAGI, dayPeriodOf(LocalTime.of(10, 59)))
        assertEquals(DayPeriod.SIANG, dayPeriodOf(LocalTime.of(11, 0)))
        assertEquals(DayPeriod.SIANG, dayPeriodOf(LocalTime.of(14, 59)))
        assertEquals(DayPeriod.SORE, dayPeriodOf(LocalTime.of(15, 0)))
        assertEquals(DayPeriod.SORE, dayPeriodOf(LocalTime.of(17, 59)))
        assertEquals(DayPeriod.MALAM, dayPeriodOf(LocalTime.of(18, 0)))
        assertEquals(DayPeriod.MALAM, dayPeriodOf(LocalTime.of(23, 59)))
    }

    @Test
    fun `greeting includes trimmed name when present`() {
        assertEquals("Selamat pagi, Rina", greetingFor(DayPeriod.PAGI, "  Rina "))
        assertEquals("Selamat malam", greetingFor(DayPeriod.MALAM, "   "))
    }
}

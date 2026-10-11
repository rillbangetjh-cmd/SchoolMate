package id.schoolmate.app.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import id.schoolmate.app.data.local.dao.ScheduleDao
import id.schoolmate.app.data.local.entity.ScheduleEntryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.TUESDAY
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class ScheduleDaoTest {
    private lateinit var db: SchoolMateDatabase
    private lateinit var dao: ScheduleDao
    private var math = 0L
    private var physics = 0L

    @Before fun setUp() = runTest {
        db = inMemoryDatabase()
        dao = db.scheduleDao()
        math = db.insertSubject("Matematika")
        physics = db.insertSubject("Fisika")
    }

    @After fun tearDown() = db.close()

    private fun entry(subject: Long, day: DayOfWeek, start: String, end: String, room: String = "") =
        ScheduleEntryEntity(
            subjectId = subject, dayOfWeek = day, startTime = LocalTime.parse(start), endTime = LocalTime.parse(end),
            room = room, note = "", createdAt = 1, updatedAt = 1,
        )

    @Test fun insertAndReadWithSubject() = runTest {
        val id = dao.insert(entry(math, MONDAY, "07:00", "08:30", room = "X-1"))
        val slot = dao.observeAllWithSubject().first().single()
        assertEquals(id, slot.entry.id)
        assertEquals("Matematika", slot.subject.name)
        assertEquals(LocalTime.of(7, 0), slot.entry.startTime)
        assertEquals(LocalTime.of(8, 30), slot.entry.endTime)
        assertEquals("X-1", slot.entry.room)
    }

    @Test fun weekIsOrderedByDayThenStartTime() = runTest {
        dao.insert(entry(math, FRIDAY, "07:00", "08:00"))
        dao.insert(entry(physics, MONDAY, "10:00", "11:00"))
        dao.insert(entry(math, MONDAY, "07:00", "08:00"))
        dao.insert(entry(physics, TUESDAY, "08:00", "09:00"))
        val order = dao.observeAllWithSubject().first().map { it.entry.dayOfWeek to it.entry.startTime.toString() }
        assertEquals(
            listOf(MONDAY to "07:00", MONDAY to "10:00", TUESDAY to "08:00", FRIDAY to "07:00"),
            order,
        )
    }

    @Test fun filterByDay() = runTest {
        dao.insert(entry(math, MONDAY, "07:00", "08:00"))
        dao.insert(entry(physics, MONDAY, "08:00", "09:00"))
        dao.insert(entry(math, TUESDAY, "07:00", "08:00"))
        assertEquals(2, dao.observeForDayWithSubject(MONDAY).first().size)
        assertEquals(2, dao.observeCountForDay(MONDAY).first())
        assertEquals(0, dao.observeCountForDay(DayOfWeek.SUNDAY).first())
    }

    @Test fun updateAndDelete() = runTest {
        val id = dao.insert(entry(math, MONDAY, "07:00", "08:00"))
        val updated = dao.getById(id)!!.copy(endTime = LocalTime.of(8, 45), room = "Lab")
        assertEquals(1, dao.update(updated))
        assertEquals(LocalTime.of(8, 45), dao.getById(id)!!.endTime)
        assertEquals("Lab", dao.getById(id)!!.room)
        assertEquals(1, dao.deleteById(id))
        assertNull(dao.getById(id))
    }

    @Test fun overlapIsHalfOpenAndIgnoresExcludedId() = runTest {
        val id = dao.insert(entry(math, MONDAY, "07:00", "08:30"))
        val t = { s: String -> LocalTime.parse(s) }
        assertEquals(1, dao.countOverlapping(MONDAY, t("08:00"), t("09:00"), 0))
        assertEquals(1, dao.countOverlapping(MONDAY, t("06:00"), t("10:00"), 0))
        // Back-to-back lessons do not overlap.
        assertEquals(0, dao.countOverlapping(MONDAY, t("08:30"), t("09:30"), 0))
        assertEquals(0, dao.countOverlapping(MONDAY, t("06:00"), t("07:00"), 0))
        assertEquals(0, dao.countOverlapping(TUESDAY, t("07:00"), t("08:30"), 0))
        assertEquals(0, dao.countOverlapping(MONDAY, t("07:00"), t("08:30"), id))
    }

    @Test fun deletingSubjectCascadesToItsSchedule() = runTest {
        dao.insert(entry(math, MONDAY, "07:00", "08:00"))
        dao.insert(entry(physics, MONDAY, "08:00", "09:00"))
        db.subjectDao().deleteById(math)
        assertEquals(listOf("Fisika"), dao.observeAllWithSubject().first().map { it.subject.name })
    }
}

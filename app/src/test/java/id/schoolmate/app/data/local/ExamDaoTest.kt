package id.schoolmate.app.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import id.schoolmate.app.data.local.dao.ExamDao
import id.schoolmate.app.data.local.entity.ExamEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class ExamDaoTest {
    private lateinit var db: SchoolMateDatabase
    private lateinit var dao: ExamDao
    private val day = LocalDate.of(2026, 10, 12)

    @Before fun setUp() { db = inMemoryDatabase(); dao = db.examDao() }
    @After fun tearDown() = db.close()

    private fun exam(title: String, date: LocalDate, time: LocalTime? = null, subjectId: Long? = null) = ExamEntity(
        title = title, subjectId = subjectId, date = date, startTime = time, durationMinutes = 90,
        location = "", note = "", createdAt = 1, updatedAt = 1,
    )

    @Test fun insertReadUpdateDelete() = runTest {
        val id = dao.insert(exam("UTS Kimia", day, LocalTime.of(8, 0)))
        val stored = dao.getById(id)!!
        assertEquals(day, stored.date)
        assertEquals(LocalTime.of(8, 0), stored.startTime)
        assertEquals(90, stored.durationMinutes)
        dao.update(stored.copy(location = "Aula", durationMinutes = 120))
        assertEquals("Aula", dao.getById(id)!!.location)
        assertEquals(120, dao.getById(id)!!.durationMinutes)
        dao.deleteById(id)
        assertNull(dao.getById(id))
    }

    @Test fun upcomingAndRangeFiltersAreChronological() = runTest {
        dao.insert(exam("past", day.minusDays(1)))
        dao.insert(exam("today-10", day, LocalTime.of(10, 0)))
        dao.insert(exam("today-untimed", day))
        dao.insert(exam("today-08", day, LocalTime.of(8, 0)))
        dao.insert(exam("next-week", day.plusDays(7)))
        dao.insert(exam("far", day.plusDays(30)))

        assertEquals(
            listOf("today-untimed", "today-08", "today-10", "next-week", "far"),
            dao.observeUpcoming(day).first().map { it.title },
        )
        assertEquals(
            listOf("today-untimed", "today-08", "today-10", "next-week"),
            dao.observeBetween(day, day.plusDays(7)).first().map { it.title },
        )
        assertEquals(4, dao.observeCountBetween(day, day.plusDays(7)).first())
        assertEquals(6, dao.observeAll().first().size)
    }

    @Test fun deletingSubjectKeepsExam() = runTest {
        val chem = db.insertSubject("Kimia")
        val id = dao.insert(exam("Ulangan", day, subjectId = chem))
        db.subjectDao().deleteById(chem)
        assertNull(dao.getById(id)!!.subjectId)
    }
}

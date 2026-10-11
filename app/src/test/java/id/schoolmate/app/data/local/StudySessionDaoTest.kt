package id.schoolmate.app.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import id.schoolmate.app.data.local.dao.StudySessionDao
import id.schoolmate.app.data.local.entity.StudySessionEntity
import id.schoolmate.app.data.local.entity.SubjectMinutesRow
import id.schoolmate.app.data.local.entity.TaskEntity
import id.schoolmate.app.domain.model.Priority
import id.schoolmate.app.domain.model.TaskStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class StudySessionDaoTest {
    private lateinit var db: SchoolMateDatabase
    private lateinit var dao: StudySessionDao
    private val day = LocalDate.of(2026, 10, 12)

    @Before fun setUp() { db = inMemoryDatabase(); dao = db.studySessionDao() }
    @After fun tearDown() = db.close()

    private fun session(date: LocalDate, minutes: Int, startedAt: Long, subjectId: Long? = null, taskId: Long? = null) =
        StudySessionEntity(
            subjectId = subjectId, taskId = taskId, startedAt = Instant.ofEpochSecond(startedAt), date = date,
            durationMinutes = minutes, note = "", createdAt = 1,
        )

    @Test fun insertReadUpdateDelete() = runTest {
        val id = dao.insert(session(day, 25, startedAt = 1_000))
        assertEquals(25, dao.getById(id)!!.durationMinutes)
        dao.update(dao.getById(id)!!.copy(durationMinutes = 50, note = "Latihan soal"))
        assertEquals(50, dao.getById(id)!!.durationMinutes)
        assertEquals("Latihan soal", dao.getById(id)!!.note)
        dao.deleteById(id)
        assertNull(dao.getById(id))
    }

    @Test fun rangeFilterNewestFirstAndTotals() = runTest {
        val math = db.insertSubject("Matematika")
        dao.insert(session(day.minusDays(1), 30, startedAt = 10))
        dao.insert(session(day, 25, startedAt = 20, subjectId = math))
        dao.insert(session(day.plusDays(2), 50, startedAt = 30, subjectId = math))
        dao.insert(session(day.plusDays(2), 15, startedAt = 40))
        dao.insert(session(day.plusDays(7), 60, startedAt = 50))

        val week = dao.observeBetween(day, day.plusDays(6)).first()
        assertEquals(listOf(40L, 30L, 20L), week.map { it.startedAt.epochSecond })
        assertEquals(90, dao.observeTotalMinutesBetween(day, day.plusDays(6)).first())
        assertEquals(0, dao.observeTotalMinutesBetween(day.plusDays(100), day.plusDays(101)).first())
        assertEquals(
            listOf(SubjectMinutesRow(math, 75), SubjectMinutesRow(null, 15)),
            dao.observeMinutesPerSubjectBetween(day, day.plusDays(6)).first(),
        )
    }

    @Test fun deletingTaskKeepsSessionButClearsLink() = runTest {
        val taskId = db.taskDao().insert(
            TaskEntity(
                title = "Makalah", description = "", subjectId = null, dueDate = day, dueTime = null,
                priority = Priority.MEDIUM, status = TaskStatus.TODO, completedAt = null, createdAt = 1, updatedAt = 1,
            ),
        )
        val id = dao.insert(session(day, 25, startedAt = 1, taskId = taskId))
        db.taskDao().deleteById(taskId)
        assertNull(dao.getById(id)!!.taskId)
    }
}

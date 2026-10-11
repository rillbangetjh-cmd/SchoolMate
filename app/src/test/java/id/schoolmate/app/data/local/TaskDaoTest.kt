package id.schoolmate.app.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import id.schoolmate.app.data.local.dao.TaskDao
import id.schoolmate.app.data.local.entity.TaskEntity
import id.schoolmate.app.domain.model.Priority
import id.schoolmate.app.domain.model.TaskStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class TaskDaoTest {
    private lateinit var db: SchoolMateDatabase
    private lateinit var dao: TaskDao
    private val day = LocalDate.of(2026, 10, 12)

    @Before fun setUp() { db = inMemoryDatabase(); dao = db.taskDao() }
    @After fun tearDown() = db.close()

    private fun task(
        title: String,
        date: LocalDate = day,
        time: LocalTime? = null,
        priority: Priority = Priority.MEDIUM,
        status: TaskStatus = TaskStatus.TODO,
        completedAt: Instant? = null,
        subjectId: Long? = null,
    ) = TaskEntity(
        title = title, description = "", subjectId = subjectId, dueDate = date, dueTime = time,
        priority = priority, status = status, completedAt = completedAt, createdAt = 1, updatedAt = 1,
    )

    @Test fun insertReadUpdateDelete() = runTest {
        val id = dao.insert(task("PR Matematika", time = LocalTime.of(21, 0), priority = Priority.HIGH))
        val stored = dao.getById(id)!!
        assertEquals("PR Matematika", stored.title)
        assertEquals(day, stored.dueDate)
        assertEquals(LocalTime.of(21, 0), stored.dueTime)
        assertEquals(Priority.HIGH, stored.priority)
        assertEquals(TaskStatus.TODO, stored.status)

        dao.update(stored.copy(title = "PR Matematika bab 3", dueDate = day.plusDays(1)))
        assertEquals("PR Matematika bab 3", dao.observeById(id).first()!!.title)
        assertEquals(day.plusDays(1), dao.getById(id)!!.dueDate)

        assertEquals(1, dao.deleteById(id))
        assertNull(dao.getById(id))
    }

    @Test fun activeTasksSortedByDeadlineThenPriority() = runTest {
        dao.insert(task("later-day", date = day.plusDays(2)))
        dao.insert(task("end-of-day-low", priority = Priority.LOW))
        dao.insert(task("end-of-day-high", priority = Priority.HIGH))
        dao.insert(task("evening", time = LocalTime.of(19, 0)))
        dao.insert(task("morning", time = LocalTime.of(7, 0), priority = Priority.LOW))
        dao.insert(task("yesterday", date = day.minusDays(1)))
        dao.insert(task("done", date = day.minusDays(5), status = TaskStatus.DONE, completedAt = Instant.EPOCH))

        assertEquals(
            listOf("yesterday", "morning", "evening", "end-of-day-high", "end-of-day-low", "later-day"),
            dao.observeActive().first().map { it.title },
        )
    }

    @Test fun observeAllPutsCompletedLastNewestFirst() = runTest {
        dao.insert(task("done-old", status = TaskStatus.DONE, completedAt = Instant.ofEpochSecond(100)))
        dao.insert(task("todo", date = day.plusDays(3)))
        dao.insert(task("done-new", status = TaskStatus.DONE, completedAt = Instant.ofEpochSecond(200)))
        dao.insert(task("in-progress", status = TaskStatus.IN_PROGRESS))
        assertEquals(
            listOf("in-progress", "todo", "done-new", "done-old"),
            dao.observeAll().first().map { it.title },
        )
    }

    @Test fun filterByDueDateRangeIsInclusive() = runTest {
        dao.insert(task("before", date = day.minusDays(1)))
        dao.insert(task("start", date = day))
        dao.insert(task("end", date = day.plusDays(6), status = TaskStatus.DONE, completedAt = Instant.EPOCH))
        dao.insert(task("after", date = day.plusDays(7)))
        assertEquals(listOf("start", "end"), dao.observeDueBetween(day, day.plusDays(6)).first().map { it.title })
    }

    @Test fun countsActiveAndOverdue() = runTest {
        dao.insert(task("overdue", date = day.minusDays(1)))
        dao.insert(task("due-today", date = day))
        dao.insert(task("done-overdue", date = day.minusDays(3), status = TaskStatus.DONE, completedAt = Instant.EPOCH))
        assertEquals(2, dao.observeActiveCount().first())
        assertEquals(1, dao.observeOverdueCount(day).first())
    }

    @Test fun updateStatusAndDeleteCompleted() = runTest {
        val a = dao.insert(task("a"))
        dao.insert(task("b"))
        val doneAt = Instant.ofEpochSecond(1_000)
        assertEquals(1, dao.updateStatus(a, TaskStatus.DONE, doneAt, updatedAt = 5))
        val updated = dao.getById(a)!!
        assertEquals(TaskStatus.DONE, updated.status)
        assertEquals(doneAt, updated.completedAt)
        assertEquals(5, updated.updatedAt)
        assertEquals(1, dao.deleteCompleted())
        assertEquals(listOf("b"), dao.observeAll().first().map { it.title })
    }

    @Test fun deletingSubjectKeepsTaskButClearsLink() = runTest {
        val bio = db.insertSubject("Biologi")
        val id = dao.insert(task("Laporan praktikum", subjectId = bio))
        assertEquals(1, dao.observeBySubject(bio).first().size)
        db.subjectDao().deleteById(bio)
        val stored = dao.getById(id)
        assertNotNull(stored)
        assertNull(stored!!.subjectId)
    }
}

package id.schoolmate.app.domain

import id.schoolmate.app.domain.model.DeadlineUrgency
import id.schoolmate.app.domain.model.Priority
import id.schoolmate.app.domain.model.Task
import id.schoolmate.app.domain.model.TaskFilter
import id.schoolmate.app.domain.model.TaskStatus
import id.schoolmate.app.domain.model.countFor
import id.schoolmate.app.domain.model.filterAndSort
import id.schoolmate.app.domain.model.urgency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class TaskQueriesTest {
    private val today = LocalDate.of(2026, 10, 12)
    private val now = LocalDateTime.of(today, LocalTime.of(9, 0))

    private fun task(id: Long, due: LocalDate, time: LocalTime? = null, priority: Priority = Priority.MEDIUM,
                     status: TaskStatus = TaskStatus.TODO, completedAt: Instant? = null) =
        Task(id = id, title = "T$id", dueDate = due, dueTime = time, priority = priority, status = status,
            completedAt = completedAt ?: if (status == TaskStatus.DONE) Instant.ofEpochSecond(id) else null)

    private val tasks = listOf(
        task(1, today.plusDays(3)),
        task(2, today, LocalTime.of(8, 0)), // overdue (08:00 < 09:00)
        task(3, today), // due end of today, not overdue
        task(4, today, LocalTime.of(13, 0), Priority.HIGH),
        task(5, today.minusDays(2), status = TaskStatus.IN_PROGRESS), // overdue
        task(6, today.minusDays(1), status = TaskStatus.DONE, completedAt = Instant.ofEpochSecond(100)),
        task(7, today.plusDays(1), status = TaskStatus.DONE, completedAt = Instant.ofEpochSecond(200)),
        task(8, today.plusDays(3), priority = Priority.HIGH),
    )

    @Test fun activeFilterSortsByDeadlineTimeThenPriority() {
        val ids = tasks.filterAndSort(TaskFilter.ACTIVE, now).map { it.id }
        // date asc; timed before end-of-day; same slot → higher priority first.
        assertEquals(listOf(5L, 2L, 4L, 3L, 8L, 1L), ids)
    }

    @Test fun overdueFilterOnlyKeepsActiveTasksPastDeadline() {
        assertEquals(listOf(5L, 2L), tasks.filterAndSort(TaskFilter.OVERDUE, now).map { it.id })
    }

    @Test fun doneFilterShowsMostRecentlyCompletedFirst() {
        assertEquals(listOf(7L, 6L), tasks.filterAndSort(TaskFilter.DONE, now).map { it.id })
    }

    @Test fun allFilterListsActiveBeforeCompleted() {
        assertEquals(listOf(5L, 2L, 4L, 3L, 8L, 1L, 7L, 6L), tasks.filterAndSort(TaskFilter.ALL, now).map { it.id })
    }

    @Test fun countsMatchFilters() {
        assertEquals(8, tasks.countFor(TaskFilter.ALL, now))
        assertEquals(6, tasks.countFor(TaskFilter.ACTIVE, now))
        assertEquals(2, tasks.countFor(TaskFilter.OVERDUE, now))
        assertEquals(2, tasks.countFor(TaskFilter.DONE, now))
    }

    @Test fun urgencyClassification() {
        assertEquals(DeadlineUrgency.Overdue(2), task(1, today.minusDays(2)).urgency(now))
        assertEquals(DeadlineUrgency.Overdue(0), task(1, today, LocalTime.of(8, 59)).urgency(now))
        assertEquals(DeadlineUrgency.Today(LocalTime.of(9, 0)), task(1, today, LocalTime.of(9, 0)).urgency(now))
        assertEquals(DeadlineUrgency.Today(null), task(1, today).urgency(now))
        assertEquals(DeadlineUrgency.Tomorrow(null), task(1, today.plusDays(1)).urgency(now))
        assertEquals(DeadlineUrgency.Soon(6), task(1, today.plusDays(6)).urgency(now))
        assertEquals(DeadlineUrgency.Later(today.plusDays(7)), task(1, today.plusDays(7)).urgency(now))
        assertEquals(DeadlineUrgency.Done, task(1, today.minusDays(5), status = TaskStatus.DONE).urgency(now))
    }

    @Test fun onlyNearDeadlinesAreUrgent() {
        assertTrue(DeadlineUrgency.Overdue(1).isUrgent)
        assertTrue(DeadlineUrgency.Today(null).isUrgent)
        assertTrue(DeadlineUrgency.Tomorrow(null).isUrgent)
        assertFalse(DeadlineUrgency.Soon(3).isUrgent)
        assertFalse(DeadlineUrgency.Done.isUrgent)
    }
}

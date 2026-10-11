package id.schoolmate.app.notifications

import id.schoolmate.app.domain.model.ReminderKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationIdentityTest {
    @Test fun idsAreStableAndDistinctAcrossKinds() {
        assertEquals((1 shl 28) + 5, ReminderKey(ReminderKind.TASK, 5).notificationId)
        assertEquals("stable between calls", ReminderKey(ReminderKind.EXAM, 5).notificationId, ReminderKey(ReminderKind.EXAM, 5).notificationId)
        val ids = ReminderKind.entries.flatMap { k -> (1L..500L).map { ReminderKey(k, it).notificationId } }
        assertEquals("no collisions for 3 kinds × 500 ids", ids.size, ids.toSet().size)
        assertNotEquals(ReminderKey(ReminderKind.TASK, 1).notificationId, ReminderKey(ReminderKind.LESSON, 1).notificationId)
        // Never 0 (reserved for the resync alarm request code).
        assertNotEquals(0, ReminderKey(ReminderKind.TASK, 1L shl 28).notificationId)
    }

    @Test fun tokensRoundTrip() {
        ReminderKind.entries.forEach { k ->
            val key = ReminderKey(k, 42)
            assertEquals(key, ReminderKey.parse(key.token))
        }
        assertEquals("task:42", ReminderKey(ReminderKind.TASK, 42).token)
        listOf(null, "", "task", "task:", "task:0", "task:-3", "quiz:1", "task:1:2", "task:x").forEach {
            assertNull("'$it' must not parse", ReminderKey.parse(it))
        }
    }
}

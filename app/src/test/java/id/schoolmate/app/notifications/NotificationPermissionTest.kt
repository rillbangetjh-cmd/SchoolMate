package id.schoolmate.app.notifications

import id.schoolmate.app.notifications.NotificationPermissionStatus.BLOCKED
import id.schoolmate.app.notifications.NotificationPermissionStatus.GRANTED
import id.schoolmate.app.notifications.NotificationPermissionStatus.NEEDS_REQUEST
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationPermissionTest {
    private fun resolve(sdk: Int, granted: Boolean, appEnabled: Boolean = true, requested: Boolean = false, rationale: Boolean = false) =
        NotificationPermission.resolve(sdk, granted, appEnabled, requested, rationale)

    @Test fun android13AndUpAsksOnlyWhenTheDialogCanStillAppear() {
        assertEquals("never asked", NEEDS_REQUEST, resolve(33, granted = false))
        assertEquals("denied once – Android allows one more dialog", NEEDS_REQUEST, resolve(34, granted = false, requested = true, rationale = true))
        assertEquals("denied twice – only settings can fix it", BLOCKED, resolve(34, granted = false, requested = true, rationale = false))
        assertEquals(GRANTED, resolve(35, granted = true))
    }

    @Test fun switchedOffInSystemSettingsIsBlockedOnEveryVersion() {
        assertEquals(BLOCKED, resolve(34, granted = true, appEnabled = false))
        assertEquals(BLOCKED, resolve(26, granted = true, appEnabled = false))
    }

    @Test fun olderAndroidNeedsNoRuntimePermission() {
        assertEquals(GRANTED, resolve(26, granted = true))
        assertEquals(GRANTED, resolve(32, granted = true))
    }
}

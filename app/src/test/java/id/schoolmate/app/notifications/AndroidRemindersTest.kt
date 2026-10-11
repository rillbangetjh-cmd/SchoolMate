package id.schoolmate.app.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.schoolmate.app.R
import id.schoolmate.app.domain.model.ReminderKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Android glue under Robolectric (API 34 → POST_NOTIFICATIONS is a runtime permission). */
@Suppress("DEPRECATION") // ScheduledAlarm.operation has no non-deprecated accessor
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AndroidRemindersTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val nm = app.getSystemService(NotificationManager::class.java)
    private val am = app.getSystemService(AlarmManager::class.java)
    private val zone = ZoneId.of("Asia/Jakarta")
    private val clock = Clock.fixed(Instant.parse("2026-10-12T02:00:00Z"), zone)
    private val notifier = AndroidReminderNotifier(app, clock)

    private fun reminder(kind: ReminderKind = ReminderKind.TASK, id: Long = 5, triggerMinutes: Long = 60) = PlannedReminder(
        ReminderKey(kind, id),
        triggerAt = clock.instant().plusSeconds(triggerMinutes * 60),
        eventAt = clock.instant().plusSeconds(triggerMinutes * 120),
        content = ReminderContent.TaskDue("PR Aljabar", "Matematika", LocalDate.of(2026, 10, 12), LocalTime.of(14, 0)),
    )

    @Before fun setUp() = notifier.createChannels()

    @Test fun channelsHaveIndonesianNames() {
        val names = ReminderKind.entries.map { nm.getNotificationChannel(AndroidReminderNotifier.channelId(it))?.name?.toString() }
        assertEquals(listOf("Pengingat tugas", "Pengingat ujian", "Pengingat pelajaran"), names)
    }

    @Test fun deniedPermissionPostsNothingAndDoesNotThrow() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(notifier.canPost(ReminderKind.TASK))
        assertFalse(notifier.post(reminder()))
        assertTrue(shadowOf(nm).allNotifications.isEmpty())
    }

    @Test fun notificationsSwitchedOffInSystemPostNothing() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(nm).setNotificationsEnabled(false)
        assertFalse(notifier.post(reminder()))
        assertTrue(shadowOf(nm).allNotifications.isEmpty())
    }

    @Test fun grantedPostsOnePerEntityAndTapOpensThatEntity() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertTrue(notifier.post(reminder()))
        assertTrue("re-posting replaces (same id)", notifier.post(reminder()))
        assertTrue(notifier.post(reminder(kind = ReminderKind.EXAM, id = 5).copy(content = ReminderContent.ExamSoon("PTS", null, LocalDate.of(2026, 10, 13), null))))
        val all = shadowOf(nm).allNotifications
        assertEquals(2, all.size)

        val task = shadowOf(nm).getNotification(ReminderKey(ReminderKind.TASK, 5).notificationId)
        assertEquals("reminders_task", task.channelId)
        assertEquals("PR Aljabar", task.extras.getString("android.title"))
        assertEquals("Deadline hari ini pukul 14:00 · Matematika", task.extras.getCharSequence("android.text").toString())

        val tap = shadowOf(task.contentIntent).savedIntent
        assertEquals(ReminderTarget(ReminderKind.TASK, 5), ReminderTarget.from(tap))
        assertEquals(id.schoolmate.app.MainActivity::class.java.name, tap.component?.className)
        assertTrue("explicit + immutable", shadowOf(task.contentIntent).isActivity && shadowOf(task.contentIntent).isImmutable)

        notifier.dismiss(ReminderKey(ReminderKind.TASK, 5))
        assertEquals(1, shadowOf(nm).allNotifications.size)
    }

    @Test fun textsAreIndonesian() {
        val today = LocalDate.of(2026, 10, 12)
        fun f(c: ReminderContent) = ReminderText.format(app, c, today)
        assertEquals("PR" to "Deadline besok (akhir hari)", f(ReminderContent.TaskDue("PR", null, today.plusDays(1), null)))
        assertEquals("Ujian: PTS" to "Matematika · besok pukul 07:30", f(ReminderContent.ExamSoon("PTS", "Matematika", today.plusDays(1), LocalTime.of(7, 30))))
        assertEquals("Ujian: UAS" to "Sen, 19 Okt", f(ReminderContent.ExamSoon("UAS", null, today.plusDays(7), null)))
        assertEquals(
            "Matematika mulai 10 menit lagi" to "07:00–08:30 · Ruang 12",
            f(ReminderContent.LessonSoon("Matematika", LocalTime.of(7, 0), LocalTime.of(8, 30), "12", 10)),
        )
    }

    @Test fun alarmsAreInexactAndNeverDuplicated() {
        val scheduler = AlarmReminderScheduler(app)
        val shadow = shadowOf(am)
        scheduler.schedule(reminder(triggerMinutes = 60))
        scheduler.schedule(reminder(triggerMinutes = 90)) // edit → replaces
        assertEquals(1, shadow.scheduledAlarms.size)
        assertEquals(clock.instant().plusSeconds(90 * 60).toEpochMilli(), shadow.scheduledAlarms.single().triggerAtMs)
        assertTrue("allow-while-idle, no exact alarm", shadow.scheduledAlarms.single().isAllowWhileIdle)

        scheduler.schedule(reminder(kind = ReminderKind.EXAM, id = 5)) // same id, other kind → separate alarm
        assertEquals(2, shadow.scheduledAlarms.size)
        val fired = shadowOf(shadow.scheduledAlarms.first().operation!!).savedIntent
        assertNotNull(ReminderKey.parse(fired.getStringExtra(AlarmReminderScheduler.EXTRA_TOKEN)))
        assertTrue(fired.getLongExtra(AlarmReminderScheduler.EXTRA_TRIGGER_AT, -1) > 0)

        scheduler.cancel(ReminderKey(ReminderKind.TASK, 5))
        scheduler.cancel(ReminderKey(ReminderKind.TASK, 99)) // unknown → no-op
        assertEquals(1, shadow.scheduledAlarms.size)

        scheduler.scheduleResync(clock.instant().plusSeconds(3_600))
        assertEquals(2, shadow.scheduledAlarms.size)
        scheduler.scheduleResync(null)
        assertEquals(1, shadow.scheduledAlarms.size)
    }

    @Test fun systemEventsReachTheResyncReceiver() {
        val pm = app.packageManager
        listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED).forEach { action ->
            val receivers = pm.queryBroadcastReceivers(Intent(action).setPackage(app.packageName), 0).map { it.activityInfo.name }
            assertTrue("$action → $receivers", SystemEventsReceiver::class.java.name in receivers)
            assertTrue(action in SystemEventsReceiver.HANDLED)
        }
        val requested = pm.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toSet()
        assertTrue(Manifest.permission.POST_NOTIFICATIONS in requested)
        assertTrue(Manifest.permission.RECEIVE_BOOT_COMPLETED in requested)
        assertFalse("no exact-alarm permission", "android.permission.SCHEDULE_EXACT_ALARM" in requested || "android.permission.USE_EXACT_ALARM" in requested)
    }

    @Test fun reminderTargetIgnoresForeignIntents() {
        assertNull(ReminderTarget.from(null))
        assertNull(ReminderTarget.from(Intent(Intent.ACTION_MAIN)))
        assertNull(ReminderTarget.from(Intent(ReminderTarget.ACTION_OPEN)))
    }
}

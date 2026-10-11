package id.schoolmate.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri
import java.time.Instant

/**
 * Inexact, Doze-friendly alarms: [AlarmManager.setAndAllowWhileIdle] needs no exact-alarm
 * permission and survives idle mode, but Android may deliver it some minutes late. Each entity has
 * one PendingIntent (same request code + data URI), so re-scheduling replaces instead of duplicating.
 */
class AlarmReminderScheduler(context: Context) : ReminderScheduler {
    private val context = context.applicationContext
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun schedule(reminder: PlannedReminder) {
        val pi = PendingIntent.getBroadcast(
            context, reminder.key.notificationId,
            fireIntent(context, reminder.key).putExtra(EXTRA_TRIGGER_AT, reminder.triggerAt.toEpochMilli()),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        guarded("schedule ${reminder.key.token}") {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerAt.toEpochMilli(), pi)
        }
    }

    override fun cancel(key: ReminderKey) {
        val pi = PendingIntent.getBroadcast(
            context, key.notificationId, fireIntent(context, key),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
        ) ?: return
        guarded("cancel ${key.token}") { alarmManager.cancel(pi) }
        pi.cancel()
    }

    override fun scheduleResync(at: Instant?) {
        val intent = Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_RESYNC)
            .setData("schoolmate-reminder://resync".toUri())
        if (at == null) {
            PendingIntent.getBroadcast(context, RESYNC_REQUEST_CODE, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE)
                ?.let { pi -> guarded("cancel resync") { alarmManager.cancel(pi) }; pi.cancel() }
            return
        }
        val pi = PendingIntent.getBroadcast(context, RESYNC_REQUEST_CODE, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        guarded("schedule resync") { alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pi) }
    }

    /** OEM limits (e.g. the 500-alarm cap) must never crash the app. */
    private inline fun guarded(what: String, block: () -> Unit) {
        try { block() } catch (e: RuntimeException) { Log.w(TAG, "AlarmManager failed to $what", e) }
    }

    companion object {
        private const val TAG = "AlarmReminderScheduler"
        const val EXTRA_TOKEN = "id.schoolmate.app.extra.REMINDER_TOKEN"
        const val EXTRA_TRIGGER_AT = "id.schoolmate.app.extra.REMINDER_TRIGGER_AT"
        private const val RESYNC_REQUEST_CODE = 0

        /** Explicit intent; the data URI keeps PendingIntents of different entities distinct. */
        fun fireIntent(context: Context, key: ReminderKey): Intent =
            Intent(context, ReminderReceiver::class.java)
                .setAction(ReminderReceiver.ACTION_FIRE)
                .setData("schoolmate-reminder://${key.kind.slug}/${key.entityId}".toUri())
                .putExtra(EXTRA_TOKEN, key.token)
    }
}

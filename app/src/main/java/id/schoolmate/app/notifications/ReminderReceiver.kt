package id.schoolmate.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import id.schoolmate.app.SchoolMateApplication
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Not exported: only our own (immutable, explicit) alarm PendingIntents reach it. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as SchoolMateApplication).container
        val pending = goAsync()
        container.applicationScope.launch {
            try {
                // goAsync allows ~10 s; a database read + notify takes milliseconds.
                withTimeoutOrNull(8_000) {
                    when (intent.action) {
                        ACTION_FIRE -> {
                            val token = intent.getStringExtra(AlarmReminderScheduler.EXTRA_TOKEN)
                            val trigger = intent.getLongExtra(AlarmReminderScheduler.EXTRA_TRIGGER_AT, -1L)
                            if (token != null && trigger > 0) container.reminderSync.onAlarm(token, trigger)
                            else container.reminderSync.sync()
                        }
                        ACTION_RESYNC -> container.reminderSync.sync()
                    }
                }
            } catch (e: Exception) {
                Log.w("ReminderReceiver", "Reminder handling failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "id.schoolmate.app.action.REMINDER_FIRE"
        const val ACTION_RESYNC = "id.schoolmate.app.action.REMINDER_RESYNC"
    }
}

/**
 * Re-registers every alarm after events that drop or shift them: reboot (alarms are cleared),
 * manual clock change, time zone change, and app update. All are exempt from the implicit-broadcast
 * limits, and the system can deliver them to a non-exported receiver.
 */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED) return
        val container = (context.applicationContext as SchoolMateApplication).container
        val pending = goAsync()
        container.applicationScope.launch {
            try {
                withTimeoutOrNull(8_000) { container.reminderSync.sync(force = true) }
            } catch (e: Exception) {
                Log.w("SystemEventsReceiver", "Resync after ${intent.action} failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}

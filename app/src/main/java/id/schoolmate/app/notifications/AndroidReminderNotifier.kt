package id.schoolmate.app.notifications

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import id.schoolmate.app.R
import id.schoolmate.app.domain.model.ReminderKind
import id.schoolmate.app.util.Formatters
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime

/** Posts reminder notifications on three Indonesian-named channels (one per kind). */
class AndroidReminderNotifier(context: Context, private val clock: Clock) : ReminderNotifier {
    private val context = context.applicationContext
    private val manager = NotificationManagerCompat.from(this.context)

    /** Idempotent; safe to call on every process start. */
    fun createChannels() {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        ReminderKind.entries.forEach { kind ->
            val (name, description) = when (kind) {
                ReminderKind.TASK -> R.string.channel_tasks_name to R.string.channel_tasks_description
                ReminderKind.EXAM -> R.string.channel_exams_name to R.string.channel_exams_description
                ReminderKind.LESSON -> R.string.channel_lessons_name to R.string.channel_lessons_description
            }
            val channel = NotificationChannel(channelId(kind), context.getString(name), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { this.description = context.getString(description) }
            nm.createNotificationChannel(channel)
        }
    }

    override fun canPost(kind: ReminderKind): Boolean {
        if (!NotificationPermission.canPost(context)) return false
        val channel = context.getSystemService(NotificationManager::class.java)?.getNotificationChannel(channelId(kind))
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    @SuppressLint("MissingPermission") // checked by canPost(); SecurityException is caught as well
    override fun post(reminder: PlannedReminder): Boolean {
        if (!canPost(reminder.key.kind)) return false
        val (title, text) = ReminderText.format(context, reminder.content, LocalDate.now(clock))
        val tap = PendingIntent.getActivity(
            context, reminder.key.notificationId, ReminderTarget.intent(context, reminder.key),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, channelId(reminder.key.kind))
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setColor(context.getColor(R.color.brand_teal))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(if (reminder.key.kind == ReminderKind.TASK) NotificationCompat.CATEGORY_REMINDER else NotificationCompat.CATEGORY_EVENT)
            .setWhen(reminder.eventAt.toEpochMilli())
            .setShowWhen(true)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        return try {
            manager.notify(reminder.key.notificationId, notification)
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission revoked", e)
            false
        }
    }

    override fun dismiss(key: ReminderKey) = manager.cancel(key.notificationId)

    companion object {
        private const val TAG = "ReminderNotifier"
        fun channelId(kind: ReminderKind) = "reminders_${kind.slug}"
    }
}

/** Indonesian notification texts, built from string resources. */
object ReminderText {
    fun format(context: Context, content: ReminderContent, today: LocalDate): Pair<String, String> = when (content) {
        is ReminderContent.TaskDue -> {
            val whenText = moment(context, content.dueDate, content.dueTime, today, endOfDay = true)
            val text = context.getString(R.string.reminder_task_text, whenText)
            content.title to withSubject(text, content.subjectName)
        }
        is ReminderContent.ExamSoon -> {
            val whenText = moment(context, content.date, content.startTime, today, endOfDay = false)
            context.getString(R.string.reminder_exam_title, content.title) to withSubject(whenText, content.subjectName, subjectFirst = true)
        }
        is ReminderContent.LessonSoon -> {
            val title = context.getString(R.string.reminder_lesson_title, content.subjectName, content.leadMinutes)
            val range = Formatters.timeRange(content.start, content.end)
            title to if (content.room.isBlank()) range else context.getString(R.string.reminder_lesson_room, range, content.room)
        }
    }

    private fun moment(context: Context, date: LocalDate, time: LocalTime?, today: LocalDate, endOfDay: Boolean): String {
        val day = when (date) {
            today -> context.getString(R.string.reminder_today)
            today.plusDays(1) -> context.getString(R.string.reminder_tomorrow)
            else -> Formatters.dateShort(date, today)
        }
        return when {
            time != null -> context.getString(R.string.reminder_day_time, day, Formatters.time(time))
            endOfDay -> context.getString(R.string.reminder_day_end, day)
            else -> day
        }
    }

    private fun withSubject(text: String, subject: String?, subjectFirst: Boolean = false): String = when {
        subject.isNullOrBlank() -> text
        subjectFirst -> "$subject · $text"
        else -> "$text · $subject"
    }
}

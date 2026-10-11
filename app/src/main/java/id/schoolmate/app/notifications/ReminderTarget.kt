package id.schoolmate.app.notifications

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import id.schoolmate.app.MainActivity
import id.schoolmate.app.domain.model.ReminderKind

/** Where a tapped notification should lead. Parsed from the MainActivity intent. */
data class ReminderTarget(val kind: ReminderKind, val entityId: Long) {
    companion object {
        const val ACTION_OPEN = "id.schoolmate.app.action.OPEN_REMINDER"
        private const val EXTRA_TOKEN = "id.schoolmate.app.extra.OPEN_TOKEN"

        /** Explicit intent into our own activity (not exported via any intent-filter). */
        fun intent(context: Context, key: ReminderKey): Intent =
            Intent(context, MainActivity::class.java)
                .setAction(ACTION_OPEN)
                .setData("schoolmate://open/${key.kind.slug}/${key.entityId}".toUri())
                .putExtra(EXTRA_TOKEN, key.token)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        fun from(intent: Intent?): ReminderTarget? {
            if (intent?.action != ACTION_OPEN) return null
            val key = ReminderKey.parse(intent.getStringExtra(EXTRA_TOKEN)) ?: return null
            return ReminderTarget(key.kind, key.entityId)
        }
    }
}

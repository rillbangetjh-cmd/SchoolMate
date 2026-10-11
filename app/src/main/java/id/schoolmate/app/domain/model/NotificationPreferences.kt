package id.schoolmate.app.domain.model

/** The three reminder sources. [code] is part of stable notification / alarm identities. */
enum class ReminderKind(val code: Int, val slug: String) { TASK(1, "task"), EXAM(2, "exam"), LESSON(3, "lesson") }

/**
 * Local reminder settings. Notifications are opt-in ([enabled] = false by default) so the app never
 * asks for the Android 13+ permission before the user actually wants reminders.
 */
data class NotificationPreferences(
    val enabled: Boolean = false,
    val taskReminders: Boolean = true,
    val examReminders: Boolean = true,
    val lessonReminders: Boolean = true,
    val taskLeadMinutes: Int = DEFAULT_TASK_LEAD,
    val examLeadMinutes: Int = DEFAULT_EXAM_LEAD,
    val lessonLeadMinutes: Int = DEFAULT_LESSON_LEAD,
    /** True once the runtime permission dialog has been shown; used to detect "blocked". */
    val permissionRequested: Boolean = false,
) {
    val tasksActive: Boolean get() = enabled && taskReminders
    val examsActive: Boolean get() = enabled && examReminders
    val lessonsActive: Boolean get() = enabled && lessonReminders

    companion object {
        val TASK_LEAD_OPTIONS = listOf(60, 180, 1_440, 2_880)
        val EXAM_LEAD_OPTIONS = listOf(60, 1_440, 2_880, 4_320)
        val LESSON_LEAD_OPTIONS = listOf(5, 10, 15, 30)
        const val DEFAULT_TASK_LEAD = 1_440
        const val DEFAULT_EXAM_LEAD = 1_440
        const val DEFAULT_LESSON_LEAD = 10

        /** Stored values outside the offered options fall back to the default. */
        fun sanitize(value: Int?, options: List<Int>, default: Int): Int = value?.takeIf { it in options } ?: default
    }
}

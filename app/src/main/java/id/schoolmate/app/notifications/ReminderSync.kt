package id.schoolmate.app.notifications

import android.util.Log
import id.schoolmate.app.data.preferences.NotificationPreferencesRepository
import id.schoolmate.app.data.repository.ExamRepository
import id.schoolmate.app.data.repository.ScheduleRepository
import id.schoolmate.app.data.repository.SubjectRepository
import id.schoolmate.app.data.repository.TaskRepository
import id.schoolmate.app.domain.model.ReminderKind
import id.schoolmate.app.domain.model.TaskStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock

/**
 * Keeps Android alarms in line with the database. It never polls: it runs when data or settings
 * change (Room/DataStore flows while the app is open), when an alarm fires, and after system events
 * (boot, time/zone change, app update) – see [SystemEventsReceiver].
 */
class ReminderSync(
    private val prefs: NotificationPreferencesRepository,
    private val tasks: TaskRepository,
    private val exams: ExamRepository,
    private val schedule: ScheduleRepository,
    private val subjects: SubjectRepository,
    private val scheduler: ReminderScheduler,
    private val notifier: ReminderNotifier,
    private val registry: ReminderRegistry,
    private val clock: Clock,
) {
    data class Result(val scheduled: Int, val cancelled: Int, val unchanged: Int)

    private val mutex = Mutex()
    private var observer: Job? = null

    private suspend fun snapshot() = ReminderPlanner.Input(
        prefs = prefs.preferences.first(),
        tasks = tasks.observeAll().first(),
        exams = exams.observeAll().first(),
        slots = schedule.observeWeek().first(),
        subjectNames = subjects.observeSubjects().first().associate { it.id to it.name },
    )

    /**
     * Re-plans every reminder. With [force] all alarms are registered again (needed after reboot,
     * time/zone change or app update, when the system may have dropped or shifted them).
     */
    suspend fun sync(force: Boolean = false): Result = mutex.withLock {
        val input = snapshot()
        val plan = ReminderPlanner.plan(clock.instant(), clock.zone, input)
        val desired = plan.reminders.associateBy { it.key.token }
        val current = registry.scheduled()

        var cancelled = 0
        (current.keys - desired.keys).forEach { token ->
            ReminderKey.parse(token)?.let { scheduler.cancel(it); cancelled++ }
        }
        var scheduled = 0
        var unchanged = 0
        desired.values.forEach { r ->
            if (force || current[r.key.token] != r.triggerAt.toEpochMilli()) {
                scheduler.schedule(r); scheduled++
            } else {
                unchanged++
            }
        }
        scheduler.scheduleResync(plan.resyncAt)
        registry.setScheduled(desired.mapValues { it.value.triggerAt.toEpochMilli() })
        dismissStale(input)
        Result(scheduled, cancelled, unchanged)
    }

    /** Removes shown notifications whose task is done/deleted, exam deleted, or that are disabled. */
    private suspend fun dismissStale(input: ReminderPlanner.Input) {
        val activeTasks = input.tasks.filter { it.status != TaskStatus.DONE }.map { it.id }.toSet()
        val exams = input.exams.map { it.id }.toSet()
        val lessons = input.slots.map { it.entry.id }.toSet()
        val stale = registry.deliveredTokens().filter { token ->
            val key = ReminderKey.parse(token) ?: return@filter true
            when (key.kind) {
                ReminderKind.TASK -> key.entityId !in activeTasks || !input.prefs.tasksActive
                ReminderKind.EXAM -> key.entityId !in exams || !input.prefs.examsActive
                ReminderKind.LESSON -> key.entityId !in lessons || !input.prefs.lessonsActive
            }
        }.toSet()
        stale.forEach { token -> ReminderKey.parse(token)?.let(notifier::dismiss) }
        registry.forgetDelivered(stale)
    }

    /**
     * Called by the alarm. Re-checks the entity against the database before showing anything, so an
     * alarm that outlived an edit, completion or deletion stays silent. Then plans the next one.
     *
     * @return true if a notification was shown.
     */
    suspend fun onAlarm(token: String, triggerAtMillis: Long): Boolean {
        val posted = mutex.withLock {
            val key = ReminderKey.parse(token) ?: return@withLock false
            val input = snapshot()
            // Evaluate "as of" the intended trigger so the same reminder is found again.
            val asOf = java.time.Instant.ofEpochMilli(triggerAtMillis - 1)
            val reminder = ReminderPlanner.planAll(asOf, clock.zone, input).firstOrNull { it.key == key }
            val now = clock.instant()
            when {
                reminder == null || reminder.triggerAt.toEpochMilli() != triggerAtMillis -> false // edited/removed
                !now.isBefore(reminder.eventAt) -> false // delivered too late: event already started/passed
                registry.wasDelivered(token, triggerAtMillis) -> false // duplicate alarm
                !notifier.post(reminder) -> false // permission missing or notifications off
                else -> {
                    registry.markDelivered(token, triggerAtMillis, now.toEpochMilli())
                    true
                }
            }
        }
        sync()
        return posted
    }

    /** Starts the in-app observer once; re-syncs (debounced) on every relevant change. */
    @OptIn(FlowPreview::class)
    fun startObserving(scope: CoroutineScope) {
        if (observer?.isActive == true) return
        observer = scope.launch {
            var first = true
            combine(
                prefs.preferences,
                tasks.observeAll(),
                exams.observeAll(),
                schedule.observeWeek(),
                subjects.observeSubjects(),
            ) { _, _, _, _, _ -> Unit }
                .debounce(DEBOUNCE_MILLIS)
                .collect {
                    // First run after process start is forced: alarms may be gone after a force-stop.
                    runCatching { sync(force = first) }.onFailure { Log.w(TAG, "sync failed", it) }
                    first = false
                }
        }
    }

    companion object {
        private const val TAG = "ReminderSync"
        const val DEBOUNCE_MILLIS = 400L
    }
}

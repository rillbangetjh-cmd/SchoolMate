package id.schoolmate.app.ui.screens.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.schoolmate.app.data.repository.ScheduleRepository
import id.schoolmate.app.data.repository.SubjectRepository
import id.schoolmate.app.domain.model.ScheduleEntry
import id.schoolmate.app.domain.model.ScheduleSlot
import id.schoolmate.app.domain.model.Subject
import id.schoolmate.app.domain.validation.Limits
import id.schoolmate.app.domain.validation.ValidationError
import id.schoolmate.app.domain.validation.ValidationException
import id.schoolmate.app.domain.validation.validateScheduleEntry
import id.schoolmate.app.ui.model.EditorEvent
import id.schoolmate.app.ui.model.FieldError
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalTime

data class ScheduleForm(
    val day: DayOfWeek,
    val subjectId: Long? = null,
    val start: LocalTime = DEFAULT_START,
    val end: LocalTime = DEFAULT_START.plusMinutes(DEFAULT_LENGTH_MINUTES),
    val room: String = "",
    val note: String = "",
)

enum class TimeRangeError { END_BEFORE_START, TOO_SHORT }

data class ScheduleFormErrors(
    val subject: FieldError? = null,
    val time: TimeRangeError? = null,
    val room: FieldError? = null,
    val note: FieldError? = null,
    /** Set when the save was rejected because of an overlap. */
    val overlapRejected: Boolean = false,
)

data class ScheduleEditorUiState(
    val isNew: Boolean,
    val isLoading: Boolean,
    val form: ScheduleForm,
    val errors: ScheduleFormErrors = ScheduleFormErrors(),
    /** Existing lessons that overlap the current form, computed live. */
    val conflicts: List<ScheduleSlot> = emptyList(),
    val subjects: List<Subject> = emptyList(),
    val isSaving: Boolean = false,
    val notFound: Boolean = false,
)

const val DEFAULT_LENGTH_MINUTES = 90L
private val DEFAULT_START: LocalTime = LocalTime.of(7, 0)

/** Slots on the same day overlapping [start, end) (half-open), excluding [excludeId]. */
fun List<ScheduleSlot>.conflictsWith(day: DayOfWeek, start: LocalTime, end: LocalTime, excludeId: Long) =
    filter {
        it.entry.id != excludeId && it.entry.dayOfWeek == day &&
            it.entry.startTime.isBefore(end) && it.entry.endTime.isAfter(start)
    }

/** Suggests the next free slot after the last lesson of [day]. */
fun suggestNextSlot(week: List<ScheduleSlot>, day: DayOfWeek): Pair<LocalTime, LocalTime> {
    val lastEnd = week.filter { it.entry.dayOfWeek == day }.maxOfOrNull { it.entry.endTime }
        ?: return DEFAULT_START to DEFAULT_START.plusMinutes(DEFAULT_LENGTH_MINUTES)
    val latestStart = LocalTime.of(23, 59).minusMinutes(Limits.SCHEDULE_MIN_MINUTES.toLong())
    val start = if (lastEnd.isAfter(latestStart)) latestStart else lastEnd
    val end = if (start.isAfter(LocalTime.of(23, 59).minusMinutes(DEFAULT_LENGTH_MINUTES))) {
        LocalTime.of(23, 59)
    } else {
        start.plusMinutes(DEFAULT_LENGTH_MINUTES)
    }
    return start to end
}

class ScheduleEditorViewModel(
    private val entryId: Long,
    initialDay: DayOfWeek,
    private val scheduleRepository: ScheduleRepository,
    subjectRepository: SubjectRepository,
) : ViewModel() {

    private val form = MutableStateFlow(
        ScheduleEditorUiState(isNew = entryId == 0L, isLoading = true, form = ScheduleForm(day = initialDay)),
    )

    private val events = Channel<EditorEvent>(Channel.BUFFERED)
    val eventFlow: Flow<EditorEvent> = events.receiveAsFlow()

    private val week = scheduleRepository.observeWeek()

    val uiState: StateFlow<ScheduleEditorUiState> = combine(
        form,
        week,
        subjectRepository.observeSubjects(),
    ) { state, slots, subjects ->
        val f = state.form
        state.copy(
            subjects = subjects,
            conflicts = if (f.end.isAfter(f.start)) slots.conflictsWith(f.day, f.start, f.end, entryId) else emptyList(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), form.value)

    init {
        viewModelScope.launch {
            val slots = week.first()
            if (entryId == 0L) {
                val (start, end) = suggestNextSlot(slots, initialDay)
                val firstSubject = subjectRepository.observeSubjects().first().singleOrNull()?.id
                form.update { it.copy(isLoading = false, form = it.form.copy(start = start, end = end, subjectId = firstSubject)) }
            } else {
                val entry = slots.firstOrNull { it.entry.id == entryId }?.entry
                form.update {
                    if (entry == null) it.copy(isLoading = false, notFound = true)
                    else it.copy(
                        isLoading = false,
                        form = ScheduleForm(entry.dayOfWeek, entry.subjectId, entry.startTime, entry.endTime, entry.room, entry.note),
                    )
                }
            }
        }
    }

    private fun edit(block: (ScheduleForm) -> ScheduleForm, clear: (ScheduleFormErrors) -> ScheduleFormErrors) =
        form.update { it.copy(form = block(it.form), errors = clear(it.errors).copy(overlapRejected = false)) }

    fun onDayChange(d: DayOfWeek) = edit({ it.copy(day = d) }, { it })
    fun onSubjectChange(id: Long) = edit({ it.copy(subjectId = id) }, { it.copy(subject = null) })

    /** Moving the start keeps the lesson length when possible. */
    fun onStartChange(t: LocalTime) = edit({
        val length = java.time.Duration.between(it.start, it.end).toMinutes()
        val newEnd = if (length > 0 && t.plusMinutes(length).isAfter(t)) t.plusMinutes(length) else it.end
        it.copy(start = t, end = newEnd)
    }, { it.copy(time = null) })

    fun onEndChange(t: LocalTime) = edit({ it.copy(end = t) }, { it.copy(time = null) })
    fun onRoomChange(v: String) = edit({ it.copy(room = v) }, { it.copy(room = null) })
    fun onNoteChange(v: String) = edit({ it.copy(note = v) }, { it.copy(note = null) })

    private fun Set<ValidationError>.toErrors() = ScheduleFormErrors(
        subject = if (ValidationError.INVALID_SUBJECT in this || ValidationError.INVALID_REFERENCE in this) FieldError.REQUIRED else null,
        time = when {
            ValidationError.INVALID_TIME_RANGE in this -> TimeRangeError.END_BEFORE_START
            ValidationError.SCHEDULE_TOO_SHORT in this -> TimeRangeError.TOO_SHORT
            else -> null
        },
        room = if (ValidationError.TEXT_TOO_LONG in this) FieldError.TOO_LONG else null,
        note = if (ValidationError.NOTE_TOO_LONG in this) FieldError.TOO_LONG else null,
        overlapRejected = ValidationError.SCHEDULE_OVERLAP in this,
    )

    fun save() {
        // Read the source-of-truth form, not the combined uiState: the latter may still be one
        // emission behind right after a field change.
        val state = form.value
        if (state.isSaving || state.isLoading || state.notFound) return
        val f = state.form
        val entry = ScheduleEntry(
            id = entryId, subjectId = f.subjectId ?: 0, dayOfWeek = f.day,
            startTime = f.start, endTime = f.end, room = f.room.trim(), note = f.note.trim(),
        )
        form.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val local = validateScheduleEntry(entry)
            val conflicts = if (f.end.isAfter(f.start)) week.first().conflictsWith(f.day, f.start, f.end, entryId) else emptyList()
            if (local.isNotEmpty() || conflicts.isNotEmpty()) {
                val errors = (if (conflicts.isNotEmpty()) local + ValidationError.SCHEDULE_OVERLAP else local).toErrors()
                form.update { it.copy(isSaving = false, errors = errors) }
                return@launch
            }
            val result = if (entryId == 0L) scheduleRepository.addEntry(entry).map { } else scheduleRepository.updateEntry(entry)
            result.onSuccess {
                form.update { it.copy(isSaving = false) }
                events.send(EditorEvent.Saved)
            }.onFailure { e ->
                val errors = (e as? ValidationException)?.errors?.toErrors() ?: ScheduleFormErrors()
                form.update { it.copy(isSaving = false, errors = errors, notFound = e is NoSuchElementException) }
            }
        }
    }

    fun delete() {
        if (entryId == 0L) return
        viewModelScope.launch {
            scheduleRepository.deleteEntry(entryId)
            events.send(EditorEvent.Deleted)
        }
    }
}

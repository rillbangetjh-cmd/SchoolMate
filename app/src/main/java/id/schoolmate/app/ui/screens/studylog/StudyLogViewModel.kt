package id.schoolmate.app.ui.screens.studylog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.schoolmate.app.data.repository.StudySessionRepository
import id.schoolmate.app.data.repository.SubjectRepository
import id.schoolmate.app.domain.model.StudySession
import id.schoolmate.app.domain.model.Subject
import id.schoolmate.app.domain.validation.Limits
import id.schoolmate.app.ui.model.FieldError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

data class SubjectMinutes(val subject: Subject?, val minutes: Int)

data class StudyLogUiState(
    val isLoading: Boolean = true,
    val weekStart: LocalDate,
    val isCurrentWeek: Boolean = true,
    val sessions: List<Pair<StudySession, Subject?>> = emptyList(),
    val totalMinutes: Int = 0,
    val perSubject: List<SubjectMinutes> = emptyList(),
) {
    val weekEnd: LocalDate get() = weekStart.plusDays(6)
}

data class SessionForm(
    val subjectId: Long? = null,
    val date: LocalDate,
    val start: LocalTime,
    val durationText: String = "30",
    val note: String = "",
)

data class SessionFormState(val form: SessionForm, val durationError: FieldError? = null, val noteError: FieldError? = null)

fun weekStartOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/** Validates the free-text duration field for a study session. */
fun parseSessionDuration(text: String): Int? =
    text.trim().toIntOrNull()?.takeIf { it in Limits.STUDY_SESSION_MIN_MINUTES..Limits.STUDY_SESSION_MAX_MINUTES }

@OptIn(ExperimentalCoroutinesApi::class)
class StudyLogViewModel(
    private val sessionRepository: StudySessionRepository,
    private val subjectRepository: SubjectRepository,
    private val clock: Clock,
) : ViewModel() {

    private val currentWeek = weekStartOf(LocalDate.now(clock))
    private val weekStart = MutableStateFlow(currentWeek)

    val uiState: StateFlow<StudyLogUiState> = weekStart.flatMapLatest { start ->
        val end = start.plusDays(6)
        combine(
            sessionRepository.observeBetween(start, end),
            sessionRepository.observeMinutesPerSubject(start, end),
            subjectRepository.observeSubjects(),
        ) { sessions, perSubject, subjects ->
            val byId = subjects.associateBy { it.id }
            StudyLogUiState(
                isLoading = false,
                weekStart = start,
                isCurrentWeek = start == currentWeek,
                sessions = sessions.sortedWith(compareByDescending<StudySession> { it.startedAt }.thenByDescending { it.id })
                    .map { it to it.subjectId?.let(byId::get) },
                totalMinutes = perSubject.sumOf { it.minutes },
                perSubject = perSubject.sortedByDescending { it.minutes }.map { SubjectMinutes(it.subjectId?.let(byId::get), it.minutes) },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StudyLogUiState(weekStart = currentWeek))

    val subjects: StateFlow<List<Subject>> = subjectRepository.observeSubjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _editor = MutableStateFlow<SessionFormState?>(null)
    val editor: StateFlow<SessionFormState?> = _editor.asStateFlow()

    fun previousWeek() = weekStart.update { it.minusWeeks(1) }

    fun nextWeek() = weekStart.update { if (it < currentWeek) it.plusWeeks(1) else it }

    fun openEditor() {
        // Default: a 30-minute session that just ended (clamped to today).
        val now = java.time.LocalDateTime.now(clock).withSecond(0).withNano(0)
        val start = now.minusMinutes(30)
        val startTime = if (start.toLocalDate() == now.toLocalDate()) start.toLocalTime() else LocalTime.MIDNIGHT
        _editor.value = SessionFormState(SessionForm(date = now.toLocalDate(), start = startTime))
    }

    fun dismissEditor() {
        _editor.value = null
    }

    fun onFormChange(form: SessionForm) = _editor.update { it?.copy(form = form, durationError = null, noteError = null) }

    fun saveSession() {
        val state = _editor.value ?: return
        val f = state.form
        val duration = parseSessionDuration(f.durationText)
        val noteError = if (f.note.trim().length > Limits.NOTE_MAX) FieldError.TOO_LONG else null
        if (duration == null || noteError != null) {
            _editor.value = state.copy(durationError = if (duration == null) FieldError.INVALID else null, noteError = noteError)
            return
        }
        viewModelScope.launch {
            val startedAt = f.date.atTime(f.start).atZone(clock.zone).toInstant()
            sessionRepository.logSession(startedAt, duration, subjectId = f.subjectId, note = f.note.trim())
                .onSuccess {
                    _editor.value = null
                    weekStart.value = weekStartOf(f.date)
                }
                .onFailure { _editor.value = state.copy(durationError = FieldError.INVALID) }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { sessionRepository.deleteSession(id) }
    }
}

package id.schoolmate.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.schoolmate.app.data.preferences.UserPreferencesRepository
import id.schoolmate.app.data.repository.SubjectRepository
import id.schoolmate.app.util.dayPeriodOf
import id.schoolmate.app.util.greetingFor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class HomeUiState(
    val greeting: String = "",
    val dateLabel: String = "",
    val subjectCount: Int = 0,
    val hasName: Boolean = false,
    val isLoading: Boolean = true,
)

class HomeViewModel(
    preferencesRepository: UserPreferencesRepository,
    subjectRepository: SubjectRepository,
    private val now: () -> Pair<LocalDate, LocalTime> = { LocalDate.now() to LocalTime.now() },
) : ViewModel() {

    private val dateFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.forLanguageTag("id-ID"))

    val uiState: StateFlow<HomeUiState> = combine(
        preferencesRepository.userPreferences,
        subjectRepository.observeSubjectCount(),
    ) { prefs, count ->
        val (date, time) = now()
        HomeUiState(
            greeting = greetingFor(dayPeriodOf(time), prefs.studentName),
            dateLabel = date.format(dateFormatter).replaceFirstChar { it.titlecase(Locale.forLanguageTag("id-ID")) },
            subjectCount = count,
            hasName = prefs.studentName.isNotBlank(),
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}

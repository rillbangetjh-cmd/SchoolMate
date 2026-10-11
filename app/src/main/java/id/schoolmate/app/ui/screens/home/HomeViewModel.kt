package id.schoolmate.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.schoolmate.app.data.preferences.UserPreferencesRepository
import id.schoolmate.app.data.repository.SubjectRepository
import id.schoolmate.app.domain.usecase.DashboardSummary
import id.schoolmate.app.util.dayPeriodOf
import id.schoolmate.app.util.greetingFor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class HomeUiState(
    val greeting: String = "",
    val dateLabel: String = "",
    val subjectCount: Int = 0,
    val hasName: Boolean = false,
    val summary: DashboardSummary = DashboardSummary(),
    val isLoading: Boolean = true,
)

class HomeViewModel(
    preferencesRepository: UserPreferencesRepository,
    subjectRepository: SubjectRepository,
    observeDashboardSummary: () -> Flow<DashboardSummary>,
    private val clock: Clock,
) : ViewModel() {

    private val locale = Locale.forLanguageTag("id-ID")
    private val dateFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", locale)

    val uiState: StateFlow<HomeUiState> = combine(
        preferencesRepository.userPreferences,
        subjectRepository.observeSubjectCount(),
        observeDashboardSummary(),
    ) { prefs, count, summary ->
        HomeUiState(
            greeting = greetingFor(dayPeriodOf(LocalTime.now(clock)), prefs.studentName),
            dateLabel = LocalDate.now(clock).format(dateFormatter).replaceFirstChar { it.titlecase(locale) },
            subjectCount = count,
            hasName = prefs.studentName.isNotBlank(),
            summary = summary,
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}

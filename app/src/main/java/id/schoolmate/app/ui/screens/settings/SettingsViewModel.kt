package id.schoolmate.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.schoolmate.app.data.preferences.UserPreferencesRepository
import id.schoolmate.app.domain.model.ThemeMode
import id.schoolmate.app.domain.model.UserPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

const val MAX_STUDENT_NAME_LENGTH = 30

sealed interface SettingsUiState {
    data object Loading : SettingsUiState
    data class Ready(val preferences: UserPreferences) : SettingsUiState
}

class SettingsViewModel(private val repository: UserPreferencesRepository) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = repository.userPreferences
        .map<UserPreferences, SettingsUiState> { SettingsUiState.Ready(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState.Loading)

    fun saveStudentName(name: String) {
        viewModelScope.launch { repository.setStudentName(name.trim().take(MAX_STUDENT_NAME_LENGTH)) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun setUseDynamicColor(enabled: Boolean) {
        viewModelScope.launch { repository.setUseDynamicColor(enabled) }
    }
}

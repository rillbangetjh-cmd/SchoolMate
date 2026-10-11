package id.schoolmate.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.schoolmate.app.data.preferences.NotificationPreferencesRepository
import id.schoolmate.app.domain.model.NotificationPreferences
import id.schoolmate.app.domain.model.ReminderKind
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Notification settings only touch DataStore keys; the reminder observer reacts to the change and
 * re-plans alarms. No task/exam/schedule data is modified here.
 */
class NotificationSettingsViewModel(private val repository: NotificationPreferencesRepository) : ViewModel() {

    /** null while loading. */
    val preferences: StateFlow<NotificationPreferences?> = repository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(enabled) }
    }

    fun setKindEnabled(kind: ReminderKind, enabled: Boolean) {
        viewModelScope.launch { repository.setKindEnabled(kind, enabled) }
    }

    fun setLeadMinutes(kind: ReminderKind, minutes: Int) {
        viewModelScope.launch { repository.setLeadMinutes(kind, minutes) }
    }

    fun onPermissionRequested() {
        viewModelScope.launch { repository.markPermissionRequested() }
    }
}

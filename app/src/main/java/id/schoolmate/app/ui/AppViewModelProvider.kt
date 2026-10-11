package id.schoolmate.app.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import id.schoolmate.app.SchoolMateApplication
import id.schoolmate.app.di.AppContainer
import id.schoolmate.app.ui.screens.home.HomeViewModel
import id.schoolmate.app.ui.screens.settings.SettingsViewModel
import id.schoolmate.app.ui.screens.subjects.SubjectsViewModel

/** Single factory for every ViewModel; dependencies come from the [AppContainer]. */
object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer { MainViewModel(container().userPreferencesRepository) }
        initializer {
            HomeViewModel(container().userPreferencesRepository, container().subjectRepository)
        }
        initializer { SubjectsViewModel(container().subjectRepository) }
        initializer { SettingsViewModel(container().userPreferencesRepository) }
    }
}

private fun CreationExtras.container(): AppContainer =
    (this[APPLICATION_KEY] as SchoolMateApplication).container

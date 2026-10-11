package id.schoolmate.app.ui.settings

import id.schoolmate.app.domain.model.ThemeMode
import id.schoolmate.app.testing.FakeUserPreferencesRepository
import id.schoolmate.app.testing.MainDispatcherRule
import id.schoolmate.app.ui.screens.settings.MAX_STUDENT_NAME_LENGTH
import id.schoolmate.app.ui.screens.settings.SettingsUiState
import id.schoolmate.app.ui.screens.settings.SettingsViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {
    @get:Rule val mainRule = MainDispatcherRule()

    @Test
    fun `exposes stored preferences and persists changes`() = runTest {
        val repo = FakeUserPreferencesRepository()
        val vm = SettingsViewModel(repo)
        backgroundScope.launch(mainRule.dispatcher) { vm.uiState.collect {} }

        vm.saveStudentName("  Budi  ")
        vm.setThemeMode(ThemeMode.DARK)
        vm.setUseDynamicColor(true)

        val ready = vm.uiState.value as SettingsUiState.Ready
        assertEquals("Budi", ready.preferences.studentName)
        assertEquals(ThemeMode.DARK, ready.preferences.themeMode)
        assertEquals(true, repo.state.first().useDynamicColor)
    }

    @Test
    fun `student name is capped`() = runTest {
        val repo = FakeUserPreferencesRepository()
        val vm = SettingsViewModel(repo)
        vm.saveStudentName("x".repeat(MAX_STUDENT_NAME_LENGTH + 10))
        assertEquals(MAX_STUDENT_NAME_LENGTH, repo.state.value.studentName.length)
    }
}

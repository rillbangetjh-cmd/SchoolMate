package id.schoolmate.app.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.schoolmate.app.domain.model.NotificationPreferences
import id.schoolmate.app.domain.model.ReminderKind
import id.schoolmate.app.notifications.NotificationPermissionStatus
import id.schoolmate.app.testing.FakeNotificationPreferencesRepository
import id.schoolmate.app.testing.MainDispatcherRule
import id.schoolmate.app.testing.awaitValue
import id.schoolmate.app.ui.screens.settings.NotificationSettingsContent
import id.schoolmate.app.ui.screens.settings.NotificationSettingsViewModel
import id.schoolmate.app.ui.theme.SchoolMateTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w400dp-h1200dp")
class NotificationSettingsTest {
    @Suppress("DEPRECATION")
    @get:Rule val compose = createComposeRule()
    @get:Rule val main = MainDispatcherRule()

    private val calls = mutableListOf<String>()

    private fun show(prefs: NotificationPreferences, status: NotificationPermissionStatus) {
        var p by mutableStateOf(prefs)
        compose.setContent {
            SchoolMateTheme {
                NotificationSettingsContent(
                    prefs = p, status = status,
                    onEnabledChange = { calls += "enabled=$it"; p = p.copy(enabled = it) },
                    onKindChange = { k, on -> calls += "${k.slug}=$on" },
                    onLeadChange = { k, m -> calls += "${k.slug}@$m" },
                    onRequestPermission = { calls += "request" },
                    onOpenSettings = { calls += "settings" },
                )
            }
        }
    }

    @Test fun deniedPermissionOffersTheDialogAgain() {
        show(NotificationPreferences(enabled = true), NotificationPermissionStatus.NEEDS_REQUEST)
        compose.onNodeWithText("Belum diizinkan. Pengingat tidak akan muncul.").assertExists()
        compose.onNodeWithText("Izinkan").performClick()
        assertEquals(listOf("request"), calls)
    }

    @Test fun blockedPermissionSendsTheUserToSystemSettings() {
        show(NotificationPreferences(enabled = true), NotificationPermissionStatus.BLOCKED)
        compose.onNodeWithText("Dimatikan di pengaturan Android. Aktifkan lagi agar pengingat muncul.").assertExists()
        compose.onNodeWithText("Izinkan").assertDoesNotExist()
        compose.onNodeWithText("Buka pengaturan").performClick()
        assertEquals(listOf("settings"), calls)
    }

    @Test fun masterSwitchRevealsPerKindOptions() {
        show(NotificationPreferences(enabled = false), NotificationPermissionStatus.GRANTED)
        compose.onNodeWithText("Diizinkan").assertExists()
        compose.onNodeWithTag("notif_master").assertIsOff()
        compose.onNodeWithText("Pengingat tugas").assertDoesNotExist()

        compose.onNodeWithTag("notif_master").performClick()
        compose.onNodeWithTag("notif_master").assertIsOn()
        compose.onNodeWithText("Pengingat tugas").assertExists()
        compose.onNodeWithText("Pengingat pelajaran").assertExists()
        compose.onNodeWithTag("lead_task_180").performClick()
        compose.onNodeWithTag("lead_lesson_15").performClick()
        compose.onNodeWithTag("notif_exam").performClick()
        assertEquals(listOf("enabled=true", "task@180", "lesson@15", "exam=false"), calls)
    }

    @Test fun viewModelWritesThroughToTheRepository(): Unit = runBlocking {
        val repo = FakeNotificationPreferencesRepository(NotificationPreferences())
        val vm = NotificationSettingsViewModel(repo)
        val job = launch { vm.preferences.collect {} }
        vm.preferences.awaitValue { it != null }
        vm.setEnabled(true)
        vm.setKindEnabled(ReminderKind.TASK, false)
        vm.setLeadMinutes(ReminderKind.EXAM, 60)
        vm.onPermissionRequested()
        val p = vm.preferences.awaitValue { it?.permissionRequested == true }!!
        assertEquals(NotificationPreferences(enabled = true, taskReminders = false, examLeadMinutes = 60, permissionRequested = true), p)
        job.cancel()
    }
}

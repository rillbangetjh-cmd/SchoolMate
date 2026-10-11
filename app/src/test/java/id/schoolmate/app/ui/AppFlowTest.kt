package id.schoolmate.app.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.schoolmate.app.MainActivity
import id.schoolmate.app.SchoolMateApplication
import id.schoolmate.app.domain.model.Subject
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * End-to-end UI flow on the real app (real Room + DataStore inside Robolectric): every step goes
 * through visible buttons and forms, exactly like a user would.
 *
 * Set the env var SCHOOLMATE_SCREENSHOT_DIR to also save PNG screenshots of each screen.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h860dp-xhdpi")
class AppFlowTest {
    // v1 rule on purpose: the v2 StandardTestDispatcher never idles while the app's minute ticker runs.
    @Suppress("DEPRECATION")
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val shotDir = System.getenv("SCHOOLMATE_SCREENSHOT_DIR")?.let(::File)

    private fun shot(name: String) {
        val dir = shotDir ?: return
        rule.waitForIdle()
        dir.mkdirs()
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    // Unmerged tree: Material's ExtendedFloatingActionButton doesn't merge its label upwards.
    private fun waitText(text: String, substring: Boolean = false) = rule.waitUntil(10_000) {
        rule.onAllNodesWithText(text, substring = substring, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }

    /** Waits until [text] is on screen, then returns the first match. */
    private fun text(text: String): SemanticsNodeInteraction {
        waitText(text)
        return rule.onAllNodesWithText(text, useUnmergedTree = true).onFirst()
    }

    private fun field(label: String): SemanticsNodeInteraction {
        waitText(label)
        return rule.onNodeWithText(label)
    }

    private fun desc(description: String): SemanticsNodeInteraction {
        rule.waitUntilAtLeastOneExists(androidx.compose.ui.test.hasContentDescription(description), 10_000)
        return rule.onAllNodes(androidx.compose.ui.test.hasContentDescription(description)).onFirst()
    }

    private fun save() = text("Simpan").performClick()

    @Test fun userCanManageScheduleTasksAndExams() {
        // Home with onboarding, read from the real (empty) database.
        waitText("Agenda hari ini")
        waitText("Belum ada mata pelajaran", substring = true)
        shot("01_home_empty")

        // Subject is seeded through the real repository: Compose dialogs never report idle inside
        // this Robolectric activity, so the subject dialog is covered by SubjectsViewModelTest instead.
        val app = ApplicationProvider.getApplicationContext<SchoolMateApplication>()
        runBlocking {
            app.container.subjectRepository.addSubject(Subject(name = "Matematika", colorArgb = 0xFF00897B.toInt()))
        }
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText("Belum ada mata pelajaran", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        }

        // Schedule: add two lessons via the full-screen editor (next free slot is suggested).
        rule.onNodeWithTag("nav_schedule").performClick()
        text("Tambah jadwal").performClick()
        waitText("Durasi 1 jam 30 menit")
        shot("02_schedule_editor")
        save()
        waitText("1 pelajaran", substring = true)
        text("Matematika").assertExists()
        text("Tambah jadwal").performClick()
        waitText("Durasi 1 jam 30 menit")
        save()
        waitText("2 pelajaran", substring = true)
        shot("03_schedule")

        // Tasks: validation, create, quick-complete with undo snackbar, filter.
        rule.onNodeWithTag("nav_tasks").performClick()
        waitText("Belum ada tugas")
        shot("04_tasks_empty")
        text("Tambah tugas").performClick()
        save()
        waitText("Wajib diisi")
        shot("05_task_validation")
        field("Judul tugas").performTextInput("PR Aljabar")
        shot("06_task_editor")
        save()
        waitText("PR Aljabar")
        shot("07_tasks")
        desc("Tandai PR Aljabar selesai").performClick()
        waitText("Tugas ditandai selesai")
        text("Selesai 1").performClick()
        waitText("PR Aljabar")

        // Task detail opens from the list and shows the saved status.
        text("PR Aljabar").performClick()
        waitText("Status")
        shot("08_task_detail")
        desc("Kembali").performClick()

        // Exams: create and open the detail.
        rule.onNodeWithTag("nav_exams").performClick()
        waitText("Belum ada ujian", substring = true)
        text("Tambah ujian").performClick()
        field("Nama ujian").performTextInput("PTS Matematika")
        save()
        waitText("PTS Matematika")
        shot("09_exams")
        text("PTS Matematika").performClick()
        waitText("7 hari lagi", substring = true)
        shot("10_exam_detail")
        desc("Kembali").performClick()

        // Home shows today's lessons and the upcoming exam.
        rule.onNodeWithTag("nav_home").performClick()
        waitText("Agenda hari ini")
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("PTS Matematika"))
        waitText("PTS Matematika")
        shot("11_home_filled")

        // Activity recreation (e.g. rotation) keeps everything: data comes from Room.
        rule.activityRule.scenario.recreate()
        waitText("Agenda hari ini")
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("PTS Matematika"))
        waitText("PTS Matematika")

        // Settings: switch to dark mode; the whole app re-themes.
        rule.onNodeWithTag("nav_settings").performClick()
        text("Gelap").performClick()
        rule.waitForIdle()
        shot("12_settings_dark")
        rule.onNodeWithTag("nav_home").performClick()
        waitText("Agenda hari ini")
        shot("13_home_dark")
        rule.onNodeWithTag("nav_schedule").performClick()
        waitText("2 pelajaran", substring = true)
        shot("14_schedule_dark")
        rule.onNodeWithTag("nav_tasks").performClick()
        text("Semua 1").performClick()
        waitText("PR Aljabar")
        shot("15_tasks_dark")
    }
}

package id.schoolmate.app

import android.content.Intent
import android.os.Bundle
import id.schoolmate.app.notifications.ReminderTarget
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.schoolmate.app.domain.model.ThemeMode
import id.schoolmate.app.ui.AppViewModelProvider
import id.schoolmate.app.ui.MainUiState
import id.schoolmate.app.ui.MainViewModel
import id.schoolmate.app.ui.SchoolMateApp
import id.schoolmate.app.ui.theme.SchoolMateTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels { AppViewModelProvider.Factory }

    /** Set when a reminder notification was tapped; consumed by the navigation layer. */
    private val reminderTarget = MutableStateFlow<ReminderTarget?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Keep the splash until preferences are loaded so the theme never flickers.
        splashScreen.setKeepOnScreenCondition { viewModel.uiState.value is MainUiState.Loading }
        enableEdgeToEdge()
        // Recreation (rotation) must not replay an old notification tap.
        if (savedInstanceState == null) reminderTarget.value = ReminderTarget.from(intent)
        (application as SchoolMateApplication).container.let { it.reminderSync.startObserving(it.applicationScope) }

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val prefs = (state as? MainUiState.Ready)?.preferences
            SchoolMateTheme(
                themeMode = prefs?.themeMode ?: ThemeMode.SYSTEM,
                useDynamicColor = prefs?.useDynamicColor ?: false,
            ) {
                val target by reminderTarget.collectAsStateWithLifecycle()
                SchoolMateApp(openTarget = target, onTargetHandled = { reminderTarget.value = null })
            }
        }
    }

    /** singleTop: a tap while the app is open arrives here instead of creating a new activity. */
    public override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // The target is consumed once via state; getIntent() deliberately keeps the launch intent so
        // a later recreation does not re-open the same detail screen.
        ReminderTarget.from(intent)?.let { reminderTarget.value = it }
    }
}

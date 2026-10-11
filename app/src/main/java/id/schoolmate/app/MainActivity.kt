package id.schoolmate.app

import android.os.Bundle
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

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Keep the splash until preferences are loaded so the theme never flickers.
        splashScreen.setKeepOnScreenCondition { viewModel.uiState.value is MainUiState.Loading }
        enableEdgeToEdge()

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val prefs = (state as? MainUiState.Ready)?.preferences
            SchoolMateTheme(
                themeMode = prefs?.themeMode ?: ThemeMode.SYSTEM,
                useDynamicColor = prefs?.useDynamicColor ?: false,
            ) {
                SchoolMateApp()
            }
        }
    }
}

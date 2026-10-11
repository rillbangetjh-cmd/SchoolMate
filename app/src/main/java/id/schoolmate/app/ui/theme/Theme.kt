package id.schoolmate.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import id.schoolmate.app.domain.model.ThemeMode

private val LightColors = lightColorScheme(
    primary = Teal40, onPrimary = Slate99, primaryContainer = Teal90, onPrimaryContainer = Teal10,
    secondary = Amber40, onSecondary = Slate99, secondaryContainer = Amber90, onSecondaryContainer = Amber10,
    error = Red40, onError = Slate99, errorContainer = Red90, onErrorContainer = Red20,
    background = Slate99, onBackground = Slate10, surface = Slate99, onSurface = Slate10,
    surfaceVariant = Slate90, onSurfaceVariant = Slate30, outline = Slate50,
)

private val DarkColors = darkColorScheme(
    primary = Teal80, onPrimary = Teal20, primaryContainer = Teal30, onPrimaryContainer = Teal90,
    secondary = Amber80, onSecondary = Amber20, secondaryContainer = Amber30, onSecondaryContainer = Amber90,
    error = Red80, onError = Red20, errorContainer = Red40, onErrorContainer = Red90,
    background = Slate10, onBackground = Slate90, surface = Slate10, onSurface = Slate90,
    surfaceVariant = Slate30, onSurfaceVariant = Slate80, outline = Slate60,
)

@Composable
fun SchoolMateTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    useDynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, typography = SchoolMateTypography, content = content)
}

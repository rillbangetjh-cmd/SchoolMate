package id.schoolmate.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
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
    surfaceVariant = Slate90, onSurfaceVariant = Slate30, outline = Slate50, outlineVariant = Slate80,
    // Neutral slate containers (cards, nav bar, sheets) instead of the baseline purple tint.
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF2F4F2),
    surfaceContainer = Color(0xFFECEFED), surfaceContainerHigh = Color(0xFFE6E9E7),
    surfaceContainerHighest = Color(0xFFE0E3E1), surfaceDim = Color(0xFFD8DBD9), surfaceBright = Slate99,
    surfaceTint = Teal40, inverseSurface = Slate20, inverseOnSurface = Slate95, inversePrimary = Teal80,
    // Tertiary = soft rose, reserved for "due today" / countdown highlights.
    tertiary = Color(0xFF8C4A5A), onTertiary = Slate99, tertiaryContainer = Color(0xFFFFD9E0), onTertiaryContainer = Color(0xFF3A0718),
)

private val DarkColors = darkColorScheme(
    primary = Teal80, onPrimary = Teal20, primaryContainer = Teal30, onPrimaryContainer = Teal90,
    secondary = Amber80, onSecondary = Amber20, secondaryContainer = Amber30, onSecondaryContainer = Amber90,
    error = Red80, onError = Red20, errorContainer = Red40, onErrorContainer = Red90,
    background = Slate10, onBackground = Slate90, surface = Slate10, onSurface = Slate90,
    surfaceVariant = Slate30, onSurfaceVariant = Slate80, outline = Slate60, outlineVariant = Slate30,
    surfaceContainerLowest = Color(0xFF111413), surfaceContainerLow = Color(0xFF1D201F),
    surfaceContainer = Color(0xFF212524), surfaceContainerHigh = Color(0xFF2B2F2E),
    surfaceContainerHighest = Color(0xFF363A39), surfaceDim = Slate10, surfaceBright = Color(0xFF373B3A),
    surfaceTint = Teal80, inverseSurface = Slate90, inverseOnSurface = Slate20, inversePrimary = Teal40,
    tertiary = Color(0xFFFFB1C2), onTertiary = Color(0xFF561D2D), tertiaryContainer = Color(0xFF713343), onTertiaryContainer = Color(0xFFFFD9E0),
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

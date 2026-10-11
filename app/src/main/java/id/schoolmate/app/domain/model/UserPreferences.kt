package id.schoolmate.app.domain.model

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class UserPreferences(
    val studentName: String = "",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = false,
)

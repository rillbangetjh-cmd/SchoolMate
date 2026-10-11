package id.schoolmate.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import id.schoolmate.app.domain.model.ThemeMode
import id.schoolmate.app.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

interface UserPreferencesRepository {
    val userPreferences: Flow<UserPreferences>
    suspend fun setStudentName(name: String)
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setUseDynamicColor(enabled: Boolean)
}

class DataStoreUserPreferencesRepository(
    private val dataStore: DataStore<Preferences>,
) : UserPreferencesRepository {

    private object Keys {
        val STUDENT_NAME = stringPreferencesKey("student_name")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    }

    override val userPreferences: Flow<UserPreferences> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs ->
            UserPreferences(
                studentName = prefs[Keys.STUDENT_NAME].orEmpty(),
                themeMode = prefs[Keys.THEME_MODE]
                    ?.let { stored -> ThemeMode.entries.firstOrNull { it.name == stored } }
                    ?: ThemeMode.SYSTEM,
                useDynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: false,
            )
        }

    override suspend fun setStudentName(name: String) {
        dataStore.edit { it[Keys.STUDENT_NAME] = name.trim() }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    override suspend fun setUseDynamicColor(enabled: Boolean) {
        dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }
}

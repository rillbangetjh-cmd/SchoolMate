package id.schoolmate.app.testing

import id.schoolmate.app.data.preferences.UserPreferencesRepository
import id.schoolmate.app.data.repository.DuplicateSubjectException
import id.schoolmate.app.data.repository.SubjectRepository
import id.schoolmate.app.domain.model.Subject
import id.schoolmate.app.domain.model.ThemeMode
import id.schoolmate.app.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeUserPreferencesRepository(initial: UserPreferences = UserPreferences()) : UserPreferencesRepository {
    val state = MutableStateFlow(initial)
    override val userPreferences: Flow<UserPreferences> = state
    override suspend fun setStudentName(name: String) = state.update { it.copy(studentName = name.trim()) }
    override suspend fun setThemeMode(mode: ThemeMode) = state.update { it.copy(themeMode = mode) }
    override suspend fun setUseDynamicColor(enabled: Boolean) = state.update { it.copy(useDynamicColor = enabled) }
}

class FakeSubjectRepository : SubjectRepository {
    val subjects = MutableStateFlow<List<Subject>>(emptyList())
    private var nextId = 1L

    override fun observeSubjects(): Flow<List<Subject>> = subjects
    override fun observeSubjectCount(): Flow<Int> = subjects.map { it.size }

    override suspend fun addSubject(subject: Subject): Result<Long> {
        if (subjects.value.any { it.name.equals(subject.name, ignoreCase = true) }) {
            return Result.failure(DuplicateSubjectException(subject.name))
        }
        val id = nextId++
        subjects.update { it + subject.copy(id = id) }
        return Result.success(id)
    }

    override suspend fun deleteSubject(id: Long) = subjects.update { list -> list.filterNot { it.id == id } }
}

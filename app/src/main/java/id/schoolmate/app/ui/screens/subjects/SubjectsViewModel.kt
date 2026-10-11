package id.schoolmate.app.ui.screens.subjects

import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.schoolmate.app.data.repository.DuplicateSubjectException
import id.schoolmate.app.data.repository.SubjectRepository
import id.schoolmate.app.domain.model.Subject
import id.schoolmate.app.ui.theme.SubjectPalette
import id.schoolmate.app.util.SubjectNameError
import id.schoolmate.app.util.validateSubjectName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SubjectEditorState(
    val name: String = "",
    val teacher: String = "",
    val colorIndex: Int = 0,
    val error: SubjectEditorError? = null,
    val isSaving: Boolean = false,
)

enum class SubjectEditorError { EMPTY, TOO_LONG, DUPLICATE }

class SubjectsViewModel(private val repository: SubjectRepository) : ViewModel() {

    val subjects: StateFlow<List<Subject>?> = repository.observeSubjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _editor = MutableStateFlow<SubjectEditorState?>(null)
    val editor: StateFlow<SubjectEditorState?> = _editor.asStateFlow()

    fun openEditor() {
        val nextColor = (subjects.value?.size ?: 0) % SubjectPalette.size
        _editor.value = SubjectEditorState(colorIndex = nextColor)
    }

    fun dismissEditor() { _editor.value = null }

    fun onNameChange(value: String) = _editor.update { it?.copy(name = value, error = null) }
    fun onTeacherChange(value: String) = _editor.update { it?.copy(teacher = value) }
    fun onColorChange(index: Int) = _editor.update { it?.copy(colorIndex = index) }

    fun save() {
        val current = _editor.value ?: return
        when (validateSubjectName(current.name)) {
            SubjectNameError.Empty -> { _editor.value = current.copy(error = SubjectEditorError.EMPTY); return }
            SubjectNameError.TooLong -> { _editor.value = current.copy(error = SubjectEditorError.TOO_LONG); return }
            null -> Unit
        }
        _editor.value = current.copy(isSaving = true)
        viewModelScope.launch {
            val result = repository.addSubject(
                Subject(
                    name = current.name.trim(),
                    teacher = current.teacher.trim(),
                    colorArgb = SubjectPalette[current.colorIndex].toArgb(),
                ),
            )
            _editor.value = result.fold(
                onSuccess = { null },
                onFailure = { e ->
                    if (e is DuplicateSubjectException) current.copy(error = SubjectEditorError.DUPLICATE, isSaving = false)
                    else throw e
                },
            )
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.deleteSubject(id) }
    }
}

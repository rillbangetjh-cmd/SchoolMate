package id.schoolmate.app.util

const val MAX_SUBJECT_NAME_LENGTH = 40

sealed interface SubjectNameError {
    data object Empty : SubjectNameError
    data object TooLong : SubjectNameError
}

fun validateSubjectName(raw: String): SubjectNameError? {
    val name = raw.trim()
    return when {
        name.isEmpty() -> SubjectNameError.Empty
        name.length > MAX_SUBJECT_NAME_LENGTH -> SubjectNameError.TooLong
        else -> null
    }
}

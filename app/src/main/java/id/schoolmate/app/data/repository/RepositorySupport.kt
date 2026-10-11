package id.schoolmate.app.data.repository

import android.database.sqlite.SQLiteConstraintException
import id.schoolmate.app.domain.validation.ValidationError
import id.schoolmate.app.domain.validation.ValidationException

/** Thrown inside a failed [Result] when an update/delete targets a row that doesn't exist. */
class RecordNotFoundException(type: String, id: Long) : NoSuchElementException("$type #$id not found")

internal fun Set<ValidationError>.toFailureOrNull(): Result<Nothing>? =
    if (isEmpty()) null else Result.failure(ValidationException(this))

/**
 * Runs a write and converts SQLite foreign-key violations into a typed validation failure,
 * so callers never see raw SQL exceptions. Other errors are rethrown (they indicate bugs).
 */
internal suspend fun <T> guardedWrite(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: SQLiteConstraintException) {
        if (e.message?.contains("FOREIGN KEY", ignoreCase = true) == true) {
            Result.failure(ValidationException(setOf(ValidationError.INVALID_REFERENCE)))
        } else {
            throw e
        }
    }

package id.schoolmate.app.domain.model

/**
 * Task priority. [level] is the value persisted in the database, so it must never change;
 * higher means more urgent and is used for ordering.
 */
enum class Priority(val level: Int) {
    LOW(1),
    MEDIUM(2),
    HIGH(3),
    ;

    companion object {
        fun fromLevel(level: Int): Priority = entries.firstOrNull { it.level == level } ?: MEDIUM
    }
}

/** Task lifecycle. Persisted by [name]; renaming a constant requires a migration. */
enum class TaskStatus {
    TODO,
    IN_PROGRESS,
    DONE,
    ;

    val isActive: Boolean get() = this != DONE
}

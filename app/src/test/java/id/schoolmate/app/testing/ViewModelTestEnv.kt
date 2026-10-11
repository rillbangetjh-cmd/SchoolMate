package id.schoolmate.app.testing

import id.schoolmate.app.data.local.SchoolMateDatabase
import id.schoolmate.app.data.local.inMemoryDatabase
import id.schoolmate.app.data.local.insertSubject
import id.schoolmate.app.data.repository.OfflineExamRepository
import id.schoolmate.app.data.repository.OfflineScheduleRepository
import id.schoolmate.app.data.repository.OfflineStudySessionRepository
import id.schoolmate.app.data.repository.OfflineSubjectRepository
import id.schoolmate.app.data.repository.OfflineTaskRepository
import id.schoolmate.app.domain.usecase.ObserveDashboardSummaryUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Real repositories over an in-memory Room database, a fixed clock
 * (Senin 12 Oktober 2026, 09:00 WIB) and a controllable "now" flow.
 */
class ViewModelTestEnv(val db: SchoolMateDatabase = inMemoryDatabase()) {
    val zone: ZoneId = ZoneId.of("Asia/Jakarta")
    val clock: Clock = Clock.fixed(Instant.parse("2026-10-12T02:00:00Z"), zone)
    val today: LocalDate = LocalDate.of(2026, 10, 12)
    val now = MutableStateFlow(LocalDateTime.of(2026, 10, 12, 9, 0))

    val subjects = OfflineSubjectRepository(db.subjectDao(), clock::millis)
    val schedule = OfflineScheduleRepository(db.scheduleDao(), clock)
    val tasks = OfflineTaskRepository(db.taskDao(), clock)
    val exams = OfflineExamRepository(db.examDao(), clock)
    val sessions = OfflineStudySessionRepository(db.studySessionDao(), clock)
    val dashboard = ObserveDashboardSummaryUseCase(schedule, tasks, exams, sessions, clock)

    suspend fun subject(name: String) = db.insertSubject(name)

    fun close() = db.close()
}

/** Waits (real time) until the flow emits a value matching [predicate]. */
suspend fun <T> Flow<T>.awaitValue(timeoutMs: Long = 5_000, predicate: (T) -> Boolean): T =
    withTimeout(timeoutMs) { first(predicate) }

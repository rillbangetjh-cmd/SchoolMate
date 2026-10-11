package id.schoolmate.app.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Emits the current local date-time (truncated to minutes) now and at every minute boundary,
 * so "today", "overdue", and greetings update while a screen stays open.
 */
fun minuteTicker(clock: Clock): Flow<LocalDateTime> = flow {
    while (true) {
        val now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES)
        emit(now)
        val nextMinuteMillis = 60_000 - (clock.millis() % 60_000)
        delay(nextMinuteMillis.coerceAtLeast(1_000))
    }
}.distinctUntilChanged()

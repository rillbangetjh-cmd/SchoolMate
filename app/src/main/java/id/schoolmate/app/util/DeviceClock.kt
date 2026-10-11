package id.schoolmate.app.util

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * System clock that re-reads the device time zone on every call, so date-based filters stay
 * correct if the user travels or changes the zone while the app is running.
 */
object DeviceClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()
    override fun withZone(zone: ZoneId): Clock = system(zone)
    override fun instant(): Instant = Instant.now()
    override fun millis(): Long = System.currentTimeMillis()
}

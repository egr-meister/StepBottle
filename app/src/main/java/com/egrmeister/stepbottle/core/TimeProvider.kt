package com.egrmeister.stepbottle.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Injectable clock and time-zone source so date logic is testable. */
interface TimeProvider {
    fun now(): Instant
    fun zone(): ZoneId

    fun zonedNow(): ZonedDateTime = now().atZone(zone())
    fun today(): LocalDate = zonedNow().toLocalDate()
    fun localTimeNow(): LocalTime = zonedNow().toLocalTime()
}

object SystemTimeProvider : TimeProvider {
    override fun now(): Instant = Instant.now()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}

class FixedTimeProvider(var instant: Instant, var zoneId: ZoneId) : TimeProvider {
    override fun now(): Instant = instant
    override fun zone(): ZoneId = zoneId
}

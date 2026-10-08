package com.egrmeister.stepbottle.domain

import com.egrmeister.stepbottle.domain.reminders.ReminderAvailability
import com.egrmeister.stepbottle.domain.reminders.ReminderDecision
import com.egrmeister.stepbottle.domain.reminders.ReminderPolicy
import com.egrmeister.stepbottle.domain.reminders.ReminderSettings
import com.egrmeister.stepbottle.domain.reminders.ReminderStatus
import com.egrmeister.stepbottle.domain.reminders.SkipReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderPolicyTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val settings = ReminderSettings(enabled = true, time = LocalTime.of(19, 0), generation = 4)

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0) = ZonedDateTime.of(y, m, d, h, min, 0, 0, zone).toInstant()

    @Test fun nextOccurrenceIsTodayOrTomorrow() {
        assertEquals(at(2026, 5, 4, 19), ReminderPolicy.nextOccurrence(settings.time, at(2026, 5, 4, 10), zone).toInstant())
        assertEquals(at(2026, 5, 5, 19), ReminderPolicy.nextOccurrence(settings.time, at(2026, 5, 4, 19), zone).toInstant())
        assertEquals(at(2026, 5, 5, 19), ReminderPolicy.nextOccurrence(settings.time, at(2026, 5, 4, 21), zone).toInstant())
    }

    @Test fun recoveryAfterRebootOrZoneChangeUsesCurrentZone() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        val now = ZonedDateTime.of(2026, 5, 4, 20, 0, 0, 0, tokyo).toInstant()
        val next = ReminderPolicy.nextOccurrence(settings.time, now, tokyo)
        assertEquals(ZonedDateTime.of(2026, 5, 5, 19, 0, 0, 0, tokyo), next)
    }

    @Test fun daylightSavingGapMovesForward() {
        val gapTime = LocalTime.of(2, 30)
        val next = ReminderPolicy.nextOccurrence(gapTime, at(2026, 3, 28, 12), zone)
        assertEquals(ZonedDateTime.of(2026, 3, 29, 3, 30, 0, 0, zone), next)
    }

    @Test fun currentCallbackPosts() {
        val slot = at(2026, 5, 4, 19)
        val d = ReminderPolicy.decide(settings, 4, slot, slot.plus(Duration.ofMinutes(20)), zone, canPost = true)
        assertEquals(ReminderDecision.Post, d)
    }

    @Test fun staleCallbacksAreSkipped() {
        val slot = at(2026, 5, 4, 19)
        // Older schedule generation (settings changed since).
        assertEquals(
            ReminderDecision.Skip(SkipReason.OUTDATED_SCHEDULE),
            ReminderPolicy.decide(settings, 3, slot, slot.plusSeconds(60), zone, true),
        )
        // Delivered the next day (device was asleep/off): no backlog.
        assertEquals(
            ReminderDecision.Skip(SkipReason.STALE_PREVIOUS_DAY),
            ReminderPolicy.decide(settings, 4, slot, at(2026, 5, 5, 8), zone, true),
        )
        // Disabled since scheduling.
        assertEquals(
            ReminderDecision.Skip(SkipReason.DISABLED),
            ReminderPolicy.decide(settings.copy(enabled = false), 4, slot, slot, zone, true),
        )
        // Time changed to 20:00 but same generation (should not happen, still rejected).
        assertEquals(
            ReminderDecision.Skip(SkipReason.OUTDATED_SCHEDULE),
            ReminderPolicy.decide(settings.copy(time = LocalTime.of(20, 0)), 4, slot, slot, zone, true),
        )
        // Never ahead of time.
        assertEquals(
            ReminderDecision.Skip(SkipReason.NOT_DUE),
            ReminderPolicy.decide(settings, 4, slot, slot.minusSeconds(300), zone, true),
        )
    }

    @Test fun permissionDenialBlocksOnlyTheNotification() {
        val slot = at(2026, 5, 4, 19)
        assertEquals(ReminderDecision.Skip(SkipReason.BLOCKED), ReminderPolicy.decide(settings, 4, slot, slot, zone, canPost = false))
        assertEquals(ReminderStatus.PERMISSION_DENIED, ReminderAvailability.status(true, false, true, true))
        assertEquals(ReminderStatus.APP_NOTIFICATIONS_OFF, ReminderAvailability.status(true, true, false, true))
        assertEquals(ReminderStatus.CHANNEL_OFF, ReminderAvailability.status(true, true, true, false))
        assertEquals(ReminderStatus.ACTIVE, ReminderAvailability.status(true, true, true, true))
        assertEquals(ReminderStatus.OFF, ReminderAvailability.status(false, false, false, false))
        assertTrue(ReminderAvailability.explanation(ReminderStatus.PERMISSION_DENIED)!!.contains("Logging works normally"))
        assertNull(ReminderAvailability.explanation(ReminderStatus.ACTIVE))
    }

    @Test fun permissionIsRequestedAtMostOnceAndOnlyOnAndroid13Plus() {
        assertTrue(ReminderAvailability.shouldRequestPermission(33, hasPermission = false, alreadyAsked = false))
        assertFalse(ReminderAvailability.shouldRequestPermission(33, hasPermission = false, alreadyAsked = true))
        assertFalse(ReminderAvailability.shouldRequestPermission(32, hasPermission = false, alreadyAsked = false))
        assertFalse(ReminderAvailability.shouldRequestPermission(36, hasPermission = true, alreadyAsked = false))
    }

    @Test fun remindersAreOffByDefault() {
        val d = ReminderSettings()
        assertFalse(d.enabled)
        assertEquals(LocalTime.of(19, 0), d.time)
    }
}

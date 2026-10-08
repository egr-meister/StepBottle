package com.egrmeister.stepbottle.domain.reminders

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * One optional daily logging reminder. [generation] identifies the current schedule: every
 * settings change increments it, so callbacks from older schedules can be recognised and dropped.
 */
data class ReminderSettings(
    val enabled: Boolean = false,
    val time: LocalTime = DEFAULT_TIME,
    val generation: Long = 0,
) {
    companion object {
        val DEFAULT_TIME: LocalTime = LocalTime.of(19, 0)
    }
}

sealed interface ReminderDecision {
    data object Post : ReminderDecision
    data class Skip(val reason: SkipReason) : ReminderDecision
}

enum class SkipReason { DISABLED, OUTDATED_SCHEDULE, STALE_PREVIOUS_DAY, NOT_DUE, BLOCKED }

object ReminderPolicy {

    /**
     * The next occurrence of the reminder time strictly after [now], in the device's current
     * zone. In a daylight-saving gap the time is moved forward by the gap length; in an overlap
     * the earlier instant is used.
     */
    fun nextOccurrence(time: LocalTime, now: Instant, zone: ZoneId): ZonedDateTime {
        val today = now.atZone(zone).toLocalDate()
        var date = today
        repeat(3) {
            val candidate = ZonedDateTime.of(date, time, zone).withEarlierOffsetAtOverlap()
            if (candidate.toInstant().isAfter(now)) return candidate
            date = date.plusDays(1)
        }
        return ZonedDateTime.of(date, time, zone)
    }

    /**
     * Decides at delivery time whether the scheduled callback may post. Only the callback of the
     * current schedule posts, only on the day it was scheduled for, and never ahead of time.
     * Missed earlier days are dropped, so no backlog is ever delivered.
     */
    fun decide(
        settings: ReminderSettings,
        callbackGeneration: Long,
        scheduledFor: Instant,
        now: Instant,
        zone: ZoneId,
        canPost: Boolean,
    ): ReminderDecision {
        if (!settings.enabled) return ReminderDecision.Skip(SkipReason.DISABLED)
        if (callbackGeneration != settings.generation) return ReminderDecision.Skip(SkipReason.OUTDATED_SCHEDULE)
        val scheduledLocal = scheduledFor.atZone(zone)
        if (scheduledLocal.toLocalTime().withSecond(0).withNano(0) != settings.time.withSecond(0).withNano(0) &&
            ZonedDateTime.of(scheduledLocal.toLocalDate(), settings.time, zone).toInstant() != scheduledFor
        ) {
            return ReminderDecision.Skip(SkipReason.OUTDATED_SCHEDULE)
        }
        if (now.isBefore(scheduledFor)) return ReminderDecision.Skip(SkipReason.NOT_DUE)
        if (scheduledLocal.toLocalDate() != now.atZone(zone).toLocalDate()) {
            return ReminderDecision.Skip(SkipReason.STALE_PREVIOUS_DAY)
        }
        if (!canPost) return ReminderDecision.Skip(SkipReason.BLOCKED)
        return ReminderDecision.Post
    }
}

enum class ReminderStatus { OFF, ACTIVE, PERMISSION_DENIED, APP_NOTIFICATIONS_OFF, CHANNEL_OFF }

/** Permission and channel availability. Denial never affects logging. */
object ReminderAvailability {
    fun status(
        enabled: Boolean,
        hasPermission: Boolean,
        appNotificationsEnabled: Boolean,
        channelEnabled: Boolean,
    ): ReminderStatus = when {
        !enabled -> ReminderStatus.OFF
        !hasPermission -> ReminderStatus.PERMISSION_DENIED
        !appNotificationsEnabled -> ReminderStatus.APP_NOTIFICATIONS_OFF
        !channelEnabled -> ReminderStatus.CHANNEL_OFF
        else -> ReminderStatus.ACTIVE
    }

    /**
     * POST_NOTIFICATIONS is requested only on Android 13+, only right after the user turns
     * reminders on, and at most once; later the app explains the state and offers system settings.
     */
    fun shouldRequestPermission(sdkInt: Int, hasPermission: Boolean, alreadyAsked: Boolean): Boolean =
        sdkInt >= 33 && !hasPermission && !alreadyAsked

    fun explanation(status: ReminderStatus): String? = when (status) {
        ReminderStatus.OFF, ReminderStatus.ACTIVE -> null
        ReminderStatus.PERMISSION_DENIED ->
            "Notification permission is not granted, so the reminder can't appear. Logging works normally."
        ReminderStatus.APP_NOTIFICATIONS_OFF ->
            "Notifications are turned off for StepBottle, so the reminder can't appear. Logging works normally."
        ReminderStatus.CHANNEL_OFF ->
            "The “Logging reminder” notification category is turned off. Logging works normally."
    }
}

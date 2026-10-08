package com.egrmeister.stepbottle.domain.activity

import com.egrmeister.stepbottle.domain.time.EntryTimeValidator
import com.egrmeister.stepbottle.domain.time.NumberResult
import com.egrmeister.stepbottle.domain.time.TimeCheck
import com.egrmeister.stepbottle.domain.time.WholeNumberParser
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Self-reported activity categories. Nothing is detected or measured. */
enum class ActivityCategory(val label: String) {
    WALK("Walk"),
    EXERCISE("Exercise"),
    STRETCH("Stretch"),
    OTHER("Other"),
    ;

    companion object {
        fun fromName(name: String?): ActivityCategory? = entries.firstOrNull { it.name == name }
    }
}

/**
 * A duration the user reports. The timestamp only places the entry in the diary; it is not a
 * recorded workout start or end time.
 */
data class ActivityEntry(
    val id: Long,
    val durationMinutes: Int,
    val category: ActivityCategory,
    val label: String?,
    val timestamp: Instant,
    val localDate: LocalDate,
    val localTime: LocalTime,
    val zoneId: ZoneId,
    val createdAt: Instant,
    val updatedAt: Instant,
)

object ActivityLimits {
    const val MIN_MINUTES = 1
    const val MAX_MINUTES = 600
    const val DAILY_MAX_MINUTES = 1_440L
    const val LABEL_MAX = 40
    val DEFAULT_SHORTCUTS = listOf(5, 15, 30)

    fun isValid(minutes: Int) = minutes in MIN_MINUTES..MAX_MINUTES

    fun parse(raw: String): NumberResult =
        WholeNumberParser.parse(raw, MIN_MINUTES, MAX_MINUTES, "minutes", "Enter a duration in minutes.")

    /** Daily recorded activity must not exceed 1,440 minutes for one local date. */
    fun fitsDailyLimit(otherMinutesThatDay: Long, minutes: Int): Boolean =
        otherMinutesThatDay + minutes <= DAILY_MAX_MINUTES

    fun dailyLimitMessage(otherMinutesThatDay: Long): String {
        val left = (DAILY_MAX_MINUTES - otherMinutesThatDay).coerceAtLeast(0)
        return "A day can hold at most 1,440 recorded activity minutes. $left minutes remain for this date."
    }
}

object LabelRules {
    /** Trims and collapses whitespace; blank labels are stored as null. */
    fun normalize(raw: String): String? =
        raw.replace(Regex("\\s+"), " ").trim().takeIf { it.isNotEmpty() }

    fun error(raw: String): String? {
        val n = normalize(raw) ?: return null
        return if (n.length > ActivityLimits.LABEL_MAX) "Use at most ${ActivityLimits.LABEL_MAX} characters." else null
    }
}

sealed interface ActivityValidation {
    data class Valid(
        val minutes: Int,
        val category: ActivityCategory,
        val label: String?,
        val zoned: ZonedDateTime,
    ) : ActivityValidation

    data class Invalid(val durationError: String?, val labelError: String?, val timeError: String?) : ActivityValidation
}

object ActivityValidator {
    fun validate(
        durationText: String,
        category: ActivityCategory,
        labelText: String,
        date: LocalDate,
        time: LocalTime?,
        zone: ZoneId,
        now: Instant,
    ): ActivityValidation {
        val duration = ActivityLimits.parse(durationText)
        val labelError = LabelRules.error(labelText)
        val timeCheck = EntryTimeValidator.validate(date, time, zone, now)
        return if (duration is NumberResult.Valid && labelError == null && timeCheck is TimeCheck.Valid) {
            ActivityValidation.Valid(duration.value, category, LabelRules.normalize(labelText), timeCheck.zoned)
        } else {
            ActivityValidation.Invalid(
                durationError = (duration as? NumberResult.Invalid)?.message,
                labelError = labelError,
                timeError = (timeCheck as? TimeCheck.Invalid)?.message,
            )
        }
    }
}

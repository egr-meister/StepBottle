package com.egrmeister.stepbottle.domain.time

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

sealed interface NumberResult {
    data class Valid(val value: Int) : NumberResult
    data class Invalid(val message: String) : NumberResult
}

/** Parses whole numbers only (no decimals, signs or units) within an inclusive range. */
object WholeNumberParser {
    private val digits = Regex("^[0-9]+$")

    fun parse(raw: String, min: Int, max: Int, unit: String, emptyMessage: String): NumberResult {
        val text = raw.trim().replace(",", "")
        if (text.isEmpty()) return NumberResult.Invalid(emptyMessage)
        if (!digits.matches(text)) return NumberResult.Invalid("Use whole $unit only.")
        val value = text.toLongOrNull()
        if (value == null || value < min || value > max) {
            return NumberResult.Invalid("Enter between ${fmt(min)} and ${fmt(max)} $unit.")
        }
        return NumberResult.Valid(value.toInt())
    }

    private fun fmt(v: Int) = String.format(Locale.US, "%,d", v)
}

sealed interface LocalTimeResolution {
    data class Resolved(val zoned: ZonedDateTime) : LocalTimeResolution

    /** The wall-clock time does not exist on that date (daylight-saving gap). */
    data object Nonexistent : LocalTimeResolution
}

object TimeResolver {
    /**
     * Resolves a wall-clock time in [zone]. Ambiguous times (DST overlap) consistently use the
     * earlier offset; nonexistent times are rejected instead of being silently shifted.
     */
    fun resolve(date: LocalDate, time: LocalTime, zone: ZoneId): LocalTimeResolution {
        val ldt = LocalDateTime.of(date, time)
        val offsets = zone.rules.getValidOffsets(ldt)
        if (offsets.isEmpty()) return LocalTimeResolution.Nonexistent
        val earlier = offsets.maxBy { it.totalSeconds } // larger offset = earlier instant
        return LocalTimeResolution.Resolved(ZonedDateTime.ofStrict(ldt, earlier, zone))
    }
}

sealed interface TimeCheck {
    data class Valid(val zoned: ZonedDateTime) : TimeCheck
    data class Invalid(val message: String) : TimeCheck
}

/** Shared date/time rules for both entry types: today or a past date, no future timestamps. */
object EntryTimeValidator {
    const val MISSING_TIME = "Choose a time for this entry."
    const val FUTURE_DATE = "Choose today or a past date."
    const val FUTURE_TIME = "Future times can't be recorded."
    const val NONEXISTENT_TIME =
        "This time doesn't exist on that date because of a daylight-saving change. Choose another time."

    fun validate(date: LocalDate, time: LocalTime?, zone: ZoneId, now: Instant): TimeCheck {
        val today = now.atZone(zone).toLocalDate()
        if (date.isAfter(today)) return TimeCheck.Invalid(FUTURE_DATE)
        if (time == null) return TimeCheck.Invalid(MISSING_TIME)
        return when (val r = TimeResolver.resolve(date, time.withSecond(0).withNano(0), zone)) {
            LocalTimeResolution.Nonexistent -> TimeCheck.Invalid(NONEXISTENT_TIME)
            is LocalTimeResolution.Resolved -> {
                // Minute precision: the current minute itself is accepted.
                val endOfCurrentMinute = now.atZone(zone).withSecond(0).withNano(0).plusMinutes(1)
                if (!r.zoned.isBefore(endOfCurrentMinute)) TimeCheck.Invalid(FUTURE_TIME) else TimeCheck.Valid(r.zoned)
            }
        }
    }
}

package com.egrmeister.stepbottle.domain.water

import com.egrmeister.stepbottle.domain.time.EntryTimeValidator
import com.egrmeister.stepbottle.domain.time.NumberResult
import com.egrmeister.stepbottle.domain.time.TimeCheck
import com.egrmeister.stepbottle.domain.time.WholeNumberParser
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Domain view of a stored water entry. Grouping always uses the stored local date/time. */
data class WaterEntry(
    val id: Long,
    val amountMl: Int,
    val timestamp: Instant,
    val localDate: LocalDate,
    val localTime: LocalTime,
    val zoneId: ZoneId,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** Technical input limits, not consumption guidance. */
object WaterLimits {
    const val MIN_ML = 1
    const val MAX_ML = 5_000
    val DEFAULT_SHORTCUTS = listOf(150, 250, 500)

    fun isValid(ml: Int) = ml in MIN_ML..MAX_ML

    fun parse(raw: String): NumberResult =
        WholeNumberParser.parse(raw, MIN_ML, MAX_ML, "mL", "Enter an amount in mL.")
}

sealed interface WaterValidation {
    data class Valid(val amountMl: Int, val zoned: ZonedDateTime) : WaterValidation
    data class Invalid(val amountError: String?, val timeError: String?) : WaterValidation
}

object WaterValidator {
    fun validate(amountText: String, date: LocalDate, time: LocalTime?, zone: ZoneId, now: Instant): WaterValidation {
        val amount = WaterLimits.parse(amountText)
        val timeCheck = EntryTimeValidator.validate(date, time, zone, now)
        return if (amount is NumberResult.Valid && timeCheck is TimeCheck.Valid) {
            WaterValidation.Valid(amount.value, timeCheck.zoned)
        } else {
            WaterValidation.Invalid(
                amountError = (amount as? NumberResult.Invalid)?.message,
                timeError = (timeCheck as? TimeCheck.Invalid)?.message,
            )
        }
    }
}

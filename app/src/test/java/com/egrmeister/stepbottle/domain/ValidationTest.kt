package com.egrmeister.stepbottle.domain

import com.egrmeister.stepbottle.domain.activity.ActivityCategory
import com.egrmeister.stepbottle.domain.activity.ActivityLimits
import com.egrmeister.stepbottle.domain.activity.ActivityValidation
import com.egrmeister.stepbottle.domain.activity.ActivityValidator
import com.egrmeister.stepbottle.domain.activity.LabelRules
import com.egrmeister.stepbottle.domain.time.EntryTimeValidator
import com.egrmeister.stepbottle.domain.time.NumberResult
import com.egrmeister.stepbottle.domain.time.TimeCheck
import com.egrmeister.stepbottle.domain.water.WaterLimits
import com.egrmeister.stepbottle.domain.water.WaterValidation
import com.egrmeister.stepbottle.domain.water.WaterValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ValidationTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val now = ZonedDateTime.of(2026, 3, 10, 14, 30, 20, 0, zone).toInstant()
    private val today = LocalDate.of(2026, 3, 10)

    @Test fun waterAmountLimitsAndWholeNumbers() {
        assertTrue(WaterLimits.parse("1") is NumberResult.Valid)
        assertTrue(WaterLimits.parse("5000") is NumberResult.Valid)
        assertTrue(WaterLimits.parse("5,000") is NumberResult.Valid)
        assertTrue(WaterLimits.parse("0") is NumberResult.Invalid)
        assertTrue(WaterLimits.parse("5001") is NumberResult.Invalid)
        assertTrue(WaterLimits.parse("250.5") is NumberResult.Invalid)
        assertTrue(WaterLimits.parse("-5") is NumberResult.Invalid)
        assertTrue(WaterLimits.parse("") is NumberResult.Invalid)
        assertTrue(WaterLimits.parse("99999999999999999999") is NumberResult.Invalid)
    }

    @Test fun waterValidatorRejectsFutureDatesAndTimes() {
        val ok = WaterValidator.validate("250", today, LocalTime.of(14, 30), zone, now)
        assertTrue(ok is WaterValidation.Valid)
        val future = WaterValidator.validate("250", today, LocalTime.of(14, 31), zone, now) as WaterValidation.Invalid
        assertEquals(EntryTimeValidator.FUTURE_TIME, future.timeError)
        val tomorrow = WaterValidator.validate("250", today.plusDays(1), LocalTime.of(8, 0), zone, now) as WaterValidation.Invalid
        assertEquals(EntryTimeValidator.FUTURE_DATE, tomorrow.timeError)
        val past = WaterValidator.validate("250", today.minusDays(3), LocalTime.of(23, 59), zone, now)
        assertTrue(past is WaterValidation.Valid)
    }

    @Test fun historicalEntryRequiresExplicitTime() {
        val r = EntryTimeValidator.validate(today.minusDays(1), null, zone, now)
        assertEquals(TimeCheck.Invalid(EntryTimeValidator.MISSING_TIME), r)
    }

    @Test fun activityDurationLabelAndCategory() {
        assertTrue(ActivityLimits.parse("600") is NumberResult.Valid)
        assertTrue(ActivityLimits.parse("601") is NumberResult.Invalid)
        assertTrue(ActivityLimits.parse("0") is NumberResult.Invalid)
        assertTrue(ActivityLimits.parse("1.5") is NumberResult.Invalid)
        val forty = "a".repeat(40)
        assertNull(LabelRules.error(forty))
        assertEquals("Use at most 40 characters.", LabelRules.error(forty + "b"))
        assertNull(LabelRules.normalize("   "))
        assertEquals("Evening walk", LabelRules.normalize("  Evening \n  walk "))
        val v = ActivityValidator.validate("30", ActivityCategory.STRETCH, "  yoga  ", today, LocalTime.NOON, zone, now)
        v as ActivityValidation.Valid
        assertEquals(30, v.minutes)
        assertEquals("yoga", v.label)
        assertEquals(ActivityCategory.STRETCH, v.category)
        val bad = ActivityValidator.validate("700", ActivityCategory.WALK, "x".repeat(41), today, null, zone, now)
        bad as ActivityValidation.Invalid
        assertTrue(bad.durationError != null && bad.labelError != null && bad.timeError != null)
    }

    @Test fun dailyActivityLimitArithmetic() {
        assertTrue(ActivityLimits.fitsDailyLimit(840, 600))
        assertFalse(ActivityLimits.fitsDailyLimit(841, 600))
        assertTrue(ActivityLimits.fitsDailyLimit(1439, 1))
        assertFalse(ActivityLimits.fitsDailyLimit(1440, 1))
    }

    @Test fun nonexistentDaylightSavingTimeIsRejected() {
        // Europe/Berlin springs forward on 2026-03-29 at 02:00 → 03:00.
        val later = ZonedDateTime.of(2026, 4, 1, 12, 0, 0, 0, zone).toInstant()
        val gap = EntryTimeValidator.validate(LocalDate.of(2026, 3, 29), LocalTime.of(2, 30), zone, later)
        assertEquals(TimeCheck.Invalid(EntryTimeValidator.NONEXISTENT_TIME), gap)
        // Falls back on 2026-10-25 at 03:00 → 02:00: ambiguous time uses the earlier instant.
        val afterFall = ZonedDateTime.of(2026, 10, 26, 12, 0, 0, 0, zone).toInstant()
        val overlap = EntryTimeValidator.validate(LocalDate.of(2026, 10, 25), LocalTime.of(2, 30), zone, afterFall)
        overlap as TimeCheck.Valid
        assertEquals(2 * 3600, overlap.zoned.offset.totalSeconds)
    }
}

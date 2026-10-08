package com.egrmeister.stepbottle.domain

import com.egrmeister.stepbottle.domain.activity.ActivityCategory
import com.egrmeister.stepbottle.domain.activity.ActivityEntry
import com.egrmeister.stepbottle.domain.summary.DailySummary
import com.egrmeister.stepbottle.domain.summary.EntryFilter
import com.egrmeister.stepbottle.domain.summary.LogItem
import com.egrmeister.stepbottle.domain.summary.LogList
import com.egrmeister.stepbottle.domain.summary.WeekCalculator
import com.egrmeister.stepbottle.domain.water.WaterEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class SummaryAndWeekTest {
    private val zone = ZoneId.of("UTC")
    private val day = LocalDate.of(2026, 6, 1)

    private fun water(id: Long, ml: Int, t: LocalTime) = WaterEntry(
        id, ml, day.atTime(t).atZone(zone).toInstant(), day, t, zone, Instant.EPOCH, Instant.EPOCH,
    )

    private fun act(id: Long, min: Int, t: LocalTime) = ActivityEntry(
        id, min, ActivityCategory.WALK, null, day.atTime(t).atZone(zone).toInstant(), day, t, zone, Instant.EPOCH, Instant.EPOCH,
    )

    @Test fun dailyTotalsAreIndependentAndTextMatchesSpec() {
        val s = DailySummary.of(listOf(water(1, 500, LocalTime.of(8, 0)), water(2, 250, LocalTime.of(9, 0))), listOf(act(1, 25, LocalTime.of(10, 0))))
        assertEquals(750L, s.waterMl)
        assertEquals(25L, s.activityMinutes)
        assertEquals("750 mL water recorded.", s.waterLine)
        assertEquals("25 activity minutes recorded.", s.activityLine)
        assertEquals("2 water entries", s.waterCountLine)
        assertEquals("1 activity entry", s.activityCountLine)
    }

    @Test fun missingCategoryIsNoEntriesNotZero() {
        val onlyWater = DailySummary.of(listOf(water(1, 300, LocalTime.NOON)), emptyList())
        assertEquals("No entries.", onlyWater.activityLine)
        val empty = DailySummary.of(emptyList(), emptyList())
        assertTrue(empty.isEmpty)
        assertEquals("No records for this day.", DailySummary.EMPTY_DAY)
    }

    @Test fun combinedListIsChronologicalAndFiltered() {
        val w = listOf(water(1, 100, LocalTime.of(12, 0)), water(2, 200, LocalTime.of(7, 0)))
        val a = listOf(act(5, 10, LocalTime.of(9, 0)))
        val all = LogList.combine(w, a, EntryFilter.ALL)
        assertEquals(listOf(2L, 5L, 1L), all.map { it.id })
        assertTrue(LogList.combine(w, a, EntryFilter.WATER).all { it is LogItem.Water })
        assertTrue(LogList.combine(w, a, EntryFilter.ACTIVITY).all { it is LogItem.Activity })
    }

    @Test fun weeklyAveragesUseSeparateDenominators() {
        val end = LocalDate.of(2026, 6, 7)
        val water = mapOf(
            end to (1000L to 3),
            end.minusDays(1) to (500L to 1),
            end.minusDays(2) to (600L to 2),
        )
        val activity = mapOf(end.minusDays(6) to (45L to 2))
        val stats = WeekCalculator.compute(end, water, activity)
        assertEquals(7, stats.days.size)
        assertEquals(end.minusDays(6), stats.days.first().date)
        assertEquals(2100L, stats.totalWaterMl)
        assertEquals(45L, stats.totalActivityMinutes)
        assertEquals(3, stats.daysWithWater)
        assertEquals(1, stats.daysWithActivity)
        assertEquals(700L, stats.averageWaterOnWaterDays) // 2100 / 3 water days, not / 7
        assertEquals(45L, stats.averageActivityOnActivityDays) // 45 / 1 activity day
    }

    @Test fun averagesAreAbsentWithoutRecordsAndRoundHalfUp() {
        val stats = WeekCalculator.compute(LocalDate.of(2026, 1, 7), emptyMap(), emptyMap())
        assertNull(stats.averageWaterOnWaterDays)
        assertNull(stats.averageActivityOnActivityDays)
        assertEquals(2L, WeekCalculator.average(5, 3)) // 1.67 → 2
        assertEquals(3L, WeekCalculator.average(5, 2)) // 2.5 → 3
    }

    @Test fun weekRangeCrossesMonthAndYear() {
        val r = WeekCalculator.range(LocalDate.of(2026, 1, 3))
        assertEquals(LocalDate.of(2025, 12, 28), r.first())
        assertEquals(LocalDate.of(2026, 1, 3), r.last())
    }
}

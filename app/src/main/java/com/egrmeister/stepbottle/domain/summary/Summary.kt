package com.egrmeister.stepbottle.domain.summary

import com.egrmeister.stepbottle.core.Format
import com.egrmeister.stepbottle.domain.activity.ActivityEntry
import com.egrmeister.stepbottle.domain.water.WaterEntry
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Totals for one local date. Water and activity are independent and never combined. */
data class DailySummary(
    val waterMl: Long = 0,
    val waterCount: Int = 0,
    val activityMinutes: Long = 0,
    val activityCount: Int = 0,
) {
    val isEmpty: Boolean get() = waterCount == 0 && activityCount == 0

    /** “750 mL water recorded.” or “No entries.” — missing records are not described as zero intake. */
    val waterLine: String
        get() = if (waterCount == 0) "No entries." else "${Format.ml(waterMl)} water recorded."

    val activityLine: String
        get() = when {
            activityCount == 0 -> "No entries."
            activityMinutes == 1L -> "1 activity minute recorded."
            else -> "${Format.number(activityMinutes)} activity minutes recorded."
        }

    val waterCountLine: String get() = Format.plural(waterCount, "water entry", "water entries")
    val activityCountLine: String get() = Format.plural(activityCount, "activity entry", "activity entries")

    companion object {
        const val EMPTY_DAY = "No records for this day."

        fun of(water: List<WaterEntry>, activity: List<ActivityEntry>) = DailySummary(
            waterMl = water.sumOf { it.amountMl.toLong() },
            waterCount = water.size,
            activityMinutes = activity.sumOf { it.durationMinutes.toLong() },
            activityCount = activity.size,
        )
    }
}

enum class EntryFilter(val label: String) { ALL("All"), WATER("Water"), ACTIVITY("Activity") }

/** One row of the combined chronological list. */
sealed interface LogItem {
    val id: Long
    val localTime: LocalTime
    val timestamp: Instant

    data class Water(val entry: WaterEntry) : LogItem {
        override val id get() = entry.id
        override val localTime: LocalTime get() = entry.localTime
        override val timestamp: Instant get() = entry.timestamp
    }

    data class Activity(val entry: ActivityEntry) : LogItem {
        override val id get() = entry.id
        override val localTime: LocalTime get() = entry.localTime
        override val timestamp: Instant get() = entry.timestamp
    }
}

object LogList {
    /** Combined list in diary order: saved local time, then absolute time, water before activity. */
    fun combine(water: List<WaterEntry>, activity: List<ActivityEntry>, filter: EntryFilter): List<LogItem> {
        val items = buildList {
            if (filter != EntryFilter.ACTIVITY) water.forEach { add(LogItem.Water(it)) }
            if (filter != EntryFilter.WATER) activity.forEach { add(LogItem.Activity(it)) }
        }
        return items.sortedWith(
            compareBy<LogItem> { it.localTime }
                .thenBy { it.timestamp }
                .thenBy { if (it is LogItem.Water) 0 else 1 }
                .thenBy { it.id },
        )
    }
}

/** Per-day values for the weekly history. */
data class DayTotals(
    val date: LocalDate,
    val waterMl: Long = 0,
    val waterCount: Int = 0,
    val activityMinutes: Long = 0,
    val activityCount: Int = 0,
)

data class WeeklyStats(
    val days: List<DayTotals>,
    val totalWaterMl: Long,
    val totalActivityMinutes: Long,
    val daysWithWater: Int,
    val daysWithActivity: Int,
    /** Total water ÷ days with water records (null when there are none). */
    val averageWaterOnWaterDays: Long?,
    /** Total activity ÷ days with activity records (null when there are none). */
    val averageActivityOnActivityDays: Long?,
) {
    val maxWater: Long get() = days.maxOfOrNull { it.waterMl } ?: 0
    val maxActivity: Long get() = days.maxOfOrNull { it.activityMinutes } ?: 0
}

object WeekCalculator {
    const val DAYS = 7L

    /** Seven consecutive dates ending at [end] (inclusive), oldest first. */
    fun range(end: LocalDate): List<LocalDate> = (DAYS - 1 downTo 0).map { end.minusDays(it) }

    fun startOf(end: LocalDate): LocalDate = end.minusDays(DAYS - 1)

    fun compute(end: LocalDate, water: Map<LocalDate, Pair<Long, Int>>, activity: Map<LocalDate, Pair<Long, Int>>): WeeklyStats {
        val days = range(end).map { d ->
            val w = water[d]
            val a = activity[d]
            DayTotals(d, w?.first ?: 0, w?.second ?: 0, a?.first ?: 0, a?.second ?: 0)
        }
        val totalWater = days.sumOf { it.waterMl }
        val totalActivity = days.sumOf { it.activityMinutes }
        val waterDays = days.count { it.waterCount > 0 }
        val activityDays = days.count { it.activityCount > 0 }
        return WeeklyStats(
            days = days,
            totalWaterMl = totalWater,
            totalActivityMinutes = totalActivity,
            daysWithWater = waterDays,
            daysWithActivity = activityDays,
            averageWaterOnWaterDays = average(totalWater, waterDays),
            averageActivityOnActivityDays = average(totalActivity, activityDays),
        )
    }

    /** Rounded half-up integer average; null when the denominator is zero. */
    fun average(total: Long, days: Int): Long? = if (days == 0) null else (total * 2 + days) / (days * 2L)
}

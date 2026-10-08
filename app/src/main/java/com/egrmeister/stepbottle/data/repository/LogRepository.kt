package com.egrmeister.stepbottle.data.repository

import com.egrmeister.stepbottle.core.TimeProvider
import com.egrmeister.stepbottle.data.local.ActivityEntryDao
import com.egrmeister.stepbottle.data.local.ActivityEntryEntity
import com.egrmeister.stepbottle.data.local.DailyAggregateRow
import com.egrmeister.stepbottle.data.local.TransactionRunner
import com.egrmeister.stepbottle.data.local.WaterEntryDao
import com.egrmeister.stepbottle.data.local.WaterEntryEntity
import com.egrmeister.stepbottle.domain.activity.ActivityCategory
import com.egrmeister.stepbottle.domain.activity.ActivityEntry
import com.egrmeister.stepbottle.domain.activity.ActivityLimits
import com.egrmeister.stepbottle.domain.summary.WeekCalculator
import com.egrmeister.stepbottle.domain.summary.WeeklyStats
import com.egrmeister.stepbottle.domain.water.WaterEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

sealed interface SaveResult {
    data class Saved(val id: Long) : SaveResult

    /** The activity minutes would push that local date past 1,440 recorded minutes. */
    data class DailyLimitExceeded(val otherMinutesThatDay: Long) : SaveResult
    data object NotFound : SaveResult
}

/**
 * Single source of truth for both record types. Totals are always derived from records.
 * All Room calls are suspend/Flow and run off the main thread.
 */
class LogRepository(
    private val water: WaterEntryDao,
    private val activity: ActivityEntryDao,
    private val tx: TransactionRunner,
    private val time: TimeProvider,
) {
    // ---- observation --------------------------------------------------------------------

    fun observeWater(date: LocalDate): Flow<List<WaterEntry>> =
        water.observeForDate(date.toString()).map { list -> list.map { it.toDomain() } }

    fun observeActivity(date: LocalDate): Flow<List<ActivityEntry>> =
        activity.observeForDate(date.toString()).map { list -> list.map { it.toDomain() } }

    fun observeWeek(end: LocalDate): Flow<WeeklyStats> {
        val from = WeekCalculator.startOf(end).toString()
        val to = end.toString()
        return combine(
            water.observeDailyAggregates(from, to),
            activity.observeDailyAggregates(from, to),
        ) { w, a -> WeekCalculator.compute(end, w.toMap(), a.toMap()) }
    }

    suspend fun getWater(id: Long): WaterEntry? = water.getById(id)?.toDomain()
    suspend fun getActivity(id: Long): ActivityEntry? = activity.getById(id)?.toDomain()
    suspend fun waterTotal(date: LocalDate): Long = water.totalForDate(date.toString())
    suspend fun activityTotal(date: LocalDate): Long = activity.totalForDateExcluding(date.toString(), 0L)

    // ---- water ---------------------------------------------------------------------------

    suspend fun addWater(amountMl: Int, at: ZonedDateTime): Long {
        val nowMs = time.now().toEpochMilli()
        return water.insert(
            WaterEntryEntity(
                amountMl = amountMl,
                timestamp = at.toInstant().toEpochMilli(),
                loggedLocalDate = at.toLocalDate().toString(),
                loggedLocalTime = at.toLocalTime().format(TIME),
                zoneId = at.zone.id,
                createdAt = nowMs,
                updatedAt = nowMs,
            ),
        )
    }

    /** Explicit edit: amount and date attribution change together. */
    suspend fun updateWater(id: Long, amountMl: Int, at: ZonedDateTime): SaveResult = tx.run {
        val existing = water.getById(id) ?: return@run SaveResult.NotFound
        water.update(
            existing.copy(
                amountMl = amountMl,
                timestamp = at.toInstant().toEpochMilli(),
                loggedLocalDate = at.toLocalDate().toString(),
                loggedLocalTime = at.toLocalTime().format(TIME),
                zoneId = at.zone.id,
                updatedAt = time.now().toEpochMilli(),
            ),
        )
        SaveResult.Saved(id)
    }

    /** Deletes and returns the exact removed record so it can be restored once. */
    suspend fun deleteWater(id: Long): WaterEntry? = tx.run {
        val existing = water.getById(id) ?: return@run null
        water.deleteById(id)
        existing.toDomain()
    }

    /** Restores an exact deleted record (same id and fields). Returns false if it already exists. */
    suspend fun restoreWater(entry: WaterEntry): Boolean = water.insertIfAbsent(entry.toEntity()) != -1L

    // ---- activity ------------------------------------------------------------------------

    /** Adds activity minutes; the 1,440-minute daily limit is checked in the same transaction. */
    suspend fun addActivity(minutes: Int, category: ActivityCategory, label: String?, at: ZonedDateTime): SaveResult =
        tx.run {
            val date = at.toLocalDate().toString()
            val other = activity.totalForDateExcluding(date, 0L)
            if (!ActivityLimits.fitsDailyLimit(other, minutes)) return@run SaveResult.DailyLimitExceeded(other)
            val nowMs = time.now().toEpochMilli()
            val id = activity.insert(
                ActivityEntryEntity(
                    durationMinutes = minutes,
                    category = category.name,
                    label = label,
                    timestamp = at.toInstant().toEpochMilli(),
                    loggedLocalDate = date,
                    loggedLocalTime = at.toLocalTime().format(TIME),
                    zoneId = at.zone.id,
                    createdAt = nowMs,
                    updatedAt = nowMs,
                ),
            )
            SaveResult.Saved(id)
        }

    /** Edits any field, including moving the record to another date, with the limit checked on the target date. */
    suspend fun updateActivity(
        id: Long,
        minutes: Int,
        category: ActivityCategory,
        label: String?,
        at: ZonedDateTime,
    ): SaveResult = tx.run {
        val existing = activity.getById(id) ?: return@run SaveResult.NotFound
        val date = at.toLocalDate().toString()
        val other = activity.totalForDateExcluding(date, id)
        if (!ActivityLimits.fitsDailyLimit(other, minutes)) return@run SaveResult.DailyLimitExceeded(other)
        activity.update(
            existing.copy(
                durationMinutes = minutes,
                category = category.name,
                label = label,
                timestamp = at.toInstant().toEpochMilli(),
                loggedLocalDate = date,
                loggedLocalTime = at.toLocalTime().format(TIME),
                zoneId = at.zone.id,
                updatedAt = time.now().toEpochMilli(),
            ),
        )
        SaveResult.Saved(id)
    }

    suspend fun deleteActivity(id: Long): ActivityEntry? = tx.run {
        val existing = activity.getById(id) ?: return@run null
        activity.deleteById(id)
        existing.toDomain()
    }

    /** Restores the exact record once; refuses if that date has since filled up. */
    suspend fun restoreActivity(entry: ActivityEntry): SaveResult = tx.run {
        if (activity.getById(entry.id) != null) return@run SaveResult.NotFound
        val other = activity.totalForDateExcluding(entry.localDate.toString(), entry.id)
        if (!ActivityLimits.fitsDailyLimit(other, entry.durationMinutes)) return@run SaveResult.DailyLimitExceeded(other)
        activity.insertIfAbsent(entry.toEntity())
        SaveResult.Saved(entry.id)
    }

    suspend fun clearAll() = tx.run {
        water.deleteAll()
        activity.deleteAll()
    }

    companion object {
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    }
}

private fun List<DailyAggregateRow>.toMap(): Map<LocalDate, Pair<Long, Int>> =
    associate { LocalDate.parse(it.date) to (it.total to it.count) }

private fun zoneOf(id: String): ZoneId = runCatching { ZoneId.of(id) }.getOrDefault(ZoneId.of("UTC"))

internal fun WaterEntryEntity.toDomain() = WaterEntry(
    id = id,
    amountMl = amountMl,
    timestamp = Instant.ofEpochMilli(timestamp),
    localDate = LocalDate.parse(loggedLocalDate),
    localTime = LocalTime.parse(loggedLocalTime),
    zoneId = zoneOf(zoneId),
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
)

internal fun WaterEntry.toEntity() = WaterEntryEntity(
    id = id,
    amountMl = amountMl,
    timestamp = timestamp.toEpochMilli(),
    loggedLocalDate = localDate.toString(),
    loggedLocalTime = localTime.format(LogRepository.TIME),
    zoneId = zoneId.id,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
)

internal fun ActivityEntryEntity.toDomain() = ActivityEntry(
    id = id,
    durationMinutes = durationMinutes,
    category = ActivityCategory.fromName(category) ?: ActivityCategory.OTHER,
    label = label,
    timestamp = Instant.ofEpochMilli(timestamp),
    localDate = LocalDate.parse(loggedLocalDate),
    localTime = LocalTime.parse(loggedLocalTime),
    zoneId = zoneOf(zoneId),
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
)

internal fun ActivityEntry.toEntity() = ActivityEntryEntity(
    id = id,
    durationMinutes = durationMinutes,
    category = category.name,
    label = label,
    timestamp = timestamp.toEpochMilli(),
    loggedLocalDate = localDate.toString(),
    loggedLocalTime = localTime.format(LogRepository.TIME),
    zoneId = zoneId.id,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
)

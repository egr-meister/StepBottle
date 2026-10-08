package com.egrmeister.stepbottle.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * loggedLocalDate is ISO yyyy-MM-dd and loggedLocalTime is ISO HH:mm:ss, so both sort
 * lexicographically. Historical grouping always uses these saved values, never the timestamp.
 */
@Entity(
    tableName = "water_entries",
    indices = [
        Index(value = ["loggedLocalDate", "loggedLocalTime"]),
    ],
)
data class WaterEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "amountMl") val amountMl: Int,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "loggedLocalDate") val loggedLocalDate: String,
    @ColumnInfo(name = "loggedLocalTime") val loggedLocalTime: String,
    @ColumnInfo(name = "zoneId") val zoneId: String,
    @ColumnInfo(name = "createdAt") val createdAt: Long,
    @ColumnInfo(name = "updatedAt") val updatedAt: Long,
)

@Entity(
    tableName = "activity_entries",
    indices = [
        Index(value = ["loggedLocalDate", "loggedLocalTime"]),
    ],
)
data class ActivityEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "durationMinutes") val durationMinutes: Int,
    @ColumnInfo(name = "category") val category: String,
    @ColumnInfo(name = "label") val label: String?,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "loggedLocalDate") val loggedLocalDate: String,
    @ColumnInfo(name = "loggedLocalTime") val loggedLocalTime: String,
    @ColumnInfo(name = "zoneId") val zoneId: String,
    @ColumnInfo(name = "createdAt") val createdAt: Long,
    @ColumnInfo(name = "updatedAt") val updatedAt: Long,
)

/** Aggregate row; totals are Long. */
data class DailyAggregateRow(
    @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "total") val total: Long,
    @ColumnInfo(name = "count") val count: Int,
)

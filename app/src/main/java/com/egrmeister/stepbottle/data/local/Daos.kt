package com.egrmeister.stepbottle.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WaterEntryDao {
    @Query("SELECT * FROM water_entries WHERE loggedLocalDate = :date ORDER BY loggedLocalTime ASC, timestamp ASC, id ASC")
    fun observeForDate(date: String): Flow<List<WaterEntryEntity>>

    @Query("SELECT COALESCE(SUM(amountMl), 0) FROM water_entries WHERE loggedLocalDate = :date")
    suspend fun totalForDate(date: String): Long

    @Query(
        "SELECT loggedLocalDate AS date, COALESCE(SUM(amountMl), 0) AS total, COUNT(*) AS count FROM water_entries " +
            "WHERE loggedLocalDate BETWEEN :from AND :to GROUP BY loggedLocalDate",
    )
    fun observeDailyAggregates(from: String, to: String): Flow<List<DailyAggregateRow>>

    @Query("SELECT * FROM water_entries WHERE id = :id")
    suspend fun getById(id: Long): WaterEntryEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: WaterEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entity: WaterEntryEntity): Long

    @Update
    suspend fun update(entity: WaterEntryEntity): Int

    @Query("DELETE FROM water_entries WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM water_entries")
    suspend fun deleteAll()
}

@Dao
interface ActivityEntryDao {
    @Query("SELECT * FROM activity_entries WHERE loggedLocalDate = :date ORDER BY loggedLocalTime ASC, timestamp ASC, id ASC")
    fun observeForDate(date: String): Flow<List<ActivityEntryEntity>>

    /** Minutes recorded on [date], excluding the entry being edited (pass 0 for a new entry). */
    @Query("SELECT COALESCE(SUM(durationMinutes), 0) FROM activity_entries WHERE loggedLocalDate = :date AND id != :excludeId")
    suspend fun totalForDateExcluding(date: String, excludeId: Long): Long

    @Query(
        "SELECT loggedLocalDate AS date, COALESCE(SUM(durationMinutes), 0) AS total, COUNT(*) AS count FROM activity_entries " +
            "WHERE loggedLocalDate BETWEEN :from AND :to GROUP BY loggedLocalDate",
    )
    fun observeDailyAggregates(from: String, to: String): Flow<List<DailyAggregateRow>>

    @Query("SELECT * FROM activity_entries WHERE id = :id")
    suspend fun getById(id: Long): ActivityEntryEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ActivityEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entity: ActivityEntryEntity): Long

    @Update
    suspend fun update(entity: ActivityEntryEntity): Int

    @Query("DELETE FROM activity_entries WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM activity_entries")
    suspend fun deleteAll()
}

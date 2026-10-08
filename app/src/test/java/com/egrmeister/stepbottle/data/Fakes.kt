package com.egrmeister.stepbottle.data

import com.egrmeister.stepbottle.data.local.ActivityEntryDao
import com.egrmeister.stepbottle.data.local.ActivityEntryEntity
import com.egrmeister.stepbottle.data.local.DailyAggregateRow
import com.egrmeister.stepbottle.data.local.TransactionRunner
import com.egrmeister.stepbottle.data.local.WaterEntryDao
import com.egrmeister.stepbottle.data.local.WaterEntryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serialises blocks like a database transaction would. */
class FakeTransactionRunner : TransactionRunner {
    private val mutex = Mutex()
    override suspend fun <T> run(block: suspend () -> T): T = mutex.withLock { block() }
}

class FakeWaterDao : WaterEntryDao {
    val rows = linkedMapOf<Long, WaterEntryEntity>()
    private val version = MutableStateFlow(0)
    private var nextId = 1L
    private fun changed() { version.value++ }

    override fun observeForDate(date: String): Flow<List<WaterEntryEntity>> = version.map {
        rows.values.filter { it.loggedLocalDate == date }.sortedWith(compareBy({ it.loggedLocalTime }, { it.timestamp }, { it.id }))
    }
    override suspend fun totalForDate(date: String): Long = rows.values.filter { it.loggedLocalDate == date }.sumOf { it.amountMl.toLong() }
    override fun observeDailyAggregates(from: String, to: String): Flow<List<DailyAggregateRow>> = version.map {
        rows.values.filter { it.loggedLocalDate in from..to }.groupBy { it.loggedLocalDate }
            .map { (d, l) -> DailyAggregateRow(d, l.sumOf { it.amountMl.toLong() }, l.size) }
    }
    override suspend fun getById(id: Long): WaterEntryEntity? = rows[id]
    override suspend fun insert(entity: WaterEntryEntity): Long {
        val id = if (entity.id == 0L) nextId++ else entity.id
        check(id !in rows)
        rows[id] = entity.copy(id = id); changed(); return id
    }
    override suspend fun insertIfAbsent(entity: WaterEntryEntity): Long {
        if (entity.id in rows) return -1
        rows[entity.id] = entity; changed(); return entity.id
    }
    override suspend fun update(entity: WaterEntryEntity): Int {
        if (entity.id !in rows) return 0
        rows[entity.id] = entity; changed(); return 1
    }
    override suspend fun deleteById(id: Long): Int = if (rows.remove(id) != null) { changed(); 1 } else 0
    override suspend fun deleteAll() { rows.clear(); changed() }
}

class FakeActivityDao : ActivityEntryDao {
    val rows = linkedMapOf<Long, ActivityEntryEntity>()
    private val version = MutableStateFlow(0)
    private var nextId = 1L
    private fun changed() { version.value++ }

    override fun observeForDate(date: String): Flow<List<ActivityEntryEntity>> = version.map {
        rows.values.filter { it.loggedLocalDate == date }.sortedWith(compareBy({ it.loggedLocalTime }, { it.timestamp }, { it.id }))
    }
    override suspend fun totalForDateExcluding(date: String, excludeId: Long): Long =
        rows.values.filter { it.loggedLocalDate == date && it.id != excludeId }.sumOf { it.durationMinutes.toLong() }
    override fun observeDailyAggregates(from: String, to: String): Flow<List<DailyAggregateRow>> = version.map {
        rows.values.filter { it.loggedLocalDate in from..to }.groupBy { it.loggedLocalDate }
            .map { (d, l) -> DailyAggregateRow(d, l.sumOf { it.durationMinutes.toLong() }, l.size) }
    }
    override suspend fun getById(id: Long): ActivityEntryEntity? = rows[id]
    override suspend fun insert(entity: ActivityEntryEntity): Long {
        val id = if (entity.id == 0L) nextId++ else entity.id
        check(id !in rows)
        rows[id] = entity.copy(id = id); changed(); return id
    }
    override suspend fun insertIfAbsent(entity: ActivityEntryEntity): Long {
        if (entity.id in rows) return -1
        rows[entity.id] = entity; changed(); return entity.id
    }
    override suspend fun update(entity: ActivityEntryEntity): Int {
        if (entity.id !in rows) return 0
        rows[entity.id] = entity; changed(); return 1
    }
    override suspend fun deleteById(id: Long): Int = if (rows.remove(id) != null) { changed(); 1 } else 0
    override suspend fun deleteAll() { rows.clear(); changed() }
}

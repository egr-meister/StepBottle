package com.egrmeister.stepbottle.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.room.withTransaction

@Database(
    entities = [WaterEntryEntity::class, ActivityEntryEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class StepBottleDatabase : RoomDatabase() {
    abstract fun waterDao(): WaterEntryDao
    abstract fun activityDao(): ActivityEntryDao

    companion object {
        const val NAME = "stepbottle.db"

        /**
         * Explicit migrations for every schema bump (schemas are exported to app/schemas).
         * Destructive migration is never enabled, so a missing migration fails loudly instead of
         * wiping user data.
         */
        val MIGRATIONS: Array<Migration> = emptyArray()

        fun create(context: Context): StepBottleDatabase =
            Room.databaseBuilder(context.applicationContext, StepBottleDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}

/** Abstraction over Room transactions so repositories are unit-testable. */
interface TransactionRunner {
    suspend fun <T> run(block: suspend () -> T): T
}

class RoomTransactionRunner(private val db: StepBottleDatabase) : TransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = db.withTransaction { block() }
}

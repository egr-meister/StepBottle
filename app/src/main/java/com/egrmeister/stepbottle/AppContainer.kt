package com.egrmeister.stepbottle

import android.content.Context
import com.egrmeister.stepbottle.core.SystemTimeProvider
import com.egrmeister.stepbottle.core.TimeProvider
import com.egrmeister.stepbottle.core.TodayClock
import com.egrmeister.stepbottle.data.local.RoomTransactionRunner
import com.egrmeister.stepbottle.data.local.SettingsStore
import com.egrmeister.stepbottle.data.local.StepBottleDatabase
import com.egrmeister.stepbottle.data.repository.LogRepository
import com.egrmeister.stepbottle.domain.actions.ActionGuard
import com.egrmeister.stepbottle.reminders.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency injection. */
class AppContainer(context: Context, val time: TimeProvider = SystemTimeProvider) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val database: StepBottleDatabase by lazy { StepBottleDatabase.create(context) }
    val settings = SettingsStore(context)
    val repository: LogRepository by lazy {
        LogRepository(database.waterDao(), database.activityDao(), RoomTransactionRunner(database), time)
    }
    val reminderScheduler = ReminderScheduler(context.applicationContext, time)
    val todayClock = TodayClock(time, appScope)

    /** Shared across screens so one activation is never processed twice. */
    val actionGuard = ActionGuard()

    /** Confirmations with Undo, shared by the daily log and the editor. */
    val feedback = FeedbackCenter { repository }

    /** Cancels reminders and visible notifications, deletes all records and preferences (defaults return). */
    suspend fun clearAllData() {
        reminderScheduler.cancelAll()
        feedback.clear()
        repository.clearAll()
        settings.clear()
    }
}

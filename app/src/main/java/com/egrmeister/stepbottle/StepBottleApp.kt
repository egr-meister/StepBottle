package com.egrmeister.stepbottle

import android.app.Application
import com.egrmeister.stepbottle.reminders.ReminderScheduler
import kotlinx.coroutines.launch

class StepBottleApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ReminderScheduler.ensureChannel(this)
        // Recover the schedule on every process start (covers updates and reopening after force-stop).
        container.appScope.launch {
            container.reminderScheduler.reschedule(container.settings.current().reminders)
        }
    }
}

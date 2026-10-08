package com.egrmeister.stepbottle.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.egrmeister.stepbottle.StepBottleApp
import com.egrmeister.stepbottle.domain.reminders.ReminderDecision
import com.egrmeister.stepbottle.domain.reminders.ReminderPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

private fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    receiverScope.launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

/** Fires for the scheduled occurrence, re-checks eligibility, posts at most one notification, reschedules. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderScheduler.ACTION_REMIND) return
        val app = context.applicationContext as StepBottleApp
        runAsync {
            val c = app.container
            val settings = c.settings.current().reminders
            val decision = ReminderPolicy.decide(
                settings = settings,
                callbackGeneration = intent.getLongExtra(ReminderScheduler.EXTRA_GENERATION, -1L),
                scheduledFor = Instant.ofEpochMilli(intent.getLongExtra(ReminderScheduler.EXTRA_SLOT, 0L)),
                now = c.time.now(),
                zone = c.time.zone(),
                canPost = ReminderScheduler.canPost(context),
            )
            if (decision == ReminderDecision.Post) ReminderScheduler.post(context)
            // Always schedule only the next future occurrence; missed days are never replayed.
            c.reminderScheduler.reschedule(settings)
        }
    }
}

/** Reboot, app update, time and time-zone changes: recompute the next occurrence. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> Unit
            else -> return
        }
        val app = context.applicationContext as StepBottleApp
        runAsync {
            val c = app.container
            c.todayClock.refresh()
            c.reminderScheduler.reschedule(c.settings.current().reminders)
        }
    }
}

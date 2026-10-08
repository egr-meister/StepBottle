package com.egrmeister.stepbottle

import com.egrmeister.stepbottle.data.repository.LogRepository
import com.egrmeister.stepbottle.data.repository.SaveResult
import com.egrmeister.stepbottle.domain.actions.UndoSlot
import com.egrmeister.stepbottle.domain.activity.ActivityEntry
import com.egrmeister.stepbottle.domain.activity.ActivityLimits
import com.egrmeister.stepbottle.domain.water.WaterEntry
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.concurrent.atomic.AtomicLong

/** A reversible change. Each one can be undone exactly once. */
sealed interface UndoAction {
    data class RemoveAddedWater(val id: Long) : UndoAction
    data class RemoveAddedActivity(val id: Long) : UndoAction
    data class RestoreWater(val entry: WaterEntry) : UndoAction
    data class RestoreActivity(val entry: ActivityEntry) : UndoAction
}

data class Feedback(val message: String, val undoToken: Long? = null)

/**
 * Confirmation messages with Undo, shared by the daily log and the entry editor. Messages are
 * buffered until a daily-log screen shows them, so a confirmation is not lost while navigating back.
 */
class FeedbackCenter(private val repository: () -> LogRepository) {
    private val channel = Channel<Feedback>(capacity = Channel.BUFFERED)
    val messages: Flow<Feedback> = channel.receiveAsFlow()

    private data class Pending(val token: Long, val action: UndoAction)

    private val slot = UndoSlot<Pending>()
    private val tokens = AtomicLong(1)

    fun show(message: String) {
        channel.trySend(Feedback(message))
    }

    fun showUndoable(message: String, action: UndoAction) {
        val token = tokens.getAndIncrement()
        slot.offer(Pending(token, action))
        channel.trySend(Feedback(message, token))
    }

    /** Performs the undo for [token] once; later calls with the same token do nothing. */
    suspend fun undo(token: Long) {
        val pending = slot.takeIf { it.token == token } ?: return
        val repo = repository()
        when (val a = pending.action) {
            is UndoAction.RemoveAddedWater -> repo.deleteWater(a.id)
            is UndoAction.RemoveAddedActivity -> repo.deleteActivity(a.id)
            is UndoAction.RestoreWater -> repo.restoreWater(a.entry)
            is UndoAction.RestoreActivity -> {
                val r = repo.restoreActivity(a.entry)
                if (r is SaveResult.DailyLimitExceeded) show(ActivityLimits.dailyLimitMessage(r.otherMinutesThatDay))
            }
        }
    }

    fun clear() = slot.clear()
}

package com.egrmeister.stepbottle.domain.actions

/**
 * Protects each activation from duplicate processing: an activation token is processed at most
 * once, and a new activation is ignored only while the previous one is still being saved.
 * Deliberate separate taps after a save completes create separate entries.
 */
class ActionGuard {
    private var busy = false
    private val processed = LinkedHashSet<Long>()

    @Synchronized
    fun tryBegin(activationId: Long): Boolean {
        if (activationId in processed) return false
        if (busy) return false
        busy = true
        processed += activationId
        if (processed.size > 64) processed.remove(processed.first())
        return true
    }

    @Synchronized
    fun end() {
        busy = false
    }
}

/** Holds the last undoable action; each can be taken (undone) exactly once. */
class UndoSlot<T : Any> {
    private var pending: T? = null

    @Synchronized
    fun offer(value: T) {
        pending = value
    }

    @Synchronized
    fun take(): T? = pending.also { pending = null }

    @Synchronized
    fun takeIf(predicate: (T) -> Boolean): T? {
        val p = pending ?: return null
        return if (predicate(p)) p.also { pending = null } else null
    }

    @Synchronized
    fun clear() {
        pending = null
    }
}

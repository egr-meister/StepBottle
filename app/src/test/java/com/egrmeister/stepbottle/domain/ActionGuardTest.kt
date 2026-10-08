package com.egrmeister.stepbottle.domain

import com.egrmeister.stepbottle.domain.actions.ActionGuard
import com.egrmeister.stepbottle.domain.actions.UndoSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionGuardTest {
    @Test fun sameActivationIsProcessedOnce() {
        val g = ActionGuard()
        assertTrue(g.tryBegin(1))
        g.end()
        assertFalse(g.tryBegin(1))
    }

    @Test fun overlappingActivationIsIgnoredWhileBusy() {
        val g = ActionGuard()
        assertTrue(g.tryBegin(1))
        assertFalse(g.tryBegin(2))
        g.end()
        assertTrue(g.tryBegin(3)) // a deliberate separate tap after the save is a new entry
    }

    @Test fun undoIsTakenOnlyOnceAndOnlyForItsToken() {
        val slot = UndoSlot<Pair<Long, String>>()
        slot.offer(7L to "a")
        assertNull(slot.takeIf { it.first == 8L })
        assertEquals("a", slot.takeIf { it.first == 7L }?.second)
        assertNull(slot.take())
    }
}

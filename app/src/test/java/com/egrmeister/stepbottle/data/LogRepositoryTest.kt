package com.egrmeister.stepbottle.data

import com.egrmeister.stepbottle.FeedbackCenter
import com.egrmeister.stepbottle.UndoAction
import com.egrmeister.stepbottle.core.FixedTimeProvider
import com.egrmeister.stepbottle.core.TodayClock
import com.egrmeister.stepbottle.data.repository.LogRepository
import com.egrmeister.stepbottle.data.repository.SaveResult
import com.egrmeister.stepbottle.domain.activity.ActivityCategory
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class LogRepositoryTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private val time = FixedTimeProvider(ZonedDateTime.of(2026, 5, 4, 23, 30, 0, 0, berlin).toInstant(), berlin)
    private val waterDao = FakeWaterDao()
    private val activityDao = FakeActivityDao()
    private val repo = LogRepository(waterDao, activityDao, FakeTransactionRunner(), time)
    private val may4 = LocalDate.of(2026, 5, 4)
    private val may3 = LocalDate.of(2026, 5, 3)

    private fun at(date: LocalDate, h: Int, m: Int = 0) = date.atTime(h, m).atZone(berlin)

    @Test fun totalsAreIndependentAndDerivedFromRecords() = runTest {
        repo.addWater(250, time.zonedNow())
        repo.addWater(500, at(may4, 9))
        repo.addActivity(25, ActivityCategory.WALK, null, at(may4, 10))
        assertEquals(750L, repo.waterTotal(may4))
        assertEquals(25L, repo.activityTotal(may4))
        val week = repo.observeWeek(may4).first()
        assertEquals(750L, week.totalWaterMl)
        assertEquals(25L, week.totalActivityMinutes)
        assertEquals(1, week.daysWithWater)
        assertEquals(1, week.daysWithActivity)
    }

    @Test fun dailyActivityLimitIsEnforcedOnAdd() = runTest {
        assertTrue(repo.addActivity(600, ActivityCategory.EXERCISE, null, at(may4, 8)) is SaveResult.Saved)
        assertTrue(repo.addActivity(600, ActivityCategory.WALK, null, at(may4, 9)) is SaveResult.Saved)
        val r = repo.addActivity(241, ActivityCategory.OTHER, null, at(may4, 10))
        assertEquals(SaveResult.DailyLimitExceeded(1200), r)
        assertTrue(repo.addActivity(240, ActivityCategory.OTHER, null, at(may4, 10)) is SaveResult.Saved)
        assertEquals(1440L, repo.activityTotal(may4))
        // Another date is independent.
        assertTrue(repo.addActivity(600, ActivityCategory.WALK, null, at(may3, 10)) is SaveResult.Saved)
    }

    @Test fun concurrentAddsCannotExceedTheDailyLimit() = runTest {
        repo.addActivity(600, ActivityCategory.WALK, null, at(may4, 6))
        val results = (1..3).map { async { repo.addActivity(500, ActivityCategory.WALK, null, at(may4, 7 + it)) } }.awaitAll()
        assertEquals(1, results.count { it is SaveResult.Saved })
        assertTrue(repo.activityTotal(may4) <= 1440L)
    }

    @Test fun editingChecksTheLimitOnTheTargetDateExcludingItself() = runTest {
        val id = (repo.addActivity(600, ActivityCategory.WALK, null, at(may4, 8)) as SaveResult.Saved).id
        repo.addActivity(600, ActivityCategory.WALK, null, at(may4, 9))
        // Growing this entry to 600 is fine because its own minutes are excluded.
        assertTrue(repo.updateActivity(id, 600, ActivityCategory.STRETCH, "mat", at(may4, 8)) is SaveResult.Saved)
        repo.addActivity(500, ActivityCategory.WALK, null, at(may3, 9))
        repo.addActivity(500, ActivityCategory.WALK, null, at(may3, 10))
        // Moving 600 minutes to May 3 (already 1,000) would exceed 1,440.
        assertEquals(SaveResult.DailyLimitExceeded(1000), repo.updateActivity(id, 600, ActivityCategory.WALK, null, at(may3, 11)))
        assertEquals(1200L, repo.activityTotal(may4))
    }

    @Test fun movingRecordsUpdatesBothDays() = runTest {
        val w = repo.addWater(400, time.zonedNow())
        repo.updateWater(w, 450, at(may3, 20))
        assertEquals(0L, repo.waterTotal(may4))
        assertEquals(450L, repo.waterTotal(may3))
        assertEquals("20:00:00", waterDao.rows[w]!!.loggedLocalTime)
        val a = (repo.addActivity(30, ActivityCategory.WALK, "park", at(may4, 7)) as SaveResult.Saved).id
        repo.updateActivity(a, 35, ActivityCategory.EXERCISE, null, at(may3, 7))
        assertEquals(0L, repo.activityTotal(may4))
        assertEquals(35L, repo.activityTotal(may3))
        val moved = repo.getActivity(a)!!
        assertEquals(ActivityCategory.EXERCISE, moved.category)
        assertNull(moved.label)
        val week = repo.observeWeek(may4).first()
        assertEquals(450L, week.days.first { it.date == may3 }.waterMl)
        assertEquals(0L, week.days.first { it.date == may4 }.waterMl)
    }

    @Test fun deleteAndRestoreExactRecordOnce() = runTest {
        val w = repo.addWater(300, time.zonedNow())
        val before = waterDao.rows[w]!!
        val removed = repo.deleteWater(w)
        assertNotNull(removed)
        assertEquals(0L, repo.waterTotal(may4))
        assertTrue(repo.restoreWater(removed!!))
        assertEquals(before, waterDao.rows[w])
        assertFalse(repo.restoreWater(removed))

        val a = (repo.addActivity(20, ActivityCategory.STRETCH, "yoga", at(may4, 7)) as SaveResult.Saved).id
        val beforeA = activityDao.rows[a]!!
        val removedA = repo.deleteActivity(a)!!
        assertEquals(SaveResult.Saved(a), repo.restoreActivity(removedA))
        assertEquals(beforeA, activityDao.rows[a])
        assertEquals(SaveResult.NotFound, repo.restoreActivity(removedA))
    }

    @Test fun restoringActivityRespectsTheLimit() = runTest {
        val a = (repo.addActivity(600, ActivityCategory.WALK, null, at(may4, 7)) as SaveResult.Saved).id
        val removed = repo.deleteActivity(a)!!
        repo.addActivity(600, ActivityCategory.WALK, null, at(may4, 8))
        repo.addActivity(600, ActivityCategory.WALK, null, at(may4, 9))
        assertEquals(SaveResult.DailyLimitExceeded(1200), repo.restoreActivity(removed))
    }

    @Test fun undoTokensWorkExactlyOnce() = runTest {
        val feedback = FeedbackCenter { repo }
        val id = repo.addWater(250, time.zonedNow())
        feedback.showUndoable("250 mL water recorded.", UndoAction.RemoveAddedWater(id))
        val msg = feedback.messages.first()
        assertNotNull(msg.undoToken)
        feedback.undo(msg.undoToken!!)
        assertEquals(0L, repo.waterTotal(may4))
        val id2 = repo.addWater(100, time.zonedNow())
        feedback.undo(msg.undoToken!!) // second undo with the same token does nothing
        assertNotNull(repo.getWater(id2))
    }

    @Test fun travelDoesNotMoveSavedRecords() = runTest {
        repo.addWater(500, time.zonedNow()) // 23:30 on May 4 in Berlin
        time.zoneId = ZoneId.of("America/New_York")
        time.instant = time.instant.plusSeconds(8 * 3600)
        assertEquals(500L, repo.waterTotal(may4))
        assertEquals(0L, repo.waterTotal(may4.plusDays(1)))
        assertEquals("Europe/Berlin", repo.observeWater(may4).first().single().zoneId.id)
    }

    @Test fun todayRefreshesAtMidnightAndOnZoneChange() = runTest {
        val scope = TestScope()
        val clock = TodayClock(time, scope)
        assertEquals(may4, clock.today.value)
        time.instant = time.instant.plusSeconds(3600) // 00:30 May 5 Berlin
        clock.refresh()
        assertEquals(may4.plusDays(1), clock.today.value)
        time.zoneId = ZoneId.of("America/Los_Angeles") // still May 4 there
        clock.refresh()
        assertEquals(may4, clock.today.value)
        scope.cancel()
    }

    @Test fun clearAllRemovesEveryRecord() = runTest {
        repo.addWater(100, time.zonedNow())
        repo.addActivity(10, ActivityCategory.WALK, null, at(may4, 8))
        repo.clearAll()
        assertEquals(0L, repo.waterTotal(may4))
        assertEquals(0L, repo.activityTotal(may4))
    }
}

package org.schabi.newpipe.zen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.zen.ZenSettingKeys.DAILY_CAP
import org.schabi.newpipe.zen.ZenSettingKeys.FEED_BUDGET
import org.schabi.newpipe.zen.ZenSettingKeys.MIN_LENGTH_MIN
import org.schabi.newpipe.zen.ZenSettingKeys.PAUSE_ENABLED
import org.schabi.newpipe.zen.ZenSettingKeys.PAUSE_MAX_MIN
import org.schabi.newpipe.zen.ZenSettingKeys.PAUSE_MIN_MIN
import org.schabi.newpipe.zen.ZenSettingKeys.REDUCED_DETAILS

class ZenSettingsLockTest {
    private val h = 60 * 60 * 1000L
    private val min = 60 * 1000L

    private class Env {
        var mono = 1_000_000L
        var boot = 1
        val store = MemoryZenStore()
        val events = ArrayList<String>()

        fun lock(): ZenSettingsLock {
            fun cd(n: String) = ZenCountdown(store, n, { mono }, { boot })
            return ZenSettingsLock(store, cd("l"), cd("u"), cd("s"), { t, d -> events.add("$t $d") })
        }
    }

    /** Env with a finished first-time setup (lock of the setup already expired). */
    private fun ready(): Pair<Env, ZenSettingsLock> {
        val e = Env()
        val l = e.lock()
        assertTrue(l.isSetupMode())
        l.completeSetup()
        e.mono += 25 * h
        assertFalse(l.isLocked())
        return e to l
    }

    @Test
    fun `defaults equal the former constants`() {
        val l = Env().lock()
        assertEquals(15, l.effective(FEED_BUDGET))
        assertEquals(30, l.effective(DAILY_CAP))
        assertEquals(5, l.effective(MIN_LENGTH_MIN))
        assertEquals(20, l.effective(ZenSettingKeys.PAUSE_PERCENT))
        assertEquals(1, l.effective(PAUSE_MIN_MIN))
        assertEquals(15, l.effective(PAUSE_MAX_MIN))
        assertEquals(5, l.effective(ZenSettingKeys.PAUSE_TRIGGER_MIN))
        assertEquals(5, l.effective(ZenSettingKeys.INTENT_WAIT_S))
        assertEquals(5, l.effective(ZenSettingKeys.INTENT_BACKGROUND_MIN))
        assertEquals(1, l.effective(PAUSE_ENABLED))
        assertEquals(1, l.effective(REDUCED_DETAILS))
        ZenSettingDefs.all.forEach { assertTrue(it.key, it.inRange(it.default)) }
    }

    @Test
    fun `setup mode changes are free and start no lock`() {
        val e = Env()
        val l = e.lock()
        val r = l.apply(mapOf(FEED_BUDGET to 10, MIN_LENGTH_MIN to 3))
        assertTrue((r as ZenSettingsLock.Result.Applied).pending.isEmpty())
        assertEquals(10, l.effective(FEED_BUDGET))
        assertEquals(3, l.effective(MIN_LENGTH_MIN)) // looser, but immediate in setup
        assertFalse(l.isLocked())
        assertEquals(0L, l.undoRemainingMs())
        // a second change is still free
        l.apply(mapOf(MIN_LENGTH_MIN to 1))
        assertEquals(1, l.effective(MIN_LENGTH_MIN))
    }

    @Test
    fun `done ends the setup and starts the lock`() {
        val e = Env()
        val l = e.lock()
        l.completeSetup()
        assertFalse(l.isSetupMode())
        assertTrue(l.isLocked())
        assertEquals(ZenSettingsRules.LOCK_MS, l.snapshot().lockRemainingMs)
    }

    @Test
    fun `a never finished setup ends after the maximum time`() {
        val e = Env()
        val l = e.lock()
        assertTrue(l.isSetupMode())
        e.mono += ZenSettingsRules.SETUP_MAX_MS - min
        assertTrue(l.isSetupMode())
        e.mono += 2 * min
        assertFalse(l.isSetupMode())
        assertTrue(l.isLocked())
        // changes are no longer free
        assertEquals(
            ZenSettingsLock.Reason.LOCKED,
            (l.apply(mapOf(MIN_LENGTH_MIN to 2)) as ZenSettingsLock.Result.Rejected).reason
        )
    }

    @Test
    fun `first playback ends the setup`() {
        val e = Env()
        val l = e.lock()
        l.onPlaybackStarted()
        assertFalse(l.isSetupMode())
        assertTrue(l.isLocked())
    }

    @Test
    fun `setup time does not run while the device is off`() {
        val e = Env()
        val l = e.lock()
        assertTrue(l.isSetupMode())
        e.boot = 2
        e.mono = 10 * min // new boot, 10 min uptime; weeks may have passed in wall time
        assertTrue(l.isSetupMode())
    }

    @Test
    fun `stricter takes effect at once and starts no lock`() {
        val (_, l) = ready()
        val r = l.apply(mapOf(FEED_BUDGET to 10, MIN_LENGTH_MIN to 10)) as ZenSettingsLock.Result.Applied
        assertEquals(setOf(FEED_BUDGET, MIN_LENGTH_MIN), r.immediate.toSet())
        assertTrue(r.pending.isEmpty())
        assertFalse(r.lockStarted)
        assertEquals(10, l.effective(FEED_BUDGET))
        assertFalse(l.isLocked())
        // and again right away: not locked
        assertTrue(l.apply(mapOf(FEED_BUDGET to 8)) is ZenSettingsLock.Result.Applied)
    }

    @Test
    fun `looser becomes pending, starts the lock and takes effect after 24 hours`() {
        val (e, l) = ready()
        l.apply(mapOf(FEED_BUDGET to 10))
        val r = l.apply(mapOf(FEED_BUDGET to 14)) as ZenSettingsLock.Result.Applied
        assertEquals(listOf(FEED_BUDGET), r.pending)
        assertTrue(r.lockStarted)
        assertEquals(10, l.effective(FEED_BUDGET))
        assertEquals(mapOf(FEED_BUDGET to 14), l.pending())
        e.mono += 24 * h - min
        assertEquals(10, l.effective(FEED_BUDGET))
        e.mono += 2 * min
        assertEquals(14, l.effective(FEED_BUDGET))
        assertTrue(l.pending().isEmpty())
        assertFalse(l.isLocked())
    }

    @Test
    fun `while locked further loosening is rejected but stricter works without restarting`() {
        val (e, l) = ready()
        l.apply(mapOf(MIN_LENGTH_MIN to 2)) // looser, pending, lock starts
        e.mono += 10 * h
        val rej = l.apply(mapOf(DAILY_CAP to 30, PAUSE_MAX_MIN to 10)) // 10 is looser
        assertEquals(ZenSettingsLock.Reason.LOCKED, (rej as ZenSettingsLock.Result.Rejected).reason)
        assertEquals(15, l.effective(PAUSE_MAX_MIN)) // nothing of the group applied
        val before = l.snapshot().lockRemainingMs
        val ok = l.apply(mapOf(DAILY_CAP to 20, PAUSE_MAX_MIN to 20)) as ZenSettingsLock.Result.Applied
        assertFalse(ok.lockStarted)
        assertEquals(20, l.effective(DAILY_CAP))
        assertEquals(before, l.snapshot().lockRemainingMs) // not extended, not restarted
    }

    @Test
    fun `mixed group stricter part immediate looser part pending`() {
        val (_, l) = ready()
        l.apply(mapOf(FEED_BUDGET to 10))
        val r = l.apply(mapOf(FEED_BUDGET to 12, MIN_LENGTH_MIN to 10)) as ZenSettingsLock.Result.Applied
        assertEquals(listOf(MIN_LENGTH_MIN), r.immediate)
        assertEquals(listOf(FEED_BUDGET), r.pending)
        assertEquals(10, l.effective(MIN_LENGTH_MIN))
        assertEquals(10, l.effective(FEED_BUDGET))
    }

    @Test
    fun `turning the mandatory pause off is a loosening`() {
        val (e, l) = ready()
        val r = l.apply(mapOf(PAUSE_ENABLED to 0)) as ZenSettingsLock.Result.Applied
        assertEquals(listOf(PAUSE_ENABLED), r.pending)
        assertEquals(1, l.effective(PAUSE_ENABLED))
        e.mono += 25 * h
        assertEquals(0, l.effective(PAUSE_ENABLED))
        // turning it on again is stricter and immediate
        assertTrue(l.apply(mapOf(PAUSE_ENABLED to 1)) is ZenSettingsLock.Result.Applied)
        assertEquals(1, l.effective(PAUSE_ENABLED))
    }

    @Test
    fun `reduced video details are on by default and turning them off is a loosening`() {
        ZenSettings.install(null)
        assertTrue(ZenSettings.reducedVideoDetails())
        val (e, l) = ready()
        ZenSettings.install(l)
        try {
            val r = l.apply(mapOf(REDUCED_DETAILS to 0)) as ZenSettingsLock.Result.Applied
            assertEquals(listOf(REDUCED_DETAILS), r.pending)
            assertTrue(l.isLocked())
            assertTrue(ZenSettings.reducedVideoDetails())
            // undo within 5 min restores the state without a lock
            assertTrue(l.undo())
            assertTrue(l.pending().isEmpty())
            assertFalse(l.isLocked())
            l.apply(mapOf(REDUCED_DETAILS to 0))
            e.mono += 23 * h
            assertTrue(ZenSettings.reducedVideoDetails())
            e.mono += 2 * h
            assertFalse(ZenSettings.reducedVideoDetails())
            // turning it on again is stricter: immediate, even while a new lock would run
            assertTrue(l.apply(mapOf(REDUCED_DETAILS to 1)) is ZenSettingsLock.Result.Applied)
            assertTrue(ZenSettings.reducedVideoDetails())
            assertTrue(l.pending().isEmpty())
        } finally {
            ZenSettings.install(null)
        }
    }

    @Test
    fun `cancelling a pending loosening keeps the lock`() {
        val (e, l) = ready()
        l.apply(mapOf(MIN_LENGTH_MIN to 2))
        e.mono += 2 * h
        assertTrue(l.cancelPending())
        assertTrue(l.pending().isEmpty())
        assertEquals(5, l.effective(MIN_LENGTH_MIN))
        assertTrue(l.isLocked())
        e.mono += 30 * h
        assertEquals(5, l.effective(MIN_LENGTH_MIN)) // nothing comes back later
        assertFalse(l.cancelPending())
    }

    @Test
    fun `setting a pending value back to the effective one cancels it`() {
        val (_, l) = ready()
        l.apply(mapOf(MIN_LENGTH_MIN to 2))
        assertEquals(ZenSettingsLock.Kind.CANCEL_PENDING, l.classify(MIN_LENGTH_MIN, 5))
        assertTrue(l.apply(mapOf(MIN_LENGTH_MIN to 5)) is ZenSettingsLock.Result.Applied)
        assertTrue(l.pending().isEmpty())
    }

    @Test
    fun `stricter on a setting with a pending loosening drops the pending one`() {
        val (e, l) = ready()
        l.apply(mapOf(MIN_LENGTH_MIN to 2))
        l.apply(mapOf(MIN_LENGTH_MIN to 8))
        assertTrue(l.pending().isEmpty())
        e.mono += 30 * h
        assertEquals(8, l.effective(MIN_LENGTH_MIN))
    }

    @Test
    fun `undo restores the exact previous state and the lock`() {
        val (e, l) = ready()
        l.apply(mapOf(DAILY_CAP to 20))
        l.apply(mapOf(DAILY_CAP to 25, MIN_LENGTH_MIN to 10)) // 25 looser (pending), 10 stricter
        assertTrue(l.isLocked())
        e.mono += 4 * min
        assertTrue(l.undo())
        assertEquals(20, l.effective(DAILY_CAP))
        assertEquals(5, l.effective(MIN_LENGTH_MIN))
        assertTrue(l.pending().isEmpty())
        assertFalse(l.isLocked()) // the change had started the lock
        assertFalse(l.undo()) // only once
    }

    @Test
    fun `undo of a stricter change restores the old value and starts no lock`() {
        val (_, l) = ready()
        l.apply(mapOf(DAILY_CAP to 15)) // typo-like strict change
        assertEquals(15, l.effective(DAILY_CAP))
        assertTrue(l.undo())
        assertEquals(30, l.effective(DAILY_CAP))
        assertFalse(l.isLocked())
    }

    @Test
    fun `undo window ends after 5 minutes`() {
        val (e, l) = ready()
        l.apply(mapOf(FEED_BUDGET to 10))
        e.mono += 5 * min + 1000
        assertFalse(l.undo())
        assertEquals(10, l.effective(FEED_BUDGET))
    }

    @Test
    fun `undo does not touch an older running lock`() {
        val (e, l) = ready()
        l.apply(mapOf(MIN_LENGTH_MIN to 2)) // lock starts
        e.mono += 6 * min
        l.apply(mapOf(FEED_BUDGET to 10)) // stricter during the lock
        assertTrue(l.undo())
        assertEquals(15, l.effective(FEED_BUDGET))
        assertTrue(l.isLocked())
        assertEquals(mapOf(MIN_LENGTH_MIN to 2), l.pending())
    }

    @Test
    fun `plausibility rules`() {
        val (_, l) = ready()
        assertEquals(
            ZenSettingsLock.Reason.OUT_OF_RANGE,
            (l.apply(mapOf(FEED_BUDGET to 4)) as ZenSettingsLock.Result.Rejected).reason
        )
        assertEquals(
            ZenSettingsLock.Reason.OUT_OF_RANGE,
            (l.apply(mapOf(ZenSettingKeys.PAUSE_PERCENT to 22)) as ZenSettingsLock.Result.Rejected).reason
        )
        assertEquals(
            ZenSettingsLock.Reason.UNKNOWN_KEY,
            (l.apply(mapOf("nope" to 1)) as ZenSettingsLock.Result.Rejected).reason
        )
        // cap below the budget
        assertEquals(
            ZenSettingsLock.Reason.INVALID_COMBINATION,
            (l.apply(mapOf(DAILY_CAP to 10)) as ZenSettingsLock.Result.Rejected).reason
        )
        l.apply(mapOf(FEED_BUDGET to 10, DAILY_CAP to 10))
        // a pending loosening of the budget must not exceed the cap either
        assertEquals(
            ZenSettingsLock.Reason.INVALID_COMBINATION,
            (l.apply(mapOf(FEED_BUDGET to 12)) as ZenSettingsLock.Result.Rejected).reason
        )
        assertTrue(l.pending().isEmpty())
        assertEquals(ZenSettingsLock.Result.NoChange, l.apply(mapOf(DAILY_CAP to 10)))
    }

    @Test
    fun `lower cap than budget is rejected, atomically`() {
        val (_, l) = ready()
        val r = l.apply(mapOf(DAILY_CAP to 10, MIN_LENGTH_MIN to 9))
        assertTrue(r is ZenSettingsLock.Result.Rejected)
        assertEquals(30, l.effective(DAILY_CAP))
        assertEquals(5, l.effective(MIN_LENGTH_MIN))
    }

    @Test
    fun `the lock only counts device uptime across a reboot`() {
        val (e, l) = ready()
        l.apply(mapOf(MIN_LENGTH_MIN to 2))
        e.mono += 20 * h
        l.snapshot() // looked at 4 h before the end (progress is stored when looked at)
        // reboot: the new uptime is only 10 min; the 2 days the device was off do not count
        e.boot = 2
        e.mono = 10 * min
        val left = l.snapshot().lockRemainingMs
        assertEquals(4 * h - 10 * min, left)
        assertEquals(5, l.effective(MIN_LENGTH_MIN))
        e.mono += 4 * h
        assertEquals(2, l.effective(MIN_LENGTH_MIN))
    }

    @Test
    fun `state survives a new instance (process restart in the same boot)`() {
        val (e, l) = ready()
        l.apply(mapOf(MIN_LENGTH_MIN to 2))
        val l2 = e.lock()
        assertTrue(l2.isLocked())
        assertEquals(mapOf(MIN_LENGTH_MIN to 2), l2.pending())
        assertTrue(l2.undoRemainingMs() > 0)
    }

    @Test
    fun `changes are logged`() {
        val (e, l) = ready()
        l.apply(mapOf(MIN_LENGTH_MIN to 2))
        l.undo()
        assertTrue(e.events.any { it.startsWith("settings_change") })
        assertTrue(e.events.any { it.startsWith("settings_undo") })
    }

    @Test
    fun `installed settings feed the rules`() {
        val (_, l) = ready()
        ZenSettings.install(l)
        try {
            l.apply(mapOf(MIN_LENGTH_MIN to 10, ZenSettingKeys.PAUSE_PERCENT to 40))
            assertEquals(600, ZenSettings.minLengthSec())
            assertEquals(4 * min, ZenPauseRules.pauseLengthMs(10 * min))
        } finally {
            ZenSettings.install(null)
        }
        assertEquals(300, ZenSettings.minLengthSec())
        assertEquals(2 * min, ZenPauseRules.pauseLengthMs(10 * min))
    }
}

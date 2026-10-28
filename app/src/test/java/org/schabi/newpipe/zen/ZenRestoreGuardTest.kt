package org.schabi.newpipe.zen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.zen.ZenSettingKeys.FEED_BUDGET
import org.schabi.newpipe.zen.ZenSettingKeys.FEED_LANGS
import org.schabi.newpipe.zen.ZenSettingKeys.MIN_LENGTH_MIN
import org.schabi.newpipe.zen.ZenSettingKeys.PAUSE_ENABLED

class ZenRestoreGuardTest {
    private val h = 60 * 60 * 1000L

    /** One device: [store] survives process restarts, [mono]/[boot] model uptime and reboots. */
    private class Env {
        var mono = 1_000_000L
        var boot = 1
        val store = MemoryZenStore()
        val events = ArrayList<String>()
        var ids = 0
        lateinit var lock: ZenSettingsLock
        lateinit var guard: ZenRestoreGuard

        init {
            restartProcess()
        }

        fun restartProcess() {
            fun cd(n: String) = ZenCountdown(store, n, { mono }, { boot })
            val log: (String, String) -> Unit = { t, d -> events.add("$t $d") }
            lock = ZenSettingsLock(store, cd("l"), cd("u"), cd("s"), log)
            guard = ZenRestoreGuard(store, lock, { "id${++ids}" }, log)
        }

        fun reboot() {
            boot++
            mono = 5_000L
            restartProcess()
        }

        fun restoreEvents() = events.filter { it.startsWith("settings_restore_detected") }
    }

    @Test
    fun `fresh install without data keeps the free setup`() {
        val e = Env()
        val s = e.guard.onAppStart(null)
        assertTrue(s.probe)
        assertEquals("id1", s.marker)
        e.guard.onProbeResult(0)
        assertTrue(e.lock.isSetupMode())
        // loosening in the setup is still immediate
        val r = e.lock.apply(mapOf(PAUSE_ENABLED to 0)) as ZenSettingsLock.Result.Applied
        assertTrue(r.setupMode)
        assertEquals(0, e.lock.effective(PAUSE_ENABLED))
        assertTrue(e.restoreEvents().isEmpty())
        assertFalse(e.guard.showNotice(0))
    }

    @Test
    fun `data in the database at the very first start ends the setup strictly`() {
        val e = Env()
        assertTrue(e.guard.onAppStart(null).probe)
        e.guard.onProbeResult(42)
        assertFalse(e.lock.isSetupMode())
        assertTrue(e.lock.isLocked())
        assertEquals(listOf("settings_restore_detected reason=data"), e.restoreEvents())
        assertTrue(e.events.contains("settings_setup_done reason=restore"))
        // stricter stays immediate, loosening is rejected while the lock runs
        assertTrue(e.lock.apply(mapOf(MIN_LENGTH_MIN to 10)) is ZenSettingsLock.Result.Applied)
        assertEquals(10, e.lock.effective(MIN_LENGTH_MIN))
        val loose = e.lock.apply(mapOf(PAUSE_ENABLED to 0))
        assertTrue(loose is ZenSettingsLock.Result.Rejected)
        assertEquals(1, e.lock.effective(PAUSE_ENABLED))
        assertTrue(e.guard.showNotice(e.lock.snapshot().lockRemainingMs))
    }

    @Test
    fun `import during the setup ends it and moves setup loosenings to pending`() {
        val e = Env()
        e.guard.onAppStart(null)
        e.guard.onProbeResult(0)
        assertTrue(e.lock.isSetupMode())
        // free setup: loosen first, then import (the bypass order)
        e.lock.apply(mapOf(PAUSE_ENABLED to 0, MIN_LENGTH_MIN to 1, FEED_BUDGET to 10))
        e.lock.addPraxis(ZenPraxisChannel(0, "https://example.org/c", "Yoga"))
        e.guard.onImportFinished()
        assertFalse(e.lock.isSetupMode())
        assertEquals(listOf("settings_restore_detected reason=import"), e.restoreEvents())
        // loosened values wait 24 h, stricter ones stay
        assertEquals(1, e.lock.effective(PAUSE_ENABLED))
        assertEquals(5, e.lock.effective(MIN_LENGTH_MIN))
        assertEquals(10, e.lock.effective(FEED_BUDGET))
        assertEquals(mapOf(MIN_LENGTH_MIN to 1, PAUSE_ENABLED to 0), e.lock.pending())
        assertTrue(e.lock.praxisChannels().isEmpty())
        assertEquals(1, e.lock.pendingPraxis().size)
        assertEquals(0, e.lock.undoRemainingMs())
        // after 24 h of uptime they take effect like any loosening
        e.mono += 24 * h + 1
        assertEquals(0, e.lock.effective(PAUSE_ENABLED))
        assertEquals(1, e.lock.effective(MIN_LENGTH_MIN))
        assertEquals(1, e.lock.praxisChannels().size)
        assertFalse(e.guard.showNotice(e.lock.snapshot().lockRemainingMs))
    }

    @Test
    fun `mask loosening keeps the stricter common part`() {
        val e = Env()
        e.guard.onAppStart(null)
        e.guard.onProbeResult(0)
        val def = ZenSettingDefs.get(FEED_LANGS)!!.default
        val first = Integer.lowestOneBit(def)
        val other = (ZenLangOptions.ALL_MASK and def.inv()).let { Integer.lowestOneBit(it) }
        e.lock.apply(mapOf(FEED_LANGS to (first or other)))
        e.guard.onImportFinished()
        assertEquals(first, e.lock.effective(FEED_LANGS))
        assertEquals(first or other, e.lock.pending()[FEED_LANGS])
    }

    @Test
    fun `subscribing during the setup does not trigger`() {
        val e = Env()
        e.guard.onAppStart(null)
        e.guard.onProbeResult(0)
        // the user subscribes, the app restarts: the probe does not run again
        e.restartProcess()
        val s = e.guard.onAppStart("id1")
        assertFalse(s.probe)
        e.guard.onProbeResult(7)
        assertTrue(e.lock.isSetupMode())
        assertTrue(e.restoreEvents().isEmpty())
    }

    @Test
    fun `a probe that never finished runs again at the next start`() {
        val e = Env()
        assertTrue(e.guard.onAppStart(null).probe)
        e.restartProcess()
        assertTrue(e.guard.onAppStart("id1").probe)
        e.guard.onProbeResult(3)
        assertFalse(e.lock.isSetupMode())
    }

    @Test
    fun `foreign marker in the default preferences is a restore`() {
        val e = Env()
        e.guard.onAppStart("old-install")
        assertFalse(e.lock.isSetupMode())
        assertEquals(listOf("settings_restore_detected reason=marker"), e.restoreEvents())
        // own marker is written back; the late probe result changes nothing
        assertEquals("id1", e.guard.onAppStart("id1").marker)
        e.guard.onProbeResult(5)
        assertEquals(1, e.restoreEvents().size)
    }

    @Test
    fun `finished setup stays unchanged`() {
        val e = Env()
        e.guard.onAppStart(null)
        e.guard.onProbeResult(0)
        assertTrue(e.lock.isSetupMode())
        e.lock.completeSetup()
        e.mono += 25 * h
        assertFalse(e.lock.isLocked())
        e.guard.onImportFinished()
        e.restartProcess()
        e.guard.onAppStart("foreign")
        assertFalse(e.lock.isLocked())
        assertTrue(e.restoreEvents().isEmpty())
        assertFalse(e.guard.showNotice(0))
    }

    @Test
    fun `existing install from before the guard is no fresh start`() {
        val e = Env()
        // setup was started by an older version (no install id yet)
        assertTrue(e.lock.isSetupMode())
        e.restartProcess()
        val s = e.guard.onAppStart(null)
        assertFalse(s.probe)
        assertTrue(e.lock.isSetupMode())
    }

    @Test
    fun `restore lock survives restart and reboot, no second detection`() {
        val e = Env()
        e.guard.onAppStart(null)
        e.guard.onProbeResult(1)
        val before = e.lock.snapshot().lockRemainingMs
        e.mono += h
        e.reboot()
        e.guard.onAppStart("id1")
        e.guard.onImportFinished()
        assertFalse(e.lock.isSetupMode())
        val after = e.lock.snapshot().lockRemainingMs
        assertTrue(after in (before - h - 1)..before)
        assertTrue(e.guard.showNotice(after))
        assertEquals(1, e.restoreEvents().size)
    }

    @Test
    fun `setup that ran out is not a restore`() {
        val e = Env()
        e.guard.onAppStart(null)
        e.guard.onProbeResult(0)
        assertTrue(e.lock.isSetupMode())
        e.mono += 25 * h
        e.guard.onImportFinished()
        assertTrue(e.restoreEvents().isEmpty())
        assertTrue(e.events.contains("settings_setup_done reason=timeout"))
    }
}

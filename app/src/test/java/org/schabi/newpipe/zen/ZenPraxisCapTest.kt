package org.schabi.newpipe.zen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Soft practice cap (D61): when the calm question is due, and the setting's lock direction. */
class ZenPraxisCapTest {
    private val min = 60_000L

    @Test
    fun `due from the threshold, once per day and threshold`() {
        assertFalse(ZenPraxisCap.due(89 * min, 90, "2026-10-20", null))
        assertTrue(ZenPraxisCap.due(90 * min, 90, "2026-10-20", null))
        assertFalse(ZenPraxisCap.due(120 * min, 90, "2026-10-20", ZenPraxisCap.askedValue("2026-10-20", 90)))
        // next Zen day, or another threshold the same day: asked again
        assertTrue(ZenPraxisCap.due(120 * min, 90, "2026-10-21", ZenPraxisCap.askedValue("2026-10-20", 90)))
        assertTrue(ZenPraxisCap.due(60 * min, 60, "2026-10-20", ZenPraxisCap.askedValue("2026-10-20", 90)))
        // off
        assertFalse(ZenPraxisCap.due(1000 * min, ZenPraxisCap.OFF, "2026-10-20", null))
    }

    @Test
    fun `lower threshold applies at once, higher or off is a loosening`() {
        val def = ZenSettingDefs.get(ZenSettingKeys.PRAXIS_CAP_MIN)!!
        assertEquals(90, def.default)
        assertTrue(def.inRange(30) && def.inRange(240) && def.inRange(ZenPraxisCap.OFF))
        assertFalse(def.inRange(0) || def.inRange(100))
        assertTrue(def.isStricter(90, 60))
        assertFalse(def.isStricter(90, 120))
        assertFalse(def.isStricter(240, ZenPraxisCap.OFF))

        val store = MemoryZenStore()
        var mono = 1_000_000L
        fun cd(n: String) = ZenCountdown(store, n, { mono }, { 1 })
        val lock = ZenSettingsLock(store, cd("l"), cd("u"), cd("s"))
        lock.completeSetup()
        mono += 25 * 60 * min
        val lower = lock.apply(mapOf(ZenSettingKeys.PRAXIS_CAP_MIN to 60)) as ZenSettingsLock.Result.Applied
        assertFalse(lower.lockStarted)
        assertEquals(60, lock.effective(ZenSettingKeys.PRAXIS_CAP_MIN))
        val off = lock.apply(mapOf(ZenSettingKeys.PRAXIS_CAP_MIN to ZenPraxisCap.OFF)) as ZenSettingsLock.Result.Applied
        assertTrue(off.lockStarted)
        assertEquals(60, lock.effective(ZenSettingKeys.PRAXIS_CAP_MIN)) // pending for 24 h
    }
}

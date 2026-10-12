package org.schabi.newpipe.zen

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZenTimeTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private fun ms(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0) = ZonedDateTime.of(y, mo, d, h, mi, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun `zen day changes at 4 am`() {
        assertEquals(LocalDate.of(2026, 10, 11), ZenDay.dayOf(ms(2026, 10, 12, 3, 59), zone))
        assertEquals(LocalDate.of(2026, 10, 12), ZenDay.dayOf(ms(2026, 10, 12, 4, 0), zone))
        assertEquals(LocalDate.of(2026, 10, 12), ZenDay.dayOf(ms(2026, 10, 12, 23, 59), zone))
    }

    @Test
    fun `hour boundaries`() {
        assertEquals(ms(2026, 10, 12, 14), ZenDay.hourStart(ms(2026, 10, 12, 14, 37), zone))
        assertEquals(ms(2026, 10, 12, 15), ZenDay.nextHourStart(ms(2026, 10, 12, 14, 37), zone))
        assertEquals(ms(2026, 10, 13, 0), ZenDay.nextHourStart(ms(2026, 10, 12, 23, 5), zone))
    }

    @Test
    fun `next day start is 4 am`() {
        assertEquals(ms(2026, 10, 12, 4), ZenDay.nextDayStart(ms(2026, 10, 12, 1, 0), zone))
        assertEquals(ms(2026, 10, 13, 4), ZenDay.nextDayStart(ms(2026, 10, 12, 20, 0), zone))
    }

    private class Fake(var wall: Long, var mono: Long)

    private fun clock(f: Fake, store: ZenKeyValueStore = MemoryZenStore()) = ZenClock(store, { f.wall }, { f.mono })

    @Test
    fun `normal time passing is not tampering`() {
        val f = Fake(1_000_000_000L, 5_000L)
        val c = clock(f)
        assertFalse(c.now().tampered)
        f.wall += 60_000
        f.mono += 60_500
        val r = c.now()
        assertFalse(r.tampered)
        assertEquals(f.wall, r.trustedMillis)
    }

    @Test
    fun `setting the clock forward is detected and ignored`() {
        val f = Fake(1_000_000_000L, 5_000L)
        val c = clock(f)
        c.now()
        f.mono += 10_000
        f.wall += 10_000 + 3_600_000 // +1h jump
        val r = c.now()
        assertTrue(r.tampered)
        assertEquals(1_000_000_000L + 10_000, r.trustedMillis)
    }

    @Test
    fun `setting the clock back is detected`() {
        val f = Fake(1_000_000_000L, 5_000L)
        val c = clock(f)
        c.now()
        f.mono += 10_000
        f.wall -= 3_600_000
        assertTrue(c.now().tampered)
    }

    @Test
    fun `reboot with later wall time is fine, earlier is tampering`() {
        val store = MemoryZenStore()
        val f = Fake(1_000_000_000L, 500_000L)
        clock(f, store).now()
        f.mono = 1_000L // rebooted
        f.wall = 1_000_000_000L + 3_600_000
        assertFalse(clock(f, store).now().tampered)
        f.mono = 1_000L
        f.wall = 1_000_000_000L - 3_600_000
        val r = clock(f, store).now()
        assertTrue(r.tampered)
        assertEquals(1_000_000_000L + 3_600_000, r.trustedMillis)
    }

    @Test
    fun `reboot detected by boot counter even if uptime is larger than before`() {
        val store = MemoryZenStore()
        val f = Fake(1_000_000_000L, 60_000L) // anchored 1 min after boot
        var boot = 7
        fun c() = ZenClock(store, { f.wall }, { f.mono }, null, { boot })
        assertFalse(c().now().tampered)
        // device restarted; the clock ran on normally, 3 h later the new uptime is 5 min
        boot = 8
        f.mono = 300_000L
        f.wall = 1_000_000_000L + 3 * 3_600_000L
        val r = c().now()
        assertTrue(r.rebooted)
        assertFalse(r.tampered)
        assertEquals(f.wall, r.trustedMillis)
    }

    @Test
    fun `same boot with changed clock stays tampering even with boot counter`() {
        val store = MemoryZenStore()
        val f = Fake(1_000_000_000L, 60_000L)
        fun c() = ZenClock(store, { f.wall }, { f.mono }, null, { 7 })
        c().now()
        f.mono += 10_000
        f.wall += 10_000 + 3_600_000
        val r = c().now()
        assertTrue(r.tampered)
        assertFalse(r.rebooted)
    }

    @Test
    fun `reboot with large forward gap is logged`() {
        val store = MemoryZenStore()
        val f = Fake(1_000_000_000L, 60_000L)
        val logged = ArrayList<String>()
        var boot = 1
        fun c() = ZenClock(store, { f.wall }, { f.mono }, { t, _ -> logged.add(t) }, { boot })
        c().now()
        boot = 2
        f.mono = 5_000L
        f.wall += 20 * 3_600_000L
        c().now()
        assertTrue(logged.contains("clock_reboot_gap"))
    }

    @Test
    fun `countdown shrinks with uptime within one boot`() {
        val store = MemoryZenStore()
        val f = Fake(0, 10_000L)
        val cd = ZenCountdown(store, "t", { f.mono }, { 1 })
        cd.start(60_000)
        f.mono += 20_000
        assertEquals(40_000L, cd.remainingMs())
        f.mono += 50_000
        assertEquals(0L, cd.remainingMs())
        assertFalse(cd.isRunning())
    }

    @Test
    fun `countdown ignores the wall clock and the time the device was off`() {
        val store = MemoryZenStore()
        val f = Fake(1_000_000_000L, 100_000L)
        var boot = 1
        val cd = { ZenCountdown(store, "t", { f.mono }, { boot }) }
        cd().start(10 * 60_000L)
        f.mono += 60_000
        assertEquals(9 * 60_000L, cd().remainingMs())
        // clock set 1 day forward, device restarted: only the new uptime counts
        f.wall += 24 * 3_600_000L
        boot = 2
        f.mono = 30_000L
        assertEquals(9 * 60_000L - 30_000L, cd().remainingMs())
        f.mono += 60_000
        assertEquals(9 * 60_000L - 90_000L, cd().remainingMs())
    }

    @Test
    fun `countdown without boot counter detects reboot by uptime going back`() {
        val store = MemoryZenStore()
        val f = Fake(0, 500_000L)
        val cd = ZenCountdown(store, "t", { f.mono })
        cd.start(5 * 60_000L)
        f.mono = 10_000L // reboot
        assertEquals(5 * 60_000L - 10_000L, cd.remainingMs())
    }

    @Test
    fun `log keeps order and caps entries`() {
        val f = Fake(1_000L, 1L)
        val c = clock(f)
        val log = ZenLog(MemoryZenStore(), c)
        repeat(ZenLog.MAX_ENTRIES + 10) { log.add("t", "d\t$it") }
        val e = log.entries()
        assertEquals(ZenLog.MAX_ENTRIES, e.size)
        assertEquals("d 10", e.first().detail)
        assertEquals("t", e.last().type)
    }
}

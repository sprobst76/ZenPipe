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

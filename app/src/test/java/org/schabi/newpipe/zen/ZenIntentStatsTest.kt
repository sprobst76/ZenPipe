package org.schabi.newpipe.zen

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZenIntentStatsTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private fun at(d: Int, h: Int, m: Int) = ZonedDateTime.of(2026, 10, d, h, m, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun `counts each result kind separately`() {
        val s = ZenIntentStats(MemoryZenStore(), zone)
        s.record(ZenIntentResult.CONTINUE, at(10, 12, 0))
        s.record(ZenIntentResult.CONTINUE, at(10, 13, 0))
        s.record(ZenIntentResult.NOT_NOW, at(10, 14, 0))
        s.record(ZenIntentResult.SHEET, at(10, 15, 0))
        assertEquals(
            mapOf(ZenIntentResult.CONTINUE to 2, ZenIntentResult.NOT_NOW to 1, ZenIntentResult.SHEET to 1),
            s.countsOn(at(10, 16, 0))
        )
    }

    @Test
    fun `zen day changes at 04_00`() {
        val s = ZenIntentStats(MemoryZenStore(), zone)
        s.record(ZenIntentResult.CONTINUE, at(10, 3, 59))
        s.record(ZenIntentResult.CONTINUE, at(10, 4, 0))
        assertEquals(1, s.countsOn(at(9, 12, 0))[ZenIntentResult.CONTINUE])
        assertEquals(1, s.countsOn(at(10, 12, 0))[ZenIntentResult.CONTINUE])
        assertEquals(1, s.countsOn(at(10, 3, 59))[ZenIntentResult.CONTINUE])
        assertNull(s.countsOn(at(10, 4, 0))[ZenIntentResult.NOT_NOW])
    }

    @Test
    fun `only 30 days are kept`() {
        val store = MemoryZenStore()
        val s = ZenIntentStats(store, zone)
        for (i in 0 until 35) s.record(ZenIntentResult.CONTINUE, at(1, 12, 0) + i * 86_400_000L)
        assertEquals(30, store.getString("ia_days", "")!!.split(',').size)
        assertTrue(s.countsOn(at(1, 12, 0)).isEmpty())
        assertEquals(1, s.countsOn(at(1, 12, 0) + 34 * 86_400_000L)[ZenIntentResult.CONTINUE])
    }

    @Test
    fun `acknowledge starts the cooldown and releases the feed`() {
        var now = 1_000L
        val g = ZenIntentGate { now }
        assertFalse(g.confirmed)
        assertFalse(g.isSkippable(false))
        g.decide()
        assertTrue(g.confirmed)
        now += ZenIntentGate.MIN_INTERVAL_MS - 1
        assertTrue(g.isSkippable(false)) // second prompt after "Just view" is suppressed
        now += 1
        assertFalse(g.isSkippable(false))
    }

    @Test
    fun `not now costs nothing and own playback always skips`() {
        val g = ZenIntentGate { 5L }
        g.decide()
        g.decline()
        assertFalse(g.confirmed)
        assertFalse(g.isSkippable(false))
        assertTrue(g.isSkippable(true))
    }
}

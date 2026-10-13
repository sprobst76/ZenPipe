package org.schabi.newpipe.zen

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Task B: a due edition waits for the classification of its candidates (or the limit). */
class ZenEditionPrepTest {
    private var clock = 0L
    private val queued = ArrayList<Runnable>()

    /** Runner that only queues: the test runs the work explicitly. */
    private fun prep() = ZenEditionPrep({ clock }, { queued.add(it) })

    private fun jobs(vararg scores: Int, log: MutableList<String>? = null, costMs: Long = 100, failAt: String? = null) = scores.mapIndexed { i, s ->
        ZenEditionPrep.Job("k$i", s) { _ ->
            clock += costMs
            log?.add("k$i")
            "k$i" != failAt
        }
    }

    @Test
    fun `nothing missing builds at once without a preparation`() {
        val p = prep()
        assertEquals(ZenEditionPrep.Decision.Build(null), p.decide(1000) { emptyList() })
        assertTrue(queued.isEmpty())
    }

    @Test
    fun `missing candidates are classified best score first, then the edition is built with the start time`() {
        val p = prep()
        val order = ArrayList<String>()
        val d = p.decide(5_000) { jobs(10, 50, 30, log = order) }
        assertEquals(ZenEditionPrep.Decision.Wait(0, 3), d)
        // while running the feed keeps waiting (missing is not evaluated again)
        assertEquals(ZenEditionPrep.Phase.RUNNING, p.currentPhase)
        assertTrue(p.decide(6_000) { error("not called while running") } is ZenEditionPrep.Decision.Wait)
        queued.single().run()
        assertEquals(listOf("k1", "k2", "k0"), order)
        assertEquals(ZenEditionPrep.Phase.FINISHED, p.currentPhase)
        assertTrue(p.lastRun.contains("done=3") && p.lastRun.contains("reason=complete"))
        // the edition gets the time the preparation started (hourly rhythm unchanged)
        assertEquals(ZenEditionPrep.Decision.Build(5_000), p.decide(30_000) { error("finished: no new check") })
        assertEquals(ZenEditionPrep.Phase.IDLE, p.currentPhase)
    }

    @Test
    fun `the limit ends the preparation, the rest stays unclassified`() {
        val p = prep()
        val n = (ZenEditionPrep.LIMIT_MS / 1000).toInt() + 10
        p.decide(0) { jobs(*IntArray(n) { 100 - it }, costMs = 1000) }
        queued.single().run()
        assertTrue(p.lastRun, p.lastRun.contains("reason=limit"))
        assertTrue(p.lastRun.contains("done=${ZenEditionPrep.LIMIT_MS / 1000}"))
        assertTrue(p.decide(1) { emptyList() } is ZenEditionPrep.Decision.Build)
    }

    @Test
    fun `a classification error ends the preparation`() {
        val p = prep()
        p.decide(0) { jobs(3, 2, 1, failAt = "k1") }
        queued.single().run()
        assertTrue(p.lastRun.contains("done=1") && p.lastRun.contains("reason=error"))
    }

    @Test
    fun `an outdated finished preparation starts again`() {
        val p = prep()
        p.decide(0) { jobs(1) }
        queued.removeAt(0).run()
        clock += ZenEditionPrep.STALE_MS + 1
        val d = p.decide(99) { jobs(5, 4) }
        assertEquals(ZenEditionPrep.Decision.Wait(0, 2), d)
        assertEquals(1, queued.size)
    }

    @Test
    fun `listeners hear the end`() {
        val p = prep()
        var calls = 0
        p.addListener { calls++ }
        p.decide(0) { jobs(1, 2) }
        queued.single().run()
        assertEquals(1, calls)
    }

    // ZenFeedState side: isDue and the creation time

    private val zone = ZoneId.of("Europe/Berlin")
    private val now = ZonedDateTime.of(2026, 10, 12, 14, 20, 0, 0, zone).toInstant().toEpochMilli()

    private fun many(n: Int) = (1L..n).map {
        ZenFeedPlanner.Candidate("https://y/v$it", "c$it", "Video $it", "c$it", now - 3_600_000L, 20L * 60, false, null, null)
    }

    @Test
    fun `isDue follows the edition rules and does not change state`() {
        val s = ZenFeedState(MemoryZenStore(), null, zone)
        assertTrue(s.isDue(now))
        assertFalse(s.view(now, false) { many(20) }.edition != null)
        assertTrue(s.isDue(now))
        s.view(now, true) { many(20) }
        assertFalse(s.isDue(now + 59 * 60_000L))
        assertTrue(s.isDue(now + 60 * 60_000L))
    }

    @Test
    fun `a prepared edition keeps the time the preparation started`() {
        val s = ZenFeedState(MemoryZenStore(), null, zone)
        val v = s.view(now + 15_000, true, ZenFeedPlanner.ContentRules.OFF, now) { many(20) }
        assertEquals(now, v.edition!!.createdAt)
        // next edition one hour after the start, not after the build
        assertTrue(s.isDue(now + 60 * 60_000L))
        // a creation time in the future or on another Zen day is not used
        val s2 = ZenFeedState(MemoryZenStore(), null, zone)
        assertEquals(now, s2.view(now, true, ZenFeedPlanner.ContentRules.OFF, now + 5_000) { many(20) }.edition!!.createdAt)
    }
}

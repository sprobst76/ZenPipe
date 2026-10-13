package org.schabi.newpipe.zen

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sleep timer: countdown on device uptime, fade-out, end without mandatory pause. */
class ZenSleepTest {
    private val min = 60 * 1000L
    private var mono = 5_000_000L

    @Test
    fun `timer runs on uptime, fades during the last 30 s and is due at the end`() {
        val t = ZenSleepTimer { mono }
        assertFalse(t.isRunning())
        assertEquals(-1L, t.remainingMs())
        assertEquals(1f, t.volume())

        t.start(15 * min)
        assertTrue(t.isRunning())
        assertEquals(15 * min, t.remainingMs())
        mono += 15 * min - 31_000
        assertEquals(1f, t.volume())
        mono += 1_000 // 30 s left: fade starts
        assertEquals(1f, t.volume(), 0.001f)
        mono += 15_000 // 15 s left: about half way
        assertEquals(ZenSleepTimer.MIN_VOLUME + (1 - ZenSleepTimer.MIN_VOLUME) / 2, t.volume(), 0.01f)
        assertFalse(t.isDue())
        mono += 15_000
        assertTrue(t.isDue())
        assertEquals(0L, t.remainingMs())
        assertEquals(ZenSleepTimer.MIN_VOLUME, t.volume(), 0.001f) // never 0 (= "muted")
        mono += 60 * min
        assertEquals(ZenSleepTimer.MIN_VOLUME, t.volume(), 0.001f)

        t.cancel()
        assertFalse(t.isRunning())
        assertEquals(1f, t.volume())
    }

    @Test
    fun `restarting replaces the duration, hooks shorten and extend`() {
        val t = ZenSleepTimer { mono }
        t.start(30 * min)
        mono += 10 * min
        t.start(15 * min)
        assertEquals(15 * min, t.remainingMs())

        t.shortenTo(2 * min) // e.g. "likely asleep"
        assertEquals(2 * min, t.remainingMs())
        t.shortenTo(10 * min) // never extends
        assertEquals(2 * min, t.remainingMs())
        t.extend(15 * min) // e.g. "awake"
        assertEquals(17 * min, t.remainingMs())

        t.cancel()
        t.extend(5 * min)
        t.shortenTo(1 * min)
        assertFalse(t.isRunning())
    }

    // ------------------------------------------------------------------ end of the session

    private class Env {
        var mono = 1_000_000L
        var wall = ZonedDateTime.of(2026, 10, 13, 22, 0, 0, 0, ZoneId.of("Europe/Berlin")).toInstant().toEpochMilli()
        val store = MemoryZenStore()
        val events = ArrayList<String>()
        fun engine() = ZenPauseEngine(
            store,
            ZenCountdown(store, "pause", { mono }, { 1 }),
            { mono },
            { wall },
            { ZoneId.of("Europe/Berlin") },
            { t, d -> events.add("$t $d") }
        )

        fun play(e: ZenPauseEngine, ms: Long, dur: Long) {
            var t = 0L
            while (t < ms) {
                mono += 1000
                wall += 1000
                t += 1000
                e.onProgress(t, dur, true)
            }
        }
    }

    @Test
    fun `the sleep timer ending is no abort and starts no pause`() {
        val env = Env()
        val e = env.engine()
        e.onItem("podcast", 120 * min)
        e.onPlaying(true, 0)
        e.setKind(ZenKind.PODCAST)
        env.play(e, 45 * min, 120 * min)
        assertTrue(e.endBySleepTimer() is ZenPauseEngine.Outcome.Slept)
        assertFalse(e.hasOpenSession())
        assertTrue(env.events.any { it.startsWith("sleep_end") })

        // closing the paused player next morning costs nothing
        assertEquals(ZenPauseEngine.Outcome.None, e.onClosed(45 * min, 120 * min))
        assertFalse(e.isActive())
        // a killed process does not turn it into an abort either
        assertEquals(ZenPauseEngine.Outcome.None, env.engine().recover())
        assertFalse(env.engine().isActive())
    }

    @Test
    fun `resuming after the sleep timer starts a new counted session`() {
        val env = Env()
        val e = env.engine()
        e.onItem("video", 120 * min)
        e.onPlaying(true, 0)
        env.play(e, 30 * min, 120 * min)
        e.endBySleepTimer()
        e.onPlaying(false, 30 * min)
        assertFalse(e.hasOpenSession())

        e.onPlaying(true, 30 * min) // user presses play again
        assertTrue(e.hasOpenSession())
        assertEquals("video", e.sessionUrl())
        assertTrue(e.needsKind()) // classified again
        e.setKind(ZenKind.VIDEO)
        env.play(e, 10 * min, 120 * min)
        assertTrue(e.onClosed(40 * min, 120 * min) is ZenPauseEngine.Outcome.Paused)
    }

    @Test
    fun `practice minutes count up to the sleep timer`() {
        val env = Env()
        val e = env.engine()
        e.onItem("yoga", 60 * min)
        e.onPlaying(true, 0)
        e.setKind(ZenKind.PRAXIS)
        env.play(e, 20 * min, 60 * min)
        e.endBySleepTimer()
        assertEquals(20 * min, ZenPraxisTime(env.store).msOn("2026-10-13"))
    }
}

package org.schabi.newpipe.zen

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Practice channels: assignment, lock (add pending / remove immediate), quota and pause exemption, minutes. */
class ZenPraxisTest {
    private val h = 60 * 60 * 1000L
    private val min = 60 * 1000L
    private val zone = ZoneId.of("Europe/Berlin")

    private val yoga = ZenPraxisChannel(0, "https://www.youtube.com/channel/UCyoga", "Yoga")
    private val breath = ZenPraxisChannel(0, "https://www.youtube.com/channel/UCbreath", "Breath")

    private class Env {
        var mono = 1_000_000L
        var boot = 1
        var wall = ZonedDateTime.of(2026, 10, 13, 12, 0, 0, 0, ZoneId.of("Europe/Berlin")).toInstant().toEpochMilli()
        val store = MemoryZenStore()
        val events = ArrayList<String>()

        fun cd(n: String) = ZenCountdown(store, n, { mono }, { boot })
        fun lock() = ZenSettingsLock(store, cd("l"), cd("u"), cd("s"), { t, d -> events.add("$t $d") })
        fun engine() = ZenPauseEngine(
            store,
            cd("pause"),
            { mono },
            { wall },
            { ZoneId.of("Europe/Berlin") },
            { t, d -> events.add("$t $d") }
        )

        fun advance(ms: Long) {
            mono += ms
            wall += ms
        }

        fun watch(e: ZenPauseEngine, url: String, durationMs: Long, ms: Long, kind: ZenKind?) {
            e.onItem(url, durationMs)
            e.onPlaying(true, 0)
            kind?.let { e.setKind(it) }
            var t = 0L
            while (t < ms) {
                advance(1000)
                t += 1000
                e.onProgress(t, durationMs, true)
            }
        }
    }

    private fun ready(): Pair<Env, ZenSettingsLock> {
        val e = Env()
        val l = e.lock()
        l.completeSetup()
        e.mono += 25 * h
        assertFalse(l.isLocked())
        return e to l
    }

    // ------------------------------------------------------------------ assignment

    @Test
    fun `channel identity ignores scheme, www, m and trailing slash but keeps the id case`() {
        val k = ZenPraxisChannel.keyOf(0, "https://www.youtube.com/channel/UCyoga")
        assertEquals(k, ZenPraxisChannel.keyOf(0, "http://youtube.com/channel/UCyoga/"))
        assertEquals(k, ZenPraxisChannel.keyOf(0, "https://m.YouTube.com/channel/UCyoga"))
        assertFalse(k == ZenPraxisChannel.keyOf(0, "https://www.youtube.com/channel/UCYOGA"))
        assertFalse(k == ZenPraxisChannel.keyOf(1, "https://www.youtube.com/channel/UCyoga"))
    }

    @Test
    fun `channel list survives encoding`() {
        val list = listOf(yoga, ZenPraxisChannel(0, "https://y/c/x", "Name\twith\ttabs"))
        val back = ZenPraxisChannel.decodeList(ZenPraxisChannel.encodeList(list))
        assertEquals(2, back.size)
        assertEquals(yoga, back[0])
        assertEquals("Name with tabs", back[1].name)
        assertTrue(ZenPraxisChannel.decodeList(null).isEmpty())
    }

    @Test
    fun `practice wins over every other kind`() {
        assertEquals(ZenKind.PRAXIS, ZenContentKind.classify("Music", null, "X", "Song", 600, false, praxis = true))
        assertEquals(ZenKind.PRAXIS, ZenContentKind.classify("Education", null, "Yoga", "Flow", 600, false, praxis = true))
        assertEquals(ZenKind.VIDEO, ZenContentKind.classify("Education", null, "Yoga", "Flow", 600, false))
    }

    // ------------------------------------------------------------------ lock

    @Test
    fun `first-time setup adds at once without lock`() {
        val e = Env()
        val l = e.lock()
        val r = l.addPraxis(yoga) as ZenSettingsLock.Result.Applied
        assertTrue(r.setupMode)
        assertEquals(listOf(yoga), l.praxisChannels())
        assertFalse(l.isLocked())
        assertEquals(0L, l.undoRemainingMs())
    }

    @Test
    fun `adding is pending for 24 h, starts the lock and activates afterwards`() {
        val (e, l) = ready()
        val r = l.addPraxis(yoga) as ZenSettingsLock.Result.Applied
        assertTrue(r.lockStarted)
        assertEquals(listOf(yoga.key), r.pending)
        assertTrue(l.praxisChannels().isEmpty())
        assertEquals(listOf(yoga), l.pendingPraxis())
        assertTrue(l.isLocked())
        assertTrue(e.events.any { it.startsWith("praxis_add_pending") })

        e.mono += 23 * h
        assertTrue(l.praxisChannels().isEmpty())
        e.mono += 1 * h + 1
        assertEquals(listOf(yoga), l.praxisChannels())
        assertTrue(l.pendingPraxis().isEmpty())
        assertTrue(e.events.any { it.startsWith("praxis_activated") })
        // adding the same channel again changes nothing
        assertEquals(ZenSettingsLock.Result.NoChange, l.addPraxis(yoga))
    }

    @Test
    fun `adding while locked is rejected, the wall clock does not help`() {
        val (e, l) = ready()
        l.addPraxis(yoga)
        val r = l.addPraxis(breath)
        assertTrue(r is ZenSettingsLock.Result.Rejected && r.reason == ZenSettingsLock.Reason.LOCKED)
        e.wall += 48 * h // clock set forward: the lock runs on device uptime only
        assertTrue(l.addPraxis(breath) is ZenSettingsLock.Result.Rejected)
        // a stricter setting change during the lock does not touch the pending channel
        l.apply(mapOf(ZenSettingKeys.FEED_BUDGET to 10))
        assertEquals(listOf(yoga), l.pendingPraxis())
    }

    @Test
    fun `removing applies immediately, also while locked, and never starts the lock`() {
        val (e, l) = ready()
        l.addPraxis(yoga)
        e.mono += 25 * h
        assertEquals(listOf(yoga), l.praxisChannels())
        assertFalse(l.isLocked())

        val r = l.removePraxis(yoga.key) as ZenSettingsLock.Result.Applied
        assertFalse(r.lockStarted)
        assertTrue(l.praxisChannels().isEmpty())
        assertFalse(l.isLocked())
        assertTrue(e.events.any { it.startsWith("praxis_remove") })

        // while a lock runs (started by another loosening)
        l.addPraxis(breath)
        e.mono += 25 * h
        l.apply(mapOf(ZenSettingKeys.MIN_LENGTH_MIN to 3)) // loosening: lock again
        assertTrue(l.isLocked())
        assertTrue(l.removePraxis(breath.key) is ZenSettingsLock.Result.Applied)
        assertTrue(l.praxisChannels().isEmpty())
        assertEquals(ZenSettingsLock.Result.NoChange, l.removePraxis(breath.key))
    }

    @Test
    fun `removing a pending channel cancels it and the lock keeps running`() {
        val (e, l) = ready()
        l.addPraxis(yoga)
        e.mono += 1 * h
        l.removePraxis(yoga.key)
        assertTrue(l.pendingPraxis().isEmpty())
        assertTrue(l.isLocked())
        e.mono += 24 * h
        assertTrue(l.praxisChannels().isEmpty())
        assertTrue(e.events.any { it.startsWith("praxis_pending_cancelled") })
    }

    @Test
    fun `cancel pending also drops pending practice channels`() {
        val (_, l) = ready()
        l.addPraxis(yoga)
        assertTrue(l.cancelPending())
        assertTrue(l.pendingPraxis().isEmpty())
        assertTrue(l.isLocked())
    }

    @Test
    fun `undo within 5 minutes reverts an addition including the lock, and a removal`() {
        val (e, l) = ready()
        l.addPraxis(yoga)
        e.mono += 4 * min
        assertTrue(l.undo())
        assertTrue(l.pendingPraxis().isEmpty())
        assertFalse(l.isLocked())

        l.addPraxis(yoga)
        e.mono += 25 * h
        assertEquals(listOf(yoga), l.praxisChannels())
        l.removePraxis(yoga.key)
        assertTrue(l.undo())
        assertEquals(listOf(yoga), l.praxisChannels())

        l.removePraxis(yoga.key)
        e.mono += 6 * min
        assertFalse(l.undo())
        assertTrue(l.praxisChannels().isEmpty())
    }

    @Test
    fun `undo of a settings change keeps practice channels unchanged`() {
        val (e, l) = ready()
        l.addPraxis(yoga)
        e.mono += 25 * h
        l.apply(mapOf(ZenSettingKeys.FEED_BUDGET to 10))
        assertTrue(l.undo())
        assertEquals(listOf(yoga), l.praxisChannels())
        assertEquals(15, l.effective(ZenSettingKeys.FEED_BUDGET))
    }

    // ------------------------------------------------------------------ feed quota

    @Test
    fun `practice videos never enter an edition and do not use the budget`() {
        val now = 1_800_000_000_000L
        fun c(n: Int, ch: String, praxis: Boolean) = ZenFeedPlanner.Candidate(
            "https://y/v$n", ch, "Video $n", ch, now - h, 40L * 60, false, 0, "G0", praxis
        )
        val px = (1..20).map { c(it, "yoga$it", true) }
        val normal = (21..25).map { c(it, "ch$it", false) }
        assertFalse(ZenFeedPlanner.isEligible(px[0], now))
        // even very short practice videos are simply not part of the feed (no minimum length applies)
        val sel = ZenFeedPlanner.select(px + normal, now, 15)
        assertEquals(5, sel.picks.size)
        assertTrue(sel.picks.none { it.url in px.map { p -> p.url } })
        assertEquals(5, sel.eligibleCount) // practice is not counted as "waiting" either
    }

    // ------------------------------------------------------------------ pause + minutes

    @Test
    fun `aborting a practice video starts no pause and counts minutes`() {
        val env = Env()
        val e = env.engine()
        env.watch(e, "yoga1", 60 * min, 20 * min, ZenKind.PRAXIS)
        assertNull(e.previewClose()) // no "resume later" question either
        val out = e.onClosed(20 * min, 60 * min)
        assertTrue(out is ZenPauseEngine.Outcome.Exempt)
        assertFalse(e.isActive())
        assertTrue(env.events.any { it.startsWith("abort_exempt") && it.contains("kind=PRAXIS") })
        assertEquals(20 * min, ZenPraxisTime(env.store).msOn("2026-10-13"))

        // switching away from a practice video is no abort with a pause either
        env.watch(e, "yoga2", 60 * min, 10 * min, ZenKind.PRAXIS)
        assertFalse(e.abandonForNewStart("other"))
        env.watch(e, "other", 60 * min, 1 * min, null)
        assertFalse(e.isActive())
        assertEquals(30 * min, ZenPraxisTime(env.store).msOn("2026-10-13"))
    }

    @Test
    fun `practice minutes count finished sessions and killed ones, per Zen day`() {
        val env = Env()
        val e = env.engine()
        env.watch(e, "yoga1", 10 * min, 9 * min, ZenKind.PRAXIS)
        assertEquals(ZenPauseEngine.Outcome.Finished, e.onCompleted())
        assertEquals(9 * min, ZenPraxisTime(env.store).msOn("2026-10-13"))

        // killed while playing: recovered at the next start, no pause, minutes counted
        env.watch(e, "yoga2", 60 * min, 30 * min, ZenKind.PRAXIS)
        val out = env.engine().recover()
        assertTrue(out is ZenPauseEngine.Outcome.Exempt)
        assertFalse(env.engine().isActive())
        assertTrue(ZenPraxisTime(env.store).msOn("2026-10-13") >= 39 * min - ZenPauseRules.PERSIST_INTERVAL_MS)

        // 03:59 still belongs to the same Zen day, 04:00 starts a new one
        env.wall = ZonedDateTime.of(2026, 10, 14, 3, 59, 0, 0, zone).toInstant().toEpochMilli()
        val e2 = env.engine()
        env.watch(e2, "yoga3", 60 * min, 0, ZenKind.PRAXIS)
        e2.onClosed(0, 60 * min)
        env.wall = ZonedDateTime.of(2026, 10, 14, 4, 0, 0, 0, zone).toInstant().toEpochMilli()
        env.watch(e2, "yoga4", 60 * min, 5 * min, ZenKind.PRAXIS)
        e2.onClosed(5 * min, 60 * min)
        assertEquals(5 * min, ZenPraxisTime(env.store).msOn("2026-10-14"))
    }

    @Test
    fun `a normal video still pauses next to practice`() {
        val env = Env()
        val e = env.engine()
        env.watch(e, "v", 60 * min, 20 * min, ZenKind.VIDEO)
        assertTrue(e.onClosed(20 * min, 60 * min) is ZenPauseEngine.Outcome.Paused)
        assertEquals(0L, ZenPraxisTime(env.store).msOn("2026-10-13"))
        assertFalse(ZenPauseRules.qualifies(60 * min, ZenKind.PRAXIS))
        assertTrue(ZenPauseRules.qualifies(60 * min, ZenKind.VIDEO))
    }

    @Test
    fun `minutes keep at most 30 days`() {
        val t = ZenPraxisTime(MemoryZenStore())
        for (d in 1..31) t.add("2026-09-%02d".format(d).replace("09-31", "10-01"), min)
        assertEquals(0L, t.msOn("2026-09-01"))
        assertEquals(min, t.msOn("2026-10-01"))
        t.add("2026-10-01", 0)
        assertEquals(min, t.msOn("2026-10-01"))
    }
}

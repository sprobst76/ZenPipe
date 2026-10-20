package org.schabi.newpipe.zen

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZenPauseTest {
    private val min = 60_000L
    private val zone = ZoneId.of("Europe/Berlin")

    private class Env(val store: ZenKeyValueStore = MemoryZenStore()) {
        var mono = 1_000_000L
        var boot = 1
        var wall = ZonedDateTime.of(2026, 10, 12, 12, 0, 0, 0, ZoneId.of("Europe/Berlin")).toInstant().toEpochMilli()
        val logged = ArrayList<String>()

        fun engine() = ZenPauseEngine(
            store,
            ZenCountdown(store, "pause", { mono }, { boot }),
            { mono },
            { wall },
            { ZoneId.of("Europe/Berlin") },
            { type, detail -> logged.add("$type $detail") },
            { boot }
        )

        /** Advance device time (both clocks). */
        fun advance(ms: Long) {
            mono += ms
            wall += ms
        }
    }

    /** Plays [ms] of a video from position 0 with a tick per second, returns the engine. */
    private fun Env.watch(e: ZenPauseEngine, url: String, durationMs: Long, ms: Long) {
        e.onItem(url, durationMs)
        e.onPlaying(true, 0)
        var t = 0L
        while (t < ms) {
            advance(1000)
            t += 1000
            e.onProgress(t, durationMs, true)
        }
    }

    // ------------------------------------------------------------------ pure rules

    @Test
    fun `pause length is 20 percent with min 1 and max 15 minutes`() {
        assertEquals(1 * min, ZenPauseRules.pauseLengthMs(5 * min)) // 20 % = 1 min
        assertEquals(1 * min, ZenPauseRules.pauseLengthMs(2 * min)) // below min
        assertEquals(2 * min, ZenPauseRules.pauseLengthMs(10 * min))
        assertEquals(12 * min, ZenPauseRules.pauseLengthMs(60 * min))
        assertEquals(15 * min, ZenPauseRules.pauseLengthMs(90 * min)) // capped
        assertEquals(15 * min, ZenPauseRules.pauseLengthMs(600 * min))
    }

    @Test
    fun `threshold is 5 minutes of playing time`() {
        assertFalse(ZenPauseRules.qualifies(5 * min - 1, ZenKind.VIDEO))
        assertTrue(ZenPauseRules.qualifies(5 * min, ZenKind.VIDEO))
        assertTrue(ZenPauseRules.qualifies(5 * min, ZenKind.PODCAST))
        assertFalse(ZenPauseRules.qualifies(120 * min, ZenKind.MUSIC))
    }

    @Test
    fun `finished means 90 percent or at most 60 seconds left`() {
        assertTrue(ZenPauseRules.isFinished(9 * min, 10 * min)) // 90 %
        assertFalse(ZenPauseRules.isFinished(9 * min - 1, 10 * min)) // 89.99 %, 60.001 s left
        assertTrue(ZenPauseRules.isFinished(8 * min - 60_000, 8 * min)) // 60 s left (87.5 %)
        assertFalse(ZenPauseRules.isFinished(8 * min - 61_000, 8 * min))
        assertTrue(ZenPauseRules.isFinished(90 * min, 100 * min))
        assertTrue(ZenPauseRules.isFinished(5 * min + 5_000, 5 * min + 20_000)) // 5:20 video ended at 5:05
        assertFalse(ZenPauseRules.isFinished(5 * min, -1)) // unknown duration is never finished
    }

    // ------------------------------------------------------------------ abort -> pause

    @Test
    fun `closing after 10 minutes starts a 2 minute pause`() {
        val env = Env()
        val e = env.engine()
        env.watch(e, "v1", 60 * min, 10 * min)
        val out = e.onClosed(10 * min, 60 * min)
        assertEquals(ZenPauseEngine.Outcome.Paused(10 * min, 2 * min), out)
        assertTrue(e.isActive())
        assertEquals(2 * min, e.remainingMs())
        assertTrue(env.logged.any { it.startsWith("pause_start") })
        env.advance(min)
        assertEquals(min, e.remainingMs())
        env.advance(min)
        assertEquals(0, e.remainingMs())
        assertFalse(e.isActive())
        assertTrue(env.logged.any { it.startsWith("pause_end") })
    }

    @Test
    fun `closing just below 5 minutes gives no pause`() {
        val env = Env()
        val e = env.engine()
        env.watch(e, "v1", 60 * min, 5 * min - 1000)
        assertTrue(e.onClosed(5 * min - 1000, 60 * min) is ZenPauseEngine.Outcome.Short)
        assertFalse(e.isActive())
        assertTrue(env.logged.any { it.startsWith("abort_short") })
    }

    @Test
    fun `exactly 5 minutes triggers`() {
        val env = Env()
        val e = env.engine()
        env.watch(e, "v1", 60 * min, 5 * min)
        assertTrue(e.onClosed(5 * min, 60 * min) is ZenPauseEngine.Outcome.Paused)
        assertEquals(1 * min, e.remainingMs())
    }

    @Test
    fun `watching to the end or nearly to the end is no abort`() {
        val env = Env()
        val e = env.engine()
        env.watch(e, "v1", 20 * min, 18 * min) // 90 %
        assertEquals(ZenPauseEngine.Outcome.Finished, e.onClosed(18 * min, 20 * min))
        assertFalse(e.isActive())

        env.watch(e, "v2", 5 * min + 20_000, 5 * min + 5_000) // 5:20 video, closed at 5:05
        assertEquals(ZenPauseEngine.Outcome.Finished, e.onClosed(5 * min + 5_000, 5 * min + 20_000))
        assertFalse(e.isActive())

        env.watch(e, "v3", 10 * min, 6 * min)
        assertEquals(ZenPauseEngine.Outcome.Finished, e.onCompleted())
        assertFalse(e.isActive())
    }

    @Test
    fun `pausing in the player is not an abort and paused time does not count`() {
        val env = Env()
        val e = env.engine()
        env.watch(e, "v1", 60 * min, 3 * min)
        e.onPlaying(false, 3 * min) // user pauses
        env.advance(30 * min) // stays paused for a long time
        assertTrue(e.hasOpenSession())
        assertFalse(e.isActive())
        e.onPlaying(true, 3 * min)
        repeat(60) {
            env.advance(1000)
            e.onProgress(3 * min + (it + 1) * 1000L, 60 * min, true)
        }
        // 3 min + 1 min of playing = 4 min: below the threshold although 34 min passed
        assertTrue(e.onClosed(4 * min, 60 * min) is ZenPauseEngine.Outcome.Short)
    }

    @Test
    fun `closing while paused counts as abort`() {
        val env = Env()
        val e = env.engine()
        env.watch(e, "v1", 60 * min, 8 * min)
        e.onPlaying(false, 8 * min)
        env.advance(10_000) // the rest time is credited (see the rest-time tests), 10 s of 96 s
        val out = e.onClosed(8 * min, 60 * min)
        assertEquals(ZenPauseEngine.Outcome.Paused(8 * min, 86_000), out)
    }

    @Test
    fun `seeking does not add playing time`() {
        val env = Env()
        val e = env.engine()
        e.onItem("v1", 120 * min)
        e.onPlaying(true, 0)
        repeat(60) {
            env.advance(1000)
            e.onProgress((it + 1) * 1000L, 120 * min, true)
        }
        e.onProgress(60 * min, 120 * min, true) // jump forward
        repeat(60) {
            env.advance(1000)
            e.onProgress(60 * min + (it + 1) * 1000L, 120 * min, true)
        }
        assertEquals(2 * min, e.playedMs())
        assertTrue(e.onClosed(61 * min, 120 * min) is ZenPauseEngine.Outcome.Short)
    }

    @Test
    fun `switching to another video aborts the first`() {
        val env = Env()
        val e = env.engine()
        env.watch(e, "v1", 60 * min, 15 * min)
        e.onItem("v2", 30 * min)
        assertTrue(e.isActive())
        assertEquals(3 * min, e.remainingMs())
        assertEquals("v2", e.sessionUrl())
    }

    @Test
    fun `starting another video is blocked and evaluated before it starts`() {
        val env = Env()
        val e = env.engine()
        env.watch(e, "v1", 60 * min, 15 * min)
        assertTrue(e.abandonForNewStart("v2"))
        assertTrue(e.isActive())
        assertFalse(e.hasOpenSession())
    }

    @Test
    fun `starting another video after only 2 minutes is allowed`() {
        val env = Env()
        val e = env.engine()
        env.watch(e, "v1", 60 * min, 2 * min)
        assertFalse(e.abandonForNewStart("v2"))
        assertFalse(e.isActive())
        assertTrue(e.hasOpenSession())
    }

    @Test
    fun `same video again after a session ended is a new session`() {
        val env = Env()
        val e = env.engine()
        env.watch(e, "v1", 60 * min, 3 * min)
        e.onClosed(3 * min, 60 * min)
        env.watch(e, "v1", 60 * min, 3 * min) // resumed: 3 min again, not 6
        assertTrue(e.onClosed(6 * min, 60 * min) is ZenPauseEngine.Outcome.Short)
    }

    // ------------------------------------------------------------------ music / podcast

    @Test
    fun `music is exempt, podcast and unknown are not`() {
        val env = Env()
        val e = env.engine()
        env.watch(e, "m", 60 * min, 20 * min)
        e.setKind(ZenKind.MUSIC)
        assertTrue(e.onClosed(20 * min, 60 * min) is ZenPauseEngine.Outcome.Exempt)
        assertFalse(e.isActive())

        env.watch(e, "p", 90 * min, 20 * min)
        e.setKind(ZenKind.PODCAST)
        assertTrue(e.onClosed(20 * min, 90 * min) is ZenPauseEngine.Outcome.Paused)
        env.advance(10 * min)
        assertFalse(e.isActive()) // 4 min pause is over

        env.watch(e, "u", 90 * min, 20 * min) // kind never determined: strict
        assertTrue(e.onClosed(20 * min, 90 * min) is ZenPauseEngine.Outcome.Paused)
    }

    @Test
    fun `kind heuristic`() {
        fun k(cat: String?, tags: List<String>?, up: String?, title: String?, dur: Long = 600, audio: Boolean = false) = ZenContentKind.classify(cat, tags, up, title, dur, audio)
        assertEquals(ZenKind.MUSIC, k("Music", null, "Some Artist", "Song"))
        assertEquals(ZenKind.MUSIC, k("Entertainment", null, "Artist - Topic", "Song"))
        assertEquals(ZenKind.MUSIC, k(null, null, "ArtistVEVO", "Song"))
        assertEquals(ZenKind.MUSIC, k("People & Blogs", null, "X", "Band - Hit (Official Music Video)"))
        assertEquals(ZenKind.MUSIC, k(null, listOf("Music", "pop"), "X", "Hit"))
        // a "music" tag alone is not enough when the category is known and is not music
        assertEquals(ZenKind.VIDEO, k("Education", listOf("music"), "Prof", "Music theory lecture"))
        assertEquals(ZenKind.PODCAST, k("Entertainment", null, "Lex Fridman", "#501 - Lex Fridman Podcast"))
        assertEquals(ZenKind.PODCAST, k("Education", null, "X", "Folge 12: Talk"))
        assertEquals(ZenKind.PODCAST, k(null, null, "X", "Talk", dur = 25 * 60, audio = true))
        assertEquals(ZenKind.VIDEO, k(null, null, "X", "Talk", dur = 25 * 60, audio = false))
        assertEquals(ZenKind.VIDEO, k(null, null, null, null))
    }

    // ------------------------------------------------------------------ process kill

    @Test
    fun `process kill while playing is detected at the next start`() {
        val env = Env()
        val e = env.engine()
        env.watch(e, "v1", 60 * min, 12 * min)
        // process dies: no onClosed. The persisted state is at most one interval behind.
        val e2 = env.engine() // new process, same store
        val out = e2.recover()
        assertTrue(out is ZenPauseEngine.Outcome.Paused)
        val played = (out as ZenPauseEngine.Outcome.Paused).playedMs
        assertTrue(played >= 12 * min - ZenPauseRules.PERSIST_INTERVAL_MS)
        assertTrue(e2.isActive())
        assertTrue(env.logged.any { it.contains("reason=KILLED") })
        assertEquals(ZenPauseEngine.Outcome.None, env.engine().recover()) // only once
    }

    @Test
    fun `kill after a finished or short session leaves nothing behind`() {
        val env = Env()
        val e = env.engine()
        env.watch(e, "v1", 60 * min, 3 * min)
        assertTrue(env.engine().recover() is ZenPauseEngine.Outcome.Short)
        val e3 = env.engine()
        env.watch(e3, "v2", 10 * min, 9 * min) // position 9:00 of 10:00 = finished
        assertEquals(ZenPauseEngine.Outcome.Finished, env.engine().recover())
    }

    @Test
    fun `kill with music kind persisted is exempt`() {
        val env = Env()
        val e = env.engine()
        env.watch(e, "m", 60 * min, 30 * min)
        e.setKind(ZenKind.MUSIC)
        assertTrue(env.engine().recover() is ZenPauseEngine.Outcome.Exempt)
    }

    // ------------------------------------------------------------------ time manipulation

    @Test
    fun `setting the clock forward does not shorten the pause`() {
        val env = Env()
        val e = env.engine()
        env.watch(e, "v1", 60 * min, 30 * min) // 6 min pause
        e.onClosed(30 * min, 60 * min)
        env.wall += 24 * 60 * min // clock set one day forward
        assertEquals(6 * min, e.remainingMs())
        env.mono += min
        env.wall += min
        assertEquals(5 * min, e.remainingMs())
    }

    @Test
    fun `setting the clock back does not extend the pause`() {
        val env = Env()
        val e = env.engine()
        env.watch(e, "v1", 60 * min, 30 * min)
        e.onClosed(30 * min, 60 * min)
        env.wall -= 5 * 24 * 60 * min
        env.mono += 2 * min
        assertEquals(4 * min, e.remainingMs())
    }

    @Test
    fun `reboot with a changed clock does not shorten the pause`() {
        val env = Env()
        val e = env.engine()
        env.watch(e, "v1", 60 * min, 30 * min) // 6 min pause
        e.onClosed(30 * min, 60 * min)
        env.advance(min)
        assertEquals(5 * min, e.remainingMs())
        // clock set +3 h, device restarted: uptime 30 s, wall far ahead
        env.boot = 2
        env.mono = 30_000
        env.wall += 3 * 60 * min
        val e2 = env.engine()
        assertEquals(5 * min - 30_000, e2.remainingMs())
        assertTrue(e2.isActive())
    }

    // ------------------------------------------------------------------ resume later (F6)

    @Test
    fun `resume later twice per zen day without a pause`() {
        val env = Env()
        val e = env.engine()
        assertEquals(2, e.laterLeft())
        env.watch(e, "v1", 60 * min, 10 * min)
        assertTrue(e.later())
        assertFalse(e.isActive())
        assertEquals(1, e.laterLeft())
        assertTrue(env.logged.any { it.startsWith("later") })

        env.watch(e, "v2", 60 * min, 10 * min)
        assertTrue(e.later())
        assertEquals(0, e.laterLeft())

        env.watch(e, "v3", 60 * min, 10 * min)
        assertFalse(e.later()) // none left: caller keeps the player open
        assertTrue(e.hasOpenSession())
        assertTrue(e.onClosed(10 * min, 60 * min) is ZenPauseEngine.Outcome.Paused)
    }

    @Test
    fun `resume later allowance resets at the next zen day`() {
        val env = Env()
        val e = env.engine()
        repeat(2) {
            env.watch(e, "v$it", 60 * min, 10 * min)
            assertTrue(e.later())
        }
        assertEquals(0, e.laterLeft())
        // 23:00 the same zen day: still none; 04:00 next day: reset
        env.wall = ZonedDateTime.of(2026, 10, 13, 3, 59, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(0, e.laterLeft())
        env.wall = ZonedDateTime.of(2026, 10, 13, 4, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(2, e.laterLeft())
    }

    @Test
    fun `resume later on a short video costs no allowance`() {
        val env = Env()
        val e = env.engine()
        env.watch(e, "v1", 60 * min, 2 * min)
        assertTrue(e.later())
        assertEquals(2, e.laterLeft())
    }

    @Test
    fun `a second pause never shortens a running one`() {
        val env = Env()
        val e = env.engine()
        env.watch(e, "v1", 120 * min, 90 * min) // 15 min
        e.onClosed(90 * min, 120 * min)
        env.watch(e, "v2", 60 * min, 6 * min) // would be 1.2 min
        e.onClosed(6 * min, 60 * min)
        // 15 min pause minus the 6 min that passed; the 1.2 min pause of the second video did not replace it
        assertEquals(9 * min, e.remainingMs())
    }

    // ------------------------------------------------------------------ rest-time credit

    @Test
    fun `abort while playing gets the full pause`() {
        val env = Env()
        val e = env.engine()
        env.watch(e, "v1", 60 * min, 10 * min)
        assertEquals(0L, e.restMs())
        assertEquals(ZenPauseEngine.Outcome.Paused(10 * min, 2 * min), e.onClosed(10 * min, 60 * min))
        assertFalse(env.logged.any { it.startsWith("pause_rest_credit") })
    }

    @Test
    fun `rest since the last stop shortens the pause`() {
        val env = Env()
        val e = env.engine()
        env.watch(e, "v1", 60 * min, 10 * min) // 2 min pause
        e.onPlaying(false, 10 * min)
        env.advance(30_000)
        assertEquals(30_000L, e.restMs())
        assertEquals(90_000L, e.previewClose()!!.pauseMs) // "resume later" question shows the shorter pause
        val out = e.onClosed(10 * min, 60 * min)
        assertEquals(ZenPauseEngine.Outcome.Paused(10 * min, 90_000), out)
        assertEquals(90_000L, e.remainingMs())
        assertTrue(env.logged.any { it.startsWith("pause_rest_credit") && it.contains("restMs=30000 fullMs=120000 lengthMs=90000") })
        assertTrue(env.logged.any { it.startsWith("pause_start") && it.contains("lengthMs=90000 fullMs=120000 restMs=30000") })
    }

    @Test
    fun `rest of the full length means no pause and no question`() {
        val env = Env()
        val e = env.engine()
        env.watch(e, "v1", 60 * min, 10 * min)
        e.onPlaying(false, 10 * min)
        env.advance(2 * min) // podcast paused on arrival, notification swiped away later
        assertEquals(null, e.previewClose())
        val out = e.onClosed(10 * min, 60 * min)
        assertEquals(ZenPauseEngine.Outcome.Rested(10 * min, 2 * min, 2 * min), out)
        assertFalse(e.isActive())
        assertTrue(env.logged.any { it.startsWith("pause_rest_credit") && it.contains("lengthMs=0") })
        assertFalse(env.logged.any { it.startsWith("pause_start") })
    }

    @Test
    fun `rest counts from the last stop only and resume later costs nothing after enough rest`() {
        val env = Env()
        val e = env.engine()
        env.watch(e, "v1", 60 * min, 10 * min)
        e.onPlaying(false, 10 * min)
        env.advance(10 * min)
        e.onPlaying(true, 10 * min) // played again: the earlier rest is gone
        env.advance(1000)
        e.onProgress(10 * min + 1000, 60 * min, true)
        assertEquals(0L, e.restMs())
        e.onPlaying(false, 10 * min + 1000)
        env.advance(5 * min)
        assertTrue(e.later()) // would cost no pause: simply closed
        assertEquals(2, e.laterLeft())
    }

    @Test
    fun `kill during rest is credited in the same boot, not after a reboot`() {
        val store = MemoryZenStore()
        val env = Env(store)
        val e = env.engine()
        env.watch(e, "v1", 60 * min, 10 * min)
        e.onPlaying(false, 10 * min)
        env.advance(60_000)
        // process killed; same boot, next start
        val out = env.engine().recover()
        assertEquals(ZenPauseEngine.Outcome.Paused(10 * min, 60_000), out)

        val store2 = MemoryZenStore()
        val env2 = Env(store2)
        val e2 = env2.engine()
        env2.watch(e2, "v1", 60 * min, 10 * min)
        e2.onPlaying(false, 10 * min)
        env2.advance(60_000)
        env2.boot = 2 // reboot: elapsedRealtime not comparable -> no credit
        env2.mono = 5_000
        assertEquals(ZenPauseEngine.Outcome.Paused(10 * min, 2 * min), env2.engine().recover())
    }

    @Test
    fun `kill while playing gets no credit however long ago`() {
        val env = Env()
        val e = env.engine()
        env.watch(e, "v1", 60 * min, 10 * min)
        env.advance(60 * min) // process died while playing, app opened an hour later
        assertEquals(ZenPauseEngine.Outcome.Paused(10 * min, 2 * min), env.engine().recover())
    }

    // ------------------------------------------------------------------ playing time per day

    private fun ZenPauseEngine.play(env: Env, ms: Long) {
        var t = 0L
        while (t < ms) {
            env.advance(1000)
            t += 1000
            onProgress(t, 60 * min, true)
        }
    }

    @Test
    fun `picture and sound are booked by player mode, paused time not`() {
        val env = Env()
        val e = env.engine()
        e.onItem("v1", 60 * min)
        e.setKind(ZenKind.VIDEO)
        e.onPlaying(true, 0)
        e.play(env, 3 * min)
        e.setAudio(true) // "switch to background"
        e.play(env, 2 * min)
        e.onPlaying(false, 5 * min)
        env.advance(30 * min) // paused: does not count
        e.onPlaying(true, 5 * min)
        e.play(env, 1 * min)
        e.onClosed(6 * min, 60 * min)
        val d = ZenWatchTime(env.store).get("2026-10-12")
        assertEquals(3 * min, d.pictureMs)
        assertEquals(3 * min, d.soundMs)
        assertEquals(0L, d.musicMs + d.praxisMs)
        assertEquals(3 * min + 90_000, d.zenMinutesMs) // sound counts half (hook only, no limit)
        assertTrue(env.logged.any { it.contains("playedMs=360000 soundMs=180000") })
    }

    @Test
    fun `booking is continuous and splits a session at 04 00`() {
        val env = Env()
        env.wall = ZonedDateTime.of(2026, 10, 13, 3, 55, 0, 0, zone).toInstant().toEpochMilli()
        val e = env.engine()
        e.onItem("v1", 60 * min)
        e.setKind(ZenKind.VIDEO)
        e.onPlaying(true, 0)
        e.play(env, 10 * min)
        // still running: already booked up to the last tick (10 s)
        val before = ZenWatchTime(env.store).get("2026-10-12").pictureMs
        val after = ZenWatchTime(env.store).get("2026-10-13").pictureMs
        assertTrue(before in (5 * min - 10_000)..(5 * min))
        assertTrue(after >= 5 * min - 20_000)
        e.onClosed(10 * min, 60 * min)
        assertEquals(10 * min, ZenWatchTime(env.store).get("2026-10-12").pictureMs + ZenWatchTime(env.store).get("2026-10-13").pictureMs)
    }

    @Test
    fun `time waits for the kind, unknown counts as picture`() {
        val env = Env()
        val e = env.engine()
        e.onItem("m", 60 * min)
        e.onPlaying(true, 0)
        e.play(env, 30_000) // StreamInfo not there yet
        e.setKind(ZenKind.MUSIC)
        e.play(env, 30_000)
        e.onClosed(60_000, 60 * min)
        assertEquals(60_000L, ZenWatchTime(env.store).get("2026-10-12").musicMs)
        e.onItem("x", 60 * min)
        e.onPlaying(true, 0)
        e.play(env, 20_000)
        e.onClosed(20_000, 60 * min) // kind never known
        assertEquals(20_000L, ZenWatchTime(env.store).get("2026-10-12").pictureMs)
    }

    @Test
    fun `kill keeps what was booked up to the last tick`() {
        val env = Env()
        val e = env.engine()
        e.onItem("v1", 60 * min)
        e.setKind(ZenKind.PODCAST)
        e.setAudio(true)
        e.onPlaying(true, 0)
        e.play(env, 2 * min + 5_000)
        env.engine().recover()
        val sound = ZenWatchTime(env.store).get("2026-10-12").soundMs
        assertTrue(sound in (2 * min + 5_000 - ZenPauseRules.PERSIST_INTERVAL_MS)..(2 * min + 5_000))
    }

    @Test
    fun `watch time keeps 30 days and reads short values`() {
        val t = ZenWatchTime(MemoryZenStore())
        for (d in 1..31) t.add("2026-09-%02d".format(d).replace("09-31", "10-01"), false, ZenKind.VIDEO, min)
        assertEquals(0L, t.get("2026-09-01").pictureMs)
        assertEquals(min, t.get("2026-10-01").pictureMs)
        t.add("2026-10-01", true, ZenKind.PRAXIS, min)
        assertEquals(min, t.get("2026-10-01").praxisMs)
        assertEquals(ZenWatchTime.Day(5, 0, 0, 0), ZenWatchTime.Day.decode("5"))
        assertEquals(ZenWatchTime.Day(), ZenWatchTime.Day.decode(null))
    }
    // ------------------------------------------------------------------ seeking to the end

    @Test
    fun `watched to the end needs the end reached by playing`() {
        // position criterion plus at least half of the covered stretch really played
        assertTrue(ZenPauseRules.watchedToEnd(18 * min, 20 * min, 0, 18 * min))
        assertTrue(ZenPauseRules.watchedToEnd(20 * min, 20 * min, 0, 10 * min)) // e.g. 2x speed
        assertFalse(ZenPauseRules.watchedToEnd(20 * min, 20 * min, 0, 10 * min - 1))
        assertFalse(ZenPauseRules.watchedToEnd(60 * min, 60 * min, 0, 20 * min)) // seek bar to the end
        assertTrue(ZenPauseRules.watchedToEnd(60 * min, 60 * min, 50 * min, 10 * min)) // resumed at 50:00
        assertTrue(ZenPauseRules.watchedToEnd(60 * min, 60 * min, -1, 30 * min)) // start unknown = 0
        assertFalse(ZenPauseRules.watchedToEnd(30 * min, 60 * min, 0, 30 * min)) // not at the end at all
    }

    @Test
    fun `seeking to the end and closing is an abort with a pause`() {
        val env = Env()
        val e = env.engine()
        env.watch(e, "v1", 60 * min, 20 * min)
        e.onProgress(59 * min + 30_000, 60 * min, true) // drag the seek bar to the end
        val out = e.onClosed(59 * min + 30_000, 60 * min)
        assertEquals(ZenPauseEngine.Outcome.Paused(20 * min, 4 * min), out)
        assertTrue(e.isActive())
        assertTrue(env.logged.any { it.startsWith("pause_start") && it.contains("startMs=0") })
        assertFalse(env.logged.any { it.startsWith("watch_end") })
    }

    @Test
    fun `seeking to the end until the player reports ended is an abort too`() {
        val env = Env()
        val e = env.engine()
        env.watch(e, "v1", 60 * min, 20 * min)
        e.onProgress(60 * min, 60 * min, true)
        assertTrue(e.onCompleted() is ZenPauseEngine.Outcome.Paused)
        assertTrue(e.isActive())
    }

    @Test
    fun `resuming near the end and watching the rest is finished`() {
        val env = Env()
        val e = env.engine()
        e.onItem("v1", 60 * min)
        e.onPlaying(true, 50 * min) // the player resumes at 50:00 (history)
        var t = 50 * min
        while (t < 60 * min) {
            env.advance(1000)
            t += 1000
            e.onProgress(t, 60 * min, true)
        }
        assertEquals(ZenPauseEngine.Outcome.Finished, e.onClosed(60 * min, 60 * min))
        assertFalse(e.isActive())
    }

    @Test
    fun `skipping an intro still counts as watched to the end`() {
        val env = Env()
        val e = env.engine()
        env.watch(e, "v1", 20 * min, 1 * min)
        e.onProgress(3 * min, 20 * min, true) // skip 2 minutes
        var t = 3 * min
        while (t < 18 * min) {
            env.advance(1000)
            t += 1000
            e.onProgress(t, 20 * min, true)
        }
        assertEquals(ZenPauseEngine.Outcome.Finished, e.onClosed(18 * min, 20 * min))
    }

    @Test
    fun `after seeking to the end, resume later costs an allowance and a new start pauses`() {
        val env = Env()
        val e = env.engine()
        env.watch(e, "v1", 60 * min, 20 * min)
        e.onProgress(59 * min + 30_000, 60 * min, true)
        assertTrue(e.previewClose() != null)
        assertEquals(2, e.laterLeft())
        assertTrue(e.later())
        assertEquals(1, e.laterLeft())

        env.watch(e, "v2", 60 * min, 20 * min)
        e.onProgress(59 * min + 30_000, 60 * min, true)
        assertTrue(e.abandonForNewStart("v3"))
        assertTrue(e.isActive())
    }

    @Test
    fun `a kill after seeking to the end pauses, also with state from older versions`() {
        val env = Env()
        val e = env.engine()
        env.watch(e, "v1", 60 * min, 20 * min)
        e.onPlaying(false, 59 * min + 30_000) // seeked to the end, then paused (persisted)
        assertTrue(env.engine().recover() is ZenPauseEngine.Outcome.Paused)

        // an open session persisted by an older version has no start position: strict (0)
        val env2 = Env()
        env2.store.put("ps_open" to 1, "ps_url" to "v1", "ps_played" to 20 * min, "ps_pos" to 59 * min + 30_000, "ps_dur" to 60 * min)
        assertTrue(env2.engine().recover() is ZenPauseEngine.Outcome.Paused)
    }

    @Test
    fun `a completed stream without known duration still counts as finished`() {
        val env = Env()
        val e = env.engine()
        env.watch(e, "live", 0, 20 * min)
        assertEquals(ZenPauseEngine.Outcome.Finished, e.onCompleted())
    }

    @Test
    fun `the pause screen says the minutes left once per minute, rounded up`() {
        assertEquals(5, ZenPauseRules.spokenMinutesLeft(5 * min))
        assertEquals(5, ZenPauseRules.spokenMinutesLeft(4 * min + 1))
        assertEquals(4, ZenPauseRules.spokenMinutesLeft(4 * min))
        assertEquals(1, ZenPauseRules.spokenMinutesLeft(1000))
        assertEquals(0, ZenPauseRules.spokenMinutesLeft(0))
    }
}

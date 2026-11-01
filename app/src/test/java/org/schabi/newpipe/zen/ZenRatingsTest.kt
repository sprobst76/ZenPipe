package org.schabi.newpipe.zen

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZenRatingsTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val now = ZonedDateTime.of(2026, 11, 1, 14, 20, 0, 0, zone).toInstant().toEpochMilli()
    private val h = 3_600_000L

    private fun r(n: Int, value: Int, channel: String = "https://y/c$n", theme: String? = null) = ZenRating(
        0,
        "https://www.youtube.com/watch?v=v$n",
        value,
        ZenPraxisChannel.keyOf(0, channel),
        "Channel $n",
        theme,
        "Title $n",
        1000L + n
    )

    private fun cand(n: Long, ch: String = "c$n", rating: Double = 1.0, sec: Long = 20L * 60) = ZenFeedPlanner.Candidate(
        "https://y/v$n", ch, "Video $n", ch, now - h, sec, false, null, null, rating = rating
    )

    @Test
    fun `same value again toggles off, the other value replaces`() {
        val s = ZenRatings(MemoryZenStore())
        assertEquals(ZenRatings.Change.SET, s.rate(r(1, ZenRating.MORE)))
        assertEquals(ZenRating.MORE, s.valueOf(0, "https://youtube.com/watch?v=v1"))
        assertEquals(ZenRatings.Change.CHANGED, s.rate(r(1, ZenRating.LESS)))
        assertEquals(ZenRating.LESS, s.valueOf(0, "https://www.youtube.com/watch?v=v1"))
        assertEquals(1, s.all().size)
        assertEquals(ZenRatings.Change.REMOVED, s.rate(r(1, ZenRating.LESS)))
        assertEquals(0, s.valueOf(0, "https://www.youtube.com/watch?v=v1"))
        assertTrue(s.all().isEmpty())
    }

    @Test
    fun `more puts the video on the like list, less and removing take it off, done keeps the rating`() {
        val s = ZenRatings(MemoryZenStore())
        s.rate(r(1, ZenRating.MORE))
        s.rate(r(2, ZenRating.LESS))
        assertEquals(listOf("Title 1"), s.likes().map { it.title })
        s.rate(r(1, ZenRating.MORE)) // toggle off
        assertTrue(s.likes().isEmpty())
        s.rate(r(1, ZenRating.MORE))
        s.rate(r(1, ZenRating.LESS)) // replaced by less
        assertTrue(s.likes().isEmpty())
        s.rate(r(3, ZenRating.MORE))
        assertTrue(s.likeDone(0, "https://www.youtube.com/watch?v=v3"))
        assertTrue(s.likes().isEmpty())
        assertEquals(ZenRating.MORE, s.valueOf(0, "https://www.youtube.com/watch?v=v3"))
        assertFalse(s.likeDone(0, "https://www.youtube.com/watch?v=v3"))
        assertTrue(s.remove(0, "https://www.youtube.com/watch?v=v3"))
        assertEquals(0, s.valueOf(0, "https://www.youtube.com/watch?v=v3"))
    }

    @Test
    fun `lists are capped, the oldest goes first`() {
        val s = ZenRatings(MemoryZenStore())
        (1..ZenRatings.MAX_RATINGS + 5).forEach { s.rate(r(it, ZenRating.MORE)) }
        assertEquals(ZenRatings.MAX_RATINGS, s.all().size)
        assertEquals("Title 6", s.all().first().title)
        assertEquals(ZenRatings.MAX_LIKES, s.likes().size)
        assertEquals("Title ${ZenRatings.MAX_RATINGS + 5}", s.likes().last().title)
    }

    @Test
    fun `encoding survives tabs and newlines and skips broken lines`() {
        val x = r(1, ZenRating.LESS, theme = "ki").copy(title = "a\tb\nc", channelName = "x\ty")
        val back = ZenRatings.decodeRatings(ZenRatings.encodeRatings(listOf(x)) + "\nbroken\n0\t\t1\n0\turl\t5")
        assertEquals(1, back.size)
        assertEquals("a b c", back[0].title)
        assertEquals("x y", back[0].channelName)
        assertEquals("ki", back[0].theme)
        assertEquals(ZenRating.LESS, back[0].value)
        assertNull(ZenRatings.decodeRatings(ZenRatings.encodeRatings(listOf(r(2, ZenRating.MORE))))[0].theme)
        val likes = ZenRatings.decodeLikes(ZenRatings.encodeLikes(listOf(ZenLikeEntry(0, "u", "T\tt", "C", 5L))) + "\nx")
        assertEquals(listOf(ZenLikeEntry(0, "u", "T t", "C", 5L)), likes)
    }

    @Test
    fun `factor formula, clamped, never zero`() {
        val none = ZenRatingWeights(emptyList())
        assertTrue(none.isEmpty())
        assertEquals(1.0, none.factor("0:y/c1", "ki"), 1e-9)
        val ch = ZenPraxisChannel.keyOf(0, "https://y/c1")
        val one = ZenRatingWeights(listOf(r(1, ZenRating.MORE, "https://y/c1", "ki")))
        assertEquals(1.10 * 1.05, one.factor(ch, "ki"), 1e-9)
        assertEquals(1.10, one.factor(ch, null), 1e-9)
        assertEquals(1.05, one.factor("other", "ki"), 1e-9)
        // two "more" on the channel: +2 steps, a third does not add (cap +2), with theme 1.32 -> 1.30
        val more = ZenRatingWeights((1..3).map { r(it, ZenRating.MORE, "https://y/c1", "ki") })
        assertEquals(Pair(2, 2), more.steps(ch, "ki"))
        assertEquals(1.2, more.factor(ch, null), 1e-9)
        assertEquals(ZenRatingWeights.MAX_FACTOR, more.factor(ch, "ki"), 1e-9)
        val less = ZenRatingWeights((1..5).map { r(it, ZenRating.LESS, "https://y/c1", "ki") })
        assertEquals(Pair(-3, -3), less.steps(ch, "ki"))
        assertEquals(0.8 * 0.8 * 0.8, less.factor(ch, null), 1e-9)
        assertEquals(ZenRatingWeights.MIN_FACTOR, less.factor(ch, "ki"), 1e-9)
        // more and less of the same channel cancel out
        val mixed = ZenRatingWeights(listOf(r(1, ZenRating.MORE, "https://y/c1"), r(2, ZenRating.LESS, "https://y/c1")))
        assertEquals(1.0, mixed.factor(ch, null), 1e-9)
        // unknown channel URL: no channel effect
        assertEquals(1.0, ZenRatingWeights(listOf(r(1, ZenRating.LESS).copy(channelKey = ""))).factor("", null), 1e-9)
        assertEquals(1, ZenRatingWeights.signOf(1.05))
        assertEquals(-1, ZenRatingWeights.signOf(0.9))
        assertEquals(0, ZenRatingWeights.signOf(1.0))
    }

    @Test
    fun `rating only reorders, a demoted video still fills a place and nothing becomes eligible`() {
        val plain = (1L..4L).map { cand(it) }
        val base = ZenFeedPlanner.select(plain, now, 4).picks.map { it.url }
        // demote the first, boost the last: same set, other order
        val rated = plain.mapIndexed { i, c ->
            if (i == 0) {
                c.copy(rating = 0.4)
            } else if (i == 3) {
                c.copy(rating = 1.3)
            } else {
                c
            }
        }
        val sel = ZenFeedPlanner.select(rated, now, 4)
        assertEquals(base.toSet(), sel.picks.map { it.url }.toSet())
        assertEquals("https://y/v4", sel.picks.first().url)
        assertEquals("https://y/v1", sel.picks.last().url)
        assertEquals(1, sel.picks.first().rated)
        assertEquals(-1, sel.picks.last().rated)
        assertEquals(0, sel.picks[1].rated)
        // a boost never lets a short video pass a longer one (D62) nor an ineligible one in
        val short = cand(9, rating = 1.3, sec = 60)
        val live = cand(10, rating = 1.3).copy(isLive = true)
        val sel2 = ZenFeedPlanner.select(listOf(cand(1, rating = 0.4), short, live), now, 3)
        assertEquals(listOf("https://y/v1", "https://y/v9"), sel2.picks.map { it.url })
        // budget unchanged
        assertEquals(2, ZenFeedPlanner.select(rated, now, 2).picks.size)
    }

    @Test
    fun `boost cannot lift news to normal weight`() {
        val news = cand(1).copy(zenCats = mapOf("aktuelles" to 0.9), classLevel = "D", rating = ZenRatingWeights.MAX_FACTOR)
        val other = cand(2).copy(zenCats = emptyMap(), rating = 1.0)
        assertTrue(
            ZenFeedPlanner.weightedScore(news, now, true) < ZenFeedPlanner.weightedScore(other, now, true)
        )
    }

    @Test
    fun `edition stores the rating sign, old lines stay readable`() {
        val store = MemoryZenStore()
        val st = ZenFeedState(store, null, zone)
        val v = st.view(now, true) { listOf(cand(1, rating = 1.3), cand(2, rating = 0.4), cand(3)) }
        assertEquals(listOf(1, 0, -1), v.open.map { it.rated })
        val again = ZenFeedState(store, null, zone).view(now + 60_000, true) { emptyList() }
        assertEquals(listOf(1, 0, -1), again.open.map { it.rated })
        assertTrue(again.open.all { it.why == null })
        // a stored 4-field line (before the rating) is read with rated = 0
        store.put("feed_edition_picks" to "https://y/v7\t20\tG\tD||1.00|")
        val old = ZenFeedState(store, null, zone).current()!!.picks.single()
        assertEquals(0, old.rated)
        assertEquals("D||1.00|", old.why)
    }
}

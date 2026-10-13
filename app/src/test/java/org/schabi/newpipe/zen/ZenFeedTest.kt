package org.schabi.newpipe.zen

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZenFeedTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val now = ZonedDateTime.of(2026, 10, 12, 14, 20, 0, 0, zone).toInstant().toEpochMilli()
    private val h = 3_600_000L
    private val min = 60

    private fun c(
        n: Long,
        ch: String = "c$n",
        title: String = "Video $n",
        ageH: Long = 1,
        sec: Long = 20L * 60,
        live: Boolean = false,
        rank: Int? = null,
        channelName: String = ch
    ) = ZenFeedPlanner.Candidate(
        "https://y/v$n", ch, title, channelName, now - ageH * h, sec, live, rank, rank?.let { "G$it" }
    )

    private fun many(n: Int) = (1L..n).map { c(it) }
    private fun state(store: ZenKeyValueStore = MemoryZenStore()) = ZenFeedState(store, null, zone)

    @Test
    fun `short, live and old videos are not eligible`() {
        assertFalse(ZenFeedPlanner.isEligible(c(1, sec = 4L * min + 59), now))
        assertTrue(ZenFeedPlanner.isEligible(c(1, sec = 5L * min), now))
        assertFalse(ZenFeedPlanner.isEligible(c(1, sec = -1), now))
        assertFalse(ZenFeedPlanner.isEligible(c(1, live = true), now))
        assertFalse(ZenFeedPlanner.isEligible(c(1, ageH = 24 * 7 + 1), now))
        assertTrue(ZenFeedPlanner.isEligible(c(1, ageH = 24 * 7 - 1), now))
    }

    @Test
    fun `music is excluded and gets no length bonus`() {
        assertFalse(ZenFeedPlanner.isEligible(c(1, channelName = "Artist - Topic"), now))
        assertFalse(ZenFeedPlanner.isEligible(c(1, channelName = "ArtistVEVO"), now))
        assertFalse(ZenFeedPlanner.isEligible(c(1, title = "Song (Official Music Video)"), now))
        assertFalse(ZenFeedPlanner.isEligible(c(1, rank = null).copy(groupName = "Musik"), now))
        val mix = c(1, title = "3 hour lofi mix", sec = 3 * 3600)
        val plain = c(2, title = "Talk", sec = 10L * min)
        assertTrue(ZenFeedPlanner.score(mix, now) <= ZenFeedPlanner.score(plain, now))
        assertTrue(
            ZenFeedPlanner.score(c(3, sec = 40L * min), now) >
                ZenFeedPlanner.score(c(4, sec = 10L * min), now)
        )
    }

    @Test
    fun `group priority beats length bonus, length beats short`() {
        val sel = ZenFeedPlanner.select(
            listOf(c(3, sec = 8L * min), c(2, sec = 60L * min), c(1, sec = 6L * min, rank = 0)),
            now,
            3
        )
        assertEquals(listOf("https://y/v1", "https://y/v2", "https://y/v3"), sel.picks.map { it.url })
    }

    @Test
    fun `at most 3 per channel and budget respected`() {
        val list = (1L..10L).map { c(it, ch = "same", ageH = it) } + c(11, ch = "other")
        val sel = ZenFeedPlanner.select(list, now, 15)
        assertEquals(4, sel.picks.size)
        assertEquals(11, sel.eligibleCount)
    }

    @Test
    fun `edition is stable, restart does not re-roll`() {
        val store = MemoryZenStore()
        val v1 = state(store).view(now, true) { many(40) }
        assertEquals(15, v1.open.size)
        val v2 = state(store).view(now + 10 * 60_000, true) { many(5) }
        assertEquals(v1.open, v2.open)
        assertEquals(25, v1.edition!!.leftover)
    }

    @Test
    fun `no edition is built before the intention prompt is confirmed`() {
        val s = state()
        val v = s.view(now, false) { many(40) }
        assertNull(v.edition)
        assertEquals(0, v.open.size)
        assertEquals(15, s.view(now, true) { many(40) }.open.size)
    }

    @Test
    fun `next edition 60 minutes after the last one`() {
        val s = state()
        s.view(now, true) { many(40) }
        val v = s.view(now + 59 * 60_000, true) { many(40) }
        assertEquals(now + h, v.nextEditionMillis)
        assertEquals(15, v.open.size)
        val v2 = s.view(now + h + 1000, true) { many(40) }
        assertEquals(now + 2 * h + 1000, v2.nextEditionMillis)
    }

    @Test
    fun `cap counts distinct videos, re-offered videos are free`() {
        val s = state()
        val pool = many(20)
        val v1 = s.view(now, true) { pool }
        val v2 = s.view(now + h, true) { pool } // same videos wait and are re-offered
        assertEquals(v1.open, v2.open)
        assertNotNull(v2.edition)
        assertTrue(v2.edition!!.createdAt > v1.edition!!.createdAt)
        assertFalse(v2.dailyCapReached) // 15 distinct, not 30
    }

    @Test
    fun `cap of 30 distinct videos ends the day`() {
        val s = state()
        val big = many(60)
        s.view(now, true) { big }.open.forEach { s.markHandled(it.url, "dismiss") }
        val v2 = s.view(now + h, true) { big }
        assertEquals(15, v2.open.size)
        assertTrue(v2.dailyCapReached)
        v2.open.forEach { s.markHandled(it.url, "dismiss") }
        val v3 = s.view(now + 3 * h, true) { big }
        assertEquals(v2.edition!!.createdAt, v3.edition!!.createdAt) // no new edition at the cap
        assertEquals(0, v3.open.size)
    }

    @Test
    fun `new zen day resets the cap`() {
        val s = state()
        s.view(now, true) { many(60) }
        s.view(now + h, true) { many(60) }
        val next = ZonedDateTime.of(2026, 10, 13, 5, 0, 0, 0, zone).toInstant().toEpochMilli()
        val v = s.view(next, true) { many(60) }
        assertFalse(v.dailyCapReached)
        assertEquals(15, v.open.size)
    }

    @Test
    fun `dismissed videos leave the edition and do not come back`() {
        val s = state()
        val gone = s.view(now, true) { many(40) }.open.first().url
        s.markHandled(gone, "dismiss")
        assertFalse(s.view(now, true) { many(40) }.open.any { it.url == gone })
        assertFalse(s.view(now + h, true) { many(40) }.open.any { it.url == gone })
    }

    @Test
    fun `empty data does not seal an edition`() {
        val s = state()
        assertNull(s.view(now, true) { emptyList() }.edition)
        assertNotNull(s.view(now, true) { many(20) }.edition)
    }

    @Test
    fun `zone is fixed after first use`() {
        val store = MemoryZenStore()
        store.put("zone_id" to "Asia/Tokyo")
        assertEquals(ZoneId.of("Asia/Tokyo"), ZenZone.fixed(store))
    }

    // ---------------------------------------------------------------- content weighting (AP 4)

    private val on = ZenFeedPlanner.ContentRules(weighting = true, heavyLimit = true)

    private fun cls(c: ZenFeedPlanner.Candidate, cats: Map<String, Double>?, tone: String? = "neutral", level: String = "T") = c.copy(zenCats = cats, tone = tone, classLevel = level)

    @Test
    fun `news goes behind education despite a better group rank`() {
        val news = cls(c(1, rank = 0), mapOf("aktuelles" to 0.9)) // 60 group points x 0.6 (title only)
        val edu = cls(c(2, rank = 2), mapOf("bildung" to 0.8)) // 30 group points x 1.0
        val off = ZenFeedPlanner.select(listOf(news, edu), now, 2)
        assertEquals(listOf("https://y/v1", "https://y/v2"), off.picks.map { it.url })
        val sel = ZenFeedPlanner.select(listOf(news, edu), now, 2, rules = on)
        assertEquals(listOf("https://y/v2", "https://y/v1"), sel.picks.map { it.url })
    }

    @Test
    fun `weights follow the bundle, no category and unclassified stay at 1`() {
        assertEquals(0.3, ZenFeedPlanner.contentWeight(cls(c(1), mapOf("aktuelles" to 0.9), level = "D")), 1e-9)
        // news from title + channel only: gentle (AP 0: news F1 only 58 % on short text)
        assertEquals(0.6, ZenFeedPlanner.contentWeight(cls(c(1), mapOf("aktuelles" to 0.9))), 1e-9)
        assertEquals(0.8, ZenFeedPlanner.contentWeight(cls(c(1), mapOf("aktuelles" to 0.9), "positiv")), 1e-9)
        assertEquals(0.8, ZenFeedPlanner.contentWeight(cls(c(1), mapOf("aktuelles" to 0.9), "positiv", "D")), 1e-9)
        assertEquals(0.5, ZenFeedPlanner.contentWeight(cls(c(1), mapOf("zerstreuung" to 0.7))), 1e-9)
        assertEquals(0.5, ZenFeedPlanner.contentWeight(cls(c(1), mapOf("zerstreuung" to 0.7, "aktuelles" to 0.8), level = "D")), 1e-9)
        assertEquals(0.15, ZenFeedPlanner.contentWeight(cls(c(1), mapOf("aktuelles" to 0.9), "belastend", "D")), 1e-9)
        assertEquals(0.3, ZenFeedPlanner.contentWeight(cls(c(1), mapOf("aktuelles" to 0.9), "belastend")), 1e-9)
        // several categories: the highest weight counts
        assertEquals(1.0, ZenFeedPlanner.contentWeight(cls(c(1), mapOf("bildung" to 0.7, "aktuelles" to 0.8))), 1e-9)
        // E4: classified without a category, not classified yet: normal weight
        assertEquals(1.0, ZenFeedPlanner.contentWeight(cls(c(1), emptyMap())), 1e-9)
        assertEquals(1.0, ZenFeedPlanner.contentWeight(cls(c(1), emptyMap(), "belastend")), 1e-9)
        assertEquals(1.0, ZenFeedPlanner.contentWeight(c(1)), 1e-9)
        // exception list: 1.0 and not heavy
        val exempt = cls(c(1), mapOf("aktuelles" to 0.9), "belastend").copy(classExempt = true)
        assertEquals(1.0, ZenFeedPlanner.contentWeight(exempt), 1e-9)
        assertFalse(ZenFeedPlanner.isHeavy(exempt))
    }

    @Test
    fun `without classification the order is unchanged (regression)`() {
        val pool = (1L..40L).map { c(it, ch = "c${it % 9}", ageH = it * 3, sec = (5L + it % 40) * min, rank = (it % 5).toInt().takeIf { r -> r < 4 }) }
        val before = ZenFeedPlanner.select(pool, now, 15)
        val withRulesNoData = ZenFeedPlanner.select(pool, now, 15, rules = on)
        assertEquals(before.picks.map { it.url }, withRulesNoData.picks.map { it.url })
        val allUncategorised = ZenFeedPlanner.select(pool.map { cls(it, emptyMap()) }, now, 15, rules = on)
        assertEquals(before.picks.map { it.url }, allUncategorised.picks.map { it.url })
        assertTrue(before.picks.all { it.why == null })
        assertTrue(withRulesNoData.picks.all { it.why == ZenFeedPlanner.WHY_UNCLASSIFIED })
    }

    @Test
    fun `heavy videos at most 1 of 15, not before place 10, never in a row`() {
        // heavy ones have the best base score (fresh, top group), light ones are older
        val heavy = (1L..5L).map { cls(c(it, ageH = 1, rank = 0), mapOf("bildung" to 0.8), "belastend") }
        val light = (10L..40L).map { cls(c(it, ageH = it), mapOf("bildung" to 0.8)) }
        val sel = ZenFeedPlanner.select(heavy + light, now, 15, rules = on)
        assertEquals(15, sel.picks.size)
        val heavyPlaces = sel.picks.mapIndexedNotNull { i, p -> if (p.url in heavy.map { it.url }) i + 1 else null }
        assertEquals(listOf(10), heavyPlaces)
        assertEquals(36 - 15, sel.eligibleCount - sel.picks.size) // skipped heavy ones wait as leftover
        // both rules off: the heavy ones lead by their base score
        val noLimit = ZenFeedPlanner.select(heavy + light, now, 15, rules = on.copy(heavyLimit = false, weighting = false))
        assertEquals(heavy.map { it.url }, noLimit.picks.take(5).map { it.url })
    }

    @Test
    fun `heavy rule rules`() {
        assertFalse(ZenFeedPlanner.heavyAllowed(9, 0, false, 15))
        assertTrue(ZenFeedPlanner.heavyAllowed(10, 0, false, 15))
        assertFalse(ZenFeedPlanner.heavyAllowed(11, 0, true, 15))
        assertFalse(ZenFeedPlanner.heavyAllowed(12, 1, false, 15)) // 2 of 15 > 10 %
        assertFalse(ZenFeedPlanner.heavyAllowed(10, 0, false, 9)) // a small edition has no place
        assertTrue(ZenFeedPlanner.heavyAllowed(12, 1, false, 20))
    }

    @Test
    fun `exception list lifts the weight and the heavy rule`() {
        val news = cls(c(1, rank = 0), mapOf("aktuelles" to 0.9), "belastend").copy(classExempt = true)
        val edu = cls(c(2, rank = 2), mapOf("bildung" to 0.8))
        val sel = ZenFeedPlanner.select(listOf(news, edu), now, 2, rules = on)
        assertEquals(listOf("https://y/v1", "https://y/v2"), sel.picks.map { it.url })
        assertEquals(ZenFeedPlanner.WHY_EXEMPT, sel.picks[0].why)
    }

    @Test
    fun `why is stored with the edition`() {
        val store = MemoryZenStore()
        val pool = listOf(
            cls(c(1, rank = 1), mapOf("aktuelles" to 0.82), "positiv"),
            cls(c(2), emptyMap()),
            c(3)
        )
        val v1 = state(store).view(now, true, on) { pool }
        val why = v1.open.associate { it.url to it.why }
        assertEquals("T|positiv|0.80|aktuelles=0.82", why["https://y/v1"])
        assertEquals("T|neutral|1.00|", why["https://y/v2"])
        assertEquals(ZenFeedPlanner.WHY_UNCLASSIFIED, why["https://y/v3"])
        // reload from the store: identical (group names may be empty)
        assertEquals(v1.open, state(store).view(now + 60_000, true, on) { pool }.open)
    }
}

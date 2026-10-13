package org.schabi.newpipe.zen.classify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.zen.classify.ZenChannelProfile.Group
import org.schabi.newpipe.zen.classify.ZenChannelProfile.Item
import org.schabi.newpipe.zen.classify.ZenChannelProfile.Kind

/** Task A: the rough channel picture of the "take over channel" sheet. */
class ZenChannelProfileTest {
    private val praxis = mapOf("praxis" to 0.9)
    private val ruhe = mapOf("ruhe" to 0.8)
    private val news = mapOf("aktuelles" to 0.9)
    private val edu = mapOf("bildung" to 0.8)
    private val ent = mapOf("zerstreuung" to 0.7)
    private val none = emptyMap<String, Double>()

    private fun items(vararg g: Triple<Int, Map<String, Double>?, String?>) = g.flatMap { (n, c, tone) -> (1..n).map { Item("${c?.keys?.firstOrNull() ?: "x"} $it", c, tone, "D") } }

    private fun t(n: Int, c: Map<String, Double>?, tone: String? = "neutral") = Triple(n, c, tone)

    @Test
    fun `mostly practice counts calm as practice`() {
        val p = ZenChannelProfile.evaluate(items(t(8, praxis), t(3, ruhe), t(4, edu)), weighting = true, exempt = false)
        assertEquals(Kind.MOSTLY, p.kind)
        assertEquals(Group.PRAXIS, p.main)
        assertEquals(11, p.mainCount)
        assertEquals(15, p.n)
        assertFalse(p.lowFeedWeight)
        assertEquals(0, p.news)
    }

    @Test
    fun `news channel with distressing tone is weighted low in the feed`() {
        val p = ZenChannelProfile.evaluate(items(t(12, news, ZenRules.TONE_HEAVY), t(3, edu)), weighting = true, exempt = false)
        assertEquals(Kind.MOSTLY, p.kind)
        assertEquals(Group.AKTUELLES, p.main)
        assertEquals(12, p.news)
        assertEquals(12, p.heavy)
        assertTrue(p.lowFeedWeight)
        // without weighting or as an exception there is no such note
        assertFalse(ZenChannelProfile.evaluate(items(t(12, news), t(3, edu)), weighting = false, exempt = false).lowFeedWeight)
        assertFalse(ZenChannelProfile.evaluate(items(t(12, news), t(3, edu)), weighting = true, exempt = true).lowFeedWeight)
    }

    @Test
    fun `few news videos give no feed note`() {
        val p = ZenChannelProfile.evaluate(items(t(4, news), t(11, edu)), weighting = true, exempt = false)
        assertEquals(Group.BILDUNG, p.main)
        assertFalse(p.lowFeedWeight)
        assertEquals(4, p.news)
    }

    @Test
    fun `mixed and unclear`() {
        val mixed = ZenChannelProfile.evaluate(items(t(6, edu), t(5, news), t(4, ent)), weighting = true, exempt = false)
        assertEquals(Kind.MIXED, mixed.kind)
        assertEquals(listOf(Group.BILDUNG to 6, Group.AKTUELLES to 5, Group.UNTERHALTUNG to 4), mixed.counts)
        val unclear = ZenChannelProfile.evaluate(items(t(8, none), t(7, edu)), weighting = true, exempt = false)
        assertEquals(Kind.UNCLEAR, unclear.kind)
        assertEquals(8, unclear.none)
        assertEquals(Kind.EMPTY, ZenChannelProfile.evaluate(emptyList(), weighting = true, exempt = false).kind)
        // failed classification (cats null) counts as without category
        assertEquals(8, ZenChannelProfile.evaluate(items(t(8, null), t(7, edu)), weighting = true, exempt = false).none)
    }

    @Test
    fun `examples cover the groups and keep the channel order`() {
        val list = items(t(5, praxis), t(3, news), t(2, none))
        val p = ZenChannelProfile.evaluate(list, weighting = true, exempt = false)
        assertEquals(ZenChannelProfile.EXAMPLES, p.examples.size)
        assertTrue(p.examples.any { it.cats == praxis })
        assertTrue(p.examples.any { it.cats == news })
        assertTrue(p.examples.any { it.cats.isNullOrEmpty() })
        assertEquals(p.examples, list.filter { it in p.examples })
    }

    @Test
    fun `feed weight is the one of the feed`() {
        assertEquals(0.3, ZenChannelProfile.feedWeight(Item("n", news, "neutral", "D")), 1e-9)
        assertEquals(0.6, ZenChannelProfile.feedWeight(Item("n", news, "neutral", "T")), 1e-9)
        assertEquals(1.0, ZenChannelProfile.feedWeight(Item("p", praxis, "neutral", "D")), 1e-9)
        assertEquals(0.5, ZenChannelProfile.feedWeight(Item("p", praxis, ZenRules.TONE_HEAVY, "D")), 1e-9)
    }
}

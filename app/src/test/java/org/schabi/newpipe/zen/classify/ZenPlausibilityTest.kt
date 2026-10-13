package org.schabi.newpipe.zen.classify

import org.junit.Assert.assertEquals
import org.junit.Test
import org.schabi.newpipe.zen.classify.ZenPlausibility.Item
import org.schabi.newpipe.zen.classify.ZenPlausibility.Result

class ZenPlausibilityTest {
    private val praxis = mapOf("praxis" to 0.9)
    private val ruhe = mapOf("ruhe" to 0.8)
    private val news = mapOf("aktuelles" to 0.9)
    private val ent = mapOf("zerstreuung" to 0.7)
    private val edu = mapOf("bildung" to 0.8)
    private val none = emptyMap<String, Double>()

    private fun items(vararg groups: Pair<Int, Map<String, Double>?>) = groups.flatMap { (n, c) -> (1..n).map { Item("v$it $c", c, "D") } }

    @Test
    fun `practice and calm from 60 percent fit`() {
        val v = ZenPlausibility.evaluate(items(6 to praxis, 3 to ruhe, 6 to edu))
        assertEquals(Result.FITS, v.result)
        assertEquals(9, v.hit)
        assertEquals(15, v.n)
    }

    @Test
    fun `30 to 60 percent warns, below 30 percent warns strongly`() {
        assertEquals(Result.WARN, ZenPlausibility.evaluate(items(8 to praxis, 7 to edu)).result) // 53 %
        assertEquals(Result.WARN, ZenPlausibility.evaluate(items(5 to praxis, 10 to edu)).result) // 33 %
        assertEquals(Result.STRONG_WARN, ZenPlausibility.evaluate(items(4 to praxis, 11 to edu)).result) // 27 %
        assertEquals(Result.STRONG_WARN, ZenPlausibility.evaluate(items(15 to news)).result)
    }

    @Test
    fun `enough practice but 20 percent news or entertainment warns`() {
        val v = ZenPlausibility.evaluate(items(10 to praxis, 2 to news, 1 to ent, 2 to edu))
        assertEquals(Result.WARN, v.result)
        assertEquals(3, v.distraction)
        assertEquals("v1 {aktuelles=0.9}", v.example)
        // 2 of 15 = 13 %: still fits
        assertEquals(Result.FITS, ZenPlausibility.evaluate(items(11 to praxis, 2 to ent, 2 to edu)).result)
    }

    @Test
    fun `practice that also looks like entertainment is no warning sign`() {
        val both = mapOf("praxis" to 0.8, "zerstreuung" to 0.6)
        val v = ZenPlausibility.evaluate(items(15 to both))
        assertEquals(Result.FITS, v.result)
        assertEquals(0, v.distraction)
        assertEquals(15, v.ent)
    }

    @Test
    fun `8 of 15 without category is no clear picture, not a warning`() {
        assertEquals(Result.UNCLEAR, ZenPlausibility.evaluate(items(8 to none, 7 to news)).result)
        assertEquals(Result.UNCLEAR, ZenPlausibility.evaluate(items(4 to null, 4 to none, 7 to praxis)).result)
        assertEquals(Result.STRONG_WARN, ZenPlausibility.evaluate(items(7 to none, 8 to news)).result)
        assertEquals(Result.UNCLEAR, ZenPlausibility.evaluate(emptyList()).result)
    }

    @Test
    fun `small samples scale, levels and log`() {
        val v = ZenPlausibility.evaluate(listOf(Item("a", praxis, "D"), Item("b", praxis, "T"), Item("c", edu, "D")))
        assertEquals(Result.FITS, v.result) // 2 of 3
        assertEquals("mixed", v.level)
        assertEquals(
            "kind=PRAXIS target=playlist n=3 hit=2 news=0 ent=0 none=0 level=mixed result=fits",
            ZenPlausibility.logDetail(v, true)
        )
    }
}

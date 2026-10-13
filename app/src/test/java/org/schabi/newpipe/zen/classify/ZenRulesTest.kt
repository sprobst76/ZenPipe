package org.schabi.newpipe.zen.classify

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The 200 rule golden cases of the bundle (probabilities only, no titles) and the feed cap. */
class ZenRulesTest {
    private fun probs(o: JsonObject?): Map<String, Double> = o?.mapValues { it.value.jsonPrimitive.double } ?: emptyMap()

    @Test
    fun `200 golden cases give the same tone, categories and weight as the Python rules`() {
        val text = javaClass.getResourceAsStream("/zen/classify/golden_cases.json")!!.bufferedReader().readText()
        val cases = Json.parseToJsonElement(text).jsonArray
        assertEquals(200, cases.size)
        cases.forEachIndexed { i, c ->
            val o = c.jsonObject
            val tone = ZenRules.toneFromProbs(probs(o["tones"]?.jsonObject))
            val cats = ZenRules.categories(
                probs(o["themes"]?.jsonObject),
                probs(o["formats"]?.jsonObject),
                o["clickbait"]!!.jsonPrimitive.boolean,
                tone
            )
            val expect = o["expect"]!!.jsonObject
            val wantTone = expect["tone"].let { if (it == null || it is JsonNull) null else it.jsonPrimitive.content }
            assertEquals("case $i tone", wantTone, tone)
            assertEquals("case $i cats", probs(expect["cats"]?.jsonObject), cats)
            assertEquals("case $i weight", expect["weight"]!!.jsonPrimitive.double, ZenRules.weight(cats, tone), 1e-9)
        }
    }

    @Test
    fun `feed keeps heavy videos rare, never adjacent and not in the first nine`() {
        val items = (0 until 40).map { ZenRules.FeedItem("L$it", mapOf("bildung" to 0.9)) } +
            (0 until 40).map { ZenRules.FeedItem("H$it", mapOf("bildung" to 0.99), ZenRules.TONE_HEAVY) }
        val feed = ZenRules.composeFeed(items, 30)
        val heavy = feed.indices.filter { feed[it].startsWith("H") }
        assertTrue(heavy.size <= 3)
        assertTrue(heavy.zipWithNext().all { (a, b) -> b - a > 1 })
        assertFalse(feed.take(9).any { it.startsWith("H") })
    }

    @Test
    fun `clickbait needs two all-caps words or double punctuation`() {
        assertFalse(ZenRules.isClickbait("Ein ruhiger Titel"))
        assertFalse(ZenRules.isClickbait("NASA erklärt"))
        assertTrue(ZenRules.isClickbait("DAS ÄNDERT ALLES"))
        assertTrue(ZenRules.isClickbait("Wirklich?!"))
        assertTrue(ZenRules.isClickbait("Achtung!!"))
        assertFalse(ZenRules.isClickbait("ABC_def GHIJ")) // "ABC_def" is one word, not all caps
        assertFalse(ZenRules.isClickbait(null))
    }

    @Test
    fun `weight has no category = 0 in the bundle rules, news positive 0_8, heavy halved`() {
        assertEquals(0.0, ZenRules.weight(emptyMap()), 0.0)
        assertEquals(0.3, ZenRules.weight(mapOf("aktuelles" to 0.8)), 1e-9)
        assertEquals(0.8, ZenRules.weight(mapOf("aktuelles" to 0.8), ZenRules.TONE_POSITIVE), 1e-9)
        assertEquals(0.5, ZenRules.weight(mapOf("bildung" to 0.8, "aktuelles" to 0.7), ZenRules.TONE_HEAVY), 1e-9)
    }
}

package org.schabi.newpipe.zen.classify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cache rules: when to recompute, retention after last access, value encoding. */
class ZenClassCacheTest {
    private fun entry(key: String, level: String = "T", model: String = "m1", at: Long = 0) = ClassEntry(
        key, "Titel", "Kanal", level, model, mapOf("bildung" to 0.85), "neutral",
        listOf("wissenschaft" to 0.9, "erklaerung_analyse" to 0.7), 1.0, 20, 300, at, at
    )

    @Test
    fun `missing, other model or title-only with details now need work`() {
        val c = ZenClassCache(MemoryClassStore())
        assertTrue(c.needsWork(null, "T", "m1"))
        assertFalse(c.needsWork(entry("a"), "T", "m1"))
        assertTrue(c.needsWork(entry("a"), "D", "m1")) // details known now: level D
        assertFalse(c.needsWork(entry("a", level = "D"), "T", "m1")) // never downgrade D to T
        assertFalse(c.needsWork(entry("a", level = "D"), "D", "m1"))
        assertTrue(c.needsWork(entry("a", level = "D"), "D", "m2")) // model changed
    }

    @Test
    fun `entries expire 30 days after the last access`() {
        val c = ZenClassCache(MemoryClassStore())
        c.put(entry("old", at = 0))
        c.put(entry("kept", at = 0))
        c.touch(listOf("kept"), ZenClassCache.RETENTION_MS)
        assertEquals(1, c.purgeAccessedBefore(ZenClassCache.RETENTION_MS))
        assertNull(c.get("old"))
        assertEquals("kept", c.get("kept")!!.key)
    }

    @Test
    fun `categories and why line survive encoding`() {
        val e = entry("a")
        assertEquals(e.cats, ClassEntry.decodeMap(ClassEntry.encodeMap(e.cats)))
        assertEquals(e.top, ClassEntry.decodePairs(ClassEntry.encodePairs(e.top)))
        assertEquals(emptyMap<String, Double>(), ClassEntry.decodeMap(ClassEntry.encodeMap(emptyMap())))
        assertEquals("bildung=0.850", ClassEntry.encodeMap(mapOf("bildung" to 0.85)))
    }

    @Test
    fun `key is service id and url`() {
        assertEquals("0:https://www.youtube.com/watch?v=x", ZenClassCache.keyOf(0, "https://www.youtube.com/watch?v=x"))
    }

    @Test
    fun `recent lists newest first`() {
        val c = ZenClassCache(MemoryClassStore())
        c.put(entry("a", at = 1))
        c.put(entry("b", at = 3))
        c.put(entry("c", at = 2))
        assertEquals(listOf("b", "c"), c.recent(2).map { it.key })
        assertEquals(3, c.count())
    }

    @Test
    fun `strongest theme ignores formats and old entries without a theme`() {
        assertEquals("wissenschaft" to 0.9, ZenClassWhy.strongestTheme(entry("a").top))
        val formatsFirst = listOf("erklaerung_analyse" to 0.8, "tech_gadgets_hardware" to 0.42, "ki" to 0.3)
        assertEquals("tech_gadgets_hardware" to 0.42, ZenClassWhy.strongestTheme(formatsFirst))
        // stored before themes were always kept: only formats -> nothing to name
        assertNull(ZenClassWhy.strongestTheme(listOf("erklaerung_analyse" to 0.8, "news_aktuell" to 0.1)))
        assertNull(ZenClassWhy.strongestTheme(emptyList()))
        assertTrue(ZenClassWhy.belowThreshold(0.42))
        assertFalse(ZenClassWhy.belowThreshold(0.6))
    }

    @Test
    fun `top keeps the strongest theme even when formats lead`() {
        val a = Analysis(
            "t", 1, FloatArray(0),
            themes = mapOf("ki" to 0.3, "wissenschaft" to 0.2),
            formats = mapOf("erklaerung_analyse" to 0.9, "doku_lang" to 0.8, "vortrag_interview" to 0.7),
            tones = emptyMap(), tone = null, categories = emptyMap(), weight = 1.0
        )
        assertEquals(listOf("erklaerung_analyse", "doku_lang", "vortrag_interview", "ki"), a.top().map { it.first })
        val b = a.copy(themes = mapOf("ki" to 0.95))
        assertEquals(listOf("ki", "erklaerung_analyse", "doku_lang"), b.top().map { it.first })
    }
}

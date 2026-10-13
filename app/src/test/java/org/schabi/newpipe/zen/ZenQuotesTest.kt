package org.schabi.newpipe.zen

import java.io.File
import java.time.ZoneId
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pause quotes: no repeat until all were shown, persistence, language, own quotes, empty pool. */
class ZenQuotesTest {
    private val en = (1..12).map { "Quote $it" }
    private val de = (1..12).map { "Spruch $it" }

    private fun draw(q: ZenQuotes, pool: List<String>, n: Int) = (1..n).map { q.next(pool)!! }

    @Test
    fun `no repetition until the whole pool was shown, then a new round`() {
        val q = ZenQuotes(MemoryZenStore(), Random(1))
        val round1 = draw(q, en, en.size)
        assertEquals(en.toSet(), round1.toSet())
        val round2 = draw(q, en, en.size)
        assertEquals(en.toSet(), round2.toSet())
        assertNotEquals(round1.last(), round2.first()) // never the same twice in a row across rounds
    }

    @Test
    fun `never the same quote twice in a row over many rounds`() {
        val q = ZenQuotes(MemoryZenStore(), Random(7))
        val seq = draw(q, en.take(3), 300)
        seq.zipWithNext().forEach { (a, b) -> assertNotEquals(a, b) }
    }

    @Test
    fun `progress survives a restart (new instance on the same store)`() {
        val store = MemoryZenStore()
        val first = draw(ZenQuotes(store, Random(2)), en, 5)
        val rest = draw(ZenQuotes(store, Random(3)), en, en.size - 5)
        assertEquals(en.toSet(), (first + rest).toSet())
        assertEquals(en.size, (first + rest).distinct().size)
    }

    @Test
    fun `quote is stable within one pause and changes with the next pause`() {
        val store = MemoryZenStore()
        val a = ZenQuotes(store, Random(4)).forPause(1, en)
        assertEquals(a, ZenQuotes(store, Random(5)).forPause(1, en))
        assertEquals(a, ZenQuotes(store, Random(6)).forPause(1, en))
        val b = ZenQuotes(store, Random(4)).forPause(2, en)
        assertNotEquals(a, b)
    }

    @Test
    fun `language switch draws from the other list and is stable afterwards`() {
        val store = MemoryZenStore()
        val q = ZenQuotes(store, Random(8))
        val enQuote = q.forPause(1, en)
        assertTrue(enQuote in en)
        // same pause, app language now German: the English quote is not in the pool any more
        val deQuote = q.forPause(1, de)
        assertTrue(deQuote in de)
        assertEquals(deQuote, q.forPause(1, de))
        // a whole German round without repeats
        val round = listOf(deQuote!!) + draw(q, de, de.size - 1)
        assertEquals(de.toSet(), round.toSet())
    }

    @Test
    fun `own quotes are mixed in, or alone with only-own`() {
        val q = ZenQuotes(MemoryZenStore(), Random(9))
        assertTrue(q.addCustom("  Mine 1 "))
        assertTrue(q.addCustom("Mine 2"))
        assertFalse(q.addCustom("Mine 1")) // duplicate
        assertFalse(q.addCustom("   ")) // empty
        assertEquals(listOf("Mine 1", "Mine 2"), q.custom())
        assertEquals((en + listOf("Mine 1", "Mine 2")).toSet(), draw(q, en, en.size + 2).toSet())

        q.setOnlyCustom(true)
        assertEquals(setOf("Mine 1", "Mine 2"), q.pool(en).toSet())
        draw(q, en, 10).forEach { assertTrue(it.startsWith("Mine")) }

        q.removeCustom("Mine 1")
        assertEquals(listOf("Mine 2"), q.custom())
        assertEquals("Mine 2", q.next(en))
        assertEquals("Mine 2", q.next(en)) // single quote may repeat
    }

    @Test
    fun `only-own with an empty own list falls back to the built-in quotes`() {
        val q = ZenQuotes(MemoryZenStore(), Random(10))
        q.setOnlyCustom(true)
        assertTrue(q.next(en) in en)
    }

    @Test
    fun `empty pool gives no quote`() {
        val q = ZenQuotes(MemoryZenStore())
        assertNull(q.next(emptyList()))
        assertNull(q.forPause(1, emptyList()))
        q.setOnlyCustom(true)
        assertNull(q.forPause(2, emptyList()))
    }

    @Test
    fun `own quotes are limited in length and number`() {
        val q = ZenQuotes(MemoryZenStore())
        assertTrue(q.addCustom("x".repeat(ZenQuotes.MAX_LENGTH + 50)))
        assertEquals(ZenQuotes.MAX_LENGTH, q.custom().single().length)
        (2..ZenQuotes.MAX_CUSTOM).forEach { assertTrue(q.addCustom("q$it")) }
        assertFalse(q.addCustom("one too many"))
    }

    @Test
    fun `every started pause gets a new number`() {
        val store = MemoryZenStore()
        var mono = 1_000_000L
        val e = ZenPauseEngine(
            store,
            ZenCountdown(store, "pause", { mono }, { 1 }),
            { mono },
            { 1_800_000_000_000L + mono },
            { ZoneId.of("Europe/Berlin") },
            { _, _ -> }
        )
        assertEquals(0L, e.pauseSeq())
        fun watchAndClose(url: String) {
            e.onItem(url, 60 * 60_000L)
            e.onPlaying(true, 0)
            mono += 10 * 60_000L
            e.onProgress(10 * 60_000L, 60 * 60_000L, true)
            e.onClosed(10 * 60_000L, 60 * 60_000L)
        }
        watchAndClose("v1")
        assertEquals(1L, e.pauseSeq())
        mono += 60 * 60_000L
        watchAndClose("v2")
        assertEquals(2L, e.pauseSeq())
    }

    /** The shipped lists: 80-100 per language, same count, no duplicates, generated from sprueche.md. */
    @Test
    fun `shipped quote resources are complete`() {
        fun items(path: String): List<String> {
            val f = listOf(File(path), File("app/$path")).first { it.exists() }
            return Regex("<item>(.*?)</item>").findAll(f.readText()).map { it.groupValues[1] }.toList()
        }
        val enRes = items("src/main/res/values/zen_quotes.xml")
        val deRes = items("src/main/res/values-de/zen_quotes.xml")
        assertTrue(enRes.size in 80..110)
        assertEquals(enRes.size, deRes.size)
        assertEquals(enRes.size, enRes.distinct().size)
        assertEquals(deRes.size, deRes.distinct().size)
        val doc = listOf(File("../zen-docs/sprueche.md"), File("zen-docs/sprueche.md")).first { it.exists() }
        val rows = doc.readLines().count { Regex("^\\|\\s*[QEA]\\d+\\s*\\|").containsMatchIn(it) }
        assertEquals("regenerate with zen-docs/gen_sprueche.py", rows, enRes.size)
    }
}

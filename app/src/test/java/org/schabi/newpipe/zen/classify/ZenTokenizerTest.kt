package org.schabi.newpipe.zen.classify

import java.io.File
import java.util.Base64
import kotlin.random.Random
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Tokenizer, text builder and Base64. The expected values in `tokenizer_cases.json` are invented
 * neutral texts; their IDs/texts come from the bundle's Python reference (`unigram_ref.py`,
 * `text_builder.py`) with the bundle's `tokenizer.bin`. Cases that need the real tokenizer run only
 * when `ZEN_MODEL_DIR` points to a directory with `tokenizer.bin` (not in the repo).
 */
class ZenTokenizerTest {
    private val cases: JsonObject by lazy {
        Json.parseToJsonElement(javaClass.getResourceAsStream("/zen/classify/tokenizer_cases.json")!!.bufferedReader().readText()).jsonObject
    }

    private fun ints(a: JsonArray) = a.map { it.jsonPrimitive.int }.toIntArray()

    /** Tiny tokenizer with an identity normalisation (empty trie). */
    private fun tiny(): UnigramTokenizer {
        val vocab = listOf(
            "<s>" to 0.0, "<pad>" to 0.0, "</s>" to 0.0, "<unk>" to 0.0, "▁" to -2.0, "▁ab" to -1.0,
            "a" to -3.0, "b" to -3.0, "▁a" to -2.5, "c" to -3.0, "abc" to -1.5
        )
        val pieces = HashMap<String, Int>()
        vocab.forEachIndexed { i, (p, _) -> pieces[p] = i }
        return UnigramTokenizer(IntArray(256), ByteArray(1), pieces, vocab.map { it.second }.toDoubleArray(), 3)
    }

    @Test
    fun `viterbi picks the best segmentation`() {
        val t = tiny()
        assertArrayEquals(intArrayOf(0, 5, 2), t.encode("ab"))
        assertArrayEquals(intArrayOf(0, 4, 10, 2), t.encode("abc")) // "▁"+"abc" (-3.5) beats "▁ab"+"c" (-4)
        assertArrayEquals(intArrayOf(0, 2), t.encode(""))
    }

    @Test
    fun `unknown characters become one fused unk per word`() {
        assertArrayEquals(intArrayOf(0, 8, 3, 2), tiny().encode("axx"))
        assertArrayEquals(intArrayOf(0, 8, 3, 5, 2), tiny().encode("axx ab"))
    }

    @Test
    fun `whitespace splitting and truncation keep bos and eos`() {
        val t = tiny()
        assertArrayEquals(intArrayOf(0, 5, 5, 2), t.encode("  ab\t\n　ab "))
        assertArrayEquals(intArrayOf(0, 5, 5, 2), t.encode("ab ab ab ab", maxLen = 4))
    }

    @Test
    fun `code points without String codePoints`() {
        assertArrayEquals(intArrayOf(0x1F600, 'a'.code, 0x20AC), UnigramTokenizer.codePoints("😀a€"))
        assertEquals(0, UnigramTokenizer.codePoints("").size)
    }

    @Test
    fun `invented texts give the reference IDs (needs ZEN_MODEL_DIR)`() {
        val dir = System.getenv("ZEN_MODEL_DIR")?.let(::File)
        assumeTrue("ZEN_MODEL_DIR not set", dir != null && File(dir, "tokenizer.bin").isFile)
        val tok = UnigramTokenizer.load(File(dir, "tokenizer.bin").readBytes())
        for (c in cases["tokens"]!!.jsonArray) {
            val o = c.jsonObject
            val text = o["text"]!!.jsonPrimitive.content
            assertArrayEquals(text.take(40), ints(o["ids128"]!!.jsonArray), tok.encode(text, 128))
            assertArrayEquals(text.take(40), ints(o["ids256"]!!.jsonArray), tok.encode(text, 256))
        }
    }

    @Test
    fun `text builder matches the Python text builder`() {
        for (c in cases["texts"]!!.jsonArray) {
            val o = c.jsonObject
            fun str(k: String) = o[k]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content
            val v = VideoText(
                str("title"),
                str("channel"),
                str("description"),
                str("category"),
                o["tags"]?.jsonArray?.map { it.jsonPrimitive.content }
            )
            assertEquals(o["expect"]!!.jsonPrimitive.content, TextBuilder.build(v))
        }
    }

    @Test
    fun `category names from the extractor map to IDs`() {
        assertEquals("27", TextBuilder.categoryIdOf("Education"))
        assertEquals("28", TextBuilder.categoryIdOf("Science & Technology"))
        assertEquals("26", TextBuilder.categoryIdOf("Howto & Style"))
        assertEquals("10", TextBuilder.categoryIdOf("Music"))
        assertEquals("25", TextBuilder.categoryIdOf("25"))
        assertNull(TextBuilder.categoryIdOf("Unbekannt"))
        assertNull(TextBuilder.categoryIdOf(""))
        assertEquals(
            "query: Titel — Kanal | Kategorie: Bildung",
            TextBuilder.build(VideoText("Titel", "Kanal", categoryId = TextBuilder.categoryIdOf("Education")))
        )
    }

    @Test
    fun `html descriptions become plain text`() {
        val html = "Erste Zeile<br>Zweite <a href=\"https://example.org\">example.org</a><br><br>Ende &amp; aus"
        assertEquals("Erste Zeile Zweite example.org Ende & aus", TextBuilder.plainDescription(html, true))
        assertEquals("a  b", TextBuilder.plainDescription("a  b", false))
        assertNull(TextBuilder.plainDescription("  ", true))
    }

    @Test
    fun `level is T for title and channel, D with details`() {
        assertEquals("T", VideoText("a", "b").level)
        assertEquals("D", VideoText("a", "b", description = "x").level)
        assertEquals("D", VideoText("a", "b", categoryId = "27").level)
    }

    @Test
    fun `own base64 decodes like java util Base64`() {
        val r = Random(7)
        for (n in 0..40) {
            val bytes = r.nextBytes(n)
            val enc = Base64.getEncoder().encodeToString(bytes)
            assertArrayEquals(bytes, ZenBase64.decode(enc))
            assertArrayEquals(bytes, ZenBase64.decode(enc.trimEnd('=')))
        }
        assertArrayEquals(floatArrayOf(1.0f, -2.5f), ZenBase64.floats(Base64.getEncoder().encodeToString(byteArrayOf(0, 0, 0x80.toByte(), 0x3F, 0, 0, 0x20, 0xC0.toByte()))), 0f)
        assertArrayEquals(intArrayOf(1, 258), ZenBase64.ints("AQAAAAIBAAA="))
    }
}

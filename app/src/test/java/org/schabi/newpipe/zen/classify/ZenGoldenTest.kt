package org.schabi.newpipe.zen.classify

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The bundle's `golden.json` (40 real videos) reproduced with tokenizer, vocabulary map, heads and
 * rules; the encoder is replaced by the reference embedding of each case. `golden.json` contains
 * real titles and stays out of the repo: this test runs only when `ZEN_GOLDEN_DIR` points to a
 * local directory with `golden.json`, `tokenizer.bin`, `vocab_map.json` and `heads.json`.
 */
class ZenGoldenTest {
    @Test
    fun `golden cases match token IDs, categories and tone (needs ZEN_GOLDEN_DIR)`() {
        val dir = System.getenv("ZEN_GOLDEN_DIR")?.let(::File)
        assumeTrue("ZEN_GOLDEN_DIR not set", dir != null && File(dir, "golden.json").isFile)
        val golden = Json.parseToJsonElement(File(dir, "golden.json").readText()).jsonArray
        val noEncoder = Encoder { throw UnsupportedOperationException() }
        val minP = golden[0].jsonObject["min_p"]?.jsonPrimitive?.double ?: ZenRules.MIN_P_SMALL
        val pipe = ZenPipeline.fromDir(dir!!, noEncoder, minP = minP, maxLen = 256)
        var idsOk = 0
        var catsOk = 0
        var toneOk = 0
        for (g in golden) {
            val o = g.jsonObject
            val text = o["text"]!!.jsonPrimitive.content
            val ids = pipe.ids(text).map { it.toInt() }
            if (ids == o["ids"]!!.jsonArray.map { it.jsonPrimitive.int }) idsOk++
            val x = o["embedding"]!!.jsonArray.map { it.jsonPrimitive.double.toFloat() }.toFloatArray()
            val title = text.substringBefore(" — ").removePrefix("query: ")
            val a = pipe.fromEmbedding(text, ids.size, x, title)
            if (a.categories.keys == o["cats"]!!.jsonObject.keys) catsOk++
            val wantTone = o["tone"].let { if (it == null || it is JsonNull) null else it.jsonPrimitive.content }
            if (a.tone == wantTone) toneOk++
        }
        println("golden: ids $idsOk, categories $catsOk, tone $toneOk of ${golden.size}")
        assertEquals(golden.size, idsOk)
        assertEquals(golden.size, catsOk)
        assertEquals(golden.size, toneOk)
    }
}

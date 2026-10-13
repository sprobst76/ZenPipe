/*
 * SPDX-FileCopyrightText: 2026 Stefan Probst-Schendzielorz
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.zen.classify

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * `golden_synthetic.json` (AP 0): 52 INVENTED videos (title + channel), expected values = the
 * reference pipeline (Python, ORT 1.31). Text building and rules run always; tokenizer, vocabulary
 * map and heads (on the reference embedding) only when `ZEN_MODEL_DIR` points to an unpacked
 * model (`tokenizer.bin`, `vocab_map.json`, `heads.json`), otherwise that part is skipped.
 */
class ZenSyntheticGoldenTest {
    private val cases: JsonArray by lazy {
        Json.parseToJsonElement(javaClass.getResourceAsStream("/zen/classify/golden_synthetic.json")!!.bufferedReader().readText()).jsonArray
    }

    private fun JsonObject.str(k: String) = this[k]!!.jsonPrimitive.content

    private fun JsonObject.probs(k: String) = this[k]!!.jsonObject.mapValues { it.value.jsonPrimitive.double }

    private fun JsonObject.toneOrNull() = this["tone"]?.jsonPrimitive?.takeIf { it.isString }?.content

    @Test
    fun `text building and rules reproduce the reference`() {
        assertEquals(52, cases.size)
        for (g in cases) {
            val o = g.jsonObject
            // most cases are title + channel only; the others carry details the fixture has only as text
            if (" | " !in o.str("text")) {
                assertEquals(o.str("title"), o.str("text"), TextBuilder.build(VideoText(o.str("title"), o.str("channel"))))
            }
            assertEquals(o.str("title"), o["clickbait"]!!.jsonPrimitive.boolean, ZenRules.isClickbait(o.str("title")))
            val cats = ZenRules.categories(
                o.probs("themes"),
                o.probs("formats"),
                o["clickbait"]!!.jsonPrimitive.boolean,
                o.toneOrNull(),
                o["min_p"]!!.jsonPrimitive.double
            )
            assertEquals(o.str("title"), o.probs("cats"), cats)
            assertEquals(o.str("title"), o["weight"]!!.jsonPrimitive.double, ZenRules.weight(cats, o.toneOrNull()), 1e-9)
            assertEquals(o.str("title"), o.toneOrNull(), ZenRules.toneFromProbs(o.probs("tones")))
        }
    }

    @Test
    fun `tokenizer, map and heads reproduce the reference (needs ZEN_MODEL_DIR)`() {
        val dir = System.getenv("ZEN_MODEL_DIR")?.let(::File)
        assumeTrue("ZEN_MODEL_DIR not set", dir != null && File(dir, ZenPipeline.TOKENIZER_FILE).isFile)
        val noEncoder = Encoder { throw UnsupportedOperationException() }
        val first = cases[0].jsonObject
        val pipe = ZenPipeline.fromDir(dir!!, noEncoder, first["min_p"]!!.jsonPrimitive.double, first["max_len"]!!.jsonPrimitive.int)
        var ok = 0
        for (g in cases) {
            val o = g.jsonObject
            val ids = pipe.ids(o.str("text")).map { it.toInt() }
            assertEquals(o.str("title"), o["ids"]!!.jsonArray.map { it.jsonPrimitive.int }, ids)
            val x = o["embedding"]!!.jsonArray.map { it.jsonPrimitive.double.toFloat() }.toFloatArray()
            val a = pipe.fromEmbedding(o.str("text"), ids.size, x, o.str("title"))
            assertEquals(o.str("title"), o.probs("cats").keys, a.categories.keys)
            assertEquals(o.str("title"), o.toneOrNull(), a.tone)
            ok++
        }
        println("synthetic golden: $ok of ${cases.size}")
    }
}

/*
 * SPDX-FileCopyrightText: 2026 Stefan Probst-Schendzielorz
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.zen.classify

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Computes the (L2-normalised) embedding for token IDs in the reduced vocabulary, one video per call. */
fun interface Encoder {
    fun embed(ids: LongArray): FloatArray
}

/** Result for one video. [themes]/[formats] keep only probabilities >= 0.05 (for "why"). */
data class Analysis(
    val text: String,
    val tokens: Int,
    val embedding: FloatArray,
    val themes: Map<String, Double>,
    val formats: Map<String, Double>,
    val tones: Map<String, Double>,
    val tone: String?,
    val categories: Map<String, Double>,
    val weight: Double
) {
    /**
     * The [n] strongest themes and formats, for the "why" line; the strongest theme is always
     * included (appended when only formats made the top [n]), so "no category" can name it.
     */
    fun top(n: Int = 3): List<Pair<String, Double>> {
        val best = (themes.entries + formats.entries).sortedByDescending { it.value }.take(n).map { it.key to it.value }
        val theme = themes.entries.maxByOrNull { it.value } ?: return best
        return if (best.any { it.first == theme.key }) best else best + (theme.key to theme.value)
    }
}

/**
 * Title/channel/description -> categories and tone. [minP] 0.6 for the small model, [maxLen] 128
 * tokens (bundle recommendation: same quality as 256, 1.6x faster).
 */
class ZenPipeline(
    private val tokenizer: UnigramTokenizer,
    private val vocabMap: IntArray,
    private val encoder: Encoder,
    private val heads: Heads,
    val minP: Double = ZenRules.MIN_P_SMALL,
    val maxLen: Int = DEFAULT_MAX_LEN
) {
    companion object {
        const val DEFAULT_MAX_LEN = 128
        const val TOKENIZER_FILE = "tokenizer.bin"
        const val VOCAB_MAP_FILE = "vocab_map.json"
        const val HEADS_FILE = "heads.json"
        private const val UNK_NEW = 3

        /** `vocab_map.json`: `old_to_new_b64_int32` = little-endian int32, index = old ID. */
        fun vocabMapFromJson(json: String): IntArray {
            val b64 = Json.parseToJsonElement(json).jsonObject["old_to_new_b64_int32"]!!.jsonPrimitive.content
            return ZenBase64.ints(b64)
        }

        /** Loads tokenizer, vocabulary map and heads from the model directory. */
        fun fromDir(dir: File, encoder: Encoder, minP: Double = ZenRules.MIN_P_SMALL, maxLen: Int = DEFAULT_MAX_LEN) = ZenPipeline(
            UnigramTokenizer.load(File(dir, TOKENIZER_FILE).readBytes()),
            vocabMapFromJson(File(dir, VOCAB_MAP_FILE).readText()),
            encoder,
            Heads.fromJson(File(dir, HEADS_FILE).readText()),
            minP,
            maxLen
        )
    }

    /** Token IDs as the encoder sees them (reduced vocabulary). */
    fun ids(text: String): LongArray {
        val old = tokenizer.encode(text, maxLen)
        return LongArray(old.size) { (vocabMap.getOrNull(old[it]) ?: UNK_NEW).toLong() }
    }

    fun analyzeText(text: String, title: String): Analysis {
        val ids = ids(text)
        return fromEmbedding(text, ids.size, encoder.embed(ids), title)
    }

    /** Heads + rules on a given embedding (also used by tests with reference embeddings). */
    fun fromEmbedding(text: String, tokens: Int, x: FloatArray, title: String): Analysis {
        val themes = heads.theme.proba(x).filterValues { it >= 0.05 }
        val formats = heads.format.proba(x).filterValues { it >= 0.05 }
        val tones = heads.tone.proba(x)
        val tone = ZenRules.toneFromProbs(tones)
        val cats = ZenRules.categories(themes, formats, ZenRules.isClickbait(title), tone, minP)
        return Analysis(text, tokens, x, themes, formats, tones, tone, cats, ZenRules.weight(cats, tone))
    }

    fun analyze(v: VideoText): Analysis = analyzeText(TextBuilder.build(v), v.title ?: "")
}

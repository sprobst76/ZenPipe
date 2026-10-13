/*
 * SPDX-FileCopyrightText: 2026 Stefan Probst-Schendzielorz
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.zen.classify

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * XLM-R tokenizer (Unigram + sentencepiece normalisation) without a native library; reads
 * `tokenizer.bin` (format "ZTK1", see zen-docs/spec-klassifikator-integration.md). Steps:
 * precompiled charsmap (double-array trie, longest match) -> whitespace split -> metaspace
 * ("▁" + word) -> Unigram Viterbi per word -> `<s>` ... `</s>`. IDs are in the original vocabulary;
 * map them with the vocabulary map before running the encoder.
 *
 * Ported from the YoutubeManager reference (`unigram_ref.py`); avoids `String.codePoints()` (API 24).
 */
class UnigramTokenizer(
    private val units: IntArray,
    private val norm: ByteArray,
    private val pieces: HashMap<String, Int>,
    private val scores: DoubleArray,
    val unkId: Int
) {
    private val minScore = scores.minOrNull() ?: 0.0
    private val maxPieceLen = pieces.keys.maxOfOrNull { it.codePointCount(0, it.length) } ?: 1

    companion object {
        const val BOS = 0
        const val EOS = 2
        private const val UNK_PENALTY = 10.0
        private const val MAGIC = 0x314B545A // "ZTK1" little-endian
        private const val META = 0x2581 // "▁"

        fun load(bytes: ByteArray): UnigramTokenizer {
            val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            require(bb.int == MAGIC) { "not a tokenizer.bin (magic ZTK1)" }
            val n = bb.int
            val unk = bb.int
            val trieBytes = bb.int
            val normBytes = bb.int
            val units = IntArray(trieBytes / 4) { bb.int }
            val norm = ByteArray(normBytes).also { bb.get(it) }
            val pieces = HashMap<String, Int>(n * 2)
            val scores = DoubleArray(n)
            for (i in 0 until n) {
                val len = bb.short.toInt() and 0xFFFF
                val s = String(bytes, bb.position(), len, Charsets.UTF_8)
                bb.position(bb.position() + len)
                scores[i] = bb.float.toDouble() // float32 -> double is exact
                pieces.putIfAbsent(s, i)
            }
            return UnigramTokenizer(units, norm, pieces, scores, unk)
        }

        /** Code points of [s] (replacement for `String.codePoints()`, which needs API 24). */
        fun codePoints(s: String): IntArray {
            val out = IntArray(s.codePointCount(0, s.length))
            var i = 0
            var k = 0
            while (i < s.length) {
                val cp = s.codePointAt(i)
                out[k++] = cp
                i += Character.charCount(cp)
            }
            return out
        }

        // Unicode White_Space (like Rust's \s in the HF WhitespaceSplit)
        private fun isWs(cp: Int) = cp in 9..13 || cp == 32 || cp == 0x85 || cp == 0xA0 || cp == 0x1680 ||
            cp in 0x2000..0x200A || cp == 0x2028 || cp == 0x2029 || cp == 0x202F || cp == 0x205F || cp == 0x3000
    }

    // --- normalisation: darts-clone double array over UTF-8 bytes, longest match
    private fun offset(unit: Int): Int = (unit ushr 10) shl ((unit and 0x200) ushr 6)

    /** Longest match at [pos]: (length in bytes, offset into the norm blob); length 0 = no match. */
    private fun longest(data: ByteArray, pos: Int): Long {
        var node = 0
        var unit = units[node]
        node = node xor offset(unit)
        var bestLen = 0
        var bestVal = 0
        for (i in pos until data.size) {
            val c = data[i].toInt() and 0xFF
            if (c == 0) break
            node = node xor c
            if (node !in units.indices) break
            unit = units[node]
            if ((unit and 0x800000FF.toInt()) != c) break
            node = node xor offset(unit)
            if ((unit ushr 8) and 1 == 1) {
                bestLen = i - pos + 1
                bestVal = units[node] and 0x7FFFFFFF
            }
        }
        return (bestLen.toLong() shl 32) or bestVal.toLong()
    }

    fun normalize(text: String): String {
        val data = text.toByteArray(Charsets.UTF_8)
        val out = java.io.ByteArrayOutputStream(data.size + 16)
        var pos = 0
        while (pos < data.size) {
            val m = longest(data, pos)
            val len = (m ushr 32).toInt()
            if (len == 0) {
                val b = data[pos].toInt() and 0xFF
                val step = if (b < 0x80) {
                    1
                } else if (b < 0xE0) {
                    2
                } else if (b < 0xF0) {
                    3
                } else {
                    4
                }
                val end = minOf(pos + step, data.size)
                out.write(data, pos, end - pos)
                pos = end
            } else {
                val value = (m and 0xFFFFFFFFL).toInt()
                var end = value
                while (norm[end].toInt() != 0) end++
                out.write(norm, value, end - value)
                pos += len
            }
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }

    private fun splitWords(text: String): List<IntArray> {
        val cps = codePoints(text)
        val words = ArrayList<IntArray>()
        var i = 0
        while (i < cps.size) {
            while (i < cps.size && isWs(cps[i])) i++
            val start = i
            while (i < cps.size && !isWs(cps[i])) i++
            if (i > start) words.add(cps.copyOfRange(start, i))
        }
        return words
    }

    private fun word(cps: IntArray, out: MutableList<Int>) {
        val n = cps.size
        val best = DoubleArray(n + 1) { -1e30 }
        val from = IntArray(n + 1) { -1 }
        val isUnk = BooleanArray(n + 1)
        best[0] = 0.0
        for (i in 0 until n) {
            if (best[i] <= -1e29) continue
            var single = false
            val maxJ = minOf(n, i + maxPieceLen)
            for (j in i + 1..maxJ) {
                val id = pieces[String(cps, i, j - i)] ?: continue
                if (j == i + 1) single = true
                val sc = best[i] + scores[id]
                if (sc > best[j]) {
                    best[j] = sc
                    from[j] = i
                    isUnk[j] = false
                }
            }
            if (!single) {
                val sc = best[i] + minScore - UNK_PENALTY
                if (sc > best[i + 1]) {
                    best[i + 1] = sc
                    from[i + 1] = i
                    isUnk[i + 1] = true
                }
            }
        }
        val ids = ArrayList<Int>()
        var j = n
        while (j > 0) {
            val i = from[j]
            ids.add(if (isUnk[j]) unkId else pieces.getValue(String(cps, i, j - i)))
            j = i
        }
        ids.reverse()
        var prev = -1
        for (t in ids) { // fuse consecutive <unk> inside a word
            if (t == unkId && prev == unkId) continue
            out.add(t)
            prev = t
        }
    }

    /** IDs like HF (`<s>` ... `</s>`), at most [maxLen] tokens. */
    fun encode(text: String, maxLen: Int = 256): IntArray {
        val body = ArrayList<Int>()
        for (w in splitWords(normalize(text))) {
            val withMeta = IntArray(w.size + 1)
            withMeta[0] = META
            System.arraycopy(w, 0, withMeta, 1, w.size)
            word(withMeta, body)
        }
        val keep = minOf(body.size, maxLen - 2)
        val out = IntArray(keep + 2)
        out[0] = BOS
        for (k in 0 until keep) out[k + 1] = body[k]
        out[keep + 1] = EOS
        return out
    }
}

/*
 * SPDX-FileCopyrightText: 2026 Stefan Probst-Schendzielorz
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.zen.classify

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal standard Base64 decoder (RFC 4648, with or without padding, whitespace ignored).
 * Own implementation because `java.util.Base64` needs API 26 and `android.util.Base64` does not
 * run in JVM unit tests.
 */
object ZenBase64 {
    private val TABLE = IntArray(128) { -1 }.also { t ->
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".forEachIndexed { i, c -> t[c.code] = i }
    }

    fun decode(s: String): ByteArray {
        val out = java.io.ByteArrayOutputStream(s.length * 3 / 4)
        var acc = 0
        var bits = 0
        for (ch in s) {
            if (ch == '=') break
            if (ch == ' ' || ch == '\n' || ch == '\r' || ch == '\t') continue
            val v = if (ch.code < 128) TABLE[ch.code] else -1
            require(v >= 0) { "invalid Base64 character '$ch'" }
            acc = (acc shl 6) or v
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.write((acc shr bits) and 0xFF)
            }
        }
        return out.toByteArray()
    }

    /** Little-endian float32 array. */
    fun floats(s: String): FloatArray {
        val bb = ByteBuffer.wrap(decode(s)).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(bb.remaining() / 4) { bb.float }
    }

    /** Little-endian int32 array. */
    fun ints(s: String): IntArray {
        val bb = ByteBuffer.wrap(decode(s)).order(ByteOrder.LITTLE_ENDIAN)
        return IntArray(bb.remaining() / 4) { bb.int }
    }
}

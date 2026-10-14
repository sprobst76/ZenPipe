/*
 * SPDX-FileCopyrightText: 2026 Stefan Probst-Schendzielorz
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.zen.classify

/**
 * Display-only reflow of the shipped license notices for a narrow phone screen: hard line breaks
 * inside paragraphs (the ONNX Runtime notices are wrapped at ~80 columns) are joined, and long
 * separator lines ("=====", "_____") become a short rule that never wraps. The raw files stay
 * unchanged; nothing is removed, only whitespace between words changes.
 */
object ZenNoticeText {
    /** A line this long (or longer) was most likely wrapped by hand, so the next line continues it. */
    private const val MIN_WRAPPED = 60
    const val RULE = "────────"

    private val SEPARATOR = Regex("^\\s*([=\\-_*~#])\\1{3,}\\s*$")
    private val STARTS_BLOCK = Regex("^\\s*([*\\-•·]\\s|\\d{1,3}[.)]\\s|\\([a-zA-Z0-9]{1,4}\\)\\s|https?://|www\\.)")

    @JvmStatic
    fun reflow(text: String): String {
        val out = ArrayList<String>()
        var para: StringBuilder? = null
        var lastLen = 0
        fun flush() {
            para?.let { out.add(it.toString()) }
            para = null
        }
        for (raw in text.replace("\r\n", "\n").split('\n')) {
            val line = raw.trimEnd()
            when {
                line.isBlank() -> {
                    flush()
                    out.add("")
                }

                SEPARATOR.matches(line) -> {
                    flush()
                    out.add(RULE)
                }

                para != null && lastLen >= MIN_WRAPPED && !STARTS_BLOCK.containsMatchIn(line) &&
                    !para.toString().endsWith(":") -> {
                    para!!.append(' ').append(line.trim())
                    lastLen = line.length
                }

                else -> {
                    flush()
                    para = StringBuilder(line)
                    lastLen = line.length
                }
            }
        }
        flush()
        // at most one empty line in a row
        return out.joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim()
    }
}

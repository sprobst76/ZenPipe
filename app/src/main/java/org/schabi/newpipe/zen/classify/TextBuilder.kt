/*
 * SPDX-FileCopyrightText: 2026 Stefan Probst-Schendzielorz
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.zen.classify

import org.jsoup.Jsoup

/** What the classifier gets to see of one video. Only [title] is required. */
data class VideoText(
    val title: String?,
    val channel: String?,
    val description: String? = null,
    val categoryId: String? = null,
    val tags: List<String>? = null,
    /** Language reported with the details (feed language filter only, not part of the text). */
    val langHint: String? = null,
    /** Channel URL from the details (feed language filter only, not part of the text). */
    val channelUrl: String? = null
) {
    /** Input level for the cache: "T" = title + channel only, "D" = with details (description/tags/category). */
    val level: String
        get() = if (!description.isNullOrBlank() || !tags.isNullOrEmpty() || !categoryId.isNullOrEmpty()) "D" else "T"
}

/**
 * Encoder input text (mode "rich"), identical to the server (`student_text` / `text_builder.py`):
 * `query: Title — Channel | Kategorie: … | Tags: a, b | Description` (empty parts are left out,
 * tags <= 200 characters, description <= 600, both counted in code points like Python).
 */
object TextBuilder {
    const val MAX_DESC = 600
    const val MAX_TAGS_CHARS = 200

    /** YouTube category ID -> German name (the heads were trained with these, `yt_categories.json`). */
    val CATEGORIES: Map<String, String> = mapOf(
        "1" to "Film & Animation",
        "2" to "Autos & Fahrzeuge",
        "10" to "Musik",
        "15" to "Tiere",
        "17" to "Sport",
        "19" to "Reisen & Events",
        "20" to "Gaming",
        "22" to "Menschen & Blogs",
        "23" to "Comedy",
        "24" to "Unterhaltung",
        "25" to "Nachrichten & Politik",
        "26" to "Anleitungen & Stil",
        "27" to "Bildung",
        "28" to "Wissenschaft & Technik",
        "29" to "Gemeinnütziges & Aktivismus"
    )

    /** English category name as delivered by the extractor (`StreamInfo.getCategory`) -> ID. */
    private val ENGLISH_TO_ID: Map<String, String> = mapOf(
        "film & animation" to "1",
        "autos & vehicles" to "2",
        "music" to "10",
        "pets & animals" to "15",
        "sports" to "17",
        "travel & events" to "19",
        "gaming" to "20",
        "people & blogs" to "22",
        "comedy" to "23",
        "entertainment" to "24",
        "news & politics" to "25",
        "howto & style" to "26",
        "education" to "27",
        "science & technology" to "28",
        "nonprofits & activism" to "29"
    )

    /** Category ID for an English category name (or an ID), null if unknown. */
    fun categoryIdOf(name: String?): String? {
        val n = name?.trim().orEmpty()
        if (n.isEmpty()) return null
        if (n in CATEGORIES) return n
        return ENGLISH_TO_ID[n.lowercase(java.util.Locale.ROOT)]
    }

    /** Description as plain text; YouTube delivers HTML (links, line breaks). */
    fun plainDescription(content: String?, isHtml: Boolean): String? {
        if (content.isNullOrBlank()) return null
        return if (isHtml) Jsoup.parse(content).text() else content
    }

    // Python's str.isspace(): needed for texts identical to the server
    private fun isSpace(cp: Int) = cp in 9..13 || cp in 0x1C..0x20 || cp == 0x85 || cp == 0xA0 || cp == 0x1680 ||
        cp in 0x2000..0x200A || cp == 0x2028 || cp == 0x2029 || cp == 0x202F || cp == 0x205F || cp == 0x3000

    private fun splitSpaces(s: String): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            if (isSpace(cp)) {
                if (cur.isNotEmpty()) {
                    out.add(cur.toString())
                    cur.setLength(0)
                }
            } else {
                cur.appendCodePoint(cp)
            }
            i += Character.charCount(cp)
        }
        if (cur.isNotEmpty()) out.add(cur.toString())
        return out
    }

    private fun strip(s: String): String {
        val cps = UnigramTokenizer.codePoints(s)
        var a = 0
        var b = cps.size
        while (a < b && isSpace(cps[a])) a++
        while (b > a && isSpace(cps[b - 1])) b--
        return String(cps, a, b - a)
    }

    private fun rstrip(s: String): String {
        val cps = UnigramTokenizer.codePoints(s)
        var b = cps.size
        while (b > 0 && isSpace(cps[b - 1])) b--
        return String(cps, 0, b)
    }

    /** Python `s[:n]` on code points, not on UTF-16 units. */
    private fun head(s: String, n: Int): String {
        val cps = UnigramTokenizer.codePoints(s)
        return if (cps.size <= n) s else String(cps, 0, n)
    }

    private fun len(s: String) = s.codePointCount(0, s.length)

    fun build(v: VideoText): String {
        val t = strip(v.title ?: "")
        val c = strip(v.channel ?: "")
        val parts = ArrayList<String>()
        parts.add(if (c.isNotEmpty()) "$t — $c" else t)
        CATEGORIES[v.categoryId ?: ""]?.takeIf { it.isNotEmpty() }?.let { parts.add("Kategorie: $it") }
        val tags = v.tags
        if (!tags.isNullOrEmpty()) {
            val joined = tags.joinToString(", ")
            if (joined.isNotEmpty()) {
                parts.add("Tags: " + if (len(joined) <= MAX_TAGS_CHARS) joined else rstrip(head(joined, MAX_TAGS_CHARS - 1)) + "…")
            }
        }
        val desc = splitSpaces(v.description ?: "").joinToString(" ")
        if (desc.isNotEmpty()) parts.add(if (len(desc) <= MAX_DESC) desc else rstrip(head(desc, MAX_DESC - 1)) + "…")
        return "query: " + parts.joinToString(" | ")
    }
}

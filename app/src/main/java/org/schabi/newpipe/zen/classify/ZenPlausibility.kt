/*
 * SPDX-FileCopyrightText: 2026 Stefan Probst-Schendzielorz
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.zen.classify

/**
 * Plausibility check when a channel/playlist is marked as practice (spec 4.4): how many of its
 * newer videos the content recognition sees as practice. Pure logic; it only ever warns, the
 * marking itself stays the user's decision (rules.md, Praxis/Zuordnung).
 */
object ZenPlausibility {
    /** Videos checked (the newest of a channel, the first of a playlist). */
    const val SAMPLE = 15

    /** Practice share from which the check says "fits" (E8: start value, calibrate with 6.3). */
    const val FITS_SHARE = 0.6

    /** Below this share: strong warning. */
    const val STRONG_SHARE = 0.3

    /** News + entertainment share from which a warning is shown even with enough practice. */
    const val DISTRACTION_SHARE = 0.2

    /** "No clear picture" from 8 of 15 unclassified videos. */
    const val UNCLEAR_SHARE = 8.0 / 15.0

    private val PRACTICE = setOf("praxis", "ruhe")

    enum class Result { FITS, WARN, STRONG_WARN, UNCLEAR }

    /** One checked video; [cats] null = could not be classified at all. */
    data class Item(val title: String, val cats: Map<String, Double>?, val level: String? = null)

    data class Verdict(
        val result: Result,
        val n: Int,
        val hit: Int,
        val news: Int,
        val ent: Int,
        /** News or entertainment (counted once per video). */
        val distraction: Int,
        val none: Int,
        /** Title of the first news/entertainment video, for the warning text. */
        val example: String?,
        /** "D" all with details, "T" all title + channel only, "mixed", "-" none. */
        val level: String
    )

    @JvmStatic
    fun evaluate(items: List<Item>): Verdict {
        val n = items.size
        val hit = items.count { i -> i.cats?.keys?.any { it in PRACTICE } == true }
        val news = items.count { it.cats?.containsKey("aktuelles") == true }
        val ent = items.count { it.cats?.containsKey("zerstreuung") == true }
        val distr = items.filter { i -> i.cats?.let { "aktuelles" in it || "zerstreuung" in it } == true }
        // a practice video that also looks like entertainment is not a warning sign
        val distraction = distr.count { i -> i.cats!!.keys.none { it in PRACTICE } }
        val none = items.count { it.cats.isNullOrEmpty() }
        val levels = items.mapNotNull { it.level }.toSet()
        val level = when {
            levels.isEmpty() -> "-"
            levels.size == 1 -> levels.first()
            else -> "mixed"
        }
        val example = distr.firstOrNull { i -> i.cats!!.keys.none { it in PRACTICE } }?.title
        val result = when {
            n == 0 || none >= UNCLEAR_SHARE * n - 1e-9 -> Result.UNCLEAR
            hit < STRONG_SHARE * n - 1e-9 -> Result.STRONG_WARN
            hit >= FITS_SHARE * n - 1e-9 && distraction < DISTRACTION_SHARE * n - 1e-9 -> Result.FITS
            else -> Result.WARN
        }
        return Verdict(result, n, hit, news, ent, distraction, none, example, level)
    }

    /** ZenLog detail (spec 4.4 step 6). */
    @JvmStatic
    fun logDetail(v: Verdict, playlist: Boolean): String = "kind=PRAXIS target=${if (playlist) "playlist" else "channel"} n=${v.n} hit=${v.hit} " +
        "news=${v.news} ent=${v.ent} none=${v.none} level=${v.level} result=${v.result.name.lowercase()}"
}

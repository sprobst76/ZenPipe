/*
 * SPDX-FileCopyrightText: 2026 Stefan Probst-Schendzielorz
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.zen.classify

import android.content.Context
import java.text.NumberFormat
import org.schabi.newpipe.R
import org.schabi.newpipe.zen.ZenFeedPlanner

/** Human readable "why" of the content recognition (feed entry, plausibility check). */
object ZenClassWhy {
    @JvmStatic
    fun catName(context: Context, cat: String): String = context.getString(
        when (cat) {
            "praxis" -> R.string.zen_cat_praxis
            "ruhe" -> R.string.zen_cat_ruhe
            "psyche" -> R.string.zen_cat_psyche
            "bildung" -> R.string.zen_cat_bildung
            "koerper" -> R.string.zen_cat_koerper
            "aktuelles" -> R.string.zen_cat_aktuelles
            else -> R.string.zen_cat_zerstreuung
        }
    )

    private fun num(v: Double): String = NumberFormat.getNumberInstance().apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }.format(v)

    private fun toneName(context: Context, tone: String?): String? = when (tone) {
        ZenRules.TONE_HEAVY -> context.getString(R.string.zen_tone_belastend)
        ZenRules.TONE_POSITIVE -> context.getString(R.string.zen_tone_positiv)
        else -> null
    }

    private fun levelName(context: Context, level: String?): String? = when (level) {
        "T" -> context.getString(R.string.zen_why_level_t)
        "D" -> context.getString(R.string.zen_why_level_d)
        else -> null
    }

    /** "News 0.82 · distressing · title and channel only" (categories, tone, level). */
    @JvmStatic
    fun summary(context: Context, cats: Map<String, Double>, tone: String?, level: String?): String {
        val parts = ArrayList<String>()
        parts.add(
            if (cats.isEmpty()) {
                context.getString(R.string.zen_why_no_category)
            } else {
                cats.entries.joinToString(", ") { "${catName(context, it.key)} ${num(it.value)}" }
            }
        )
        toneName(context, tone)?.let { parts.add(it) }
        levelName(context, level)?.let { parts.add(it) }
        return parts.joinToString(" · ")
    }

    /** Feed entry line from a stored [ZenFeedPlanner.Pick.why]; null = nothing to show. */
    @JvmStatic
    fun feedLine(context: Context, why: String?): String? {
        if (why.isNullOrEmpty()) return null
        if (why == ZenFeedPlanner.WHY_EXEMPT) return context.getString(R.string.zen_why_exempt)
        if (why == ZenFeedPlanner.WHY_UNCLASSIFIED) return context.getString(R.string.zen_why_unclassified)
        val p = why.split('|', limit = 4)
        if (p.size < 4) return null
        val cats = ClassEntry.decodeMap(p[3])
        val weight = p[2].toDoubleOrNull() ?: 1.0
        val main = summary(context, cats, p[1].ifEmpty { null }, null)
        val level = levelName(context, p[0].ifEmpty { null })
        // weight only when it moves the video back (1.0 = normal place)
        val w = if (weight < 0.995) context.getString(R.string.zen_why_weight, num(weight)) else null
        return listOfNotNull(main, w, level).joinToString(" · ")
    }
}

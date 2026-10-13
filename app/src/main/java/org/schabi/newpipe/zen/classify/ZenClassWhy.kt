/*
 * SPDX-FileCopyrightText: 2026 Stefan Probst-Schendzielorz
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.zen.classify

import android.content.Context
import java.text.NumberFormat
import org.schabi.newpipe.R
import org.schabi.newpipe.zen.ZenFeedPlanner

/** Human readable "why" of the content recognition (feed entry, plausibility check, review list). */
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

    /** Format keys of the bundle taxonomy (v5); every other key in [ClassEntry.top] is a theme. */
    val FORMAT_KEYS = setOf(
        "mitmach_praxis", "tutorial_anleitung", "erklaerung_analyse", "vortrag_interview", "doku_lang",
        "news_aktuell", "review_test", "reaktion_kommentar", "unterhaltung_vlog", "audio_musik"
    )

    /**
     * The strongest theme among the stored top themes/formats (null if none is stored, e.g. an
     * entry from before themes were always kept). Display only, never used for a decision.
     */
    @JvmStatic
    fun strongestTheme(top: List<Pair<String, Double>>): Pair<String, Double>? = top.filter { it.first !in FORMAT_KEYS }.maxByOrNull { it.second }

    /**
     * Whether a theme probability is below the category threshold of the on-device model. A
     * category also needs the right format or other conditions, so a strong theme can still give
     * no category (then "below threshold" is not said).
     */
    @JvmStatic
    fun belowThreshold(p: Double): Boolean = p < ZenRules.MIN_P_SMALL

    /** Short display name of a theme key (a handful of main themes), otherwise the raw key. */
    @JvmStatic
    fun themeName(context: Context, key: String): String = themeRes(key)?.let { context.getString(it) } ?: key

    private fun themeRes(key: String): Int? = when (key) {
        "ki" -> R.string.zen_theme_ki
        "ki_gesellschaft_ethik" -> R.string.zen_theme_ki_gesellschaft
        "programmieren" -> R.string.zen_theme_programmieren
        "tech_gadgets_hardware" -> R.string.zen_theme_technik
        "linux_maker_smarthome" -> R.string.zen_theme_linux_maker
        "it_sicherheit_datenschutz" -> R.string.zen_theme_it_sicherheit
        "wissenschaft" -> R.string.zen_theme_wissenschaft
        "philosophie_gesellschaft_geschichte" -> R.string.zen_theme_gesellschaft
        "politik_nachrichten" -> R.string.zen_theme_politik
        "finanzen_wirtschaft" -> R.string.zen_theme_wirtschaft
        "gesundheit_medizin" -> R.string.zen_theme_gesundheit
        "psyche_therapie" -> R.string.zen_theme_psyche
        "gaming" -> R.string.zen_theme_gaming
        "film_serien" -> R.string.zen_theme_film
        "musik" -> R.string.zen_theme_musik
        "sport" -> R.string.zen_theme_sport
        "reisen_outdoor" -> R.string.zen_theme_reisen
        "sonstiges" -> R.string.zen_theme_sonstiges
        else -> null
    }

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

    /** "no category (strongest topic: Tech 0.42, below threshold)", or "no category" without a theme. */
    private fun noCategory(context: Context, theme: Pair<String, Double>?): String {
        if (theme == null) return context.getString(R.string.zen_why_no_category)
        return context.getString(
            if (belowThreshold(theme.second)) R.string.zen_why_no_category_theme_below else R.string.zen_why_no_category_theme,
            themeName(context, theme.first),
            num(theme.second)
        )
    }

    /**
     * "News 0.82 · distressing · title and channel only" (categories, tone, level). Without a
     * category the strongest theme of [top] is named (display only).
     */
    @JvmStatic
    @JvmOverloads
    fun summary(context: Context, cats: Map<String, Double>, tone: String?, level: String?, top: List<Pair<String, Double>> = emptyList()): String {
        val parts = ArrayList<String>()
        parts.add(
            if (cats.isEmpty()) {
                noCategory(context, strongestTheme(top))
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
        val p = ZenFeedPlanner.parseWhy(why) ?: return null
        val main = summary(context, p.cats, p.tone, null, listOfNotNull(p.theme))
        val level = levelName(context, p.level)
        // weight only when it moves the video back (1.0 = normal place)
        val w = if (p.weight < 0.995) context.getString(R.string.zen_why_weight, num(p.weight)) else null
        return listOfNotNull(main, w, level).joinToString(" · ")
    }
}

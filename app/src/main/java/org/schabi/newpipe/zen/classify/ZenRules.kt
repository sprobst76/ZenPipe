/*
 * SPDX-FileCopyrightText: 2026 Stefan Probst-Schendzielorz
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.zen.classify

import kotlin.math.max
import kotlin.math.min

/**
 * Zen content rules (port of `zen_rules.py`, rule set v5): pure functions on head probabilities.
 * [categories]' `minP`: 0.7 for the server model (e5-base), [MIN_P_SMALL] 0.6 for the on-device
 * model (e5-small). A video can have several categories or none.
 */
object ZenRules {
    const val MIN_P_SERVER = 0.7
    const val MIN_P_SMALL = 0.6

    private const val POLITICS_BLOCK = 0.4
    private const val POLITICS_REACTION = 0.25
    private const val CLICKBAIT_FACTOR = 0.85
    private const val DOMAIN_MIN = 0.6
    private const val STRONG_KNOWLEDGE = 0.85
    private const val PSYCHE_CORE_MIN = 0.55
    private const val PSYCHE_SOFT_NEEDS_CORE = 0.5
    private const val POLITICS_ANY = 0.5
    private const val SOFT_THEME_MIN = 0.45
    private const val SOFT_FORMAT_MIN = 0.8
    private const val TONE_HEAVY_MIN = 0.4
    private const val TONE_POSITIVE_MIN = 0.5
    private const val WEIGHT_POSITIVE_NEWS = 0.8
    private const val BELASTEND_FACTOR = 0.5

    const val TONE_HEAVY = "belastend"
    const val TONE_POSITIVE = "positiv"
    const val TONE_NEUTRAL = "neutral"

    private val PRACTICE_FORMATS = setOf("mitmach_praxis")
    private val PRACTICE_THEMES = setOf("yoga", "atmen_breathwork", "meditation_hypnose", "mobilitaet_kraft_training", "schlaf_klang")
    private val CALM_FORMATS = setOf("audio_musik")
    private val EDUCATION_FORMATS = setOf("tutorial_anleitung", "erklaerung_analyse", "vortrag_interview", "doku_lang")
    private val KNOWLEDGE_THEMES = setOf(
        "programmieren", "linux_maker_smarthome", "ki", "ki_gesellschaft_ethik", "office_pkm_software",
        "xr_vr", "foto_video_design", "tech_gadgets_hardware", "it_sicherheit_datenschutz", "rad_mtb", "wissenschaft",
        "philosophie_gesellschaft_geschichte", "haushalt_diy_vorsorge", "finanzen_wirtschaft"
    )
    private val DOMAIN_THEMES = setOf(
        "gesundheit_medizin",
        "ernaehrung_abnehmen",
        "beziehungen_dating",
        "sexualitaet_aufklaerung",
        "tantra_erotik",
        "selbstentwicklung_produktivitaet",
        "spiritualitaet_esoterik"
    )
    private val BODY_THEMES = setOf("gesundheit_medizin", "ernaehrung_abnehmen")
    private val PSYCHE_CORE = setOf("psyche_therapie")
    private val PSYCHE_SOFT = setOf("selbstentwicklung_produktivitaet", "beziehungen_dating", "spiritualitaet_esoterik", "tantra_erotik")
    private val BODY_TRAINING_THEMES = setOf("mobilitaet_kraft_training")
    private val VLOG_THEMES = setOf("xr_vr", "gaming", "rad_mtb", "reisen_outdoor")
    private val REVIEW_FORMATS = setOf("review_test")
    private val ENTERTAIN_FORMATS = setOf("unterhaltung_vlog", "reaktion_kommentar")
    private val ENTERTAIN_THEMES = setOf("film_serien", "gaming", "hobby_sammeln_spiele", "mystery_internetkultur", "sport", "reisen_outdoor")
    private val NEWS_THEMES = setOf("politik_nachrichten")
    private val NEWS_FORMATS = setOf("news_aktuell")
    private val REACTION_FORMATS = setOf("reaktion_kommentar")

    val ORDER = listOf("praxis", "ruhe", "psyche", "bildung", "koerper", "aktuelles", "zerstreuung")
    private val WEIGHTS = mapOf(
        "praxis" to 1.0,
        "ruhe" to 1.0,
        "psyche" to 1.0,
        "bildung" to 1.0,
        "koerper" to 1.0,
        "aktuelles" to 0.3,
        "zerstreuung" to 0.5
    )

    /**
     * Python `len(re.findall(r"\b[A-ZÄÖÜ]{4,}\b", t)) >= 2 or "!!" in t or "?!" in t`: a hit is a
     * maximal run of word characters (letter, digit, "_") consisting only of A-Z, Ä, Ö, Ü with
     * length >= 4. Deliberately without regex: Android (ICU) and the JVM differ in word boundaries.
     */
    fun isClickbait(title: String?): Boolean {
        val t = title ?: ""
        if ("!!" in t || "?!" in t) return true
        var caps = 0
        var run = 0
        var allCaps = true
        var i = 0
        fun endRun() {
            if (run >= 4 && allCaps) caps++
            run = 0
            allCaps = true
        }
        while (i < t.length) {
            val cp = t.codePointAt(i)
            i += Character.charCount(cp)
            if (Character.isLetterOrDigit(cp) || cp == '_'.code) {
                run++
                if (!((cp in 'A'.code..'Z'.code) || cp == 'Ä'.code || cp == 'Ö'.code || cp == 'Ü'.code)) allCaps = false
            } else {
                endRun()
            }
        }
        endRun()
        return caps >= 2
    }

    private fun best(p: Map<String, Double>, keys: Set<String>): Double = keys.maxOf { p[it] ?: 0.0 }

    /** Python `round(x, 3)`: exact binary value, half-even (`(x * 1000).roundToLong()` differs, e.g. 0.8885). */
    private fun r3(x: Double) = java.math.BigDecimal(x).setScale(3, java.math.RoundingMode.HALF_EVEN).toDouble()

    fun categories(
        themes: Map<String, Double>,
        formats: Map<String, Double>,
        clickbait: Boolean = false,
        tone: String? = null,
        minP: Double = MIN_P_SERVER
    ): Map<String, Double> {
        val cb = if (clickbait) CLICKBAIT_FACTOR else 1.0
        val politics = best(themes, NEWS_THEMES)
        val reaction = best(formats, REACTION_FORMATS)
        val know = best(themes, KNOWLEDGE_THEMES)
        val eduFmt = best(formats, EDUCATION_FORMATS)
        val newsFmt = best(formats, NEWS_FORMATS)
        val entertainTheme = best(themes, ENTERTAIN_THEMES)
        val entertain = max(entertainTheme, best(formats, ENTERTAIN_FORMATS))
        val practiceFmt = best(formats, PRACTICE_FORMATS)
        val practiceTheme = best(themes, PRACTICE_THEMES)
        val out = ORDER.associateWith { 0.0 }.toMutableMap()

        val isPolitics = politics >= POLITICS_ANY || (politics >= POLITICS_REACTION && reaction >= 0.5)
        if (isPolitics) {
            out["aktuelles"] = max(politics, minP)
        } else if (newsFmt >= minP && max(know, entertainTheme) < 0.55) {
            out["aktuelles"] = newsFmt
        }
        val politicsBlock = politics >= POLITICS_BLOCK || isPolitics

        val audio = best(formats, CALM_FORMATS)
        if (min(practiceFmt, practiceTheme) >= minP) {
            out["praxis"] = min(practiceFmt, practiceTheme)
        } else if (practiceTheme >= 0.8 && practiceFmt >= 0.4) {
            out["praxis"] = min(practiceTheme, practiceFmt + 0.3)
        } else if (practiceTheme >= 0.85 && practiceFmt >= 0.3) {
            out["praxis"] = minP
        } else if (practiceTheme >= SOFT_THEME_MIN && practiceFmt >= SOFT_FORMAT_MIN) {
            out["praxis"] = minP
        } else if (practiceTheme >= 0.8 && (formats["tutorial_anleitung"] ?: 0.0) >= 0.5) {
            out["praxis"] = minP
        }
        if (audio > practiceFmt) out["praxis"] = 0.0
        val practicing = out.getValue("praxis") >= minP

        val schlaf = themes["schlaf_klang"] ?: 0.0
        val calmTheme = max(
            max(schlaf, themes["spiritualitaet_esoterik"] ?: 0.0),
            if (max(schlaf, themes["meditation_hypnose"] ?: 0.0) >= 0.4) themes["musik"] ?: 0.0 else 0.0
        )
        out["ruhe"] = min(best(formats, CALM_FORMATS), calmTheme)
        if (practicing && schlaf >= minP) out["ruhe"] = max(out.getValue("ruhe"), schlaf)

        val core = best(themes, PSYCHE_CORE)
        val soft = if (core >= PSYCHE_SOFT_NEEDS_CORE) best(themes, PSYCHE_SOFT) else 0.0
        val meditation = if (practiceFmt < 0.5) themes["meditation_hypnose"] ?: 0.0 else 0.0
        val base = if (core >= PSYCHE_CORE_MIN) max(core, minP) else max(soft, meditation)
        out["psyche"] = base * cb

        var edu = eduFmt
        val review = best(formats, REVIEW_FORMATS)
        if (edu < minP && know >= 0.5 && edu >= 0.4) edu = min(max(know, minP), edu + 0.3)
        if (edu < minP && know >= minP && formats.isEmpty()) edu = know
        if (edu < minP && know >= STRONG_KNOWLEDGE) edu = max(edu, minP)
        if (newsFmt >= minP && know >= 0.55 && !politicsBlock) edu = max(edu, min(newsFmt, know))
        if (edu < minP && review >= 0.5 && know >= minP) edu = max(edu, minP)
        val domainDominates = best(themes, DOMAIN_THEMES) >= DOMAIN_MIN && know < 0.55
        val heavyEthics = tone == TONE_HEAVY && (themes["ki_gesellschaft_ethik"] ?: 0.0) >= 0.5
        val eduBlocked = politicsBlock || entertain >= edu || heavyEthics || domainDominates
        if (!eduBlocked) out["bildung"] = edu

        val body = best(themes, BODY_THEMES)
        out["koerper"] = body
        if (eduFmt >= 0.5 && (body >= 0.5 || (schlaf >= minP && audio < 0.5))) {
            out["koerper"] = max(out.getValue("koerper"), minP)
        }
        val training = best(themes, BODY_TRAINING_THEMES)
        if (training >= 0.5 && practiceFmt < 0.8) out["koerper"] = max(out.getValue("koerper"), minP)
        if (practiceFmt < 0.5 && training >= minP && eduFmt >= 0.4 && !eduBlocked) {
            out["bildung"] = max(out.getValue("bildung"), min(minP, eduFmt + 0.3))
        }

        val musik = if (out.getValue("ruhe") < minP) themes["musik"] ?: 0.0 else 0.0
        val vlogFmt = formats["unterhaltung_vlog"] ?: 0.0
        val vlogTheme = best(themes, VLOG_THEMES)
        val vlog = if (vlogFmt >= 0.5 && vlogTheme >= 0.5) min(vlogTheme, 0.9) else 0.0
        val film = themes["film_serien"] ?: 0.0
        var softEntertain = if (film >= SOFT_THEME_MIN) film else 0.0
        if (vlogFmt >= 0.4 && max(max(know, best(themes, DOMAIN_THEMES)), practiceTheme) < 0.5) {
            softEntertain = max(softEntertain, vlogFmt)
        }
        if (!politicsBlock) {
            out["zerstreuung"] = max(max(entertain, musik), max(vlog, if (softEntertain != 0.0) minP else 0.0))
        }

        val res = LinkedHashMap<String, Double>()
        for (k in ORDER) if (out.getValue(k) >= minP) res[k] = r3(out.getValue(k))
        return res
    }

    /** Tone from probabilities: heavy from 0.4 (wins), positive from 0.5, else neutral; null without labels. */
    fun toneFromProbs(tones: Map<String, Double>): String? {
        if (tones.isEmpty()) return null
        if ((tones[TONE_HEAVY] ?: 0.0) >= TONE_HEAVY_MIN) return TONE_HEAVY
        return if ((tones[TONE_POSITIVE] ?: 0.0) >= TONE_POSITIVE_MIN) TONE_POSITIVE else TONE_NEUTRAL
    }

    /**
     * Display weight: highest category weight; positive news raised, heavy content halved.
     * Note: no category gives 0.0 here (bundle rule). ZenPipe keeps such videos with weight 1.0
     * (spec E4); that mapping belongs to the feed, not to these rules.
     */
    fun weight(cats: Map<String, Double>, tone: String? = null): Double {
        val w = cats.keys.maxOfOrNull { if (it == "aktuelles" && tone == TONE_POSITIVE) WEIGHT_POSITIVE_NEWS else WEIGHTS.getValue(it) } ?: 0.0
        return w * if (tone == TONE_HEAVY) BELASTEND_FACTOR else 1.0
    }

    data class FeedItem(val id: String, val cats: Map<String, Double>, val tone: String? = null)

    /** Bundle reference feed: by weight; heavy items at most [maxHeavyShare], never two in a row. */
    fun composeFeed(items: List<FeedItem>, n: Int, maxHeavyShare: Double = 0.1): List<String> {
        val cmp = compareBy<FeedItem>({ -weight(it.cats, it.tone) }, { -(it.cats.values.maxOrNull() ?: 0.0) }, { it.id })
        val ranked = items.filter { it.cats.isNotEmpty() }.sortedWith(cmp)
        val light = ArrayDeque(ranked.filter { it.tone != TONE_HEAVY })
        val heavy = ArrayDeque(ranked.filter { it.tone == TONE_HEAVY })
        val out = ArrayList<FeedItem>()
        while (out.size < n && (light.isNotEmpty() || heavy.isNotEmpty())) {
            val nHeavy = out.count { it.tone == TONE_HEAVY }
            val heavyOk = heavy.isNotEmpty() && (nHeavy + 1) <= maxHeavyShare * (out.size + 1) &&
                !(out.isNotEmpty() && out.last().tone == TONE_HEAVY)
            val pickHeavy = heavyOk && (light.isEmpty() || cmp.compare(heavy.first(), light.first()) < 0)
            if (pickHeavy) {
                out.add(heavy.removeFirst())
            } else if (light.isNotEmpty()) {
                out.add(light.removeFirst())
            } else {
                break
            }
        }
        return out.map { it.id }
    }
}

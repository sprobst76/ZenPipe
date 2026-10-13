/*
 * SPDX-FileCopyrightText: 2026 Stefan Probst-Schendzielorz
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.zen.classify

import org.schabi.newpipe.zen.ZenFeedPlanner

/**
 * Rough picture of a channel from its newest videos, for the "take over channel" sheet (shared
 * channel link): what it mostly is, how much news / distressing tone, whether the feed would
 * weight it down. Pure logic on the same sample as the plausibility check ([ZenPlausibilityCheck]).
 * It is a suggestion only; nothing is decided from it.
 */
object ZenChannelProfile {
    /** From this share the sheet says "mostly X". */
    const val MOSTLY_SHARE = 0.5

    /** From this share of videos with a reduced feed weight the sheet says "weighted low in the feed". */
    const val LOW_WEIGHT_SHARE = 0.5

    /** A feed weight below this counts as "reduced" (news 0.3/0.6, entertainment 0.5, distressing x 0.5). */
    const val LOW_WEIGHT = 0.75

    /** Example titles shown. */
    const val EXAMPLES = 4

    /** Category groups as the user sees them (practice includes calm, like the practice check). */
    enum class Group(val cats: Set<String>) {
        PRAXIS(setOf("praxis", "ruhe")),
        BILDUNG(setOf("bildung")),
        AKTUELLES(setOf("aktuelles")),
        UNTERHALTUNG(setOf("zerstreuung")),
        KOERPER(setOf("koerper")),
        PSYCHE(setOf("psyche"))
    }

    enum class Kind {
        /** One group has at least [MOSTLY_SHARE]. */
        MOSTLY,

        /** No group dominates. */
        MIXED,

        /** Too many videos without a category (same bound as the practice check). */
        UNCLEAR,

        /** Nothing checked. */
        EMPTY
    }

    /** One checked video; [cats] null = could not be classified at all. */
    data class Item(val title: String, val cats: Map<String, Double>?, val tone: String? = null, val level: String? = null)

    data class Profile(
        val kind: Kind,
        val n: Int,
        /** Videos per group, largest first (groups without videos left out). */
        val counts: List<Pair<Group, Int>>,
        val none: Int,
        val news: Int,
        val heavy: Int,
        /** Main group (largest), null without any category. */
        val main: Group?,
        val mainCount: Int,
        /** Many videos would get a reduced feed weight (and weighting is on, channel not exempt). */
        val lowFeedWeight: Boolean,
        val examples: List<Item>
    )

    @JvmStatic
    fun groupsOf(cats: Map<String, Double>?): Set<Group> = if (cats.isNullOrEmpty()) emptySet() else Group.values().filter { g -> cats.keys.any { it in g.cats } }.toSet()

    /** Feed weight the video would get (title-only level counts like in the feed). */
    @JvmStatic
    fun feedWeight(i: Item): Double = ZenFeedPlanner.contentWeight(
        ZenFeedPlanner.Candidate(
            url = i.title,
            channel = "",
            uploadMillis = 0,
            durationSec = 0,
            isLive = false,
            groupRank = null,
            groupName = null,
            zenCats = i.cats,
            tone = i.tone,
            classLevel = i.level
        )
    )

    /**
     * [weighting]: the Zen setting "weight feed by content" is on; [exempt]: the channel is on the
     * exception list (then the feed does not weight it at all).
     */
    @JvmStatic
    fun evaluate(items: List<Item>, weighting: Boolean, exempt: Boolean): Profile {
        val n = items.size
        val perGroup = Group.values().associateWith { g -> items.count { g in groupsOf(it.cats) } }
        val counts = perGroup.entries.filter { it.value > 0 }
            .sortedWith(compareByDescending<Map.Entry<Group, Int>> { it.value }.thenBy { it.key.ordinal })
            .map { it.key to it.value }
        val none = items.count { it.cats.isNullOrEmpty() }
        val news = perGroup.getValue(Group.AKTUELLES)
        val heavy = items.count { it.cats != null && it.tone == ZenRules.TONE_HEAVY }
        val main = counts.firstOrNull()
        val kind = when {
            n == 0 -> Kind.EMPTY
            none >= ZenPlausibility.UNCLEAR_SHARE * n - 1e-9 -> Kind.UNCLEAR
            main != null && main.second >= MOSTLY_SHARE * n - 1e-9 -> Kind.MOSTLY
            else -> Kind.MIXED
        }
        val low = items.count { it.cats != null && feedWeight(it) < LOW_WEIGHT }
        val lowFeed = weighting && !exempt && n > 0 && low >= LOW_WEIGHT_SHARE * n - 1e-9
        return Profile(kind, n, counts, none, news, heavy, main?.first, main?.second ?: 0, lowFeed, examples(items, counts.map { it.first }))
    }

    /** One example per group (largest first), then one without a category, then the next ones in order. */
    private fun examples(items: List<Item>, groups: List<Group>): List<Item> {
        val out = LinkedHashSet<Item>()
        for (g in groups) {
            if (out.size >= EXAMPLES) break
            items.firstOrNull { g in groupsOf(it.cats) && it !in out }?.let { out.add(it) }
        }
        if (out.size < EXAMPLES) items.firstOrNull { it.cats.isNullOrEmpty() && it !in out }?.let { out.add(it) }
        for (i in items) {
            if (out.size >= EXAMPLES) break
            out.add(i)
        }
        // keep the channel's order (newest first)
        return items.filter { it in out }.distinct().take(EXAMPLES)
    }

    /** ZenLog detail ("n=15 kind=mostly main=praxis:11 news=0 heavy=0 none=2 low=false"). */
    @JvmStatic
    fun logDetail(p: Profile): String = "n=${p.n} kind=${p.kind.name.lowercase()} main=${p.main?.name?.lowercase() ?: "-"}:${p.mainCount} " +
        "news=${p.news} heavy=${p.heavy} none=${p.none} lowFeed=${p.lowFeedWeight}"
}

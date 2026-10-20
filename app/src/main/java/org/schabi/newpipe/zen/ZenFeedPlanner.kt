package org.schabi.newpipe.zen

import org.schabi.newpipe.zen.classify.ZenRules

/**
 * Local, explainable selection of the videos for one feed edition. No network, no YouTube
 * recommendations. All numbers are named constants so they are easy to tune.
 *
 * Score = group priority + length bonus + freshness, then at most [MAX_PER_CHANNEL] per channel.
 *  - Group priority: the sort order of the user's feed groups (Subscriptions > feed groups,
 *    reorder dialog) is the priority: first group = highest. A subscription in several groups
 *    uses its best group. No group = no priority points.
 *  - Length bonus (smaller than a group step): long videos are preferred on purpose.
 *  - Short videos (shorter than the Zen setting `min_length_min`, default 5 min, D62) are allowed
 *    but with low priority: their score is multiplied by [SHORT_WEIGHT] and they only take the
 *    places after all longer videos of an edition (never before a longer one).
 */
object ZenFeedPlanner {
    // Budget per edition, daily cap (distinct videos per Zen day) and the short-video limit (live
    // streams and unknown length never enter an edition) are Zen settings, see ZenSettings.
    /** Minimum time between two editions (measured from the last one, not the clock hour). */
    const val EDITION_INTERVAL_MS = 60 * 60 * 1000L
    const val MAX_AGE_DAYS = 7
    const val MAX_PER_CHANNEL = 3

    const val LONG_VIDEO_SEC = 15 * 60
    const val VERY_LONG_VIDEO_SEC = 30 * 60

    const val GROUP_TOP_POINTS = 60
    const val GROUP_STEP_POINTS = 15
    const val LONG_BONUS = 12
    const val VERY_LONG_EXTRA_BONUS = 6
    const val FRESHNESS_MAX_POINTS = 20

    data class Candidate(
        /** Video URL: stable key (database ids change with a data import). */
        val url: String,
        val channel: String,
        val title: String = "",
        val channelName: String = channel,
        val uploadMillis: Long,
        val durationSec: Long,
        val isLive: Boolean,
        /** Index of the best feed group (0 = first), null if the channel is in no group. */
        val groupRank: Int?,
        val groupName: String?,
        /** Channel is an active practice channel: its videos never enter an edition (practice tab). */
        val praxis: Boolean = false,
        /**
         * Content categories from the classifier cache (spec 4.3); null = not classified yet (or the
         * classifier is not in use), empty = classified without a category. Never computed here.
         */
        val zenCats: Map<String, Double>? = null,
        /** Tone from the classifier ("belastend", "positiv", "neutral"), null if unknown. */
        val tone: String? = null,
        /** Input level of the classification: "T" = title + channel, "D" = with details. */
        val classLevel: String? = null,
        /** Channel/keyword on the classifier exception list: weight 1.0 and no heavy rule. */
        val classExempt: Boolean = false,
        /** Strongest theme (key, probability) for the "why" line only; never used for the selection. */
        val classTheme: Pair<String, Double>? = null,
        /** Detected language (ISO code, null = unknown), see [ZenFeedLanguages]. */
        val lang: String? = null,
        /** The language is not allowed in the feed ("Languages in the feed"): never offered, never deleted. */
        val langHidden: Boolean = false
    )

    /**
     * Which content rules apply to an edition. Both need an installed, working classifier; the
     * switches are Zen settings (turning them off is a loosening).
     */
    data class ContentRules(val weighting: Boolean, val heavyLimit: Boolean) {
        val active get() = weighting || heavyLimit

        companion object {
            @JvmField val OFF = ContentRules(weighting = false, heavyLimit = false)
        }
    }

    /**
     * One video of an edition. [why] is the compact classification at selection time (see
     * [whyOf]); null while the content rules are off.
     */
    data class Pick(val url: String, val minutes: Int, val groupName: String?, val why: String? = null)

    data class Selection(val picks: List<Pick>, val eligibleCount: Int)

    private val MUSIC_CHANNEL = Regex("(- topic$|vevo$)", RegexOption.IGNORE_CASE)
    private val MUSIC_GROUP = Regex("^(music|musik)$", RegexOption.IGNORE_CASE)
    private val MUSIC_TITLE_STRICT = Regex(
        "\\b(official (music )?video|official audio|lyrics?|lyric video|music video|full album)\\b",
        RegexOption.IGNORE_CASE
    )
    private val MUSIC_TITLE_MIX = Regex(
        "\\b(mix|dj set|lo-?fi|playlist|study music|ambient music|music)\\b",
        RegexOption.IGNORE_CASE
    )

    /** Heuristic on the data the feed has (no category field): topic/VEVO channels, titles, group. */
    @JvmStatic
    fun isMusic(c: Candidate): Boolean = MUSIC_CHANNEL.containsMatchIn(c.channelName) ||
        (c.groupName != null && MUSIC_GROUP.matches(c.groupName)) ||
        MUSIC_TITLE_STRICT.containsMatchIn(c.title)

    @JvmStatic
    fun isEligible(c: Candidate, nowMillis: Long): Boolean {
        // practice videos do not compete for (or count against) the edition budget and daily cap
        if (c.praxis) return false
        // a language that is not allowed in the feed (only clear detections, see ZenLangFilter)
        if (c.langHidden) return false
        if (isMusic(c)) return false
        // unknown length never enters; short videos do, with low priority (D62, see isShort)
        if (c.isLive || c.durationSec <= 0) return false
        val age = nowMillis - c.uploadMillis
        return age <= MAX_AGE_DAYS * DAY_MS
    }

    /** Score factor of a short video (low priority, D62). */
    const val SHORT_WEIGHT = 0.25

    /** Shorter than the Zen setting `min_length_min` (default 5 min): low priority, placed last. */
    @JvmStatic
    fun isShort(c: Candidate): Boolean = c.durationSec < ZenSettings.minLengthSec()

    @JvmStatic
    fun score(c: Candidate, nowMillis: Long): Int {
        val group = c.groupRank?.let { maxOf(0, GROUP_TOP_POINTS - GROUP_STEP_POINTS * it) } ?: 0
        // long mixes/compilations must not profit from the length bonus
        val length = if (MUSIC_TITLE_MIX.containsMatchIn(c.title)) {
            0
        } else {
            when {
                c.durationSec >= VERY_LONG_VIDEO_SEC -> LONG_BONUS + VERY_LONG_EXTRA_BONUS
                c.durationSec >= LONG_VIDEO_SEC -> LONG_BONUS
                else -> 0
            }
        }
        val ageFraction = ((nowMillis - c.uploadMillis).toDouble() / (MAX_AGE_DAYS * DAY_MS))
            .coerceIn(0.0, 1.0)
        val fresh = ((1.0 - ageFraction) * FRESHNESS_MAX_POINTS).toInt()
        return group + length + fresh
    }

    /** Heavy videos: at most this share of the places of an edition (15 places: 1). */
    const val HEAVY_MAX_SHARE = 0.1

    /** Heavy videos never take one of the first places (1-based place >= this). */
    const val HEAVY_FIRST_PLACE = 10

    /**
     * Content weight (spec 4.2/4.3, bundle weights): the highest category weight (news 0.3, from
     * title + channel only 0.6, positive news 0.8, distraction 0.5, everything else 1.0), heavy
     * tone halves it. Not
     * classified, classified without a category (E4) or on the exception list: 1.0.
     */
    @JvmStatic
    fun contentWeight(c: Candidate): Double {
        val cats = c.zenCats ?: return 1.0
        if (c.classExempt || cats.isEmpty()) return 1.0
        val light = if (c.tone == ZenRules.TONE_HEAVY) null else c.tone
        val w = cats.keys.maxOf { k ->
            val bundle = ZenRules.weight(mapOf(k to 1.0), light)
            // News from title + channel only is unreliable (F1 58 % vs. 71 % with the description,
            // AP 0): only a gentle step back until the video was opened (level D).
            if (k == NEWS && c.classLevel != LEVEL_DETAILS) maxOf(bundle, NEWS_WEIGHT_TITLE_ONLY) else bundle
        }
        return if (c.tone == ZenRules.TONE_HEAVY) w * HEAVY_FACTOR else w
    }

    private const val NEWS = "aktuelles"
    private const val LEVEL_DETAILS = "D"

    /** Weight of news classified from title + channel only (bundle weight 0.3 needs level D). */
    const val NEWS_WEIGHT_TITLE_ONLY = 0.6

    /** Bundle factor for heavy tone. */
    const val HEAVY_FACTOR = 0.5

    @JvmStatic
    fun isHeavy(c: Candidate): Boolean = !c.classExempt && c.zenCats != null && c.tone == ZenRules.TONE_HEAVY

    /**
     * Score of the edition order: [score] times [contentWeight] while weighting is on, times
     * [SHORT_WEIGHT] for a short video.
     */
    @JvmStatic
    fun weightedScore(c: Candidate, nowMillis: Long, weighting: Boolean): Double = score(c, nowMillis) * (if (weighting) contentWeight(c) else 1.0) * (if (isShort(c)) SHORT_WEIGHT else 1.0)

    /**
     * Compact, stored "why" of a pick: `level|tone|weight|cat=p,cat=p` and, without a category,
     * `|theme=p` (strongest theme, display only); "?" = not classified, "x" = exception list.
     * Null when no content rule is active.
     */
    @JvmStatic
    fun whyOf(c: Candidate, rules: ContentRules): String? {
        if (!rules.active) return null
        if (c.classExempt) return WHY_EXEMPT
        val cats = c.zenCats ?: return WHY_UNCLASSIFIED
        val w = if (rules.weighting) contentWeight(c) else 1.0
        val theme = c.classTheme?.takeIf { cats.isEmpty() }?.let { "|${it.first}=${fmt2(it.second)}" } ?: ""
        return "${c.classLevel ?: ""}|${c.tone ?: ""}|${fmt2(w)}|" +
            cats.entries.joinToString(",") { "${it.key}=${fmt2(it.value)}" } + theme
    }

    /** A parsed [whyOf] string (not for [WHY_EXEMPT]/[WHY_UNCLASSIFIED]). */
    data class Why(val level: String?, val tone: String?, val weight: Double, val cats: Map<String, Double>, val theme: Pair<String, Double>?)

    /** Parses a stored "why"; older 4-field values (without theme) stay valid. */
    @JvmStatic
    fun parseWhy(why: String?): Why? {
        if (why == null || why == WHY_EXEMPT || why == WHY_UNCLASSIFIED) return null
        val p = why.split('|', limit = 5)
        if (p.size < 4) return null
        return Why(
            p[0].ifEmpty { null },
            p[1].ifEmpty { null },
            p[2].toDoubleOrNull() ?: 1.0,
            org.schabi.newpipe.zen.classify.ClassEntry.decodeMap(p[3]),
            p.getOrNull(4)?.let { org.schabi.newpipe.zen.classify.ClassEntry.decodePairs(it).firstOrNull() }
        )
    }

    const val WHY_EXEMPT = "x"
    const val WHY_UNCLASSIFIED = "?"

    private fun fmt2(v: Double) = String.format(java.util.Locale.ROOT, "%.2f", v)

    @JvmStatic
    fun select(
        candidates: List<Candidate>,
        nowMillis: Long,
        count: Int,
        /** Videos already counted against today's cap (re-offering them is free). */
        counted: Set<String> = emptySet(),
        /** How many NOT yet counted videos may still be added today. */
        newAllowed: Int = count,
        /** Content weighting and heavy rule (off = the order without the classifier). */
        rules: ContentRules = ContentRules.OFF
    ): Selection {
        val eligible = candidates.filter { isEligible(it, nowMillis) }
        // short videos after all longer ones (D62), each part by its weighted score
        val sorted = eligible.sortedWith(
            compareBy<Candidate> { isShort(it) }
                .thenByDescending { weightedScore(it, nowMillis, rules.weighting) }
                .thenByDescending { it.uploadMillis }
                .thenBy { it.url }
        )
        val perChannel = HashMap<String, Int>()
        val picks = ArrayList<Pick>()
        var newUsed = 0
        var heavyUsed = 0
        var lastHeavy = false
        var shortUsed = false
        val remaining = ArrayList(sorted)
        // Fill place by place with the best candidate allowed there. Without the heavy rule this is
        // the plain order (cap and channel limits only grow, a skipped video never fits later);
        // a heavy video that does not fit yet waits for a later place (from place 10, not twice
        // in a row, at most HEAVY_MAX_SHARE of the places) or stays leftover. Once a short video
        // has a place, no longer video follows it (a waiting one stays leftover).
        while (picks.size < count) {
            val place = picks.size + 1
            val i = remaining.indexOfFirst { c ->
                (!shortUsed || isShort(c)) &&
                    (c.url in counted || newUsed < newAllowed) &&
                    perChannel.getOrDefault(c.channel, 0) < MAX_PER_CHANNEL &&
                    (!rules.heavyLimit || !isHeavy(c) || heavyAllowed(place, heavyUsed, lastHeavy, count))
            }
            if (i < 0) break
            val c = remaining.removeAt(i)
            perChannel[c.channel] = perChannel.getOrDefault(c.channel, 0) + 1
            if (c.url !in counted) newUsed++
            val heavy = rules.heavyLimit && isHeavy(c)
            if (heavy) heavyUsed++
            lastHeavy = heavy
            if (isShort(c)) shortUsed = true
            picks.add(Pick(c.url, (c.durationSec / 60).toInt(), c.groupName, whyOf(c, rules)))
        }
        return Selection(picks, eligible.size)
    }

    /** Heavy rule (spec 4.3): not before place 10, never after another heavy one, at most 10 %. */
    @JvmStatic
    fun heavyAllowed(place: Int, heavyUsed: Int, lastHeavy: Boolean, count: Int): Boolean = place >= HEAVY_FIRST_PLACE && !lastHeavy && (heavyUsed + 1) <= HEAVY_MAX_SHARE * count + 1e-9

    private const val DAY_MS = 24 * 60 * 60 * 1000L
}

package org.schabi.newpipe.zen

/**
 * Local, explainable selection of the videos for one feed edition. No network, no YouTube
 * recommendations. All numbers are named constants so they are easy to tune.
 *
 * Score = group priority + length bonus + freshness, then at most [MAX_PER_CHANNEL] per channel.
 *  - Group priority: the sort order of the user's feed groups (Subscriptions > feed groups,
 *    reorder dialog) is the priority: first group = highest. A subscription in several groups
 *    uses its best group. No group = no priority points.
 *  - Length bonus (smaller than a group step): long videos are preferred on purpose.
 */
object ZenFeedPlanner {
    // Budget per edition, daily cap (distinct videos per Zen day) and minimum length (live streams
    // and unknown length never enter an edition) are Zen settings, see ZenSettings.
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
        val praxis: Boolean = false
    )

    data class Pick(val url: String, val minutes: Int, val groupName: String?)

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
        if (isMusic(c)) return false
        if (c.isLive || c.durationSec < ZenSettings.minLengthSec()) return false
        val age = nowMillis - c.uploadMillis
        return age <= MAX_AGE_DAYS * DAY_MS
    }

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

    @JvmStatic
    fun select(
        candidates: List<Candidate>,
        nowMillis: Long,
        count: Int,
        /** Videos already counted against today's cap (re-offering them is free). */
        counted: Set<String> = emptySet(),
        /** How many NOT yet counted videos may still be added today. */
        newAllowed: Int = count
    ): Selection {
        val eligible = candidates.filter { isEligible(it, nowMillis) }
        val sorted = eligible.sortedWith(
            compareByDescending<Candidate> { score(it, nowMillis) }
                .thenByDescending { it.uploadMillis }
                .thenBy { it.url }
        )
        val perChannel = HashMap<String, Int>()
        val picks = ArrayList<Pick>()
        var newUsed = 0
        for (c in sorted) {
            if (picks.size >= count) break
            val isNew = c.url !in counted
            if (isNew && newUsed >= newAllowed) continue
            val n = perChannel.getOrDefault(c.channel, 0)
            if (n >= MAX_PER_CHANNEL) continue
            perChannel[c.channel] = n + 1
            if (isNew) newUsed++
            picks.add(Pick(c.url, (c.durationSec / 60).toInt(), c.groupName))
        }
        return Selection(picks, eligible.size)
    }

    private const val DAY_MS = 24 * 60 * 60 * 1000L
}

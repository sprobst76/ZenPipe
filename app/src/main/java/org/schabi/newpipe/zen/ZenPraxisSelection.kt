package org.schabi.newpipe.zen

/**
 * The theme view of the practice tab (D61/D63, spec-praxis-themen.md 1 and 4): per theme a fixed
 * number of videos ([PER_THEME]), of them at most one "popular" one. Popular = views divided by the
 * median views of the loaded videos of the same channel (an upward outlier; small channels are not
 * at a disadvantage); without view counts that place is left empty. The others are the first ones
 * in the order of the tab (channels newest first, then the frozen playlists in their order). Only
 * display: nothing here changes what counts as practice, a lock, a limit or a pause.
 */
object ZenPraxisSelection {
    /** Videos per theme in the theme view (proposal of the spec, fixed). */
    const val PER_THEME = 5

    /** A channel's median needs at least this many known view counts. */
    const val MEDIAN_MIN_SAMPLES = 3

    /**
     * One video of the tab, in tab order. [group] = its channel (uploader URL or source entry),
     * [views] < 0 = unknown, [mask] = its practice themes ([ZenPraxisThemes]).
     */
    data class Candidate(val key: String, val mask: Int, val group: String, val views: Long)

    /** Chip filter: a theme, or null = "More" (videos without a theme). */
    @JvmStatic
    fun matches(c: Candidate, theme: ZenPraxisTheme?): Boolean = if (theme == null) c.mask == 0 else ZenPraxisThemes.has(c.mask, theme)

    /** Views relative to the median of the same channel among [all]; null if not known. */
    @JvmStatic
    fun ratios(all: List<Candidate>): Map<String, Double> {
        val medians = all.groupBy { it.group }.mapValues { (_, list) ->
            val v = list.distinctBy { it.key }.map { it.views }.filter { it >= 0 }.sorted()
            if (v.size < MEDIAN_MIN_SAMPLES) {
                null
            } else if (v.size % 2 == 1) {
                v[v.size / 2].toDouble()
            } else {
                (v[v.size / 2 - 1] + v[v.size / 2]) / 2.0
            }
        }
        val out = HashMap<String, Double>()
        for (c in all) {
            val m = medians[c.group] ?: continue
            if (c.views < 0 || m <= 0.0) continue
            out[c.key] = c.views / m
        }
        return out
    }

    /**
     * The keys shown for [theme] (null = "More"), in tab order: the first [PER_THEME] - 1 matching
     * videos plus the matching one with the highest ratio above 1 among the rest (none = the place
     * stays empty). Each video at most once.
     */
    @JvmStatic
    fun select(all: List<Candidate>, theme: ZenPraxisTheme?): List<String> {
        val matching = all.filter { matches(it, theme) }.distinctBy { it.key }
        val first = matching.take(PER_THEME - 1).map { it.key }.toSet()
        val ratio = ratios(all)
        val popular = matching.filter { it.key !in first }
            .mapNotNull { c -> ratio[c.key]?.takeIf { it > 1.0 }?.let { c.key to it } }
            .maxByOrNull { it.second }?.first
        return matching.map { it.key }.filter { it in first || it == popular }
    }
}

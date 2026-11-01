package org.schabi.newpipe.zen

/**
 * Local rating "More of this" / "Less of this" (zen-docs/spec-bewertung.md, D79-D82). A rating
 * only steers the order of the next feed editions on this device: a small boost or a demotion of
 * the rated video's channel and its classified theme ([ZenRatingWeights]). It never hides a
 * video, never changes the budget, the daily cap, the rhythm, the language filter, the heavy or
 * short rule, and it is neutral for the settings lock (like the feed group order, F8).
 *
 * "More of this" also puts the video on the like list: a calm reminder to like it on YouTube
 * yourself (ZenPipe has no login and sends nothing). Stored in `zen_state` (`rt_items`,
 * `rt_likes`), so it is not part of NewPipe's export/import/reset nor of any backup.
 */
data class ZenRating(
    val serviceId: Int,
    val url: String,
    /** [MORE] or [LESS]. */
    val value: Int,
    /** [ZenPraxisChannel.keyOf] of the channel URL, empty if unknown (then no channel effect). */
    val channelKey: String,
    val channelName: String,
    /** Strongest classified theme at rating time (only above the model threshold), null = none. */
    val theme: String?,
    val title: String,
    val at: Long
) {
    val key: String get() = ZenPraxisChannel.keyOf(serviceId, url)

    companion object {
        const val MORE = 1
        const val LESS = -1
    }
}

/** One entry of the like list (a video rated "More of this", not yet marked done). */
data class ZenLikeEntry(val serviceId: Int, val url: String, val title: String, val channelName: String, val at: Long) {
    val key: String get() = ZenPraxisChannel.keyOf(serviceId, url)
}

class ZenRatings(private val store: ZenKeyValueStore) {

    /** What a call of [rate] did. */
    enum class Change { SET, CHANGED, REMOVED }

    fun all(): List<ZenRating> = decodeRatings(store.getString(KEY_ITEMS, null))

    fun likes(): List<ZenLikeEntry> = decodeLikes(store.getString(KEY_LIKES, null))

    /** [ZenRating.MORE], [ZenRating.LESS] or 0 (not rated). */
    fun valueOf(serviceId: Int, url: String): Int {
        val k = ZenPraxisChannel.keyOf(serviceId, url)
        return all().firstOrNull { it.key == k }?.value ?: 0
    }

    /**
     * Rates a video. The same value again removes the rating (toggle off); the other value
     * replaces it. "More" adds the video to the like list (once), "less" or removing takes it
     * off again (a video already marked done stays off).
     */
    @Synchronized
    fun rate(r: ZenRating): Change {
        val list = all().toMutableList()
        val old = list.indexOfFirst { it.key == r.key }
        val change = when {
            old >= 0 && list[old].value == r.value -> {
                list.removeAt(old)
                Change.REMOVED
            }

            old >= 0 -> {
                list.removeAt(old)
                list.add(r)
                Change.CHANGED
            }

            else -> {
                list.add(r)
                Change.SET
            }
        }
        // oldest first; the list never grows beyond MAX_RATINGS (the oldest rating is dropped)
        while (list.size > MAX_RATINGS) list.removeAt(0)
        val likes = likes().filter { it.key != r.key }.toMutableList()
        if (change != Change.REMOVED && r.value == ZenRating.MORE) {
            likes.add(ZenLikeEntry(r.serviceId, r.url, r.title, r.channelName, r.at))
            while (likes.size > MAX_LIKES) likes.removeAt(0)
        }
        store.put(KEY_ITEMS to encodeRatings(list).ifEmpty { null }, KEY_LIKES to encodeLikes(likes).ifEmpty { null })
        return change
    }

    /** Removes the rating of a video (and its like list entry). */
    @Synchronized
    fun remove(serviceId: Int, url: String): Boolean {
        val k = ZenPraxisChannel.keyOf(serviceId, url)
        val list = all()
        if (list.none { it.key == k }) return false
        store.put(
            KEY_ITEMS to encodeRatings(list.filter { it.key != k }).ifEmpty { null },
            KEY_LIKES to encodeLikes(likes().filter { it.key != k }).ifEmpty { null }
        )
        return true
    }

    /** "Done" on the like list: the entry goes, the rating stays. */
    @Synchronized
    fun likeDone(serviceId: Int, url: String): Boolean {
        val k = ZenPraxisChannel.keyOf(serviceId, url)
        val likes = likes()
        if (likes.none { it.key == k }) return false
        store.put(KEY_LIKES to encodeLikes(likes.filter { it.key != k }).ifEmpty { null })
        return true
    }

    /** Fast lookup for the feed candidates of one edition. */
    fun weights(): ZenRatingWeights = ZenRatingWeights(all())

    companion object {
        const val KEY_ITEMS = "rt_items"
        const val KEY_LIKES = "rt_likes"
        const val MAX_RATINGS = 200
        const val MAX_LIKES = 100

        private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ')

        @JvmStatic
        fun encodeRatings(list: List<ZenRating>): String = list.joinToString("\n") {
            "${it.serviceId}\t${clean(it.url)}\t${it.value}\t${clean(it.channelKey)}\t${clean(it.channelName)}\t" +
                "${clean(it.theme ?: "")}\t${it.at}\t${clean(it.title)}"
        }

        @JvmStatic
        fun decodeRatings(raw: String?): List<ZenRating> = (raw ?: "").split('\n').filter { it.isNotEmpty() }.mapNotNull {
            val p = it.split('\t', limit = 8)
            val id = p.getOrNull(0)?.toIntOrNull()
            val url = p.getOrNull(1)
            val v = p.getOrNull(2)?.toIntOrNull()
            if (id == null || url.isNullOrEmpty() || (v != ZenRating.MORE && v != ZenRating.LESS)) {
                null
            } else {
                ZenRating(
                    id,
                    url,
                    v,
                    p.getOrElse(3) { "" },
                    p.getOrElse(4) { "" },
                    p.getOrNull(5)?.ifEmpty { null },
                    p.getOrElse(7) { "" },
                    p.getOrNull(6)?.toLongOrNull() ?: 0L
                )
            }
        }

        @JvmStatic
        fun encodeLikes(list: List<ZenLikeEntry>): String = list.joinToString("\n") {
            "${it.serviceId}\t${clean(it.url)}\t${it.at}\t${clean(it.channelName)}\t${clean(it.title)}"
        }

        @JvmStatic
        fun decodeLikes(raw: String?): List<ZenLikeEntry> = (raw ?: "").split('\n').filter { it.isNotEmpty() }.mapNotNull {
            val p = it.split('\t', limit = 5)
            val id = p.getOrNull(0)?.toIntOrNull()
            val url = p.getOrNull(1)
            if (id == null || url.isNullOrEmpty()) {
                null
            } else {
                ZenLikeEntry(id, url, p.getOrElse(4) { "" }, p.getOrElse(3) { "" }, p.getOrNull(2)?.toLongOrNull() ?: 0L)
            }
        }
    }
}

/**
 * Score factor of a feed candidate from the ratings (spec-bewertung.md "Wirkung"):
 *
 *     n_c = clamp(sum of the ratings of the channel, -3, +2)
 *     n_t = clamp(sum of the ratings of the theme,   -3, +2)
 *     f   = clamp(up(n_c, 0.10) * down(n_c, 0.80) * up(n_t, 0.05) * down(n_t, 0.90), 0.40, 1.30)
 *
 * with up(n, s) = 1 + s * max(n, 0) and down(n, d) = d ^ max(-n, 0). Never 0: a demoted video
 * stays a candidate and still fills a place when nothing else is there.
 */
class ZenRatingWeights(ratings: List<ZenRating>) {
    private val channels = HashMap<String, Int>()
    private val themes = HashMap<String, Int>()

    init {
        ratings.forEach { r ->
            if (r.channelKey.isNotEmpty()) channels.merge(r.channelKey, r.value, Int::plus)
            r.theme?.let { themes.merge(it, r.value, Int::plus) }
        }
    }

    fun isEmpty() = channels.isEmpty() && themes.isEmpty()

    /** Net rating steps (channel, theme) after clamping. */
    fun steps(channelKey: String?, theme: String?): Pair<Int, Int> = Pair(
        (channelKey?.let { channels[it] } ?: 0).coerceIn(MIN_STEPS, MAX_STEPS),
        (theme?.let { themes[it] } ?: 0).coerceIn(MIN_STEPS, MAX_STEPS)
    )

    fun factor(channelKey: String?, theme: String?): Double {
        val (c, t) = steps(channelKey, theme)
        val f = up(c, CHANNEL_UP) * down(c, CHANNEL_DOWN) * up(t, THEME_UP) * down(t, THEME_DOWN)
        return f.coerceIn(MIN_FACTOR, MAX_FACTOR)
    }

    private fun up(n: Int, step: Double) = 1.0 + step * maxOf(n, 0)

    private fun down(n: Int, d: Double) = Math.pow(d, maxOf(-n, 0).toDouble())

    companion object {
        const val MAX_STEPS = 2
        const val MIN_STEPS = -3
        const val CHANNEL_UP = 0.10
        const val CHANNEL_DOWN = 0.80
        const val THEME_UP = 0.05
        const val THEME_DOWN = 0.90
        const val MIN_FACTOR = 0.40
        const val MAX_FACTOR = 1.30

        /** Sign of a factor for the "why" line: +1 "more of this", -1 "less of this", 0 nothing. */
        @JvmStatic
        fun signOf(factor: Double): Int = when {
            factor > 1.0 + 1e-9 -> 1
            factor < 1.0 - 1e-9 -> -1
            else -> 0
        }
    }
}

package org.schabi.newpipe.zen

import java.time.ZoneId

/**
 * Persisted state of the feed "edition" (stored in the Zen preferences, so an app restart,
 * data import or settings reset does not re-roll it). Videos are keyed by URL because
 * database ids change with a data import.
 *
 * Rules: a new edition is built at the earliest [ZenFeedPlanner.EDITION_INTERVAL_MS] after the
 * previous one (or on a new Zen day) and only when [view] is called with allowCreate = true
 * (i.e. after the intention prompt was confirmed). The daily cap counts DISTINCT videos.
 * Videos that were offered but not handled simply wait for the next edition.
 */
class ZenFeedState(
    private val store: ZenKeyValueStore,
    private val log: ZenLog? = null,
    private val zone: ZoneId = ZenZone.fixed(store)
) {
    data class Edition(
        val createdAt: Long,
        val day: String,
        val picks: List<ZenFeedPlanner.Pick>,
        /** Eligible videos that did not fit into this edition (they wait). */
        val leftover: Int
    )

    data class View(
        val edition: Edition?,
        /** Edition entries that are still open (not dismissed / saved for later). */
        val open: List<ZenFeedPlanner.Pick>,
        val dailyCapReached: Boolean,
        /** Null while no edition exists yet. */
        val nextEditionMillis: Long?
    )

    @Synchronized
    fun view(
        nowMillis: Long,
        allowCreate: Boolean,
        candidates: () -> List<ZenFeedPlanner.Candidate>
    ): View {
        val day = ZenDay.dayOf(nowMillis, zone).toString()
        if (store.getString(KEY_DAY, null) != day) {
            store.put(KEY_DAY to day, KEY_DAY_URLS to "")
        }
        var edition = load()
        val capReached = counted().size >= ZenFeedPlanner.DAILY_CAP
        val due = edition == null || edition.day != day ||
            (!capReached && nowMillis >= edition.createdAt + ZenFeedPlanner.EDITION_INTERVAL_MS)
        if (allowCreate && due) {
            edition = createEdition(nowMillis, day, edition, candidates)
        }
        val handled = handled()
        val open = edition?.picks?.filter { it.url !in handled } ?: emptyList()
        val capNow = counted().size >= ZenFeedPlanner.DAILY_CAP
        val next = when {
            edition == null -> null
            capNow -> ZenDay.nextDayStart(nowMillis, zone)
            else -> maxOf(nowMillis, edition.createdAt + ZenFeedPlanner.EDITION_INTERVAL_MS)
        }
        return View(edition, open, capNow, next)
    }

    private fun createEdition(
        now: Long,
        day: String,
        previous: Edition?,
        candidates: () -> List<ZenFeedPlanner.Candidate>
    ): Edition? {
        val handled = handled()
        val counted = counted()
        val sel = ZenFeedPlanner.select(
            candidates().filter { it.url !in handled },
            now,
            ZenFeedPlanner.BUDGET_PER_HOUR,
            counted,
            maxOf(0, ZenFeedPlanner.DAILY_CAP - counted.size)
        )
        // no data yet: do not seal an empty edition, retry on the next call
        if (sel.picks.isEmpty()) return previous
        val e = Edition(now, day, sel.picks, sel.eligibleCount - sel.picks.size)
        save(e)
        val all = (counted.toList() + e.picks.map { it.url }).distinct()
        store.put(KEY_DAY_URLS to all.joinToString("\n"))
        log?.add("edition", "n=${e.picks.size} leftover=${e.leftover} dayDistinct=${all.size}")
        return e
    }

    @Synchronized
    fun markHandled(url: String, reason: String) {
        val list = handledList() + url
        store.put(KEY_HANDLED to list.takeLast(MAX_HANDLED).joinToString("\n"))
        log?.add(reason, url)
    }

    private fun lines(key: String) = (store.getString(key, "") ?: "").split('\n').filter { it.isNotEmpty() }

    private fun handledList(): List<String> = lines(KEY_HANDLED)

    private fun handled() = handledList().toHashSet()

    private fun counted() = lines(KEY_DAY_URLS).toHashSet()

    private fun save(e: Edition) {
        store.put(
            KEY_AT to e.createdAt,
            KEY_EDITION_DAY to e.day,
            KEY_LEFTOVER to e.leftover,
            KEY_PICKS to e.picks.joinToString("\n") {
                "${it.url}\t${it.minutes}\t${(it.groupName ?: "").replace('\t', ' ').replace('\n', ' ')}"
            }
        )
    }

    private fun load(): Edition? {
        val at = store.getLong(KEY_AT, -1)
        if (at < 0) return null
        val picks = lines(KEY_PICKS).mapNotNull {
            val p = it.split('\t', limit = 3)
            if (p[0].isEmpty()) {
                null
            } else {
                ZenFeedPlanner.Pick(
                    p[0],
                    p.getOrNull(1)?.toIntOrNull() ?: 0,
                    p.getOrNull(2)?.ifEmpty { null }
                )
            }
        }
        return Edition(
            at,
            store.getString(KEY_EDITION_DAY, "") ?: "",
            picks,
            store.getInt(KEY_LEFTOVER, 0)
        )
    }

    companion object {
        const val MAX_HANDLED = 2000
        private const val KEY_DAY = "feed_day"
        private const val KEY_DAY_URLS = "feed_day_urls"
        private const val KEY_AT = "feed_edition_at"
        private const val KEY_EDITION_DAY = "feed_edition_day"
        private const val KEY_LEFTOVER = "feed_edition_leftover"
        private const val KEY_PICKS = "feed_edition_picks"
        private const val KEY_HANDLED = "feed_handled"
    }
}

/**
 * The Zen day must not move when the device time zone changes (travel, manipulation): the zone
 * that was active on first use is stored and used from then on.
 */
object ZenZone {
    private const val KEY = "zone_id"

    @JvmStatic
    fun fixed(store: ZenKeyValueStore): ZoneId {
        val saved = store.getString(KEY, null)
        val zone = try {
            if (saved != null) ZoneId.of(saved) else null
        } catch (e: java.time.DateTimeException) {
            null
        }
        if (zone != null) return zone
        val current = ZoneId.systemDefault()
        store.put(KEY to current.id)
        return current
    }
}

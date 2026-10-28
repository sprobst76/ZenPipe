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
    private val zone: ZoneId = ZenZone.fixed(store),
    /**
     * Shrinks only with device uptime. The hourly interval is over only when the wall clock AND
     * this countdown say so, so setting the clock forward (plus a restart) does not bring the next
     * edition earlier. Null = wall clock only (tests, read-only uses).
     */
    private val rhythm: ZenCountdown? = null
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
        val nextEditionMillis: Long?,
        /** This call built a new edition. */
        val created: Boolean = false
    )

    /**
     * [createdAt]: creation time of a new edition (default [nowMillis]); an edition whose content
     * was prepared first gets the time the preparation started, so the hourly rhythm stays.
     */
    @Synchronized
    fun view(
        nowMillis: Long,
        allowCreate: Boolean,
        rules: ZenFeedPlanner.ContentRules = ZenFeedPlanner.ContentRules.OFF,
        createdAt: Long = nowMillis,
        candidates: () -> List<ZenFeedPlanner.Candidate>
    ): View {
        val day = ZenDay.dayOf(nowMillis, zone).toString()
        if (store.getString(KEY_DAY, null) != day) {
            store.put(KEY_DAY to day, KEY_DAY_URLS to "")
        }
        var edition = load()
        var created = false
        if (allowCreate && isDue(nowMillis)) {
            val at = if (createdAt <= nowMillis && ZenDay.dayOf(createdAt, zone).toString() == day) createdAt else nowMillis
            val previous = edition
            edition = createEdition(nowMillis, at, day, edition, rules, candidates)
            created = edition !== previous
            if (created && edition != null) startRhythm(edition.createdAt, nowMillis)
        }
        val handled = handled()
        val open = edition?.picks?.filter { it.url !in handled } ?: emptyList()
        val capNow = counted().size >= ZenSettings.dailyCap()
        val next = when {
            edition == null -> null
            capNow -> ZenDay.nextDayStart(nowMillis, zone)
            else -> maxOf(nowMillis, edition.createdAt + ZenFeedPlanner.EDITION_INTERVAL_MS, nowMillis + rhythmRemaining(edition, nowMillis))
        }
        return View(edition, open, capNow, next, created)
    }

    /** Whether a new edition would be built now (new day, none yet, or the interval passed below the cap). */
    @Synchronized
    fun isDue(nowMillis: Long): Boolean {
        val day = ZenDay.dayOf(nowMillis, zone).toString()
        val edition = load() ?: return true
        if (edition.day != day) return true
        val counted = if (store.getString(KEY_DAY, null) == day) counted() else emptySet()
        val capReached = counted.size >= ZenSettings.dailyCap()
        // look at the countdown first so that it also starts at the first look after an update
        val uptimeLeft = rhythmRemaining(edition, nowMillis)
        return !capReached && nowMillis >= edition.createdAt + ZenFeedPlanner.EDITION_INTERVAL_MS && uptimeLeft == 0L
    }

    /** Starts the uptime countdown for the edition created at [createdAt] (minus the time already passed). */
    private fun startRhythm(createdAt: Long, nowMillis: Long) {
        val cd = rhythm ?: return
        cd.start(maxOf(0L, ZenFeedPlanner.EDITION_INTERVAL_MS - maxOf(0L, nowMillis - createdAt)))
        store.put(KEY_RHYTHM_FOR to createdAt)
    }

    /**
     * Uptime still to wait for [edition]. A missing countdown (state from before the countdown
     * existed, or lost) is started once with the remaining wall-clock interval (fail-closed: it can
     * never be shorter than that).
     */
    private fun rhythmRemaining(edition: Edition, nowMillis: Long): Long {
        val cd = rhythm ?: return 0L
        if (store.getLong(KEY_RHYTHM_FOR, -1) != edition.createdAt) {
            startRhythm(edition.createdAt, nowMillis)
        }
        return cd.remainingMs()
    }

    private fun createEdition(
        now: Long,
        createdAt: Long,
        day: String,
        previous: Edition?,
        rules: ZenFeedPlanner.ContentRules,
        candidates: () -> List<ZenFeedPlanner.Candidate>
    ): Edition? {
        val handled = handled()
        val counted = counted()
        val sel = ZenFeedPlanner.select(
            candidates().filter { it.url !in handled },
            now,
            ZenSettings.feedBudget(),
            counted,
            maxOf(0, ZenSettings.dailyCap() - counted.size),
            rules
        )
        // no data yet: do not seal an empty edition, retry on the next call
        if (sel.picks.isEmpty()) return previous
        val e = Edition(createdAt, day, sel.picks, sel.eligibleCount - sel.picks.size)
        save(e)
        val all = (counted.toList() + e.picks.map { it.url }).distinct()
        store.put(KEY_DAY_URLS to all.joinToString("\n"))
        val content = if (rules.active) {
            val why = e.picks.map { it.why }
            " weighting=${rules.weighting} heavyLimit=${rules.heavyLimit} unclassified=${why.count { it == ZenFeedPlanner.WHY_UNCLASSIFIED }}" +
                " heavy=${why.count { it?.split('|')?.getOrNull(1) == "belastend" }}"
        } else {
            ""
        }
        log?.add("edition", "n=${e.picks.size} leftover=${e.leftover} dayDistinct=${all.size}$content")
        return e
    }

    /**
     * Replaces the stored "why" of picks of the edition created at [createdAt] (more precise
     * classification found later). Order, selection and everything else stay as they are; a newer
     * edition is never touched. Returns the number of changed picks.
     */
    @Synchronized
    fun updateWhy(createdAt: Long, whys: Map<String, String>): Int {
        val e = load() ?: return 0
        if (e.createdAt != createdAt || whys.isEmpty()) return 0
        var changed = 0
        val picks = e.picks.map { p ->
            val w = whys[p.url]
            if (w != null && p.why != null && w != p.why) {
                changed++
                p.copy(why = w)
            } else {
                p
            }
        }
        if (changed > 0) save(e.copy(picks = picks))
        return changed
    }

    @Synchronized
    fun markHandled(url: String, reason: String) {
        val list = handledList() + url
        store.put(KEY_HANDLED to list.takeLast(MAX_HANDLED).joinToString("\n"))
        log?.add(reason, url)
    }

    /** The stored current edition (read only; for the debug export of the classifications). */
    @Synchronized
    fun current(): Edition? = load()

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
                "${it.url}\t${it.minutes}\t${(it.groupName ?: "").replace('\t', ' ').replace('\n', ' ')}" +
                    (it.why?.let { w -> "\t" + w.replace('\t', ' ').replace('\n', ' ') } ?: "")
            }
        )
    }

    private fun load(): Edition? {
        val at = store.getLong(KEY_AT, -1)
        if (at < 0) return null
        val picks = lines(KEY_PICKS).mapNotNull {
            val p = it.split('\t', limit = 4)
            if (p[0].isEmpty()) {
                null
            } else {
                ZenFeedPlanner.Pick(
                    p[0],
                    p.getOrNull(1)?.toIntOrNull() ?: 0,
                    p.getOrNull(2)?.ifEmpty { null },
                    p.getOrNull(3)?.ifEmpty { null }
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
        private const val KEY_RHYTHM_FOR = "feed_rhythm_for"
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

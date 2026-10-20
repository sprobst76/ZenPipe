package org.schabi.newpipe.zen

import android.content.Context

/**
 * A "practice" channel or (remote) playlist (yoga, breathwork, meditation, sleep aids), maintained by
 * the user (see zen-docs/spec-praxis.md). The assignment is per channel/playlist, never per video,
 * and never automatic. [playlist] = a playlist (its videos are practice, see [ZenPraxisPlaylistItems]).
 * Identity is [key] = service id + normalised channel URL, so http/https, "www."/"m." and a
 * trailing slash do not matter.
 */
data class ZenPraxisChannel(val serviceId: Int, val url: String, val name: String, val playlist: Boolean = false) {
    val key: String get() = keyOf(serviceId, url)

    companion object {
        @JvmStatic
        fun keyOf(serviceId: Int, url: String): String = "$serviceId:${normalize(url)}"

        /** Scheme and "www."/"m." removed, host lower-cased (channel ids are case-sensitive), no trailing slash. */
        @JvmStatic
        fun normalize(url: String): String {
            var s = url.trim()
            s = s.substringAfter("://", s)
            val slash = s.indexOf('/')
            var host = if (slash >= 0) s.substring(0, slash) else s
            val path = if (slash >= 0) s.substring(slash) else ""
            host = host.lowercase().removePrefix("www.").removePrefix("m.")
            return (host + path).trimEnd('/')
        }

        private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ')

        @JvmStatic
        fun encodeList(list: List<ZenPraxisChannel>): String = list.joinToString("\n") {
            "${it.serviceId}\t${clean(it.url)}\t${clean(it.name)}" + if (it.playlist) "\tp" else ""
        }

        @JvmStatic
        fun decodeList(raw: String?): List<ZenPraxisChannel> = (raw ?: "").split('\n').filter { it.isNotEmpty() }.mapNotNull {
            val p = it.split('\t', limit = 4)
            val id = p.getOrNull(0)?.toIntOrNull()
            val url = p.getOrNull(1)
            if (id == null || url.isNullOrEmpty()) null else ZenPraxisChannel(id, url, p.getOrElse(2) { "" }, p.getOrNull(3) == "p")
        }
    }
}

/**
 * Practice minutes per Zen day (04:00 rollover), kept for [KEEP_DAYS] days. No limit is attached
 * (decision); a later optional soft limit would read [msOn] (hook, see spec-praxis.md).
 * Since the playing time per day ([ZenWatchTime]) the practice time is booked continuously there
 * (picture and sound 1:1); `px_time` holds only what older versions booked at the session end, and
 * [msOn] returns the sum of both.
 */
class ZenPraxisTime(private val store: ZenKeyValueStore) {
    @Synchronized
    fun add(day: String, ms: Long) {
        if (ms <= 0) return
        val map = read()
        map[day] = (map[day] ?: 0L) + ms
        val kept = map.entries.sortedBy { it.key }.takeLast(KEEP_DAYS)
        store.put(KEY to kept.joinToString("\n") { "${it.key}\t${it.value}" })
    }

    @Synchronized
    fun msOn(day: String): Long = (read()[day] ?: 0L) + ZenWatchTime(store).get(day).praxisMs

    private fun read(): LinkedHashMap<String, Long> {
        val m = LinkedHashMap<String, Long>()
        (store.getString(KEY, "") ?: "").split('\n').filter { it.isNotEmpty() }.forEach {
            val p = it.split('\t')
            val v = p.getOrNull(1)?.toLongOrNull()
            if (p[0].isNotEmpty() && v != null) m[p[0]] = v
        }
        return m
    }

    companion object {
        const val KEEP_DAYS = 30
        private const val KEY = "px_time"
    }
}

/**
 * Which videos belong to the active practice playlists (D63: the content is frozen). A playlist's
 * videos are only known once it was loaded (practice tab, playlist page). What is known before the
 * playlist becomes active (loads while it is pending, the page it was marked on, first-time setup)
 * and what older versions stored is the frozen base and counts at once. A video that shows up in a
 * load of an already ACTIVE playlist is new: it is remembered with the observed device uptime
 * ([ZenUptime]) and only counts after [NEW_ITEM_WAIT_MS] of uptime (fail-closed like the other
 * locks; it does not start or need the settings lock). Loads only add (at most [MAX_PER_PLAYLIST]
 * per playlist), so a refilled playlist never replaces its base. Entries of playlists that are no
 * longer active are ignored (removing a playlist applies at once). Only extractor data of the
 * playlist itself is registered, so enqueueing another video into a practice queue does not make
 * it practice.
 *
 * Stored in `px_pl_items`, one line per video: `playlistKey<TAB>streamKey` (base, also the format
 * of older versions) or `playlistKey<TAB>streamKey<TAB>seenAtUptimeMs` (new, waiting).
 */
class ZenPraxisPlaylistItems(
    private val store: ZenKeyValueStore,
    /** Running device uptime ([ZenUptime.totalMs]). */
    private val uptime: () -> Long
) {
    private data class Entry(val playlist: String, val stream: String, val seenAt: Long) {
        val base get() = seenAt < 0
    }

    /**
     * A load of [playlistKey] delivered [streams]. Unknown videos are added: as frozen base if
     * [base] (the playlist is not active yet, or first-time setup), otherwise as new videos that
     * wait [NEW_ITEM_WAIT_MS]. With [base] a waiting video of the playlist joins the base.
     */
    @Synchronized
    fun register(playlistKey: String, streams: List<Pair<Int, String>>, base: Boolean) {
        val all = read().toMutableList()
        val known = all.withIndex().filter { it.value.playlist == playlistKey }.associate { it.value.stream to it.index }
        var count = known.size
        var changed = false
        var now = -1L
        for (key in streams.map { ZenPraxisChannel.keyOf(it.first, it.second) }.distinct()) {
            val i = known[key]
            if (i != null) {
                if (base && !all[i].base) {
                    all[i] = all[i].copy(seenAt = -1)
                    changed = true
                }
                continue
            }
            if (count >= MAX_PER_PLAYLIST) continue
            if (!base && now < 0) now = uptime()
            all.add(Entry(playlistKey, key, if (base) -1 else now))
            count++
            changed = true
        }
        if (changed) write(all)
    }

    @Synchronized
    fun contains(serviceId: Int, streamUrl: String?, activePlaylistKeys: Set<String>): Boolean {
        if (streamUrl.isNullOrEmpty() || activePlaylistKeys.isEmpty()) return false
        val key = ZenPraxisChannel.keyOf(serviceId, streamUrl)
        val matches = read().filter { it.stream == key && it.playlist in activePlaylistKeys }
        if (matches.any { it.base }) return true
        if (matches.isEmpty()) return false
        val now = uptime()
        return matches.any { ready(it, now) }
    }

    /** Stream keys of the videos that count as practice (base or waited long enough). */
    @Synchronized
    fun streamKeys(activePlaylistKeys: Set<String>): Set<String> {
        val active = read().filter { it.playlist in activePlaylistKeys }
        val now = if (active.all { it.base }) 0 else uptime()
        return active.filter { ready(it, now) }.map { it.stream }.toHashSet()
    }

    /** New videos of active playlists that still wait. */
    data class Waiting(val count: Int, val nextReadyMs: Long, val streamKeys: Set<String>)

    /** Waiting new videos of [activePlaylistKeys]; null if there are none. */
    @Synchronized
    fun waiting(activePlaylistKeys: Set<String>): Waiting? {
        val candidates = read().filter { it.playlist in activePlaylistKeys && !it.base }
        if (candidates.isEmpty()) return null
        val now = uptime()
        val left = candidates.filter { !ready(it, now) }
        if (left.isEmpty()) return null
        return Waiting(
            left.map { it.stream }.distinct().size,
            left.minOf { NEW_ITEM_WAIT_MS - (now - it.seenAt) },
            left.map { it.stream }.toHashSet()
        )
    }

    /** Drops the sets of playlists that are no longer active or pending. */
    @Synchronized
    fun prune(knownPlaylistKeys: Set<String>) {
        val all = read()
        val kept = all.filter { it.playlist in knownPlaylistKeys }
        if (kept.size != all.size) write(kept)
    }

    private fun ready(e: Entry, now: Long) = e.base || now - e.seenAt >= NEW_ITEM_WAIT_MS

    private fun write(all: List<Entry>) {
        store.put(KEY to if (all.isEmpty()) null else all.joinToString("\n") { "${it.playlist}\t${it.stream}" + if (it.base) "" else "\t${it.seenAt}" })
    }

    private fun read(): List<Entry> = (store.getString(KEY, "") ?: "").split('\n').filter { it.isNotEmpty() }.mapNotNull {
        val p = it.split('\t', limit = 3)
        when {
            p.size < 2 -> null

            // older versions: no third field = frozen base; an unreadable time is strict (waits from 0)
            p.size == 2 -> Entry(p[0], p[1], -1)

            else -> Entry(p[0], p[1], p[2].toLongOrNull()?.coerceAtLeast(0) ?: 0)
        }
    }

    companion object {
        const val MAX_PER_PLAYLIST = 200

        /** A video that appears in an active practice playlist counts after this much device uptime. */
        const val NEW_ITEM_WAIT_MS = 24 * 60 * 60 * 1000L
        private const val KEY = "px_pl_items"
    }
}

/** Process-wide access to the practice channels and playlists (they live in [ZenSettingsLock]). */
object ZenPraxis {
    /** Newest videos shown per practice channel in the practice tab (no paging). */
    const val MAX_PER_CHANNEL = 15

    enum class State { NONE, PENDING, ACTIVE }

    /** Keys of the ACTIVE practice channels (read once per decision, e.g. per feed edition). */
    @JvmStatic
    fun activeKeys(): Set<String> = ZenSettings.lock()?.praxisChannels()?.map { it.key }?.toHashSet() ?: emptySet()

    /** Keys of the ACTIVE practice playlists. */
    @JvmStatic
    fun activePlaylistKeys(): Set<String> = ZenSettings.lock()?.praxisChannels()?.filter { it.playlist }?.map { it.key }?.toHashSet() ?: emptySet()

    @JvmStatic
    fun isPraxis(serviceId: Int, channelUrl: String?): Boolean = !channelUrl.isNullOrEmpty() && ZenPraxisChannel.keyOf(serviceId, channelUrl) in activeKeys()

    @Volatile
    private var items: ZenPraxisPlaylistItems? = null

    @JvmStatic
    fun playlistItems(context: Context): ZenPraxisPlaylistItems = items ?: synchronized(this) {
        items ?: run {
            val app = context.applicationContext
            val store = Zen.store(app)
            val uptime = ZenUptime(store, "praxis", bootId = { Zen.bootCount(app) })
            ZenPraxisPlaylistItems(store) { uptime.totalMs() }
        }.also { items = it }
    }

    /** For tests: use [i] (null = no playlist items known). Set up by [ZenSettings.init] in the app. */
    @JvmStatic
    fun installPlaylistItems(i: ZenPraxisPlaylistItems?) {
        items = i
    }

    /**
     * Whether a stream counts as practice: its uploader is an active practice channel, or it is a
     * known video of an active practice playlist. Only the user's assignment counts, never
     * title/keywords (no heuristic, no bypass via titles).
     */
    @JvmStatic
    fun isPraxisStream(serviceId: Int, streamUrl: String?, uploaderUrl: String?): Boolean = isPraxis(serviceId, uploaderUrl) || (items?.contains(serviceId, streamUrl, activePlaylistKeys()) ?: false)

    /** Stream keys of the known videos of all active practice playlists (e.g. once per feed edition). */
    @JvmStatic
    fun activePlaylistStreamKeys(): Set<String> = items?.streamKeys(activePlaylistKeys()) ?: emptySet()

    /** Keys of the PENDING practice playlists (their loads are the frozen base). */
    @JvmStatic
    fun pendingPlaylistKeys(): Set<String> = ZenSettings.lock()?.pendingPraxis()?.filter { it.playlist }?.map { it.key }?.toHashSet() ?: emptySet()

    /** New videos of active practice playlists that do not count yet (D63), null if none. */
    @JvmStatic
    fun waitingPlaylistItems(): ZenPraxisPlaylistItems.Waiting? = items?.waiting(activePlaylistKeys())

    /**
     * A practice playlist (page or practice tab) was loaded: remember which videos belong to it.
     * While the playlist is pending (or during first-time setup) the videos are its frozen base;
     * a video that is new in an ACTIVE playlist counts only after 24 h of device uptime (D63).
     */
    @JvmStatic
    fun registerPlaylistStreams(context: Context, serviceId: Int, playlistUrl: String, streams: List<Pair<Int, String>>) = registerInto(playlistItems(context), serviceId, playlistUrl, streams)

    /** [registerPlaylistStreams] into [target]: base or new is decided by the playlist's state. */
    @JvmStatic
    fun registerInto(target: ZenPraxisPlaylistItems, serviceId: Int, playlistUrl: String, streams: List<Pair<Int, String>>) {
        val key = ZenPraxisChannel.keyOf(serviceId, playlistUrl)
        val base = when (key) {
            in activePlaylistKeys() -> ZenSettings.lock()?.isSetupMode() == true
            in pendingPlaylistKeys() -> true
            else -> return
        }
        target.register(key, streams, base)
    }

    /**
     * Java helper for [registerPlaylistStreams] with extractor items ([append] = a further page;
     * loads only ever add, so it makes no difference any more).
     */
    @JvmStatic
    @Suppress("UNUSED_PARAMETER")
    fun registerPlaylistItems(
        context: Context,
        serviceId: Int,
        playlistUrl: String,
        items: List<org.schabi.newpipe.extractor.InfoItem>,
        append: Boolean
    ) {
        val streams = items.filterIsInstance<org.schabi.newpipe.extractor.stream.StreamInfoItem>().map { it.serviceId to it.url }
        registerPlaylistStreams(context, serviceId, playlistUrl, streams)
    }

    @JvmStatic
    fun state(context: Context, serviceId: Int, channelUrl: String): State {
        val lock = Zen.settingsLock(context)
        val key = ZenPraxisChannel.keyOf(serviceId, channelUrl)
        return when {
            lock.praxisChannels().any { it.key == key } -> State.ACTIVE
            lock.pendingPraxis().any { it.key == key } -> State.PENDING
            else -> State.NONE
        }
    }

    @JvmStatic
    fun todayMs(context: Context): Long {
        val store = Zen.store(context)
        val day = ZenDay.dayOf(Zen.clock(context).now().trustedMillis, ZenZone.fixed(store)).toString()
        return ZenPraxisTime(store).msOn(day)
    }
}

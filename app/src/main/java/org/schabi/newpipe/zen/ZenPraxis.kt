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
    fun msOn(day: String): Long = read()[day] ?: 0L

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
 * Which videos belong to the active practice playlists. A playlist's videos are only known once it
 * was loaded (practice tab, playlist page); each load replaces that playlist's set (at most
 * [MAX_PER_PLAYLIST]). Entries of playlists that are no longer active are ignored (removing a
 * playlist applies at once). Only extractor data of the playlist itself is registered, so
 * enqueueing another video into a practice queue does not make it practice.
 */
class ZenPraxisPlaylistItems(private val store: ZenKeyValueStore) {
    @Synchronized
    fun register(playlistKey: String, streams: List<Pair<Int, String>>) {
        val keep = read().filter { it.first != playlistKey }
        val add = streams.take(MAX_PER_PLAYLIST).map { playlistKey to ZenPraxisChannel.keyOf(it.first, it.second) }.distinct()
        val all = keep + add
        store.put(KEY to if (all.isEmpty()) null else all.joinToString("\n") { "${it.first}\t${it.second}" })
    }

    @Synchronized
    fun contains(serviceId: Int, streamUrl: String?, activePlaylistKeys: Set<String>): Boolean {
        if (streamUrl.isNullOrEmpty() || activePlaylistKeys.isEmpty()) return false
        val key = ZenPraxisChannel.keyOf(serviceId, streamUrl)
        return read().any { it.second == key && it.first in activePlaylistKeys }
    }

    @Synchronized
    fun streamKeys(activePlaylistKeys: Set<String>): Set<String> = read().filter { it.first in activePlaylistKeys }.map { it.second }.toHashSet()

    /** Drops the sets of playlists that are no longer active or pending. */
    @Synchronized
    fun prune(knownPlaylistKeys: Set<String>) {
        val all = read()
        val kept = all.filter { it.first in knownPlaylistKeys }
        if (kept.size != all.size) store.put(KEY to if (kept.isEmpty()) null else kept.joinToString("\n") { "${it.first}\t${it.second}" })
    }

    private fun read(): List<Pair<String, String>> = (store.getString(KEY, "") ?: "").split('\n').filter { it.isNotEmpty() }.mapNotNull {
        val p = it.split('\t', limit = 2)
        if (p.size == 2) p[0] to p[1] else null
    }

    companion object {
        const val MAX_PER_PLAYLIST = 200
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
        items ?: ZenPraxisPlaylistItems(Zen.store(context)).also { items = it }
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

    /** A practice playlist (page or practice tab) was loaded: remember which videos belong to it. */
    @JvmStatic
    fun registerPlaylistStreams(context: Context, serviceId: Int, playlistUrl: String, streams: List<Pair<Int, String>>) {
        val key = ZenPraxisChannel.keyOf(serviceId, playlistUrl)
        if (key !in activePlaylistKeys()) return
        playlistItems(context).register(key, streams)
    }

    /** Java helper for [registerPlaylistStreams] with extractor items. */
    @JvmStatic
    fun registerPlaylistItems(
        context: Context,
        serviceId: Int,
        playlistUrl: String,
        items: List<org.schabi.newpipe.extractor.InfoItem>,
        append: Boolean
    ) {
        val key = ZenPraxisChannel.keyOf(serviceId, playlistUrl)
        if (key !in activePlaylistKeys()) return
        val store = playlistItems(context)
        val new = items.filterIsInstance<org.schabi.newpipe.extractor.stream.StreamInfoItem>().map { it.serviceId to it.url }
        if (append) {
            // keep what is known, add the next page
            val known = store.streamKeys(setOf(key))
            store.register(key, known.map { k -> serviceId to k.substringAfter(':') } + new)
        } else {
            store.register(key, new)
        }
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
